package database

import (
	"database/sql"
	"time"
)

type ShareToken struct {
	Token     string
	FileID    int64
	OwnerID   int64
	CreatedAt time.Time
	ExpiresAt *time.Time // nil = never expires
}

func (db *DB) CreateShareToken(token string, fileID, ownerID int64, expiresAt *time.Time) error {
	_, err := db.Exec(
		`INSERT INTO share_tokens (token, file_id, owner_id, expires_at) VALUES (?, ?, ?, ?)`,
		token, fileID, ownerID, expiresAt,
	)
	return err
}

// GetShareToken returns the token if it exists and hasn't expired.
func (db *DB) GetShareToken(token string) (*ShareToken, error) {
	s := &ShareToken{}
	err := db.QueryRow(
		`SELECT token, file_id, owner_id, created_at, expires_at
		 FROM share_tokens
		 WHERE token = ? AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)`,
		token,
	).Scan(&s.Token, &s.FileID, &s.OwnerID, &s.CreatedAt, &s.ExpiresAt)
	if err != nil {
		return nil, err
	}
	return s, nil
}

// GetFileByShareToken resolves the share token and returns the referenced file.
// Returns sql.ErrNoRows if the token is unknown, expired, or the file is deleted.
func (db *DB) GetFileByShareToken(token string) (*File, error) {
	st, err := db.GetShareToken(token)
	if err != nil {
		return nil, err
	}
	row := db.QueryRow(
		`SELECT `+fileColumns+` FROM files WHERE id = ? AND deleted_at IS NULL`,
		st.FileID,
	)
	return scanFile(row)
}

func (db *DB) DeleteShareToken(token string, ownerID int64) error {
	res, err := db.Exec(
		`DELETE FROM share_tokens WHERE token = ? AND owner_id = ?`, token, ownerID,
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

// ListShareTokens returns all active share tokens for a given file.
func (db *DB) ListShareTokens(fileID, ownerID int64) ([]*ShareToken, error) {
	rows, err := db.Query(
		`SELECT token, file_id, owner_id, created_at, expires_at
		 FROM share_tokens
		 WHERE file_id = ? AND owner_id = ?
		   AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
		 ORDER BY created_at DESC`,
		fileID, ownerID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tokens []*ShareToken
	for rows.Next() {
		s := &ShareToken{}
		if err := rows.Scan(&s.Token, &s.FileID, &s.OwnerID, &s.CreatedAt, &s.ExpiresAt); err != nil {
			return nil, err
		}
		tokens = append(tokens, s)
	}
	return tokens, rows.Err()
}
