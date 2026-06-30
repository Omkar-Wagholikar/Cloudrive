package queue

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
)

const ThumbnailTopic = "thumb-jobs"

// ThumbnailJob is the payload pushed to the queue after an image upload.
type ThumbnailJob struct {
	FileID   int64  `json:"file_id"`
	MimeType string `json:"mime_type"`
}

// KafkaQueue wraps a franz-go client for produce and consume.
type KafkaQueue struct {
	client *kgo.Client
}

// KafkaCerts holds the three PEM file paths required by Aiven mTLS.
type KafkaCerts struct {
	AccessKeyFile  string // path to service.key
	AccessCertFile string // path to service.cert
	CACertFile     string // path to ca.pem
}

func NewKafkaQueue(brokers []string, certs KafkaCerts) (*KafkaQueue, error) {
	tlsCfg, err := buildTLS(certs)
	if err != nil {
		return nil, fmt.Errorf("kafka tls: %w", err)
	}

	client, err := kgo.NewClient(
		kgo.SeedBrokers(brokers...),
		kgo.DialTLSConfig(tlsCfg),

		// Consumer settings
		kgo.ConsumerGroup("thumb-workers"),
		kgo.ConsumeTopics(ThumbnailTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()), // resume from committed offset

		// Producer settings – wait for leader ack only (faster, safe for this use case)
		// kgo.RequiredAcks(kgo.LeaderAck()),
		kgo.ProduceRequestTimeout(20*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("kafka client: %w", err)
	}

	// Verify connectivity with a quick metadata ping
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := client.Ping(ctx); err != nil {
		client.Close()
		return nil, fmt.Errorf("kafka ping: %w", err)
	}

	log.Printf("[kafka] connected to %v", brokers)

	return &KafkaQueue{client: client}, nil
}

// Publish serialises job and produces it to ThumbnailTopic.
// It is synchronous — it blocks until the broker acknowledges.
func (q *KafkaQueue) Publish(ctx context.Context, job ThumbnailJob) error {
	payload, err := json.Marshal(job)
	if err != nil {
		return err
	}

	results := q.client.ProduceSync(ctx, &kgo.Record{
		Topic: ThumbnailTopic,
		Value: payload,
	})
	return results.FirstErr()
}

// ReadBatch polls for up to maxCount records, blocking at most blockDur.
// Returns nil, nil on a clean timeout (no messages).
func (q *KafkaQueue) ReadBatch(ctx context.Context, maxCount int, blockDur time.Duration) ([]*kgo.Record, error) {
	pollCtx, cancel := context.WithTimeout(ctx, blockDur)
	defer cancel()

	fetches := q.client.PollRecords(pollCtx, maxCount)
	if fetches.IsClientClosed() {
		return nil, fmt.Errorf("kafka client closed")
	}

	// Timeout with no records is not an error
	if fetches.Empty() {
		return nil, nil
	}

	if err := fetches.Errors(); len(err) > 0 {
		return nil, fmt.Errorf("kafka fetch: %v", err[0])
	}

	var records []*kgo.Record
	fetches.EachRecord(func(r *kgo.Record) {
		records = append(records, r)
	})
	return records, nil
}

// CommitBatch marks all records in the slice as processed.
// Call this only after successfully handling each record.
func (q *KafkaQueue) CommitBatch(ctx context.Context, records []*kgo.Record) error {
	return q.client.CommitRecords(ctx, records...)
}

// ParseJob deserialises a Kafka record value into a ThumbnailJob.
func ParseJob(r *kgo.Record) (ThumbnailJob, error) {
	var j ThumbnailJob
	return j, json.Unmarshal(r.Value, &j)
}

// Close shuts down the Kafka client cleanly.
func (q *KafkaQueue) Close() {
	q.client.Close()
}

// buildTLS constructs an mTLS config from the three Aiven certificate files.
func buildTLS(certs KafkaCerts) (*tls.Config, error) {
	// Load client cert + key (mTLS)
	clientCert, err := tls.LoadX509KeyPair(certs.AccessCertFile, certs.AccessKeyFile)
	if err != nil {
		return nil, fmt.Errorf("load client cert/key: %w", err)
	}

	// Load CA cert
	caPEM, err := os.ReadFile(certs.CACertFile)
	if err != nil {
		return nil, fmt.Errorf("read CA cert: %w", err)
	}
	caPool := x509.NewCertPool()
	if !caPool.AppendCertsFromPEM(caPEM) {
		return nil, fmt.Errorf("failed to parse CA cert")
	}

	return &tls.Config{
		Certificates: []tls.Certificate{clientCert},
		RootCAs:      caPool,
		MinVersion:   tls.VersionTLS12,
	}, nil
}
