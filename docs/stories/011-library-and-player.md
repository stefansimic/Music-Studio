# 011 – Library and MP3 player

**As a** user **I want** to keep my music in a library inside the app and play it **so that** I can check results and
listen to music without another program.

## Context
Secondary feature. Playback goes behind a port in `core` (not tied to JavaFX Media), so MP4 and other backends can be
added later.

## Acceptance criteria
- [ ] Add one or more library folders; the app scans them (001) and keeps an index (artist, album, genre, year, path).
- [ ] Browse by artist / album / genre, search by any field.
- [ ] Play, pause, stop, next/previous, seek, volume; queue of tracks.
- [ ] Shows cover, title, artist, album of the current track.
- [ ] Library updates when files change (rescan button; automatic watch is optional).
- [ ] Open a track from the library in the tagging workspace (010).

## Edge cases
- Files moved/deleted outside the app → marked missing
- Corrupt MP3 → error, skip to next
- Large libraries (50 000 tracks)

## Data safety
The library never modifies audio files; it only stores its own index.

## Interfaces
- GUI: library view + player bar.
- CLI: `musicstudio play <file|dir>` (minimal, optional).

## Out of scope
MP4/video playback, playlists, equalizer, streaming.

## Assumptions
- Index stored locally (embedded DB or file) — architect decides.
