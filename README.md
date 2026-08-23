# Cloudrive

A self-hosted, Google Drive–style file storage system: a Go backend
(`backend/`) plus a native Android client (`app/`). No web or iOS client
exists — the Android app is the only front end. The backend is designed to
run on low-RAM ARM devices, including Termux on Android, so the whole stack
can live entirely on hardware you own.

```
Cloudrive/
├── backend/   Go REST API — SQLite, SeaweedFS, Kafka thumbnail pipeline
└── app/       Android client — Jetpack Compose, Retrofit, Media3
```

Upstream repositories:

- Android app — [github.com/Omkar-Wagholikar/CloudDrive-App](https://github.com/Omkar-Wagholikar/CloudDrive-App)

---

## What it does

- File & folder management — upload, download, rename, move, trash/restore
- Resumable, chunked uploads that survive dropped connections
- Shared links with optional expiry, no auth required to download
- Async image thumbnailing via a Kafka-driven background worker
- LAN acceleration — client and server race a local-network URL against the
  public one and use whichever answers first
- A full music library subsystem in the Android app: playlists, offline
  downloads, background playback (Media3 `MediaSessionService`)

---

## Repository layout

| Path | What | README |
|---|---|---|
| [`backend/`](backend/) | Go REST API server | [`backend/README.md`](backend/README.md) |
| [`app/`](app/) | Android client (Kotlin, Compose) | [`app/README.md`](app/README.md) |

Each half of the project is independently documented — start with the
sub-project README for setup and API/architecture details. This root README
is only an index and quick-start.

### Backend (`backend/`)

Go + Gin, SQLite (via `modernc.org/sqlite`, no CGO), SeaweedFS for blob
storage, and Kafka (Aiven, mTLS) to decouple thumbnail generation from
uploads. See [`backend/README.md`](backend/README.md) for architecture,
environment variables, the full endpoint table, and the DB schema, and
[`backend/openapi.json`](backend/openapi.json) for the machine-readable spec.

### App (`app/`)

Jetpack Compose + Material3, Retrofit/OkHttp (with a custom
`AuthInterceptor`/`TokenAuthenticator` for silent token refresh), Room for
the local music library mirror, and Media3 ExoPlayer for playback. See
[`app/README.md`](app/README.md) and the docs below.

- [`app/docs/API_REFERENCE.md`](app/docs/API_REFERENCE.md) — condensed backend endpoint reference from the client's perspective
- [`app/docs/DESIGN_DOC.md`](app/docs/DESIGN_DOC.md) — UI/UX review and feature roadmap (built vs. backend-supported-but-not-yet-exposed)
- [`app/docs/MUSIC_HANDOVER.md`](app/docs/MUSIC_HANDOVER.md) — music subsystem architecture handover
- [`app/openapi.json`](app/openapi.json) — spec snapshot bundled with the app

---

## Quick start

You need the backend running before the app can log in against it.

### 1. Run the backend

```bash
cd backend
go mod tidy
go build -o cloudrive .

# SeaweedFS (blob storage) — single node is enough for local dev
mkdir -p weed-data
weed server -dir=./weed-data -master.port=9333 -volume.port=8080 &

# Kafka mTLS certs (Aiven) go in backend/certs/ — see backend/README.md
export JWT_SECRET="replace-with-a-long-random-string"
export SEAWEEDFS_MASTER="localhost:9333"
export KAFKA_BROKER="your-kafka-broker:12128"
export KAFKA_ACCESS_KEY="certs/service.key"
export KAFKA_ACCESS_CERT="certs/service.cert"
export KAFKA_CA_CERT="certs/ca.pem"

./cloudrive
# Listening on :8081
```

Full setup, Termux prerequisites, and configuration reference:
[`backend/README.md`](backend/README.md).

### 2. Run the app

```bash
cd app
./gradlew assembleDebug
```

Install the APK, then on the auth screen point the **server URL** field at
your backend (defaults to `http://localhost:8081`; use your machine's LAN IP
if the app runs on a physical device/emulator that isn't `localhost`).

---

## How the pieces talk to each other

The app is a pure HTTP client of the backend — there's no shared code or
build between them. The server URL is user-configurable at login and
persisted in the app's `TokenStore`. Auth is JWT (15-min access token,
30-day rotating refresh token); the app's `AuthInterceptor` attaches the
access token and its `TokenAuthenticator` transparently refreshes and
retries on a 401.

For same-network transfers, the server advertises its LAN addresses
(`GET /network`) and includes a `local_url` alongside the canonical `url` in
file responses; the app's `LanResolver` races the two with a short connect
timeout and falls back to the public URL.

---

## License

MIT — see [`backend/README.md`](backend/README.md#license).
