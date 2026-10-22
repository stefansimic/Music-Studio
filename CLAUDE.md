# Music-Studio

Java tool for organizing a music library for playback on other devices (car stereo, phone, MP3 player, PC):
writing tags (ID3), genres, cover art, and file/folder structure. Later also MP4 videos (movies, TV episodes).
A media player with a library is a secondary feature.

## Tech stack
- Java 25 (LTS) on GraalVM JDK, Maven multi-module build
- Distribution: CLI as GraalVM **native image** (Maven profile `native`); GUI via `jpackage` (bundled jlink runtime),
  GluonFX native image as a later experiment
- Tagging: jaudiotagger (behind a port — must stay swappable, e.g. for mp3agic)
- CLI: picocli · GUI: JavaFX
- Tests: JUnit 5, AssertJ, ArchUnit (architecture rules)

## Architecture: ports & adapters (strict)
```
music-studio-core              domain model, use cases, ports (interfaces). NO external dependencies.
music-studio-tag-jaudiotagger  adapter: implements tag ports with jaudiotagger
music-studio-cli               picocli frontend  → depends on core only (+ wiring)
music-studio-gui               JavaFX frontend   → depends on core only (+ wiring)
```
Rules:
- `core` never imports jaudiotagger, mp3agic, picocli, JavaFX or any I/O library type. Ports use core's own types
  (e.g. `TrackMetadata`, `Genre`, `CoverArt`). Library types never leak through a port.
- Frontends contain no business logic: they parse input, call a use case, render the result.
  Anything the CLI can do the GUI does through the same use case, and the other way round.
- Adapters are chosen in exactly one place (composition root per frontend). No service locators, no statics.
- Each format (MP3 now; MP4 video later, maybe FLAC/M4A) is an adapter concern. Core models tags format-neutrally.
- Tag versions (ID3v1/v1.1, ID3v2.2/2.3/2.4) are user-selectable write options (core type), defaulted by device
  profile. Adapters declare which versions they can write; core validates before writing.
- **Native-image friendly**: no reflection-based frameworks (no Spring/Guice — manual wiring), no dynamic class
  loading, no runtime bytecode generation. If a library needs reflection, its config lives in the adapter module under
  `src/main/resources/META-INF/native-image/` (generate with the tracing agent, commit the result).
- Architecture rules are enforced by ArchUnit tests in `core`. Never weaken them to make code compile.
- Architecture decisions are recorded as ADRs in `docs/adr/NNNN-title.md`.

## Data safety (non-negotiable — this tool works on the user's real media library)
- **Original files are never modified, moved or deleted.** All writing happens on copies in an output folder (export).
- Every mutating operation supports **dry-run** and the CLI defaults to it (`--apply` to write).
- Never overwrite or delete an audio file without a backup or a verified safe write (write to temp, verify, atomic move).
- Never discard existing tag data silently. Unknown/unmapped frames are preserved unless removal is explicitly requested.
- Renames/moves: detect collisions, never overwrite, produce a plan first, make the plan reversible (undo log).
- Tests never touch files outside a temp directory. Test fixtures are copied before modification.

## Conventions
- Package root: `dev.rs.slava.icxc.musicstudio` (`….musicstudio.core.*`, `….musicstudio.tag.jaudiotagger.*`, `….musicstudio.cli.*`, `….musicstudio.gui.*`)
- Immutable domain objects (records), `Optional` for absent tag values, no `null` across module boundaries.
- Use cases return result objects (incl. warnings/changes), they don't print.
- Stories live in `docs/stories/`, ADRs in `docs/adr/`.

## Workflow
`/story` → `architect` agent (plan/ADR) → implement (`/implement`, using the `audio-metadata` skill) →
`tester` agent → `reviewer` agent.

## Commands
- Build & test: `mvn -q verify`
- Single module: `mvn -q -pl music-studio-core verify`
- Native CLI: `mvn -Pnative -pl music-studio-cli -am package` (requires GraalVM; builds only for the current OS)
