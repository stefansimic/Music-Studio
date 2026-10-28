# 0010 – Web client as a third frontend; use cases must be HTTP-exposable (REST + jobs)

Status: proposed · Date: 2026-10-28 · Details: [architecture §2, §12](../architecture.md)

## Context
A browser-based client is planned after CLI and GUI (later step, but it must not require reworking the core).
The web client is a third frontend over the same use cases. This puts requirements on the use-case layer now,
because retrofitting them later would change contracts that CLI and GUI already depend on.

Deployment (confirmed 2026-10-28): the server first runs **locally on the user's PC**, later on a **server in the
home LAN**. No TLS and no internet exposure are needed or supported. The server owns the media library, an upload
inbox and the output folders. The browser is only a UI. All paths in requests are **server-side paths**. Because the
browser may run on another machine than the library, **file upload and download are required**, and the browser can
**browse server folders** (inside the configured roots).

## Review of the current design against HTTP
Already suitable:
- Use cases take and return immutable records, never print, and hold no UI state. Services are stateless and thread-safe.
- Dry-run → plan → apply is a natural two-step HTTP flow.
- Per-item problems are `Issue`s in results, not exceptions, so they map to 200 + body rather than transport errors.

Gaps:
1. **Use cases are synchronous/blocking and progress is a callback** (`ProgressListener`, `Cancellation`). HTTP needs
   asynchronous jobs.
2. **Streaming sinks** (`ScanMediaUseCase` pushes `ScanItem`s into a sink; 10 000+ files). One JSON response is too big.
3. **A plan is passed back into `ApplyExportUseCase` as a record.** Round-tripping it through a client is wasteful and
   lets a client tamper with it.
4. **Wire format.** Core records use `Path`, `Optional`, sealed `FieldValue`, enums. No reflection-based JSON
   binding is allowed (ADR 0005).
5. **Concurrency across clients.** "One apply per output root" is an `IllegalStateException` today; with several
   browsers it becomes a normal, expected condition.
6. **Playback.** `PlaybackEngine` (ADR 0007) is server-side. A browser plays audio itself.
7. **Security.** A server that reads and writes the real library is a new attack surface (DNS rebinding, CSRF,
   path traversal, accidental exposure to the LAN).
8. **Cover art / binary data** (`readCover`) needs byte endpoints, not JSON.

## Decision
1. **Core stays transport-neutral.** No HTTP, JSON or servlet types in core. The existing use-case interfaces remain
   the only contract; HTTP is an adapter on top of them.
2. **New modules** (added by their own stories, not now):
   - `music-studio-api`: wire format. Hand-written JSON codecs (Parsson, reflection-free) for core records, a stable
     error model, and the API contract `docs/api/openapi.yaml` (hand-maintained, reviewed with every use-case change).
     Codecs are shared with the CLI (`--json` output), so CLI and API render identical JSON.
   - `music-studio-web`: HTTP server frontend. Depends on core, app, api. It contains the job runner, the plan/scan
     result store, auth and path sandboxing. No business logic.
   - `music-studio-web-client`: static SPA, only talks to the REST API. It does not re-implement pattern parsing,
     transliteration or validation; previews call `ProposeMetadataUseCase` via the API (debounced).
3. **Server technology is not fixed yet.** Constraint: reflection-free and native-image friendly (candidates: JDK
   `com.sun.net.httpserver` + virtual threads, Helidon SE, Javalin). Decide in a spike when the web story starts.
4. **Resource model (REST, not method-per-endpoint RPC):**
   - `POST /api/v1/scans` → job; `GET /scans/{id}`, `GET /scans/{id}/items?cursor=…` (paged), `DELETE /scans/{id}`
   - `POST /api/v1/proposals` (pure, synchronous)
   - `POST /api/v1/plans` → job → plan resource `GET /plans/{id}` (immutable, server-held)
   - `POST /api/v1/plans/{id}/apply` → job (explicit; carries the plan id, never the plan body)
   - `POST /api/v1/exports/{id}/undo` → job
   - `GET /api/v1/jobs/{id}` (status), `GET /jobs/{id}/events` (SSE progress), `DELETE /jobs/{id}` (cancel →
     `CancellationSource.cancel()`)
   - `GET/PUT /api/v1/profiles|presets|settings|genres`
   - `GET /api/v1/fs` (folder browse), `POST /api/v1/uploads` (+ `PUT …/files/{name}`), `GET /exports/{id}/files|archive`
     (see decisions 13–15)
   - `GET /api/v1/media/{ref}/cover`, `GET /media/{ref}/stream` (HTTP Range) for artwork and the web player
   - Long-running operations always return `202 Accepted` + job resource. Pure/fast use cases answer synchronously.
5. **Jobs live in the web module**, not in core. A `JobRunner` wraps a blocking use-case call, maps
   `ProgressListener` → SSE events and the job's `CancellationSource`. The GUI keeps its own `Task` wrapper; both
   use the same use-case contract.
6. **Plans are server-held and referenced by id.** `ApplyExportUseCase` still receives the full `ExportPlan` record
   (core unchanged); the web module resolves id → plan. Apply keeps re-fingerprinting sources (§7.5), so a stale plan
   is rejected regardless of transport. Plans expire (TTL, in memory or app data dir).
7. **Streaming use cases need a paged consumer.** The web module's sink appends to a bounded `ScanResultStore`
   (in memory, spilled to the app data dir for very large scans) that serves cursor pages. Core signature unchanged.
8. **Concurrency conflict is a result, not an accident.** The web module serialises mutating jobs per output root
   and answers `409 Conflict` with the blocking job id. The core check stays as the last line of defence.
9. **Error model:** RFC 9457 `application/problem+json`. Contract violations from `Require` → `400`/`422` (should be
   prevented by request validation), `Issue`s stay in the 200/job result body, unknown ids → `404`, busy → `409`.
   Mapping table mirrors the CLI exit codes (§11).
10. **Security defaults (plain HTTP, trusted LAN):** bind to `127.0.0.1` by default. Binding to a LAN address needs an
    explicit setting **and** a configured access token (generated on first start, stored in the app data dir). The
    token is required for every request in LAN mode and for every state-changing request in localhost mode. Plain
    HTTP means the token is visible to anyone who can sniff the LAN; this is accepted for a home network and
    documented, and TLS can later be added by a reverse proxy without API changes. Validate `Host` and `Origin`
    (DNS rebinding/CSRF). **All paths are resolved against configured roots** (`library`, `inbox`, `output`);
    anything outside, including via `..` or symlinks, → `403`. Data-safety rules from CLAUDE.md are unchanged:
    originals are never touched, apply needs an explicit apply request, dry-run is the default flow.
13. **Folder browsing:** `GET /api/v1/fs?root={rootId}&path=…` lists subfolders and media files (name, size, type,
    no content) inside a root, read-only, paged, same sandbox as above. Hidden entries follow the scan rule
    (`SkipReason.HIDDEN`). The browser never receives a path outside the roots.
14. **Upload** (`POST /api/v1/uploads` → `PUT /uploads/{id}/files/{name}`, streamed, resumable-friendly via
    `Content-Range` later): files land only in the server's **inbox root**, in a fresh per-upload folder, written
    to a temp name and moved without replacing (same safe-write rules as §8.2). Name collisions get a suffix, never
    overwrite. Limits: max file size, free-space check, allowed extensions (`MediaFormat`). After upload the files
    are ordinary library input: scan, tag and export work on them as on any other folder, and **the originals rule
    applies to uploaded files too** (they are never modified; results go to the output root). Uploads can be deleted
    by the user from the inbox only (explicit `DELETE`, nothing else in the API deletes media files).
15. **Download:** `GET /api/v1/exports/{id}/files/{ref}` streams one exported file (Range supported);
    `GET /api/v1/exports/{id}/archive` streams a zip of the export (stored, not compressed: MP3s do not compress),
    generated on the fly, not buffered in memory. Download is limited to files recorded in the export journal and
    to the library/inbox/output roots; the browser cannot download arbitrary server files.
11. **Playback in the web client is client-side** (`<audio>`/`<video>` against the Range stream endpoint). The
    `PlaybackEngine` port remains for GUI/CLI. The queue/`PlayerUseCase` logic that is pure state may be reused by the
    API later; this is decided in story 011/web story.
12. **Identifiers in the API are opaque** (`scanId`, `planId`, `fileRef`), not raw paths in URLs, which avoids
    encoding problems (Cyrillic names, `%`, `#`) and traversal tricks. Paths appear only in JSON bodies/fields.

## Alternatives
- *GraphQL / gRPC*: nicer for streaming or typed clients, but a browser SPA and curl-friendly debugging favour plain
  REST + SSE; OpenAPI gives typed clients anyway.
- *Embed the server in the GUI/CLI process only*: fine as a mode (`musicstudio serve`), but a separate module keeps
  JavaFX and picocli out of it.
- *Put job handling into core*: would make CLI and GUI pay for a concept they do not need. Rejected; core stays
  synchronous.
- *Client sends the plan back*: simpler server, but large payloads and tamperable. Rejected.
- *Server-rendered UI (htmx etc.)*: would put presentation into the server module and weaken the "frontends only
  render" rule less clearly. Not excluded, but then still as a client of the same API.

## Consequences
- **No change to core use-case interfaces is required now.** The web needs are covered by the web module. This is the
  main result of the review and should stay true. The cost of being wrong is high, so new use cases must keep it true:
  request/response records, no hidden state, no UI callbacks other than `ProgressListener`/`Cancellation`/sink.
- Every new core record must be expressible in JSON without reflection. Keep value types simple (strings, numbers,
  lists, enums, tagged variants); avoid `Object`-typed fields and map keys that are not strings.
- Stories that add use cases get a standing acceptance criterion: *reachable through the API contract*
  (`openapi.yaml` updated, codec test with a golden JSON file).
- Library, inbox and output roots become a first-class setting (`AppSettings`), needed for the path sandbox and for
  upload/download. CLI/GUI can use them as defaults too.
- Upload and download are new write/read paths on the user's real storage. They need their own storage-port
  operations (inbox write with no-replace move, journal-restricted read) and contract tests in `storage`, since
  "never overwrite or delete" must hold there too.
- The CLI `--json` output and API responses share codecs, so they cannot drift.
- Story 011 splits: the playback port/engine for desktop, and browser playback as part of the web story.

## Resolved questions (2026-10-28)
1. Deployment: local first, later a server in the home LAN. No TLS. Upload and download are required.
2. Folder browsing on the server: yes (decision 13).
3. Standing acceptance criterion on every story that adds or changes a use case: yes. *Reachable through the API
   contract* means `docs/api/openapi.yaml` is updated and a golden-JSON codec test exists. Stories 001–012 get a
   one-line pointer to it (see `docs/stories/README.md`).

## Still open
1. Single user or several? Assumed **single user, one token**. Several users would need accounts and per-user roots.
2. Upload size and quota defaults, and whether an inbox is cleaned automatically (assumed: never automatically).
3. Do uploads from the browser need to keep relative folder structure (folder drag & drop), or only flat file
   lists? Assumed: structure is kept (`webkitRelativePath`), flattened only if the user chooses.
