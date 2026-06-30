package files

import (
	"context"
	"database/sql"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"cloudrive/internal/auth"
	"cloudrive/internal/database"
	"cloudrive/internal/queue"
	"cloudrive/internal/seaweed"
)

// ── Auth handlers ────────────────────────────────────────────────────────────

func RegisterHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var body struct {
			Username string `json:"username" binding:"required,min=3,max=64"`
			Password string `json:"password" binding:"required,min=4,max=72"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		hash, err := auth.HashPassword(body.Password)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "hash failed"})
			return
		}

		user, err := db.CreateUser(body.Username, hash)
		if err != nil {
			if strings.Contains(err.Error(), "UNIQUE") {
				c.JSON(http.StatusConflict, gin.H{"error": "username already taken"})
				return
			}
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		c.JSON(http.StatusCreated, gin.H{"message": "user created", "id": user.ID})
	}
}

func LoginHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var body struct {
			Username string `json:"username" binding:"required"`
			Password string `json:"password" binding:"required"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		user, err := db.GetUserByUsername(body.Username)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
			return
		}
		if !auth.VerifyPassword(body.Password, user.PasswordHash) {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid credentials"})
			return
		}

		accessToken, err := auth.CreateToken(user.ID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "token error"})
			return
		}

		refreshToken, err := auth.NewRefreshToken()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "token error"})
			return
		}
		if err := db.CreateRefreshSession(refreshToken, user.ID, time.Now().Add(auth.RefreshTokenTTL)); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "session error"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"access_token":  accessToken,
			"refresh_token": refreshToken,
			"token_type":    "bearer",
			"expires_in":    int(auth.AccessTokenTTL.Seconds()),
		})
	}
}

// RefreshHandler exchanges a valid refresh token for a new access token.
func RefreshHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var body struct {
			RefreshToken string `json:"refresh_token" binding:"required"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		session, err := db.GetRefreshSession(body.RefreshToken)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid or expired refresh token"})
			return
		}

		// Rotate: delete old token, issue new pair
		_ = db.DeleteRefreshSession(body.RefreshToken)

		accessToken, err := auth.CreateToken(session.UserID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "token error"})
			return
		}
		newRefresh, err := auth.NewRefreshToken()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "token error"})
			return
		}
		if err := db.CreateRefreshSession(newRefresh, session.UserID, time.Now().Add(auth.RefreshTokenTTL)); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "session error"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"access_token":  accessToken,
			"refresh_token": newRefresh,
			"token_type":    "bearer",
			"expires_in":    int(auth.AccessTokenTTL.Seconds()),
		})
	}
}

// LogoutHandler revokes the supplied refresh token.
func LogoutHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		var body struct {
			RefreshToken string `json:"refresh_token" binding:"required"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		_ = db.DeleteRefreshSession(body.RefreshToken)
		c.Status(http.StatusNoContent)
	}
}

// ── Me ───────────────────────────────────────────────────────────────────────

func MeHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)

		user, err := db.GetUserByID(userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		used, err := db.UsedBytes(userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"user_id":     user.ID,
			"username":    user.Username,
			"quota_bytes": user.QuotaBytes,
			"used_bytes":  used,
			"created_at":  user.CreatedAt,
		})
	}
}

// ── Network handler ───────────────────────────────────────────────────────────

func NetworkHandler(localAddrs []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"local_addresses": localAddrs})
	}
}

func buildURLs(c *gin.Context, path string, localAddrs []string) (url string, localURL string) {
	scheme := "http"
	if c.Request.TLS != nil {
		scheme = "https"
	}
	if proto := c.GetHeader("X-Forwarded-Proto"); proto != "" {
		scheme = proto
	}
	url = scheme + "://" + c.Request.Host + path
	if len(localAddrs) > 0 {
		localURL = "http://" + localAddrs[0] + path
	}
	return
}

// ── File handlers ─────────────────────────────────────────────────────────────

func UploadHandler(db *database.DB, q *queue.KafkaQueue, master string, localAddrs []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)

		fh, err := c.FormFile("file")
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "file field required"})
			return
		}

		// Quota check
		user, err := db.GetUserByID(userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		if user.QuotaBytes > 0 {
			used, err := db.UsedBytes(userID)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
				return
			}
			if used+fh.Size > user.QuotaBytes {
				c.JSON(http.StatusRequestEntityTooLarge, gin.H{
					"error":       "quota exceeded",
					"used_bytes":  used,
					"quota_bytes": user.QuotaBytes,
				})
				return
			}
		}

		// Optional folder placement
		var folderID *int64
		if fidStr := c.PostForm("folder_id"); fidStr != "" {
			fid64, err := strconv.ParseInt(fidStr, 10, 64)
			if err != nil {
				c.JSON(http.StatusBadRequest, gin.H{"error": "invalid folder_id"})
				return
			}
			if _, err := db.GetFolder(fid64, userID); err != nil {
				if err == sql.ErrNoRows {
					c.JSON(http.StatusNotFound, gin.H{"error": "folder not found"})
				} else {
					c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
				}
				return
			}
			folderID = &fid64
		}

		src, err := fh.Open()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "cannot open upload"})
			return
		}
		defer src.Close()

		mime := fh.Header.Get("Content-Type")
		if mime == "" {
			mime = "application/octet-stream"
		}

		fid, err := seaweed.Upload(master, fh.Filename, mime, src)
		if err != nil {
			c.JSON(http.StatusBadGateway, gin.H{"error": "storage error: " + err.Error()})
			return
		}

		f := &database.File{
			OwnerID:    userID,
			FolderID:   folderID,
			Filename:   fh.Filename,
			SeaweedFID: fid,
			MimeType:   mime,
			Size:       fh.Size,
		}
		if err := db.CreateFile(f); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		if isImage(mime) {
			fileID := f.ID
			filename := f.Filename
			go func() {
				pubCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
				defer cancel()
				if err := q.Publish(pubCtx, queue.ThumbnailJob{FileID: fileID, MimeType: mime}); err != nil {
					log.Printf("[upload] WARNING: failed to queue thumbnail for file %d: %v", fileID, err)
				} else {
					log.Printf("[upload] thumbnail job queued for file %d (%s)", fileID, filename)
				}
			}()
		}

		downloadPath := fmt.Sprintf("/files/%d/download", f.ID)
		url, localURL := buildURLs(c, downloadPath, localAddrs)
		resp := gin.H{
			"id":        f.ID,
			"filename":  f.Filename,
			"size":      f.Size,
			"mime_type": f.MimeType,
			"url":       url,
		}
		if localURL != "" {
			resp["local_url"] = localURL
		}
		c.JSON(http.StatusCreated, resp)
	}
}

// ── Resumable upload ──────────────────────────────────────────────────────────

// InitResumableHandler creates an upload session and returns its ID.
// The client must know the total file size upfront.
//
//	POST /uploads/resumable
//	Body: { "filename": "video.mp4", "size": 1073741824, "mime_type": "video/mp4", "folder_id": 3 }
//	→ 201 { "upload_id": "<uuid>", "offset": 0 }
func InitResumableHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)

		var body struct {
			Filename  string `json:"filename"  binding:"required"`
			Size      int64  `json:"size"       binding:"required,min=1"`
			MimeType  string `json:"mime_type"`
			FolderID  *int64 `json:"folder_id"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		if body.MimeType == "" {
			body.MimeType = "application/octet-stream"
		}

		// Quota check
		user, err := db.GetUserByID(userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		if user.QuotaBytes > 0 {
			used, _ := db.UsedBytes(userID)
			if used+body.Size > user.QuotaBytes {
				c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "quota exceeded"})
				return
			}
		}

		if body.FolderID != nil {
			if _, err := db.GetFolder(*body.FolderID, userID); err != nil {
				if err == sql.ErrNoRows {
					c.JSON(http.StatusNotFound, gin.H{"error": "folder not found"})
				} else {
					c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
				}
				return
			}
		}

		token, err := auth.NewShareToken() // 128-bit random ID reused for session IDs
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "id error"})
			return
		}

		tempPath := fmt.Sprintf("data/uploads/%s.tmp", token)
		if err := os.MkdirAll("data/uploads", 0755); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "storage error"})
			return
		}

		sess := &database.UploadSession{
			ID:        token,
			OwnerID:   userID,
			FolderID:  body.FolderID,
			Filename:  body.Filename,
			MimeType:  body.MimeType,
			TotalSize: body.Size,
			TempPath:  tempPath,
			ExpiresAt: time.Now().Add(24 * time.Hour),
		}
		if err := db.CreateUploadSession(sess); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		c.JSON(http.StatusCreated, gin.H{"upload_id": token, "offset": 0})
	}
}

// ResumeUploadHandler appends a chunk to an existing upload session.
//
//	PATCH /uploads/resumable/:upload_id
//	Headers: Content-Range: bytes 0-1048575/1073741824
//	Body: raw bytes
//	→ 200 { "offset": 1048576 }  (partial)
//	→ 201 { "id": 7, ... }       (complete)
func ResumeUploadHandler(db *database.DB, q *queue.KafkaQueue, master string, localAddrs []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		uploadID := c.Param("upload_id")

		sess, err := db.GetUploadSession(uploadID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "upload session not found or expired"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		// Parse Content-Range: bytes <start>-<end>/<total>
		cr := c.GetHeader("Content-Range")
		var rangeStart, rangeEnd, total int64
		if _, err := fmt.Sscanf(cr, "bytes %d-%d/%d", &rangeStart, &rangeEnd, &total); err != nil || rangeStart != sess.Offset {
			c.JSON(http.StatusBadRequest, gin.H{
				"error":  "invalid or out-of-order Content-Range",
				"offset": sess.Offset,
			})
			return
		}

		f, err := os.OpenFile(sess.TempPath, os.O_WRONLY|os.O_CREATE, 0644)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "storage error"})
			return
		}
		if _, err := f.Seek(rangeStart, io.SeekStart); err != nil {
			f.Close()
			c.JSON(http.StatusInternalServerError, gin.H{"error": "storage error"})
			return
		}
		written, err := io.Copy(f, c.Request.Body)
		f.Close()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "write error"})
			return
		}

		newOffset := rangeStart + written
		if err := db.UpdateUploadSessionOffset(uploadID, newOffset); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		// Not yet complete
		if newOffset < sess.TotalSize {
			c.JSON(http.StatusOK, gin.H{"offset": newOffset})
			return
		}

		// Upload complete — stream temp file to SeaweedFS
		tmpFile, err := os.Open(sess.TempPath)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "storage error"})
			return
		}
		fid, err := seaweed.Upload(master, sess.Filename, sess.MimeType, tmpFile)
		tmpFile.Close()
		os.Remove(sess.TempPath)
		_ = db.DeleteUploadSession(uploadID)

		if err != nil {
			c.JSON(http.StatusBadGateway, gin.H{"error": "storage error: " + err.Error()})
			return
		}

		file := &database.File{
			OwnerID:    userID,
			FolderID:   sess.FolderID,
			Filename:   sess.Filename,
			SeaweedFID: fid,
			MimeType:   sess.MimeType,
			Size:       sess.TotalSize,
		}
		if err := db.CreateFile(file); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		if isImage(sess.MimeType) {
			fileID := file.ID
			go func() {
				pubCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
				defer cancel()
				_ = q.Publish(pubCtx, queue.ThumbnailJob{FileID: fileID, MimeType: sess.MimeType})
			}()
		}

		downloadPath := fmt.Sprintf("/files/%d/download", file.ID)
		url, localURL := buildURLs(c, downloadPath, localAddrs)
		resp := gin.H{
			"id":        file.ID,
			"filename":  file.Filename,
			"size":      file.Size,
			"mime_type": file.MimeType,
			"url":       url,
		}
		if localURL != "" {
			resp["local_url"] = localURL
		}
		c.JSON(http.StatusCreated, resp)
	}
}

// UploadStatusHandler returns the current byte offset for a resumable session.
//
//	GET /uploads/resumable/:upload_id
//	→ 200 { "upload_id": "...", "offset": 1048576, "total_size": 1073741824 }
func UploadStatusHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		sess, err := db.GetUploadSession(c.Param("upload_id"), userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}
		c.JSON(http.StatusOK, gin.H{
			"upload_id":  sess.ID,
			"offset":     sess.Offset,
			"total_size": sess.TotalSize,
			"expires_at": sess.ExpiresAt,
		})
	}
}

// ── File info / download / delete / patch ────────────────────────────────────

func FileInfoHandler(db *database.DB, localAddrs []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.GetFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		downloadPath := fmt.Sprintf("/files/%d/download", f.ID)
		url, localURL := buildURLs(c, downloadPath, localAddrs)
		resp := gin.H{
			"id":          f.ID,
			"filename":    f.Filename,
			"size":        f.Size,
			"mime_type":   f.MimeType,
			"folder_id":   f.FolderID,
			"thumb_ready": int(f.ThumbReady),
			"created_at":  f.CreatedAt,
			"url":         url,
		}
		if localURL != "" {
			resp["local_url"] = localURL
		}
		c.JSON(http.StatusOK, resp)
	}
}

func DownloadHandler(db *database.DB, master string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.GetFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		streamFile(c, master, f.SeaweedFID, f.Filename, f.MimeType)
	}
}

// PatchFileHandler renames and/or moves a file to another folder.
//
//	PATCH /files/:id
//	Body: { "filename": "new-name.jpg", "folder_id": 5 }
func PatchFileHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.GetFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		// Use a raw map so we can distinguish "field not sent" from "field set to null"
		var raw map[string]interface{}
		if err := c.ShouldBindJSON(&raw); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		newFilename := f.Filename
		if v, ok := raw["filename"]; ok {
			s, ok := v.(string)
			if !ok || strings.TrimSpace(s) == "" {
				c.JSON(http.StatusBadRequest, gin.H{"error": "filename must be a non-empty string"})
				return
			}
			newFilename = s
		}

		newFolderID := f.FolderID
		if _, ok := raw["folder_id"]; ok {
			if raw["folder_id"] == nil {
				newFolderID = nil // move to root
			} else {
				fid, ok := raw["folder_id"].(float64)
				if !ok {
					c.JSON(http.StatusBadRequest, gin.H{"error": "folder_id must be a number or null"})
					return
				}
				fid64 := int64(fid)
				if _, err := db.GetFolder(fid64, userID); err != nil {
					if err == sql.ErrNoRows {
						c.JSON(http.StatusNotFound, gin.H{"error": "folder not found"})
					} else {
						c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
					}
					return
				}
				newFolderID = &fid64
			}
		}

		if err := db.UpdateFile(fileID, userID, newFilename, newFolderID); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		f.Filename = newFilename
		f.FolderID = newFolderID
		c.JSON(http.StatusOK, fileItemDTO{
			ID:         f.ID,
			Filename:   f.Filename,
			Size:       f.Size,
			MimeType:   f.MimeType,
			FolderID:   f.FolderID,
			ThumbReady: int(f.ThumbReady),
			CreatedAt:  f.CreatedAt,
		})
	}
}

// DeleteFileHandler soft-deletes a file (moves it to trash).
func DeleteFileHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.SoftDeleteFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		c.JSON(http.StatusOK, gin.H{"id": f.ID, "deleted_at": f.DeletedAt})
	}
}

// ── Trash ─────────────────────────────────────────────────────────────────────

func ListTrashHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		page, limit := parsePage(c)

		fileList, total, err := db.ListTrashedFiles(userID, page, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"page": page, "limit": limit, "total": total, "items": fileItems(fileList)})
	}
}

// RestoreFileHandler moves a trashed file back to its original location.
func RestoreFileHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		if err := db.RestoreFile(fileID, userID); err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found in trash"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		c.JSON(http.StatusOK, gin.H{"id": fileID, "status": "restored"})
	}
}

// PurgeFileHandler permanently deletes a trashed file from SeaweedFS + DB.
func PurgeFileHandler(db *database.DB, master string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.GetTrashedFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found in trash"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		_ = seaweed.Delete(master, f.SeaweedFID)
		if f.ThumbFID != "" {
			_ = seaweed.Delete(master, f.ThumbFID)
		}
		if err := db.HardDeleteFile(fileID); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		c.Status(http.StatusNoContent)
	}
}

// ── Search ────────────────────────────────────────────────────────────────────

func SearchFilesHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		q := strings.TrimSpace(c.Query("q"))
		if q == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "q is required"})
			return
		}
		page, limit := parsePage(c)

		fileList, total, err := db.SearchFiles(userID, q, page, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"page": page, "limit": limit, "total": total, "items": fileItems(fileList)})
	}
}

// ── Shared links ──────────────────────────────────────────────────────────────

// CreateShareHandler generates a signed token that allows unauthenticated download.
//
//	POST /files/:id/share
//	Body: { "expires_in": 86400 }  (seconds, optional — omit for never-expiring)
//	→ 201 { "token": "...", "url": "http://.../shared/<token>" }
func CreateShareHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		// Ensure file exists and is owned by caller
		if _, err := db.GetFile(fileID, userID); err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		var body struct {
			ExpiresIn *int `json:"expires_in"` // seconds; nil = no expiry
		}
		_ = c.ShouldBindJSON(&body) // body is optional

		var expiresAt *time.Time
		if body.ExpiresIn != nil {
			t := time.Now().Add(time.Duration(*body.ExpiresIn) * time.Second)
			expiresAt = &t
		}

		token, err := auth.NewShareToken()
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "token error"})
			return
		}
		if err := db.CreateShareToken(token, fileID, userID, expiresAt); err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		scheme := "http"
		if c.Request.TLS != nil {
			scheme = "https"
		}
		shareURL := fmt.Sprintf("%s://%s/shared/%s", scheme, c.Request.Host, token)

		resp := gin.H{"token": token, "url": shareURL}
		if expiresAt != nil {
			resp["expires_at"] = expiresAt
		}
		c.JSON(http.StatusCreated, resp)
	}
}

// SharedDownloadHandler serves a file using a share token — no auth required.
func SharedDownloadHandler(db *database.DB, master string) gin.HandlerFunc {
	return func(c *gin.Context) {
		token := c.Param("token")
		f, err := db.GetFileByShareToken(token)
		if err != nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "not found or link expired"})
			return
		}
		streamFile(c, master, f.SeaweedFID, f.Filename, f.MimeType)
	}
}

// RevokeShareHandler deletes a share token.
func RevokeShareHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		token := c.Param("token")
		if err := db.DeleteShareToken(token, userID); err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}
		c.Status(http.StatusNoContent)
	}
}

// ── Thumbnail + listing ───────────────────────────────────────────────────────

func ThumbnailHandler(db *database.DB, master string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		fileID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		f, err := db.GetFile(fileID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		switch f.ThumbReady {
		case database.ThumbPending:
			c.JSON(http.StatusAccepted, gin.H{"status": "processing"})
			return
		case database.ThumbFailed:
			c.JSON(http.StatusUnprocessableEntity, gin.H{"status": "thumbnail_failed"})
			return
		}

		if f.ThumbFID == "" {
			c.JSON(http.StatusNotFound, gin.H{"error": "no thumbnail"})
			return
		}

		rc, ct, err := seaweed.Download(master, f.ThumbFID)
		if err != nil {
			c.JSON(http.StatusBadGateway, gin.H{"error": "storage error"})
			return
		}
		defer rc.Close()

		if ct == "" {
			ct = "image/jpeg"
		}
		c.Header("Content-Type", ct)
		c.Header("Cache-Control", "public, max-age=86400")
		io.Copy(c.Writer, rc) //nolint:errcheck
	}
}

func ListFilesHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		page, limit := parsePage(c)

		fileList, total, err := db.ListFiles(userID, page, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"page": page, "limit": limit, "total": total, "items": fileItems(fileList)})
	}
}

func ListThumbnailsHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		page, limit := parsePage(c)

		fileList, total, err := db.ListThumbnails(userID, page, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		c.JSON(http.StatusOK, gin.H{"page": page, "limit": limit, "total": total, "items": fileItems(fileList)})
	}
}

// ── Folder handlers ───────────────────────────────────────────────────────────

func CreateFolderHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)

		var body struct {
			Name     string `json:"name"      binding:"required,min=1,max=255"`
			ParentID *int64 `json:"parent_id"`
		}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		if body.ParentID != nil {
			if _, err := db.GetFolder(*body.ParentID, userID); err != nil {
				if err == sql.ErrNoRows {
					c.JSON(http.StatusNotFound, gin.H{"error": "parent folder not found"})
				} else {
					c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
				}
				return
			}
		}

		folder, err := db.CreateFolder(userID, body.ParentID, body.Name)
		if err != nil {
			if strings.Contains(err.Error(), "UNIQUE") {
				c.JSON(http.StatusConflict, gin.H{"error": "folder name already exists in this location"})
				return
			}
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		c.JSON(http.StatusCreated, folderJSON(folder))
	}
}

func ListFoldersHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		folders, err := db.ListRootFolders(userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}
		items := make([]gin.H, 0, len(folders))
		for _, f := range folders {
			items = append(items, folderJSON(f))
		}
		c.JSON(http.StatusOK, gin.H{"items": items})
	}
}

func GetFolderHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		folderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		folder, err := db.GetFolder(folderID, userID)
		if err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		subfolders, err := db.ListSubfolders(userID, folderID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		page, limit := parsePage(c)
		fileList, total, err := db.ListFolderFiles(userID, folderID, page, limit)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		ancestors, err := db.FolderAncestors(folderID, userID)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			return
		}

		subItems := make([]gin.H, 0, len(subfolders))
		for _, sf := range subfolders {
			subItems = append(subItems, folderJSON(sf))
		}
		breadcrumb := make([]gin.H, 0, len(ancestors))
		for _, a := range ancestors {
			breadcrumb = append(breadcrumb, gin.H{"id": a.ID, "name": a.Name})
		}

		c.JSON(http.StatusOK, gin.H{
			"folder":     folderJSON(folder),
			"breadcrumb": breadcrumb,
			"subfolders": subItems,
			"files":      gin.H{"page": page, "limit": limit, "total": total, "items": fileItems(fileList)},
		})
	}
}

func DeleteFolderHandler(db *database.DB) gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := auth.CurrentUserID(c)
		folderID, err := strconv.ParseInt(c.Param("id"), 10, 64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid id"})
			return
		}

		if err := db.DeleteFolder(folderID, userID); err != nil {
			if err == sql.ErrNoRows {
				c.JSON(http.StatusNotFound, gin.H{"error": "not found"})
			} else if err == database.ErrFolderNotEmpty {
				c.JSON(http.StatusConflict, gin.H{"error": "folder is not empty"})
			} else {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "db error"})
			}
			return
		}

		c.Status(http.StatusNoContent)
	}
}

// ── Shared DTOs + helpers ────────────────────────────────────────────────────

type fileItemDTO struct {
	ID         int64      `json:"id"`
	Filename   string     `json:"filename"`
	Size       int64      `json:"size"`
	MimeType   string     `json:"mime_type"`
	FolderID   *int64     `json:"folder_id"`
	ThumbReady int        `json:"thumb_ready"`
	CreatedAt  time.Time  `json:"created_at"`
	DeletedAt  *time.Time `json:"deleted_at,omitempty"`
}

func fileItems(list []*database.File) []fileItemDTO {
	items := make([]fileItemDTO, 0, len(list))
	for _, f := range list {
		items = append(items, fileItemDTO{
			ID:         f.ID,
			Filename:   f.Filename,
			Size:       f.Size,
			MimeType:   f.MimeType,
			FolderID:   f.FolderID,
			ThumbReady: int(f.ThumbReady),
			CreatedAt:  f.CreatedAt,
			DeletedAt:  f.DeletedAt,
		})
	}
	return items
}

func folderJSON(f *database.Folder) gin.H {
	return gin.H{
		"id":         f.ID,
		"name":       f.Name,
		"parent_id":  f.ParentID,
		"created_at": f.CreatedAt,
	}
}

func streamFile(c *gin.Context, master, fid, filename, mimeType string) {
	rc, ct, err := seaweed.Download(master, fid)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": "storage error"})
		return
	}
	defer rc.Close()
	if ct == "" {
		ct = mimeType
	}
	c.Header("Content-Disposition", `attachment; filename="`+filename+`"`)
	c.Header("Content-Type", ct)
	io.Copy(c.Writer, rc) //nolint:errcheck
}

func parsePage(c *gin.Context) (page, limit int) {
	page, _ = strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ = strconv.Atoi(c.DefaultQuery("limit", "20"))
	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 100 {
		limit = 20
	}
	return
}

func isImage(mime string) bool {
	return strings.HasPrefix(mime, "image/")
}
