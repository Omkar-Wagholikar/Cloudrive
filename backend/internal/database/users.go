package database

import "time"

type User struct {
	ID           int64
	Username     string
	PasswordHash string
	QuotaBytes   int64 // 0 = unlimited
	CreatedAt    time.Time
}

func (db *DB) CreateUser(username, passwordHash string) (*User, error) {
	res, err := db.Exec(
		`INSERT INTO users (username, password_hash) VALUES (?, ?)`,
		username, passwordHash,
	)
	if err != nil {
		return nil, err
	}
	id, _ := res.LastInsertId()
	return &User{ID: id, Username: username, PasswordHash: passwordHash}, nil
}

func (db *DB) GetUserByUsername(username string) (*User, error) {
	u := &User{}
	err := db.QueryRow(
		`SELECT id, username, password_hash, quota_bytes, created_at FROM users WHERE username = ?`,
		username,
	).Scan(&u.ID, &u.Username, &u.PasswordHash, &u.QuotaBytes, &u.CreatedAt)
	if err != nil {
		return nil, err
	}
	return u, nil
}

func (db *DB) GetUserByID(id int64) (*User, error) {
	u := &User{}
	err := db.QueryRow(
		`SELECT id, username, password_hash, quota_bytes, created_at FROM users WHERE id = ?`,
		id,
	).Scan(&u.ID, &u.Username, &u.PasswordHash, &u.QuotaBytes, &u.CreatedAt)
	if err != nil {
		return nil, err
	}
	return u, nil
}
