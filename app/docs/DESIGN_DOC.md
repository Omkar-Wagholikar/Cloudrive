# Cloudrive — UI/UX Design Review & Feature Roadmap

Prepared as input for a design pass. Covers: what the app currently does, what the
backend can already do that the UI doesn't expose, and where the visual design is
generic/unfinished.

---

## 1. What this app is

Cloudrive is a self-hosted Google-Drive-style client for a Go backend
(`CloudDrive`, see `API_REFERENCE.md`). The backend is built for a home server /
Termux-on-Android box — SQLite + SeaweedFS + Kafka thumbnail pipeline — and the
Android app is the only client. There is no web app and no iOS app, so this app
carries the full weight of the product's UX.

Stack: Jetpack Compose, Kotlin, Retrofit/OkHttp, Coil, Navigation-Compose. No DI
framework (manual `ServiceLocator`). minSdk 26.

---

## 2. Current screens (as built today)

| Screen | Route | What it does |
|---|---|---|
| Auth | `auth` | Login / register, with an editable **server URL** field (self-hosted, so users must point the app at their own box) |
| Home | `home` | Bottom-nav shell hosting 4 tabs: My Drive, Photos, Trash, Profile |
| My Drive tab | (in Home) | Root-level folder + file list, FAB to upload/create folder |
| Folder | `folder/{folderId}` | Drills into a folder: breadcrumb, subfolders, files |
| Photos tab | (in Home) | 3-column square grid of thumbnailed files (images), tap to open |
| Trash tab | (in Home) | List of soft-deleted files, restore / permanently delete |
| Profile tab | (in Home) | Username, server URL, quota bar (linear progress), logout |
| Search | `search` | Debounced (300ms) filename search, results as file list |

Per-file actions today (via overflow menu): **Download, Rename, Share, Move to
Trash**. Share creates a link and immediately opens the OS share sheet — there's
no way to view/manage links you've already created.

---

## 3. Backend capability vs. UI exposure (the gap)

The Go server (`README.md` / `openapi.json`) supports several things the UI never
surfaces. This is the highest-leverage list for "what to build next" — it's
backend work already paid for.

| Capability | Backend status | UI status |
|---|---|---|
| **Move file to another folder** | `PATCH /files/:id` with `folder_id`; `FileRepository.moveFile()` implemented | **Done.** "Move to…" menu item + folder-picker sheet (`MovePickerSheet`) on My Drive/Folder/Search |
| **Manage existing share links** | Links are created (`POST /files/:id/share`); listed via `GET /shared` (server truth, not client-inferred); revoked via `DELETE /shared/:token` | **Done.** "My Shared Links" screen (Profile → My Shared Links) lists/revokes, backed by the real `GET /shared` endpoint |
| **Share link expiry** | `POST /files/:id/share` takes `expires_in` (seconds) | **Done.** Expiry picker (never / 1h / 24h / 7d) in the share sheet |
| **LAN-accelerated transfer** | Every file/upload response includes `local_url`; server also exposes `GET /network` for LAN IPs and a cheap unauthenticated `GET /ping` (reachability + latency probe); README recommends a 300ms-timeout race between `local_url` and `url` | **Done.** `LanResolver` races a `GET /network`-derived LAN candidate against the configured server URL (300ms timeout via `GET /ping`) once per session; `FileRepository.downloadUrl()`/`thumbnailUrl()` now resolve through it instead of hardcoding `tokenStore.serverUrl` |
| **Batch file operations** | `POST /files/batch` — move/trash/restore/delete up to 100 ids per call, 207 partial-failure response | **Done.** Multi-select (long-press to enter, tap to toggle) on My Drive/Folder/Search/Trash, with a contextual action bar for batch move/trash/restore/delete-forever |
| **Resumable/chunked upload progress recovery** | `GET /uploads/resumable/:id` for one session's offset; `GET /uploads/resumable` lists *all* the caller's open sessions | **Done.** My Drive checks for open sessions on load and shows a "resume this upload" banner per session; resuming still requires re-picking the file (Android can't re-read an arbitrary SAF `Uri` across process death), validated by filename+size match |
| **Purge all trash** | `DELETE /trash` permanently deletes everything in trash in one call — powers a "Free up space" flow | **Done.** "Empty trash" action in the Trash tab with a confirmation dialog |
| **Nested folder creation from within a folder** | `POST /folders` takes `parent_id` | Present in `CreateFolderDialog`, but verify it passes current folder as parent when inside `FolderScreen` (worth confirming during implementation) |
| **Storage quota enforcement feedback** | Server 507s (or similar) when quota exceeded | **Done.** Upload checks remaining quota (from `/me`) against file size before starting and shows an inline error if it would be exceeded |
| **File preview (video/audio/pdf) beyond images** | `GET /files/:id/download` streams any mime type | Photos tab only grids images; other types have no preview — tapping a non-image file elsewhere just triggers `ACTION_VIEW` (delegates to OS) |

**Net effect:** every P0/P1 gap in this table has UI now except in-app preview
for non-image files (P2 #12). The remaining open work is visual polish: richer
empty states, dark-mode tuning, in-app preview, and swipe gestures.

---

## 4. Visual design audit

This is the part most relevant to a design pass — the app currently has **no
custom design system**, it's the Compose Material3 starter template:

- `ui/theme/Color.kt` still has the default `Purple80/PurpleGrey80/Pink80` +
  `Purple40/PurpleGrey40/Pink40` — literally unedited from `File > New Project`.
- No brand color, no app icon customization mentioned beyond default adaptive
  icon XML.
- No dark-theme-specific tuning beyond whatever Material3 dynamic color gives
  for free.
- Typography (`ui/theme/Type.kt`) — not customized (default Material type scale).
- Empty states are plain `Text("No photos yet")` / similar — no illustration,
  no call-to-action.
- File list rows (`FileListItem.kt`) show 48dp icon/thumbnail, filename, size,
  last-modified date, and overflow menu. Selection checkboxes exist (long-press
  to enter multi-select) but there's no swipe-to-act gesture yet.
- Multi-select (batch move/trash/restore/delete) exists on My Drive/Folder/
  Search/Trash, but per-file share is still single-file only via the overflow
  menu — there's no batch share flow (arguably correct, since each share link
  is tied to one file).
- List/grid toggle exists everywhere (My Drive/Folder/Search/Trash/Photos),
  persisted per-screen.
- Sort (name/date/size/type) + file-type filter exist on My Drive/Folder/Search,
  applied client-side to the already-loaded list.
- Upload entry point is a single FAB — unclear from inventory whether it
  distinguishes "upload file" vs "create folder" vs "take photo" (worth a
  design decision either way: single FAB with expanding menu is the Drive
  pattern).

**Recommendation for the design pass:** treat this as a from-scratch visual
design exercise (color system, type scale, iconography, empty/error/loading
states, list vs. grid density) rather than incremental tweaks — there isn't an
existing design language to preserve.

---

## 5. Proposed feature backlog (prioritized)

**P0 — close backend/UI gaps (cheap, high value, no new API work needed)**
1. ~~"Move to folder" action + folder-picker dialog~~ **Done** — `MovePickerSheet`
2. ~~LAN-first URL resolution~~ **Done** — `LanResolver` races `local_url`
   (via a `GET /network`-derived candidate) against `url` with a 300ms timeout,
   using `GET /ping` as the reachability probe
3. ~~"My shared links" screen~~ **Done** — lists/revokes via the real `GET /shared`
   endpoint (server truth), not client-side inference
4. ~~Share expiry picker~~ **Done** — never / 1h / 24h / 7d

**P1 — UX depth**
5. ~~Multi-select mode across My Drive/Folder/Search/Trash~~ **Done** — long-press
   to enter selection, batch move/trash/restore/delete via `POST /files/batch`
6. ~~List/grid toggle everywhere, persisted per-tab~~ **Done**
7. ~~Sort (name/date/size/type) + basic filter (file type) controls~~ **Done**
   (client-side, on My Drive/Folder/Search)
8. ~~Resume-on-relaunch for interrupted resumable uploads~~ **Done** — banner
   surfaces open sessions from `GET /uploads/resumable`; resuming still needs
   the user to re-pick the file, since Android can't re-read an arbitrary SAF
   `Uri` after process death
9. ~~Pre-upload quota check with inline warning~~ **Done**
10. ~~"Free up space" — permanently purge all trash~~ **Done** (`DELETE /trash`)

**P2 — visual polish**
11. Real design system: brand palette, dark mode pass, empty-state illustrations
    — brand palette **done** (`ui/theme/Color.kt`/`Theme.kt`, teal seed)
12. In-app preview for PDFs/video/audio (not just images), not just an
    OS-handoff via `ACTION_VIEW`
13. Richer file rows: last-modified date, swipe-to-delete/share gestures —
    last-modified date **done**; swipe gestures still open

---

## 6. Notes for whoever designs this next

- This is a **self-hosted / LAN-first** product — that should shape visual
  language too (e.g., surface "connected via LAN" vs "via internet" status
  somewhere, since it's a real behavioral difference the backend already
  tracks via `/network`).
- Server URL is user-editable per install (see Auth screen) — there is no
  single fixed backend, so any "About/Help" design should account for
  self-hosted troubleshooting (e.g., surfacing connection errors clearly, since
  "can't reach the server" is a first-class error case here, not an edge case).
- See `API_REFERENCE.md` in this folder for the full endpoint/model reference,
  and `../openapi.json` for the machine-readable spec.
