# 0008 – Local persistence as versioned JSON files (Parsson streaming API) behind store ports

Status: accepted · Date: 2026-10-22 · Details: [architecture §13](../architecture.md)

## Context
We need to persist export journals (004), device profiles (006), presets and recent patterns (007), the genre
vocabulary (008), and a library index for up to 50 000 tracks (011). Profiles and presets must be human-readable
and backup-friendly. Everything must work in a native image.

## Decision
- Store ports in core (`ExportJournalStore`, `ProfileStore`, `PresetStore`, `GenreStore`, `LibraryIndexStore`),
  implemented in `music-studio-storage`.
- Format: JSON (`jakarta.json-api` + `org.eclipse.parsson`). The streaming/object-model API is used with hand-written
  mappers, so there is no reflection binding. The provider is instantiated directly.
- Every file carries `"schemaVersion"`, and loaders migrate old versions (007 requirement). Invalid files produce
  `Issue`s and never prevent the built-ins from working.
- Writes are atomic: temp + move in the same directory. Journals are append-only JSON lines with fsync per entry.
- Library index (011): one JSON-lines snapshot, loaded into memory and queried in memory. At 50 000 tracks this is
  ≈15 MB, which is fine.
- Locations follow the XDG/APPDATA/Application Support conventions and are resolved in `app`.

## Alternatives
- *Jackson databind*: convenient, but reflection-based (native metadata for every record).
- *YAML (SnakeYAML Engine)*: nicer for hand-editing, but there is no need for two formats.
- *SQLite (xerial sqlite-jdbc) / H2 for the library*: real queries and incremental updates, but a native library or
  heavy reflection. It can replace `LibraryIndexStore` later if the in-memory approach fails.
- *Hand-written JSON parser*: zero dependencies, but a parser is not worth maintaining.

## Consequences
- Mapper code per record (small, explicitly tested via store contract tests).
- The library index is rewritten as a whole on save. That is acceptable for a rescan-driven library, and the store
  port allows an incremental backend later.
