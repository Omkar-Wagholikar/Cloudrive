# CloudDrive

A self-hosted file storage server designed for low-RAM ARM devices (including Termux on Android). Provides a REST API compatible with common cloud drive workflows — folder organisation, resumable uploads, image thumbnails, shared links, and LAN-aware direct transfers.

---

## Features

- **File management** — upload, download, rename, move, soft-delete (trash), and permanently purge files
- **Nested folders** — unlimited folder depth with breadcrumb navigation
- **Resumable uploads** — chunk-based upload sessions that survive dropped connections
- **Image thumbnails** — async 240px JPEG thumbnails generated via a background worker
- **Shared links** — public download URLs with optional expiry, no auth required to download
- **Token refresh** — short-lived access tokens (15 min) with rotating refresh tokens (30 days)
- **Storage quotas** — per-user byte limits enforced at upload time
- **Search** — filename substring search across all files
- **LAN acceleration** — server advertises its local IP addresses so clients on the same network can transfer files directly through the router
- **Trash** — soft-delete with restore and purge

---

## Architecture

```
┌─────────────┐     HTTP      ┌──────────────┐     ┌──────────────┐
│   Client    │ ────────────▶ │  Gin Router  │────▶│   SQLite DB  │
│ (any)       │               │  :8081       │     │ data/cloud.db│
└─────────────┘               └──────┬───────┘     └──────────────┘
                                     │
                        ┌────────────┴────────────┐
                        │                         │
                 ┌──────▼──────┐         ┌────────▼────────┐
                 │  SeaweedFS  │         │  Kafka (Aiven)  │
                 │  :9333/:8080│         │  thumb-jobs     │
                 └─────────────┘         └────────┬────────┘
                                                  │
                                         ┌────────▼────────┐
                                         │ Thumbnail Worker│
                                         │  (goroutine)    │
                                         └─────────────────┘
```

| Component | Technology | Notes |
|---|---|---|
| HTTP framework | [Gin](https://github.com/gin-gonic/gin) | Lightweight, fast routing |
| Database | SQLite via [modernc.org/sqlite](https://pkg.go.dev/modernc.org/sqlite) | Pure-Go, no CGO, WAL mode |
| File storage | [SeaweedFS](https://github.com/seaweedfs/seaweedfs) | Streaming upload/download, single-node |
| Message queue | Apache Kafka (Aiven mTLS) | Decouples thumbnail generation from upload |
| Auth | JWT (HS256) + bcrypt | 15-min access tokens, 30-day refresh tokens |
| Thumbnails | stdlib + [x/image](https://pkg.go.dev/golang.org/x/image) | JPEG/PNG/GIF/WebP → 240px JPEG |

---

## Requirements

- Go 1.22+
- SeaweedFS running locally (`weed server` or `weed master` + `weed volume`)
- Kafka broker (Aiven or self-hosted) with mTLS certificates

### Termux prerequisites

```bash
pkg update && pkg upgrade -y
pkg install golang git sqlite
```

Download the SeaweedFS ARM64 binary from [github.com/seaweedfs/seaweedfs/releases](https://github.com/seaweedfs/seaweedfs/releases) and place `weed` on your `PATH`.

---

## Getting Started

### 1. Clone and build

```bash
git clone https://github.com/yourname/cloudrive
cd cloudrive
go mod tidy
go build -o cloudrive .
```

For a smaller binary:
```bash
go build -ldflags="-s -w" -o cloudrive .
```

### 2. Start SeaweedFS

```bash
mkdir -p weed-data
weed server -dir=./weed-data -master.port=9333 -volume.port=8080 &
```

### 3. Place Kafka certificates

Aiven provides three files. Put them in `certs/`:

```
certs/
├── ca.pem
├── service.cert
└── service.key
```

### 4. Configure environment

```bash
export JWT_SECRET="replace-with-a-long-random-string"
export SEAWEEDFS_MASTER="localhost:9333"
export KAFKA_BROKER="your-kafka-broker:12128"
export KAFKA_ACCESS_KEY="certs/service.key"
export KAFKA_ACCESS_CERT="certs/service.cert"
export KAFKA_CA_CERT="certs/ca.pem"
export LISTEN_ADDR=":8081"   # optional, default :8081
```

### 5. Run

```bash
./cloudrive
# Listening on :8081
```

---

## API Usage

The full machine-readable spec is at [`openapi.json`](./openapi.json). Paste it into [Swagger Editor](https://editor.swagger.io) for an interactive UI.

### Authentication

```bash
# Register
curl -X POST http://localhost:8081/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}'

# Login — returns access_token (15 min) + refresh_token (30 days)
curl -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}'

# Refresh access token (old refresh token is immediately revoked)
curl -X POST http://localhost:8081/token/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<refresh_token>"}'

# Logout (revoke refresh token)
curl -X POST http://localhost:8081/logout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<refresh_token>"}'
```

All protected endpoints require `Authorization: Bearer <access_token>`.

### Files

```bash
# Upload to root
curl -X POST http://localhost:8081/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@photo.jpg"

# Upload into a folder
curl -X POST http://localhost:8081/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@photo.jpg" \
  -F "folder_id=3"

# List root-level files
curl http://localhost:8081/files \
  -H "Authorization: Bearer $TOKEN"

# Search by filename
curl "http://localhost:8081/files/search?q=tokyo" \
  -H "Authorization: Bearer $TOKEN"

# Get metadata + download URLs
curl http://localhost:8081/files/7 \
  -H "Authorization: Bearer $TOKEN"

# Download raw bytes
curl -OJ http://localhost:8081/files/7/download \
  -H "Authorization: Bearer $TOKEN"

# Rename
curl -X PATCH http://localhost:8081/files/7 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"filename": "new-name.jpg"}'

# Move to a different folder (null = move to root)
curl -X PATCH http://localhost:8081/files/7 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"folder_id": 5}'

# Soft-delete (moves to trash)
curl -X DELETE http://localhost:8081/files/7 \
  -H "Authorization: Bearer $TOKEN"
```

### Resumable Uploads

Use this for files larger than ~100 MB. Sessions survive dropped connections and expire after 24 hours.

```bash
# 1. Initialise session
curl -X POST http://localhost:8081/uploads/resumable \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"filename": "video.mp4", "size": 524288000, "mime_type": "video/mp4"}'
# → { "upload_id": "abc123", "offset": 0 }

# 2. Upload a chunk (repeat with advancing Content-Range until 201)
curl -X PATCH http://localhost:8081/uploads/resumable/abc123 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Range: bytes 0-2097151/524288000" \
  --data-binary @chunk.bin
# → 200 { "offset": 2097152 }   (in progress)
# → 201 { "id": 9, ... }        (complete)

# 3. Query progress after a dropped connection
curl http://localhost:8081/uploads/resumable/abc123 \
  -H "Authorization: Bearer $TOKEN"
# → { "upload_id": "abc123", "offset": 2097152, "total_size": 524288000 }
```

### Folders

```bash
# Create a root folder
curl -X POST http://localhost:8081/folders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Trips"}'
# → { "id": 1, ... }

# Create a nested folder
curl -X POST http://localhost:8081/folders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Japan 2026", "parent_id": 1}'

# Browse a folder — returns subfolders, files, and breadcrumb path
curl http://localhost:8081/folders/2 \
  -H "Authorization: Bearer $TOKEN"

# Delete an empty folder
curl -X DELETE http://localhost:8081/folders/2 \
  -H "Authorization: Bearer $TOKEN"
```

### Shared Links

```bash
# Create a link that expires in 24 hours
curl -X POST http://localhost:8081/files/7/share \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"expires_in": 86400}'
# → { "token": "xyz", "url": "http://localhost:8081/shared/xyz", "expires_at": "..." }

# Download via share link — no auth required
curl -OJ http://localhost:8081/shared/xyz

# Revoke a share link
curl -X DELETE http://localhost:8081/shared/xyz \
  -H "Authorization: Bearer $TOKEN"
```

### Trash

```bash
# List trashed files
curl http://localhost:8081/trash \
  -H "Authorization: Bearer $TOKEN"

# Restore a file
curl -X POST http://localhost:8081/trash/7/restore \
  -H "Authorization: Bearer $TOKEN"

# Permanently delete from storage (irreversible)
curl -X DELETE http://localhost:8081/trash/7 \
  -H "Authorization: Bearer $TOKEN"
```

### Thumbnails

```bash
# List all files that have a ready thumbnail
curl http://localhost:8081/thumbnails \
  -H "Authorization: Bearer $TOKEN"

# Download a thumbnail (240px JPEG)
# Returns 202 while still processing, 200 when ready
curl http://localhost:8081/files/7/thumbnail \
  -H "Authorization: Bearer $TOKEN" -o thumb.jpg
```

### LAN Acceleration

```bash
# Discover server LAN addresses
curl http://localhost:8081/network
# → { "local_addresses": ["192.168.1.42:8081"] }
```

File info and upload responses include a `local_url` field alongside the canonical `url`. Clients on the same network should try `local_url` first with a ~300ms connect timeout and fall back to `url` on failure.

---

## Endpoint Reference

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | — | Create account |
| POST | `/login` | — | Get token pair |
| POST | `/token/refresh` | — | Rotate refresh token |
| POST | `/logout` | ✓ | Revoke refresh token |
| GET | `/me` | ✓ | Profile + quota usage |
| GET | `/network` | — | Server LAN addresses |
| POST | `/upload` | ✓ | Single-shot file upload |
| GET | `/files` | ✓ | List root-level files |
| GET | `/files/search?q=` | ✓ | Search by filename |
| GET | `/files/:id` | ✓ | File metadata + URLs |
| PATCH | `/files/:id` | ✓ | Rename or move |
| DELETE | `/files/:id` | ✓ | Move to trash |
| GET | `/files/:id/download` | ✓ | Download raw bytes |
| GET | `/files/:id/thumbnail` | ✓ | Download thumbnail |
| POST | `/files/:id/share` | ✓ | Create share link |
| GET | `/thumbnails` | ✓ | List files with thumbnails |
| GET | `/shared/:token` | — | Public file download |
| DELETE | `/shared/:token` | ✓ | Revoke share link |
| GET | `/trash` | ✓ | List trashed files |
| POST | `/trash/:id/restore` | ✓ | Restore from trash |
| DELETE | `/trash/:id` | ✓ | Permanently delete |
| POST | `/uploads/resumable` | ✓ | Init resumable session |
| GET | `/uploads/resumable/:id` | ✓ | Query upload progress |
| PATCH | `/uploads/resumable/:id` | ✓ | Upload a chunk |
| GET | `/folders` | ✓ | List root folders |
| POST | `/folders` | ✓ | Create folder |
| GET | `/folders/:id` | ✓ | Browse folder contents |
| DELETE | `/folders/:id` | ✓ | Delete empty folder |

---

## Database Schema

```sql
users           (id, username, password_hash, quota_bytes, created_at)
files           (id, owner_id, folder_id, filename, seaweed_fid, thumb_fid,
                 thumb_ready, mime_type, size, created_at, deleted_at)
folders         (id, owner_id, parent_id, name, created_at)
refresh_sessions(token, user_id, created_at, expires_at)
upload_sessions (id, owner_id, folder_id, filename, mime_type,
                 total_size, offset, temp_path, created_at, expires_at)
share_tokens    (token, file_id, owner_id, created_at, expires_at)
```

---

## Project Layout

```
cloudrive/
├── main.go
├── go.mod
├── openapi.json
└── internal/
    ├── auth/        JWT creation/validation, bcrypt, middleware
    ├── database/    SQLite init, migrations, all queries
    ├── files/       HTTP handlers for every endpoint
    ├── network/     LAN IP enumeration
    ├── queue/       Kafka producer/consumer (franz-go, mTLS)
    ├── seaweed/     SeaweedFS HTTP client (assign, upload, download, delete)
    └── thumbnail/   Background worker — decode, resize, re-encode, store
```

---

## Configuration Reference

| Environment Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `change-this-...` | JWT signing key — **change in production** |
| `SEAWEEDFS_MASTER` | `localhost:9333` | SeaweedFS master address |
| `KAFKA_BROKER` | *(Aiven endpoint)* | Kafka broker address |
| `KAFKA_ACCESS_KEY` | `certs/service.key` | mTLS client key path |
| `KAFKA_ACCESS_CERT` | `certs/service.cert` | mTLS client cert path |
| `KAFKA_CA_CERT` | `certs/ca.pem` | Kafka CA cert path |
| `LISTEN_ADDR` | `:8081` | HTTP listen address |

---

## Resource Profile

Designed to run on a device with as little as 512 MB RAM.

| Component | Idle RAM |
|---|---|
| CloudDrive binary | ~8–12 MB |
| SeaweedFS single-node | ~30–60 MB |
| Kafka client | ~5 MB |
| **Total** | **~60–100 MB** |

- SQLite WAL mode keeps write locks short
- Thumbnail worker processes batches of 5 and frees memory after each image
- Files are never buffered locally — uploads and downloads stream directly through SeaweedFS

---

## Building an Android Client

The server is designed to be consumed by a native Android app. Recommended stack: Kotlin + Retrofit + Coroutines + Jetpack Compose. Key concerns:

- **Auth interceptor** — auto-refresh access token on 401, retry the original request once
- **Resumable upload manager** — read file in 2 MB chunks, track offset, resume after network drop
- **LAN fallback** — try `local_url` with 300ms connect timeout, fall back to `url`
- **Cleartext traffic** — add a `network_security_config.xml` to allow HTTP to the server's IP until HTTPS is configured

A native Android client already exists at [`../app`](../app) — see its [README](../app/README.md) and [`docs/API_REFERENCE.md`](../app/docs/API_REFERENCE.md) for how it consumes this API.

---

## License

MIT
