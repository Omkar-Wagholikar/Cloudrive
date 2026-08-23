# CloudDrive API Reference (condensed, for UI/design purposes)

Source of truth: `../openapi.json` (frontend copy) — backend lives at
[`../../backend`](../../backend). Base URL is user-configurable per install
(stored in `TokenStore`), default `http://localhost:8081`.

> **Note:** `../openapi.json` (this app's bundled copy) is ahead of the
> backend's own [`../../backend/openapi.json`](../../backend/openapi.json) —
> `GET /ping`, `POST /files/batch`, `GET /shared` (list), `DELETE /trash`
> (purge-all), and `GET /uploads/resumable` (list) are called by
> `FileApi`/`TrashApi`/`UploadApi` in this app but are **not implemented** by
> the current `backend/main.go` route table. Calls to these will 404 against
> a stock backend build until the server-side handlers are added.

## Endpoints

| Method | Path | Auth | Purpose | Used by current Android UI? |
|---|---|---|---|---|
| POST | `/register` | — | Create account | Yes (Auth screen) |
| POST | `/login` | — | Get access+refresh token pair | Yes |
| POST | `/token/refresh` | — | Rotate refresh token | Yes (via `TokenAuthenticator`, automatic) |
| POST | `/logout` | ✓ | Revoke refresh token | Yes (Profile) |
| GET | `/me` | ✓ | Profile + quota usage | Yes (Profile) |
| GET | `/ping` | — | Cheap unauthenticated reachability probe (service/version/server_time) | **No** — not used for server-URL validation or LAN/WAN latency detection on the Auth screen |
| GET | `/network` | — | Server LAN addresses | **No** |
| POST | `/upload` | ✓ | Single-shot upload (<5MB in this app's convention) | Yes |
| GET | `/files` | ✓ | List root-level files (`cursor`/`limit` switch to cursor-paginated `FileCursorList` shape) | Yes (page/limit shape only; cursor mode unused) |
| GET | `/files/search?q=` | ✓ | Filename search | Yes |
| GET | `/files/:id` | ✓ | File metadata + URLs | Yes |
| PATCH | `/files/:id` | ✓ | Rename or move (`filename` and/or `folder_id`) | Yes (both) |
| DELETE | `/files/:id` | ✓ | Soft-delete (trash) | Yes |
| POST | `/files/batch` | ✓ | Batch `move`/`trash`/`restore`/`delete` on up to 100 ids in one call, 207 partial-failure response | **No UI** — no multi-select anywhere in the app yet, so this is unused |
| GET | `/files/:id/download` | ✓ | Raw bytes | Yes |
| GET | `/files/:id/thumbnail` | ✓ | 240px JPEG thumbnail (202 while processing) | Yes |
| POST | `/files/:id/share` | ✓ | Create share link (`expires_in` optional) | Yes — expiry picker (never/1h/24h/7d) |
| GET | `/thumbnails` | ✓ | List files with ready thumbnails | Yes (Photos tab) |
| GET | `/shared/:token` | — | Public download, no auth | N/A (external) |
| GET | `/shared` | ✓ | List caller's own active share links (server truth, security-audit surface) | Yes ("My Shared Links", under Profile) |
| DELETE | `/shared/:token` | ✓ | Revoke share link | Yes (from "My Shared Links") |
| GET | `/trash` | ✓ | List trashed files (`cursor`/`limit` switch to cursor-paginated shape); trashed items may carry `purge_at` if server retention is enabled | Yes (page/limit shape only) |
| POST | `/trash/:id/restore` | ✓ | Restore from trash | Yes |
| DELETE | `/trash/:id` | ✓ | Permanently delete one file | Yes |
| DELETE | `/trash` | ✓ | Permanently purge **all** trashed files ("Free up space") | **No UI** |
| POST | `/uploads/resumable` | ✓ | Init resumable session | Yes (large files) |
| GET | `/uploads/resumable` | ✓ | List caller's open (unexpired) resumable sessions — lets a client rediscover an in-flight upload after crash/reinstall without having persisted `upload_id` | **No UI** |
| GET | `/uploads/resumable/:id` | ✓ | Query upload progress/offset | Implemented server-side; **no evidence of resume-after-restart flow in app** |
| PATCH | `/uploads/resumable/:id` | ✓ | Upload a chunk (`Content-Range` header) | Yes |
| GET | `/folders` | ✓ | List root folders | Yes |
| POST | `/folders` | ✓ | Create folder (`parent_id` optional) | Yes |
| GET | `/folders/:id` | ✓ | Browse folder (subfolders + files + breadcrumb) | Yes |
| DELETE | `/folders/:id` | ✓ | Delete empty folder | Verify exists in UI |

Note: `GET /files`, `GET /files/search`, and `GET /trash` all accept an opaque
`cursor` query param — passing it (or `limit`) switches the response shape from
the classic `{page, limit, total, items}` to `{files, next_cursor, total}`
(`FileCursorList`). The app currently only ever uses the page/limit shape.

## Key data models (from `Models.kt`, mirrors `openapi.json`)

- **User**: `user_id, username, quota_bytes, used_bytes, created_at`
- **FileItem / FileInfo**: `id, filename, size, mime_type, folder_id, thumb_ready, created_at, deleted_at` — `FileInfo` additionally has `url, local_url`
- **Folder**: `id, name, parent_id, created_at`
- **FolderContents**: `folder, breadcrumb[], subfolders[], files: FileList`
- **ShareToken**: `token, url, expires_at` — note: `expires_at` can be null (permanent link)
- **ShareLinkItem** (from `GET /shared`): `token, url, file_id, filename, mime_type, created_at, expires_at`
- **NetworkInfo**: `local_addresses[]` — currently unused by the app
- **UploadSession / StartResumableResponse**: `upload_id, offset, total_size, expires_at`
- **BatchFilesRequest/Response**: `op (move|trash|restore|delete), ids[] (max 100), folder_id` → `{succeeded[], failed: [{id, code, message}]}`

## Auth model

- JWT access token: 15 min TTL
- Refresh token: 30 days, rotates on every use (old one is revoked immediately —
  so only one in-flight refresh should ever happen; relevant if redesigning
  offline/background sync)
- `TokenAuthenticator` handles 401 → refresh → retry transparently today

## Things a designer should know about constraints

- **Self-hosted, low-RAM target** (README says the whole backend stack targets
  ~60-100MB RAM, ARM/Termux-capable). This isn't a hosted SaaS — expect
  variable latency, occasional unreachable-server states, and no guaranteed
  HTTPS (README explicitly calls out cleartext HTTP as the default until a user
  configures TLS). Error and offline states deserve real design attention, not
  generic "something went wrong" toasts.
- **No multi-user sharing model beyond public links** — there's no
  "share with user X" concept server-side, only anonymous expiring/permanent
  links. Don't design a collaborator-picker UI; it has no backend to support it.
- **Storage quota is per-user and hard-enforced at upload** — design should
  make remaining quota visible before a large upload starts, not just after
  it fails.
