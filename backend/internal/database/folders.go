package database

import (
	"database/sql"
	"time"
)

type Folder struct {
	ID        int64
	OwnerID   int64
	ParentID  *int64 // nil = root
	Name      string
	CreatedAt time.Time
}

func (db *DB) CreateFolder(ownerID int64, parentID *int64, name string) (*Folder, error) {
	res, err := db.Exec(
		`INSERT INTO folders (owner_id, parent_id, name) VALUES (?, ?, ?)`,
		ownerID, parentID, name,
	)
	if err != nil {
		return nil, err
	}
	id, _ := res.LastInsertId()
	return &Folder{ID: id, OwnerID: ownerID, ParentID: parentID, Name: name}, nil
}

// GetFolder returns a folder only if it belongs to ownerID.
func (db *DB) GetFolder(folderID, ownerID int64) (*Folder, error) {
	f := &Folder{}
	err := db.QueryRow(
		`SELECT id, owner_id, parent_id, name, created_at FROM folders WHERE id = ? AND owner_id = ?`,
		folderID, ownerID,
	).Scan(&f.ID, &f.OwnerID, &f.ParentID, &f.Name, &f.CreatedAt)
	if err != nil {
		return nil, err
	}
	return f, nil
}

// ListRootFolders returns top-level folders (parent_id IS NULL) for the owner.
func (db *DB) ListRootFolders(ownerID int64) ([]*Folder, error) {
	return db.listFolders(ownerID, nil)
}

// ListSubfolders returns immediate children of parentID for the owner.
func (db *DB) ListSubfolders(ownerID, parentID int64) ([]*Folder, error) {
	return db.listFolders(ownerID, &parentID)
}

func (db *DB) listFolders(ownerID int64, parentID *int64) ([]*Folder, error) {
	var rows *sql.Rows
	var err error
	if parentID == nil {
		rows, err = db.Query(
			`SELECT id, owner_id, parent_id, name, created_at
			 FROM folders WHERE owner_id = ? AND parent_id IS NULL
			 ORDER BY name`,
			ownerID,
		)
	} else {
		rows, err = db.Query(
			`SELECT id, owner_id, parent_id, name, created_at
			 FROM folders WHERE owner_id = ? AND parent_id = ?
			 ORDER BY name`,
			ownerID, *parentID,
		)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var folders []*Folder
	for rows.Next() {
		f := &Folder{}
		if err := rows.Scan(&f.ID, &f.OwnerID, &f.ParentID, &f.Name, &f.CreatedAt); err != nil {
			return nil, err
		}
		folders = append(folders, f)
	}
	return folders, rows.Err()
}

// DeleteFolder removes a folder only if it is empty (no subfolders, no files).
// Returns sql.ErrNoRows if not found/not owned; a descriptive error if not empty.
func (db *DB) DeleteFolder(folderID, ownerID int64) error {
	// Ownership check
	var dummy int
	err := db.QueryRow(
		`SELECT id FROM folders WHERE id = ? AND owner_id = ?`, folderID, ownerID,
	).Scan(&dummy)
	if err != nil {
		return err
	}

	var subCount int
	db.QueryRow(`SELECT COUNT(*) FROM folders WHERE parent_id = ?`, folderID).Scan(&subCount) //nolint:errcheck
	if subCount > 0 {
		return ErrFolderNotEmpty
	}

	var fileCount int
	db.QueryRow(`SELECT COUNT(*) FROM files WHERE folder_id = ?`, folderID).Scan(&fileCount) //nolint:errcheck
	if fileCount > 0 {
		return ErrFolderNotEmpty
	}

	_, err = db.Exec(`DELETE FROM folders WHERE id = ?`, folderID)
	return err
}

// FolderAncestors returns the chain from root down to folderID (breadcrumb path),
// using a recursive CTE. The first element is the root, last is folderID itself.
func (db *DB) FolderAncestors(folderID, ownerID int64) ([]*Folder, error) {
	rows, err := db.Query(`
		WITH RECURSIVE ancestors(id, owner_id, parent_id, name, created_at) AS (
			SELECT id, owner_id, parent_id, name, created_at
			  FROM folders WHERE id = ? AND owner_id = ?
			UNION ALL
			SELECT f.id, f.owner_id, f.parent_id, f.name, f.created_at
			  FROM folders f
			  JOIN ancestors a ON f.id = a.parent_id
		)
		SELECT id, owner_id, parent_id, name, created_at FROM ancestors
	`, folderID, ownerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var chain []*Folder
	for rows.Next() {
		f := &Folder{}
		if err := rows.Scan(&f.ID, &f.OwnerID, &f.ParentID, &f.Name, &f.CreatedAt); err != nil {
			return nil, err
		}
		chain = append(chain, f)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	// Reverse so root comes first
	for i, j := 0, len(chain)-1; i < j; i, j = i+1, j-1 {
		chain[i], chain[j] = chain[j], chain[i]
	}
	return chain, nil
}
