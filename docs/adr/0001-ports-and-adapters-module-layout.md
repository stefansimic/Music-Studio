# 0001 – Ports & adapters with a shared composition root and filesystem behind ports

Status: accepted · Date: 2026-10-22 · Details: [architecture §2–3](../architecture.md)

## Context
Several technologies are expected to change: the GUI toolkit, the tag library (jaudiotagger → maybe mp3agic), the
MP4 tag library, the playback backend and possibly persistence. CLI and GUI must offer the same capabilities. The tool
works on the user's real library, so the write path must be small and easy to test. The CLI is built as a GraalVM
native image.

## Decision
- Strict ports & adapters. `music-studio-core` holds the domain records, the ports (driven, `core.port`), the
  use-case interfaces (driving) and their `final` `*Service` implementations. It has no dependencies at all.
- Modules: `core`, `tag-jaudiotagger`, `storage`, `app`, `cli` now. `gui` (010), `image-imageio` (009),
  `player-javafx` (011) and `tag-mp4` (012) are added by their stories.
- **Core does no I/O.** Listing, fingerprinting, temp files, moves, journals and config files go through ports and
  are implemented in `music-studio-storage`. `java.nio.file.Path` is allowed in core as a value type only.
- **One shared composition root, `music-studio-app`** (`MusicStudioFactory` → `MusicStudio` record of use cases),
  used by both frontends. Frontend-only adapters (the JavaFX player) are passed in by the GUI's `GuiMain`.
- Feature-oriented core packages (`scan`, `extract`, `export`, …) on top of shared `model`/`tag`/`pattern`/`task`/
  `port` packages, with layering enforced by ArchUnit.
- No JPMS `module-info` for now. Boundaries are enforced by ArchUnit rules (A1–A11) and by Maven enforcer, which
  forbids any non-test dependency in core.

## Alternatives
- *Per-frontend composition roots*: what CLAUDE.md literally says. Rejected because the wiring would be duplicated
  and CLI and GUI could drift apart. "Exactly one place" is preserved, since it is `app` plus the GUI's player.
- *I/O directly in core via `java.nio.file.Files`*: fewer interfaces. Rejected because it makes use cases untestable
  without a real disk, and it scatters the safety-critical write logic across services.
- *JPMS modules with qualified exports*: stronger compile-time encapsulation. Postponed because of extra friction with
  test-jars, contract tests on the module path and a non-modular jaudiotagger. Can be added later without API changes.
- *Hexagonal with a separate `core-api` module*: one more module with no current benefit.

## Consequences
- Every use case is unit-testable with in-memory fakes. Fakes must pass the same contract tests as the adapters.
- Adding a library means one new adapter module plus one line in `MusicStudioFactory`.
- The `storage` module is safety-critical (no-replace move, temp handling) and gets its own contract test.
- `app` depends on all non-UI adapters, so frontends see adapter classes on their classpath. ArchUnit rule A9 stops
  them from being used.
