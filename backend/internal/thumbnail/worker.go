package thumbnail

import (
	"bytes"
	"context"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/gif"
	_ "image/png"
	"log"
	"strings"
	"time"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"cloudrive/internal/database"
	"cloudrive/internal/queue"
	"cloudrive/internal/seaweed"
)

const (
	// Thumbnail is capped at this dimension on the longer side.
	// 240px keeps files tiny and is plenty for a grid view.
	MaxThumbDim = 240

	// JPEG quality for thumbnails – aggressive compression to save space.
	ThumbQuality = 55
)

type Worker struct {
	db     *database.DB
	q      *queue.KafkaQueue
	master string
	stop   chan struct{}
}

func NewWorker(db *database.DB, q *queue.KafkaQueue, master string) *Worker {
	return &Worker{db: db, q: q, master: master, stop: make(chan struct{})}
}

// Start launches the background processing loop.
func (w *Worker) Start() {
	go w.loop()
}

// Stop signals the worker to exit cleanly.
func (w *Worker) Stop() {
	close(w.stop)
}

func (w *Worker) loop() {
	log.Println("[thumb-worker] started")
	for {
		select {
		case <-w.stop:
			log.Println("[thumb-worker] stopped")
			return
		default:
		}

		ctx := context.Background()
		records, err := w.q.ReadBatch(ctx, 5, 10*time.Second)
		if err != nil {
			// Client was closed cleanly during shutdown — exit quietly
			select {
			case <-w.stop:
				log.Println("[thumb-worker] stopped")
				return
			default:
			}
			log.Printf("[thumb-worker] read error: %v – retrying in 5s", err)
			time.Sleep(5 * time.Second)
			continue
		}

		for _, rec := range records {
			job, err := queue.ParseJob(rec)
			if err != nil {
				log.Printf("[thumb-worker] bad message offset %d: %v", rec.Offset, err)
				continue
			}

			if err := w.process(ctx, job); err != nil {
				log.Printf("[thumb-worker] process file %d: %v", job.FileID, err)
				_ = w.db.SetThumbnail(job.FileID, "", database.ThumbFailed)
			}
		}

		// Commit the whole batch after processing – at-least-once delivery
		if len(records) > 0 {
			if err := w.q.CommitBatch(ctx, records); err != nil {
				log.Printf("[thumb-worker] commit error: %v", err)
			}
		}
	}
}

func (w *Worker) process(ctx context.Context, job queue.ThumbnailJob) error {
	if !isImage(job.MimeType) {
		// Not an image – mark done with no thumb FID (caller checks mime)
		return w.db.SetThumbnail(job.FileID, "", database.ThumbFailed)
	}

	// Load file record to get the SeaweedFS FID
	f, err := w.db.GetFileByID(job.FileID)
	if err != nil {
		return fmt.Errorf("get file: %w", err)
	}

	// Stream original from SeaweedFS
	rc, _, err := seaweed.Download(w.master, f.SeaweedFID)
	if err != nil {
		return fmt.Errorf("download original: %w", err)
	}
	defer rc.Close()

	// Decode image – stdlib supports jpeg/png/gif; webp via x/image
	img, _, err := image.Decode(rc)
	if err != nil {
		return fmt.Errorf("decode image: %w", err)
	}

	thumb := resizeDown(img, MaxThumbDim)

	// Encode as JPEG (always – regardless of source format)
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, thumb, &jpeg.Options{Quality: ThumbQuality}); err != nil {
		return fmt.Errorf("jpeg encode: %w", err)
	}

	// Upload thumbnail to SeaweedFS
	thumbFID, err := seaweed.Upload(w.master, fmt.Sprintf("thumb_%d.jpg", job.FileID),
		"image/jpeg", &buf)
	if err != nil {
		return fmt.Errorf("upload thumb: %w", err)
	}

	return w.db.SetThumbnail(job.FileID, thumbFID, database.ThumbReady)
}

// resizeDown returns a new image scaled so the larger dimension equals maxDim.
// If the image is already smaller, it's returned unchanged.
// Uses draw.BiLinear for good quality at low cost (no lanczos on a phone).
func resizeDown(src image.Image, maxDim int) image.Image {
	bounds := src.Bounds()
	w, h := bounds.Dx(), bounds.Dy()

	if w <= maxDim && h <= maxDim {
		return src
	}

	var newW, newH int
	if w >= h {
		newW = maxDim
		newH = h * maxDim / w
	} else {
		newH = maxDim
		newW = w * maxDim / h
	}
	if newH < 1 {
		newH = 1
	}
	if newW < 1 {
		newW = 1
	}

	dst := image.NewRGBA(image.Rect(0, 0, newW, newH))
	draw.BiLinear.Scale(dst, dst.Bounds(), src, bounds, draw.Over, nil)
	return dst
}

func isImage(mime string) bool {
	return strings.HasPrefix(mime, "image/")
}
