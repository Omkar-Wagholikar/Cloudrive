package database

import (
	"database/sql"
	"time"
)

// RefreshSession represents a long-lived refresh token record.
type RefreshSession struct {
	Token     string
	UserID    int64
	CreatedAt time.Time
	ExpiresAt time.Time
}

func (db *DB) CreateRefreshSession(token string, userID int64, expiresAt time.Time) error {
	_, err := db.Exec(
		`INSERT INTO refresh_sessions (token, user_id, expires_at) VALUES (?, ?, ?)`,
		token, userID, expiresAt,
	)
	return err
}

// GetRefreshSession returns the session if it exists and has not expired.
func (db *DB) GetRefreshSession(token string) (*RefreshSession, error) {
	s := &RefreshSession{}
	err := db.QueryRow(
		`SELECT token, user_id, created_at, expires_at FROM refresh_sessions
		 WHERE token = ? AND expires_at > CURRENT_TIMESTAMP`,
		token,
	).Scan(&s.Token, &s.UserID, &s.CreatedAt, &s.ExpiresAt)
	if err != nil {
		return nil, err
	}
	return s, nil
}

func (db *DB) DeleteRefreshSession(token string) error {
	_, err := db.Exec(`DELETE FROM refresh_sessions WHERE token = ?`, token)
	return err
}

// DeleteUserRefreshSessions revokes all refresh tokens for a user (logout-all).
func (db *DB) DeleteUserRefreshSessions(userID int64) error {
	_, err := db.Exec(`DELETE FROM refresh_sessions WHERE user_id = ?`, userID)
	return err
}

// UploadSession tracks state for an in-progress resumable upload.
type UploadSession struct {
	ID        string
	OwnerID   int64
	FolderID  *int64
	Filename  string
	MimeType  string
	TotalSize int64
	Offset    int64
	TempPath  string
	CreatedAt time.Time
	ExpiresAt time.Time
}

func (db *DB) CreateUploadSession(s *UploadSession) error {
	_, err := db.Exec(
		`INSERT INTO upload_sessions
		 (id, owner_id, folder_id, filename, mime_type, total_size, offset, temp_path, expires_at)
		 VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)`,
		s.ID, s.OwnerID, s.FolderID, s.Filename, s.MimeType,
		s.TotalSize, s.TempPath, s.ExpiresAt,
	)
	return err
}

func (db *DB) GetUploadSession(id string, ownerID int64) (*UploadSession, error) {
	s := &UploadSession{}
	err := db.QueryRow(
		`SELECT id, owner_id, folder_id, filename, mime_type, total_size,
		        offset, temp_path, created_at, expires_at
		 FROM upload_sessions
		 WHERE id = ? AND owner_id = ? AND expires_at > CURRENT_TIMESTAMP`,
		id, ownerID,
	).Scan(
		&s.ID, &s.OwnerID, &s.FolderID, &s.Filename, &s.MimeType,
		&s.TotalSize, &s.Offset, &s.TempPath, &s.CreatedAt, &s.ExpiresAt,
	)
	if err != nil {
		return nil, err
	}
	return s, nil
}

func (db *DB) UpdateUploadSessionOffset(id string, offset int64) error {
	res, err := db.Exec(
		`UPDATE upload_sessions SET offset = ? WHERE id = ?`, offset, id,
	)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return sql.ErrNoRows
	}
	return nil
}

func (db *DB) DeleteUploadSession(id string) error {
	_, err := db.Exec(`DELETE FROM upload_sessions WHERE id = ?`, id)
	return err
}
