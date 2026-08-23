# Cloudrive

A self hosted, Google Drive style Android client for a Go backend
([`CloudDrive`](docs/API_REFERENCE.md)) built for a home server / Termux on Android
box. There's no web or iOS client this app is the only front end for the
backend, so it covers file storage, sharing, and music library/playback in one app.

## Stack

  **UI**: Jetpack Compose, Material3, Navigation Compose
  **Networking**: Retrofit + OkHttp (custom `AuthInterceptor` / `TokenAuthenticator` for token refresh)
  **Images**: Coil 3
  **Local storage**: Room (music library mirror, playlists, downloads, queue), DataStore style prefs
  **Playback**: Media3 ExoPlayer + `MediaSessionService`
  **Background work**: WorkManager (downloads)
  **DI**: none a manual `ServiceLocator` (`di/ServiceLocator.kt`) wires everything
  minSdk 26, compileSdk/targetSdk 37/36, Kotlin

## Backend

The server is a separate Go project (SQLite + SeaweedFS + a Kafka driven thumbnail
/ metadata extraction pipeline). The app talks to it entirely over HTTP; the
server URL is user configurable at login and stored in `TokenStore`. See
[`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) for the endpoint list and
[`openapi.json`](openapi.json) for the full spec.

The app also races LAN vs. WAN URLs (`LanResolver`) so downloads/streams prefer
a local network path when the server is reachable that way.

## Features

  **Auth**: login/register against a self hosted server, editable server URL
  **My Drive**: folder browsing, upload, create folder, move, rename, multi select
  batch actions (move/trash/restore/delete)
  **Photos**: grid of thumbnailed images
  **Search**: debounced filename search
  **Sharing**: create expiring share links, view/revoke active links ("My Shared Links")
  **Trash**: restore, permanently delete, empty trash
  **Preview**: in app image and PDF viewers
  **Music**: library, playlists, offline downloads, and background playback via
  Media3 see [`docs/MUSIC_HANDOVER.md`](docs/MUSIC_HANDOVER.md) for the
  subsystem's architecture
  **Resumable uploads**: chunked upload with session recovery after a crash/restart

## Project layout

```
app/src/main/java/com/example/cloudrive/
├── data/
│   ├── local/          # Room DB, prefs, token store
│   ├── model/           # API/domain models
│   ├── remote/          # Retrofit APIs, interceptors, LAN resolver
│   └── repository/       # Repositories bridging remote + local data
├── di/                  # ServiceLocator (manual DI)
├── navigation/          # Nav graph / routes
├── playback/            # Media3 playback service, network monitor
└── ui/                  # Compose screens, grouped by feature
    ├── auth/ folder/ home/ links/ music/ preview/
    └── profile/ search/ settings/ theme/ trash/ upload/
```

## Docs

  [`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) condensed backend endpoint reference
  [`docs/DESIGN_DOC.md`](docs/DESIGN_DOC.md) UI/UX design review and feature roadmap (what's built vs. what the backend supports but the UI doesn't expose yet)
  [`docs/MUSIC_HANDOVER.md`](docs/MUSIC_HANDOVER.md) music feature architecture handover

## Building

```
./gradlew assembleDebug
```

Requires a running instance of the CloudDrive backend to log in against; point
the app at it via the server URL field on the auth screen (defaults to
`http://localhost:8081`).
