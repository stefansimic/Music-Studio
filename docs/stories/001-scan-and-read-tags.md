# 001 – Scan a folder and read tags

**As a** user with many downloaded MP3s **I want** to point the app at a folder and see the existing metadata of every
file **so that** I know what's already tagged and what needs work.

## Context
Foundation for every other story: sets up `core` (domain model, read port, scan use case), the jaudiotagger
adapter and the CLI. Must read everything that exists in real-world downloads: ID3v1/v1.1, ID3v2.2/2.3/2.4, both at once,
or no tags at all.

## Acceptance criteria
- [x] Given a folder, when I scan it, then all `.mp3` files are found (optionally recursive, `--recursive`), sorted by path.
- [x] For each file I see: path, title, artist, album artist, album, track (n/total), disc (n/total), year, genre,
      comment, has-cover yes/no, duration, bitrate, and which tag versions are present (e.g. `ID3v1.1 + ID3v2.3`).
- [x] If both v1 and v2 exist and differ, both values are available (v2 is shown as primary, a marker flags the difference).
- [x] Files without tags are listed with empty fields, not skipped.
- [x] Unreadable/corrupt files are listed with an error message; the scan continues.
- [x] Non-MP3 files are ignored (count reported).
- [x] Reading never modifies a file (verified by checksum in tests).

## Edge cases
- Cyrillic, č ć š ž đ, umlauts, emoji in tags and filenames
- ID3v1 genre number only, ID3v2 `(17)` / `(17)Rock` genre references → shown as text
- ID3v2.2 files (3-character frame IDs)
- Huge folders (10 000+ files) — streaming results, no out-of-memory
- Symlinks, hidden files, files without read permission

## Data safety
Read-only. No writes at all.

## Interfaces
- CLI: `musicstudio scan ~/Downloads/Music --recursive` → table; `--format json|csv` for scripting.
- GUI: covered later by 010/011 (same use case).

## Out of scope
Writing tags, MP4/FLAC, library database.

## Assumptions
- File type is detected by extension `.mp3` (case-insensitive); content sniffing later if needed.
- Decided 2026-10-22: hidden files and folders (incl. macOS `._*`) are skipped and shown in a list of skipped
  files (reason "hidden"); `--include-hidden` scans them.
- Decided 2026-10-22: whether a v1 value that is only the transliterated/truncated v2 value counts as a difference is
  a settings checkbox (default: not a difference; CLI `--strict-v1-compare`).
- Decided 2026-10-23 (implementation): a symbolic link whose target is missing is listed under "Skipped (not
  scanned)" with reason "broken symlink"; other non-regular files (devices, pipes) are ignored. Track `3/` reads as 3,
  track `0` is reported as an invalid value, sorting is not "natural" (`track10` before `track2`).
