package database

import (
	"database/sql"
	"fmt"
	"os"

	_ "modernc.org/sqlite" // pure-Go SQLite — no CGO required
)

type DB struct {
	*sql.DB
}

func Init(path string) (*DB, error) {
	if err := os.MkdirAll("data", 0755); err != nil {
		return nil, fmt.Errorf("mkdir data: %w", err)
	}

	// modernc driver name is "sqlite", DSN pragmas use _pragma= syntax
	dsn := path + "?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)"
	sqlDB, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}

	// Termux / low-RAM: keep connection pool small
	sqlDB.SetMaxOpenConns(4)
	sqlDB.SetMaxIdleConns(2)

	db := &DB{sqlDB}
	if err := db.migrate(); err != nil {
		return nil, fmt.Errorf("migrate: %w", err)
	}
	return db, nil
}

func (db *DB) migrate() error {
	stmts := []string{
		`CREATE TABLE IF NOT EXISTS users (
			id            INTEGER PRIMARY KEY AUTOINCREMENT,
			username      TEXT    NOT NULL UNIQUE,
			password_hash TEXT    NOT NULL,
			created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
		)`,
		`CREATE TABLE IF NOT EXISTS files (
			id          INTEGER PRIMARY KEY AUTOINCREMENT,
			owner_id    INTEGER NOT NULL REFERENCES users(id),
			filename    TEXT    NOT NULL,
			seaweed_fid TEXT    NOT NULL,
			thumb_fid   TEXT    NOT NULL DEFAULT '',
			thumb_ready INTEGER NOT NULL DEFAULT 0,
			mime_type   TEXT    NOT NULL,
			size        INTEGER NOT NULL,
			created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
		)`,
		`CREATE INDEX IF NOT EXISTS idx_files_owner ON files(owner_id)`,
		`CREATE TABLE IF NOT EXISTS folders (
			id         INTEGER PRIMARY KEY AUTOINCREMENT,
			owner_id   INTEGER NOT NULL REFERENCES users(id),
			parent_id  INTEGER REFERENCES folders(id),
			name       TEXT    NOT NULL,
			created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
			UNIQUE(owner_id, parent_id, name)
		)`,
		`CREATE INDEX IF NOT EXISTS idx_folders_owner  ON folders(owner_id)`,
		`CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders(parent_id)`,
		`CREATE TABLE IF NOT EXISTS refresh_sessions (
			token      TEXT    PRIMARY KEY,
			user_id    INTEGER NOT NULL REFERENCES users(id),
			created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
			expires_at DATETIME NOT NULL
		)`,
		`CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user ON refresh_sessions(user_id)`,
		`CREATE TABLE IF NOT EXISTS upload_sessions (
			id         TEXT    PRIMARY KEY,
			owner_id   INTEGER NOT NULL REFERENCES users(id),
			folder_id  INTEGER REFERENCES folders(id),
			filename   TEXT    NOT NULL,
			mime_type  TEXT    NOT NULL,
			total_size INTEGER NOT NULL,
			offset     INTEGER NOT NULL DEFAULT 0,
			temp_path  TEXT    NOT NULL,
			created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
			expires_at DATETIME NOT NULL
		)`,
		`CREATE TABLE IF NOT EXISTS share_tokens (
			token      TEXT    PRIMARY KEY,
			file_id    INTEGER NOT NULL REFERENCES files(id),
			owner_id   INTEGER NOT NULL REFERENCES users(id),
			created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
			expires_at DATETIME
		)`,
		`CREATE INDEX IF NOT EXISTS idx_share_tokens_file ON share_tokens(file_id)`,
	}
	for _, s := range stmts {
		if _, err := db.Exec(s); err != nil {
			return err
		}
	}
	// Idempotent column additions — fail silently if already present
	_, _ = db.Exec(`ALTER TABLE files ADD COLUMN folder_id  INTEGER REFERENCES folders(id)`)
	_, _ = db.Exec(`ALTER TABLE files ADD COLUMN deleted_at DATETIME`)
	_, _ = db.Exec(`ALTER TABLE users ADD COLUMN quota_bytes INTEGER NOT NULL DEFAULT 10737418240`)
	return nil
}
