# Cloudrive Music — Implementation Handover for Claude Code

Companion to the visual spec (`Music Design Spec.dc.html`). Screen numbers below (S1–S25) refer to that canvas.

Repos:
- Android app: `uploads/project_app` (Jetpack Compose, Kotlin, Retrofit/OkHttp, Coil, manual ServiceLocator, minSdk 26)
- Backend: `uploads/project_backend` (Go, SQLite, SeaweedFS, Kafka thumbnail pipeline)

Guiding constraint: **fit the existing patterns**. No DI framework, no GraphQL, no rewrite. The backend already has a Kafka-driven derived-asset pipeline (thumbnails) — metadata extraction copies that shape. The app already has repository + ViewModel + Compose screens — Music adds more of the same plus two genuinely new subsystems (playback service, download manager).

---

## 1. Architecture

### 1.1 High-level

```
Go backend                          Android app
──────────                          ───────────
files table ──┐                     Retrofit APIs ──► Repositories ──► ViewModels ──► Compose UI
              ▼                          ▲                 │
tag-extract worker (Kafka)               │                 ▼
  ├─ tracks table (SQLite)          Room DB (library mirror, playlists, downloads, queue)
  └─ artwork blobs (SeaweedFS)           ▲
                                         │
                                    Media3 ExoPlayer + MediaSessionService
                                    DownloadManager (WorkManager + OkHttp)
```

### 1.2 Why these choices

- **Server-side metadata extraction** (not on-device): parsing tags on-device requires downloading at least the file header of every audio file — thousands of network round-trips on a LAN-optional connection, repeated per device. The backend already demonstrates the exact pattern with thumbnails: upload → Kafka message → worker → derived data + `*_ready` flag. One extractor (Go: `dhowden/tag` for ID3/MP4/Vorbis/FLAC) runs once per file, serves every client forever.
- **Room as the app-side source of truth** for the music library: the UI never renders from network responses directly. A sync job reconciles server state into Room; every screen observes Room Flows. This gives instant cold-start, offline browsing for free, and makes the "library auto-updates when files are added/removed" requirement a sync concern rather than a UI concern.
- **Media3 (ExoPlayer + MediaSessionService)**: the platform-standard stack. Gets buffering, gapless-ish transitions, playback speed, audio focus, headphone/BT controls, notification + lockscreen controls, and Android Auto compatibility essentially for free. Writing a custom player would be strictly worse.
- **Optimistic UI for destructive actions** (user-reported pain): delete/trash updates Room immediately, the API call runs behind it, failure rolls back with a toast. Applies to Drive delete (S24) and all Music mutations.

### 1.3 Required architectural changes (existing code)

- `ServiceLocator`: add `musicDatabase` (Room), `trackRepository`, `playlistRepository`, `downloadManager`, `playerController` (thin wrapper around a `MediaController` future).
- `HomeScreen`: tab enum becomes `MY_DRIVE, PHOTOS, MUSIC, PROFILE`; Trash becomes a Profile row → pushes existing `TrashTab` as a routed screen `trash`.
- Global `MiniPlayer` composable sits in `HomeScreen`'s Scaffold above the NavigationBar (visible on every tab when playback is active).
- `FileRepository.downloadUrl()/thumbnailUrl()`: implement LAN-first URL resolution (race `local_url` with 300 ms head start, fall back to `url`) — the player and download manager reuse this. This closes an existing P0 gap and makes streaming fast at home.

---

## 2. Backend changes (Go)

### 2.1 Schema (SQLite migration)

```sql
CREATE TABLE tracks (
  file_id      INTEGER PRIMARY KEY REFERENCES files(id) ON DELETE CASCADE,
  title        TEXT,            -- NULL => fall back to filename sans extension
  artist       TEXT,
  album        TEXT,
  album_artist TEXT,
  genre        TEXT,
  track_no     INTEGER,
  disc_no      INTEGER,
  year         INTEGER,
  duration_ms  INTEGER NOT NULL DEFAULT 0,
  bitrate      INTEGER,
  codec        TEXT,            -- "mp3","flac","aac","opus","vorbis","wav","alac"
  art_fid      TEXT,            -- SeaweedFS fid of embedded artwork (300px JPEG), NULL if none
  tag_status   INTEGER NOT NULL DEFAULT 0  -- 0 pending, 1 ok, 2 no_tags, 3 failed/corrupt, 4 unsupported
);
CREATE INDEX idx_tracks_artist ON tracks(artist);
CREATE INDEX idx_tracks_album  ON tracks(album);
CREATE INDEX idx_tracks_genre  ON tracks(genre);
```

### 2.2 Tag-extraction worker

New `internal/tags/worker.go`, cloned from `internal/thumbnail/worker.go`:
- Subscribes to the existing upload Kafka topic (or a new `tags` topic fed from the same producer call in the upload handlers).
- Filters on audio mime types + extension allowlist: `mp3 m4a aac wav flac ogg opus wma aiff alac`.
- Reads the file from SeaweedFS, parses with `github.com/dhowden/tag`; duration via lightweight per-format probing (or `tcolgate/mp3` etc. — do NOT shell out to ffmpeg; keep the ~100MB RAM budget).
- Resizes embedded artwork to 300px JPEG, stores in SeaweedFS, records `art_fid`.
- Writes `tracks` row with appropriate `tag_status`. Corrupt/unreadable → status 3; readable container but no tags → status 2 with duration only.
- On file delete/trash: `ON DELETE CASCADE` handles rows; artwork blob cleanup piggybacks on existing file-GC path.
- Backfill: on startup, enqueue extraction for any audio file lacking a `tracks` row (covers pre-existing libraries; powers S3 "indexing" state).

### 2.3 New endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/music/tracks?page&limit&sort&order&updated_since` | Paged track list joined with files (id, filename, size, mime, all tag fields, urls). `updated_since` (unix ts) enables delta sync. |
| GET | `/music/tracks/:id/artwork?size=88\|300` | Artwork JPEG; 404 if none; 202 while pending (mirrors thumbnail contract). |
| GET | `/music/status` | `{total_audio, indexed, pending, failed}` — powers the indexing banner (S3). |

Streaming uses the **existing** `GET /files/:id/download` — verify it honors HTTP `Range` requests (required for ExoPlayer seek). If SeaweedFS proxying doesn't pass Range through, add it; this is the one hard backend requirement for streaming.

Playlists are **client-local by design** (single-client product per DESIGN_DOC; keeps backend simple). Revisit only if a second client appears.

---

## 3. Android — file-level changes

### 3.1 New files

```
data/local/music/
  MusicDatabase.kt            Room DB, v1
  TrackEntity.kt / TrackDao.kt          (FTS4 companion table for search)
  PlaylistEntity.kt / PlaylistTrackEntity.kt / PlaylistDao.kt
  DownloadEntity.kt / DownloadDao.kt
  QueueStateEntity.kt / QueueDao.kt
data/remote/api/MusicApi.kt   the 3 new endpoints
data/repository/
  TrackRepository.kt          sync + queries + favorites
  PlaylistRepository.kt       CRUD, ordering, shuffle/restore
  DownloadRepository.kt       façade over DownloadManager + DAO
playback/
  PlaybackService.kt          Media3 MediaSessionService
  PlayerController.kt         app-facing wrapper (MediaController, StateFlows)
  QueueManager.kt             queue model, persistence, shuffle/repeat logic
  MusicSyncWorker.kt          WorkManager: delta sync + indexing status poll
  DownloadWorker.kt           per-track download with progress
ui/music/
  MusicTab.kt                 chip-nav host (Home/Songs/Albums/Artists/Genres/Playlists)
  MusicHomeScreen.kt + MusicHomeViewModel.kt          (S1–S3)
  SongsScreen.kt + LibraryViewModel.kt                (S4, S5, S11)
  AlbumsScreen.kt / AlbumDetailScreen.kt              (S6, S7)
  ArtistsScreen.kt / ArtistDetailScreen.kt            (S8)
  GenresScreen.kt                                     (S9)
  MusicSearchScreen.kt + MusicSearchViewModel.kt      (S10)
  PlaylistsScreen.kt / PlaylistDetailScreen.kt + PlaylistViewModel.kt  (S12–S16)
  NowPlayingScreen.kt + NowPlayingViewModel.kt        (S17, S18)
  QueueSheet.kt                                       (S19)
  TrackContextSheet.kt / AddToPlaylistSheet.kt / SortFilterSheet.kt    (S20, S15, S11)
  DownloadsScreen.kt + DownloadsViewModel.kt          (S21)
ui/components/music/
  MiniPlayer.kt  TrackRow.kt  ArtworkTile.kt  StatusGlyph.kt  AlphabetRail.kt  EqBars.kt
ui/theme/  (rewrite Color.kt/Type.kt/Theme.kt per design system: brass #E9B44C accent,
           dark surfaces #0C0D11/#14161C/#1C1F27, Sora display + Albert Sans body,
           dynamicColor default OFF to keep brand)
```

### 3.2 Modified files

- `HomeScreen.kt` — 4-tab nav (Music replaces Trash), MiniPlayer slot, per-tab top bars move into tabs (Music owns its own header).
- `navigation/Screen.kt`, `NavGraph.kt` — add `trash`, `music/album/{id}`, `music/artist/{name}`, `music/genre/{name}`, `music/playlist/{id}`, `music/search`, `music/downloads`, `nowPlaying` (full player as a route with slide-up transition).
- `ProfileTab.kt` — add Trash row (count badge), Shared links, Music downloads, Wi-Fi-only toggle, LAN status line (S25).
- `di/ServiceLocator.kt` — new singletons above.
- `FileRepository.kt` — LAN-first URL resolver (shared utility).
- `MyDriveTab`/`FolderScreen`/`TrashTab` ViewModels — optimistic delete/restore with undo (S24).
- `AndroidManifest.xml` — `PlaybackService` (`foregroundServiceType="mediaPlayback"`), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- `libs.versions.toml` / `build.gradle.kts` — media3-exoplayer, media3-session, media3-exoplayer-hls (no), okhttp-datasource, room-runtime/ktx/compiler (ksp), workmanager, reorderable-compose (`sh.calvin.reorderable`), palette-ktx (artwork tint).

No deletions; `TrashTab.kt` is reused at its new route.

---

## 4. Data model (Room)

```kotlin
TrackEntity(
  fileId: Long PK, filename, title?, artist?, album?, albumArtist?, genre?,
  trackNo?, discNo?, year?, durationMs, bitrate?, codec?, sizeBytes,
  hasArt: Boolean, tagStatus: Int, createdAt: Long,
  favorite: Boolean = false, lastPlayedAt: Long? = null, playCount: Int = 0)
// + TrackFts(title, artist, album, filename) FTS4 mapped table

PlaylistEntity(id PK autogen, name, createdAt, updatedAt, artHash)
PlaylistTrackEntity(playlistId, fileId, position: Long)   // position: gapped LongS (1024,2048,...)
// custom order IS `position`. Sorting by name/artist/etc is a display mode stored
// per playlist (sortMode column) — switching to "Custom" restores positions untouched.
// "Shuffle playlist order" writes a sessionOrder list in memory + a shuffledSeed column;
// restore = clear seed. Positions are never overwritten by shuffle.

DownloadEntity(fileId PK, state: PENDING|RUNNING|PAUSED|FAILED|DONE,
  bytesDone, bytesTotal, filePath, errorMsg?, updatedAt)

QueueStateEntity(singleton row: json of fileIds, currentIndex, positionMs,
  shuffleOn, shuffleOrder: json?, repeatMode, sourceLabel)  // persisted debounced 500ms
```

Storage: downloads in `context.filesDir/music/{fileId}.{ext}` (internal, excluded from backup via existing `backup_rules.xml` — add exclusion). Artwork cached by Coil (disk cache OK) keyed on `/music/tracks/{id}/artwork`.

Migration note: Room v1 fresh install — no migrations needed at launch; set `fallbackToDestructiveMigration` OFF and write real migrations from v2 on.

---

## 5. Audio system

### 5.1 Playback pipeline

- `PlaybackService : MediaSessionService` owns one ExoPlayer built with an `OkHttpDataSource.Factory` that (a) injects the auth header via the existing `AuthInterceptor`, (b) resolves LAN-first URLs. A `ResolvingDataSource` maps `cloudrive://track/{fileId}` → local file path if `DownloadEntity.state == DONE`, else the streaming URL. **The queue never cares whether a track is local or remote.**
- Buffering: `DefaultLoadControl` with `bufferForPlaybackMs=1000` (fast start, S18 goal), `maxBufferMs=60000`. Show buffered position on the scrubber from `player.bufferedPosition`.
- Seek requires server Range support (see §2.3).
- Background playback: Media3 notification with artwork; audio focus + becoming-noisy handled by ExoPlayer defaults.
- Playback speed: `player.setPlaybackSpeed(0.5–2.0)`, chips at 0.5/0.8/1.0/1.2/1.5/2.0 (S17).

### 5.2 Error recovery & network transitions (S18, S23)

- `Player.Listener.onPlayerError`: on HTTP/timeout errors retry with exponential backoff (1s,2s,4s,8s,16s; max 5), resuming from `currentPosition` — buffer keeps playing meanwhile. Surface attempt count in the toast.
- After 5 failures: skip to next track with a "Couldn't stream X — skipped" snackbar + Retry (never silently stop the session).
- `ConnectivityManager.NetworkCallback`: on network change, if a stream stalled → immediate retry (re-racing LAN vs WAN, since the network TYPE may have changed); on lost → enter offline mode (S22): a flag in `PlayerController` that UI observes; library screens add the dim/cloud_off treatment and the "Downloaded only" quick filter; queue auto-skips non-downloaded tracks with a subtle notice.
- Corrupt file (decoder exception on a DONE download): mark `tagStatus=3` locally, show S23 treatment, offer re-download (delete local + re-enqueue).

### 5.3 Download manager (S21)

- `DownloadWorker` (WorkManager, unique work per fileId, constraint = Wi-Fi if toggle on): streams via OkHttp to `filePath + ".part"`, publishes progress to `DownloadEntity` every ~200 ms, atomic rename on completion.
- Resume: `.part` size → `Range: bytes=N-` request (server already serves ranges per §2.3). "Failed at 41% · kept partial" (S21) resumes, not restarts.
- Queue: WorkManager sequential (max 2 concurrent), user can pause (cancel work, keep .part) / resume / cancel (delete .part).
- Remove download: delete file, DownloadEntity → absent; cloud copy untouched (S20 "Remove download").
- Eviction: none automatic; Downloads screen shows total and per-file sizes with "Remove all".

### 5.4 Queue management (S19)

`QueueManager` holds the canonical `List<Long>` + ExoPlayer timeline in lockstep:
- add / play-next (insert after current) / remove (swipe) / drag-reorder / clear / save-as-playlist (writes PlaylistEntity from current order).
- Shuffle ON: generate permutation of remaining indices (seeded), current track stays put; OFF restores canonical order at the current track. Repeat: OFF/ALL/ONE straight to ExoPlayer.
- Persistence: debounced 500 ms snapshot to `QueueStateEntity`; restored (paused, position intact) on process death/app relaunch.

---

## 6. Playlist system (S12–S16)

- CRUD + duplicate = simple DAO ops; duplicate copies rows with same positions.
- **Ordering**: gapped positions (1024, 2048, …). Drag-drop writes one row's position = midpoint of neighbors; renumber the whole list only when a gap collapses (<1). One UPDATE per drag = efficient for large playlists.
- **Drag-and-drop**: `sh.calvin.reorderable` LazyColumn integration; haptic on pickup; a11y fallback actions "Move up/Move down" on the drag handle.
- **Shuffle playlist order vs shuffle playback**: playback shuffle lives in QueueManager (session-only). "Shuffle playlist order" (S14) stores `shuffledSeed` and displays a derived permutation; "Restore custom order" clears the seed. Positions are immutable through both → custom order always recoverable (hard requirement).
- **Sort by name/artist/album/duration/dateAdded**: per-playlist `sortMode` display transform; selecting "Custom" returns to positions. Editing (drag) while sorted prompts: "Switch to custom order to reorder?" — converts current sorted arrangement into new positions only on explicit confirm.
- Move between playlists = insert into target (+ optional remove from source) in one transaction.

## 7. Sync & indexing (S3)

- `MusicSyncWorker`: on app open + every 6 h + manual pull-to-refresh. Calls `/music/tracks?updated_since=lastSync` and upserts/deletes in Room (deletes: server omits trashed/deleted files; reconcile by full id-list checksum weekly or when counts drift).
- While `/music/status.pending > 0`: poll every 5 s, show indexing banner with progress; rows appear incrementally as pages sync (skeletons only for never-synced cold start).
- Trash/restore/delete of audio in Drive tabs writes through to Room immediately (optimistic) so Music updates in the same frame.

## 8. Performance

- Paging 3 (or manual paged LazyColumn, keep it simple: page size 200, prefetch 2 pages) over Room; stable keys = fileId; `contentType` hints.
- Search: Room FTS4 (`MATCH`) over title/artist/album/filename, 300 ms debounce (matches existing SearchViewModel pattern), zero network.
- Alphabet rail: precomputed first-letter → index map from a single GROUP BY query.
- Artwork: Coil, 88 px request size in lists, 600 px in player; disk+memory cache; placeholder = deterministic gradient from `fileId` hash (matches design).
- Aggregates (counts, durations) via DAO SQL, never in-memory folds over full lists.
- Memory: single ExoPlayer instance; release artwork bitmaps via Coil lifecycle; queue stores ids not entities.

## 9. Testing plan

**Unit (JVM)**: QueueManager (add/remove/reorder/shuffle-restore invariants, persistence round-trip); gapped-position algorithm (drag storms, gap collapse renumber); LAN-first resolver (LAN wins, LAN timeout, both fail); sync reconciler (adds, deletes, tag updates); sort/filter query builders; playback-speed and repeat state machines.

**Integration (androidTest)**: Room DAOs incl. FTS queries + migrations; DownloadWorker against MockWebServer (progress, pause/resume with Range, failure → partial kept, retry resumes); PlaybackService with ExoPlayer test utils (RobolectricMediaTest or device): local vs remote resolution, error → backoff → skip; sync worker end-to-end against MockWebServer fixtures.

**UI (Compose test)**: TrackRow states (downloaded/downloading/failed/offline-dim); multi-select flows; drag reorder (semantics actions); mini player ↔ full player navigation; empty/indexing/offline banners; TalkBack contentDescriptions present; snackbar undo restores optimistic delete.

**Edge cases**: 0-byte and corrupt audio; file with no tags at all (S2 fallbacks: title=filename, Unknown artist bucket); 10k-track library scroll performance (macrobenchmark: no frame >16 ms during fling); token refresh mid-stream (401 → TokenAuthenticator → seamless); server unreachable at launch with populated Room (app fully browsable + downloads playable); quota-full during nothing (music is read-only vs quota); playlist with deleted tracks (rows hidden, positions preserved); process death mid-download and mid-playback (both resume).

**Backend (Go)**: tag worker table-driven tests per format incl. corrupt samples; Range request correctness (offset, suffix, 416); `/music/tracks` pagination + `updated_since`; artwork 202→200 lifecycle.

## 10. Implementation roadmap

Each milestone ships something usable; later milestones don't rework earlier ones.

1. **M1 — Backend metadata pipeline** (isolated, unblocks everything): tracks table, tag worker + backfill, 3 endpoints, Range-request verification/fix. Exit: fresh upload appears in `/music/tracks` with tags within seconds.
2. **M2 — App foundation**: theme rewrite (new color/type system, dynamicColor off), nav restructure (Music tab shell + Trash → Profile route), Room DB + sync worker, Songs list reading from Room with sort/filter + search + indexing banner. Exit: browsable library, no playback yet.
3. **M3 — Playback core**: PlaybackService, PlayerController, LAN-first streaming, MiniPlayer, full NowPlaying screen, queue (in-memory + persistence), notification/background playback, error recovery. Exit: streaming works end-to-end incl. seek + retries.
4. **M4 — Downloads & offline**: DownloadWorker + Downloads screen, ResolvingDataSource local-first, offline mode detection + UI treatments, Wi-Fi-only setting. Exit: airplane-mode playback of downloaded tracks.
5. **M5 — Playlists**: CRUD, detail screen, drag reorder, shuffle-order/restore, add-to-playlist + save-queue-as-playlist, sort modes. Exit: full playlist feature set.
6. **M6 — Library breadth + polish**: Albums/Artists/Genres browse + detail screens, Music Home landing (recently played/added via playCount/lastPlayedAt), multi-select actions, empty states, motion pass (shared-element mini→full, reorder springs), a11y audit.
7. **M7 — Drive-side wins**: optimistic delete + undo everywhere, LAN-first for Drive downloads/thumbnails, Profile additions (Trash count, shared links stub, music storage). Exit: reported delete-lag fixed.

Risk notes: the only external unknowns are (a) Range support through the SeaweedFS proxy (probe in M1 day 1) and (b) duration extraction without ffmpeg for exotic formats — acceptable fallback: duration 0 until first playback reports it back.
