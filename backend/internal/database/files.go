package database

import (
	"database/sql"
	"errors"
	"time"
)

type ThumbStatus int

const (
	ThumbPending ThumbStatus = 0
	ThumbReady   ThumbStatus = 1
	ThumbFailed  ThumbStatus = 2
)

type File struct {
	ID         int64
	OwnerID    int64
	FolderID   *int64
	Filename   string
	SeaweedFID string
	ThumbFID   string
	ThumbReady ThumbStatus
	MimeType   string
	Size       int64
	CreatedAt  time.Time
	DeletedAt  *time.Time // nil = not trashed
}

// columns listed once so every query stays consistent
const fileColumns = `id, owner_id, folder_id, filename, seaweed_fid, thumb_fid,
	thumb_ready, mime_type, size, created_at, deleted_at`

func scanFile(row interface {
	Scan(...any) error
}) (*File, error) {
	f := &File{}
	return f, row.Scan(
		&f.ID, &f.OwnerID, &f.FolderID, &f.Filename, &f.SeaweedFID, &f.ThumbFID,
		&f.ThumbReady, &f.MimeType, &f.Size, &f.CreatedAt, &f.DeletedAt,
	)
}

func (db *DB) CreateFile(f *File) error {
	res, err := db.Exec(
		`INSERT INTO files (owner_id, folder_id, filename, seaweed_fid, mime_type, size)
		 VALUES (?, ?, ?, ?, ?, ?)`,
		f.OwnerID, f.FolderID, f.Filename, f.SeaweedFID, f.MimeType, f.Size,
	)
	if err != nil {
		return err
	}
	f.ID, _ = res.LastInsertId()
	return nil
}

// GetFile returns a non-deleted file that belongs to ownerID.
func (db *DB) GetFile(fileID, ownerID int64) (*File, error) {
	row := db.QueryRow(
		`SELECT `+fileColumns+` FROM files
		 WHERE id = ? AND owner_id = ? AND deleted_at IS NULL`,
		fileID, ownerID,
	)
	return scanFile(row)
}

// GetFileByID is used internally (thumbnail worker); no owner check.
func (db *DB) GetFileByID(fileID int64) (*File, error) {
	row := db.QueryRow(
		`SELECT `+fileColumns+` FROM files WHERE id = ? AND deleted_at IS NULL`,
		fileID,
	)
	return scanFile(row)
}

// UpdateFile renames and/or moves a file. Pass the current value for fields not changing.
func (db *DB) UpdateFile(fileID, ownerID int64, filename string, folderID *int64) error {
	res, err := db.Exec(
		`UPDATE files SET filename = ?, folder_id = ?
		 WHERE id = ? AND owner_id = ? AND deleted_at IS NULL`,
		filename, folderID, fileID, ownerID,
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

// SoftDeleteFile moves a file to the trash (sets deleted_at).
func (db *DB) SoftDeleteFile(fileID, ownerID int64) (*File, error) {
	now := time.Now().UTC()
	res, err := db.Exec(
		`UPDATE files SET deleted_at = ?
		 WHERE id = ? AND owner_id = ? AND deleted_at IS NULL`,
		now, fileID, ownerID,
	)
	if err != nil {
		return nil, err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return nil, sql.ErrNoRows
	}
	row := db.QueryRow(
		`SELECT `+fileColumns+` FROM files WHERE id = ?`, fileID,
	)
	return scanFile(row)
}

// HardDeleteFile permanently removes a file record (called after SeaweedFS delete).
func (db *DB) HardDeleteFile(fileID int64) error {
	_, err := db.Exec(`DELETE FROM files WHERE id = ?`, fileID)
	return err
}

// RestoreFile moves a file out of the trash.
func (db *DB) RestoreFile(fileID, ownerID int64) error {
	res, err := db.Exec(
		`UPDATE files SET deleted_at = NULL
		 WHERE id = ? AND owner_id = ? AND deleted_at IS NOT NULL`,
		fileID, ownerID,
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

// ListFiles returns root-level, non-deleted files for the owner.
func (db *DB) ListFiles(ownerID int64, page, limit int) ([]*File, int, error) {
	return db.queryFiles(
		`owner_id = ? AND folder_id IS NULL AND deleted_at IS NULL`,
		[]any{ownerID}, page, limit,
	)
}

// ListFolderFiles returns non-deleted files inside a specific folder.
func (db *DB) ListFolderFiles(ownerID, folderID int64, page, limit int) ([]*File, int, error) {
	return db.queryFiles(
		`owner_id = ? AND folder_id = ? AND deleted_at IS NULL`,
		[]any{ownerID, folderID}, page, limit,
	)
}

// ListTrashedFiles returns soft-deleted files for the owner.
func (db *DB) ListTrashedFiles(ownerID int64, page, limit int) ([]*File, int, error) {
	return db.queryFiles(
		`owner_id = ? AND deleted_at IS NOT NULL`,
		[]any{ownerID}, page, limit,
	)
}

// ListThumbnails returns non-deleted files that have a ready thumbnail.
func (db *DB) ListThumbnails(ownerID int64, page, limit int) ([]*File, int, error) {
	return db.queryFiles(
		`owner_id = ? AND thumb_ready = ? AND deleted_at IS NULL`,
		[]any{ownerID, ThumbReady}, page, limit,
	)
}

// SearchFiles searches filenames with a case-insensitive substring match.
func (db *DB) SearchFiles(ownerID int64, query string, page, limit int) ([]*File, int, error) {
	return db.queryFiles(
		`owner_id = ? AND filename LIKE ? AND deleted_at IS NULL`,
		[]any{ownerID, "%" + query + "%"}, page, limit,
	)
}

// UsedBytes returns the total size of non-deleted files owned by the user.
func (db *DB) UsedBytes(ownerID int64) (int64, error) {
	var used int64
	err := db.QueryRow(
		`SELECT COALESCE(SUM(size), 0) FROM files WHERE owner_id = ? AND deleted_at IS NULL`,
		ownerID,
	).Scan(&used)
	return used, err
}

func (db *DB) queryFiles(where string, args []any, page, limit int) ([]*File, int, error) {
	offset := (page - 1) * limit

	var total int
	countArgs := append(args, nil)
	copy(countArgs, args)
	if err := db.QueryRow(
		`SELECT COUNT(*) FROM files WHERE `+where, args...,
	).Scan(&total); err != nil {
		return nil, 0, err
	}

	rows, err := db.Query(
		`SELECT `+fileColumns+` FROM files WHERE `+where+
			` ORDER BY created_at DESC LIMIT ? OFFSET ?`,
		append(args, limit, offset)...,
	)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var files []*File
	for rows.Next() {
		f, err := scanFile(rows)
		if err != nil {
			return nil, 0, err
		}
		files = append(files, f)
	}
	return files, total, rows.Err()
}

func (db *DB) SetThumbnail(fileID int64, thumbFID string, status ThumbStatus) error {
	_, err := db.Exec(
		`UPDATE files SET thumb_fid = ?, thumb_ready = ? WHERE id = ?`,
		thumbFID, status, fileID,
	)
	return err
}

// GetTrashedFile returns a trashed file owned by ownerID (for restore/purge).
func (db *DB) GetTrashedFile(fileID, ownerID int64) (*File, error) {
	row := db.QueryRow(
		`SELECT `+fileColumns+` FROM files
		 WHERE id = ? AND owner_id = ? AND deleted_at IS NOT NULL`,
		fileID, ownerID,
	)
	f, err := scanFile(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, sql.ErrNoRows
		}
		return nil, err
	}
	return f, nil
}
