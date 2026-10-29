---
name: audio-metadata
description: Domain knowledge for audio tagging in Music-Studio — ID3v1/v2.3/v2.4 frames and encodings, genre handling, cover art, file/folder naming, device compatibility profiles (car stereo, phone, MP3 player), and jaudiotagger specifics. Use whenever writing, testing, or reviewing code that reads/writes tags, genres, artwork, or renames/organizes files.
---

# Audio metadata reference

Verify library API details against the actual jaudiotagger version in the POM — the notes below are guidance, not
a substitute for checking.

## ID3 versions
| | ID3v1.1 | ID3v2.3 | ID3v2.4 |
|---|---|---|---|
| Location | last 128 bytes | file start | file start (footer optional) |
| Text encoding | Latin-1, 30 chars | Latin-1 or **UTF-16 (BOM)** | Latin-1, UTF-16, UTF-16BE, **UTF-8** |
| Multiple values | – | not standardized (`/` common) | null-separated |
| Device support | universal | **near universal** | poor on older car stereos / players |

## Tag version selection (user-configurable)
The written versions are a **user choice**, not hard-coded. A `TagWriteOptions` value (core type) holds:
| Option | Values | Default |
|---|---|---|
| ID3v2 version | `NONE`, `V2_2`, `V2_3`, `V2_4` | `V2_3` |
| ID3v1 version | `NONE`, `V1_0`, `V1_1` | `V1_1` |
| v2 text encoding | `AUTO` (Latin-1 if it fits, else UTF-16; UTF-8 only for v2.4), `LATIN1`, `UTF16`, `UTF8` (v2.4 only) | `AUTO` |
| v1 text mode | `ASCII` (transliterated), `LATIN1` | `ASCII` |
| remove other versions | yes/no — e.g. strip an existing v2.4 tag when writing v2.3 | no (keep, but warn) |

- Device profiles provide defaults; the user can override per run (CLI flags, e.g. `--id3v2 2.3 --id3v1 1.1`,
  `--id3v2 none`) and in the GUI. Precedence: explicit option > profile > global default.
- Validate combinations up front and fail fast with a clear message (e.g. UTF-8 with v2.2/v2.3 is invalid;
  `NONE` + `NONE` = write no tags → refuse unless removal was explicitly requested).
- Converting between v2 versions maps frames: v2.2 uses 3-char IDs (TT2, TP1, TAL, TRK, PIC …),
  v2.4 TDRC ↔ v2.3 TYER(+TDAT/TIME), v2.4 multi-values → joined string for v2.2/v2.3. Frames with no equivalent in
  the target version are reported as warnings (never silently dropped in a dry-run report).
- v1.0 vs v1.1: v1.0 has a 30-byte comment and no track number; v1.1 steals 2 bytes for the track number.
- Both tags coexist in one file: v2 at the start, v1 at the end. Newer devices prefer v2, old ones read v1.

**Adapters declare what they can write** (a capability set on the port, e.g. `supportedWriteVersions()`).
jaudiotagger can write v2.2/v2.3/v2.4 and v1/v1.1; a future adapter (mp3agic) may support less. Core checks the
requested options against the capability set before any write and reports unsupported choices — the UI can grey
them out.

## ID3v1.1 (old car stereos)
Fixed 128-byte block `TAG` + fields — the domain values must be **derived** for it, not just truncated blindly:
| Field | Size | Rule |
|---|---|---|
| title / artist / album | 30 bytes each | transliterate, then truncate at a word boundary if possible |
| year | 4 | `YYYY` |
| comment | 28 | usually empty |
| track | 1 byte (v1.1) | 1–255, 0 = none |
| genre | 1 byte | index into the ID3v1/Winamp table; map our genre via the alias map, unknown → 12 (Other) |
No album artist, disc number, cover art or multi-value fields exist in v1.

**Greek and Serbian Cyrillic stay in their script** (user decision): v2 always keeps the original (UTF-16 / UTF-8).
v1 has no encoding marker, so the write option `V1NonLatinPolicy` (set per device profile) picks: `SKIP_V1` (default, v2
only + warning), `CODEPAGE` (Windows-1253 Greek / Windows-1251 Cyrillic, one codepage per file; only right on devices
set to that locale), or `TRANSLITERATE` (Latin; Greek ELOT 743 style ου→ou, θ→th, χ→ch, ψ→ps, tonos stripped).

**Text must be ASCII-safe** (for Latin-script text, and for `TRANSLITERATE`): v1 is nominally Latin-1, but many old head units only display ASCII and Serbian/Croatian
letters (č ć š ž đ) aren't Latin-1 anyway. Apply a project-owned transliteration before writing v1:
- Serbian Cyrillic → Latin (Gaj): А→A … Ђ→Đ, Љ→Lj, Њ→Nj, Џ→Dž, Ћ→Ć, Ч→Č, Ш→Š, Ж→Ž, …
- then Latin → ASCII: č/ć→c, š→s, ž→z, đ→dj, dž→dz, ä→ae, ö→oe, ü→ue, ß→ss, other accents stripped (NFD + remove marks)
- remaining unmappable chars (emoji, CJK) → `?` is forbidden; drop them and report a warning.
The v1 strategy (ASCII vs. Latin-1) is part of the device profile. The same transliteration is reused for
FAT32-safe filenames when a profile asks for ASCII filenames.

## Core field ↔ frame mapping (v2.3)
| Domain field | Frame | Notes |
|---|---|---|
| title | TIT2 | |
| artist | TPE1 | track artist, may include "feat." |
| albumArtist | TPE2 | **drives album grouping** on phones/players; set it on every track, "Various Artists" for compilations |
| album | TALB | |
| trackNumber / trackTotal | TRCK | `"3/12"`; no zero padding inside the tag |
| discNumber / discTotal | TPOS | `"1/2"`; set it even for single-disc albums only if the profile wants it |
| year | TYER | v2.4: TDRC |
| genre | TCON | see Genres |
| compilation | TCMP = "1" | iTunes extension, widely understood |
| composer | TCOM | |
| sort names | TSOP, TSO2, TSOA | iTunes extensions, v2.3-compatible in practice |
| comment | COMM | language + description |
| lyrics | USLT | |
| cover | APIC | see Cover art |
| ReplayGain | TXXX:REPLAYGAIN_TRACK_GAIN etc. | preserve, don't recompute unless asked |

## Genres
- Write **plain text** (`Rock`), never ID3v1 numeric references like `(17)` or `(17)Rock`. When reading, resolve
  `(nn)` and bare numbers via the ID3v1/Winamp genre table.
- Devices show TCON as a raw string → for device profiles, write **one primary genre**. Keep the full
  multi-genre list in the domain model; the profile decides how it is serialized (single, or joined with `; `).
- Normalize with a project-owned genre vocabulary/alias map (`Hip Hop`/`Hip-Hop`/`HipHop` → one form), not ad hoc.

## Cover art
- APIC picture type **3 (front cover)**, MIME `image/jpeg`.
- Device-safe: **baseline (non-progressive) JPEG**, square, ≤ 500×500, ideally < 200 KB. Many players fail silently on
  progressive JPEGs or PNG.
- One embedded front cover per file for device profiles; keep the original elsewhere (folder `cover.jpg`) if asked.

## Files & folders
- Default layout: `Album Artist/Year - Album/[Disc-]NN - Title.mp3`, compilations under `Various Artists/` or
  `Compilations/`.
- **FAT32-safe names** (car stereos/USB): strip/replace `\ / : * ? " < > |`, control chars, trailing dots/spaces;
  watch total path length (keep < ~200 chars) and folder depth (≤ 2–3 levels).
- Zero-pad track numbers in filenames — some head units sort by filename.
- Some car stereos play in **FAT directory entry order** (= copy order), not alphabetically. If a "copy to USB"
  feature exists, copy files in sorted order.
- Keep folders below a few hundred files; some head units cap files per folder/total.

## Device profiles
| | Car stereo | Phone (Android/iOS) | MP3 player (generic DAP) |
|---|---|---|---|
| ID3 | v2.3 **+ v1.1 (ASCII)** | v2.3 (v2.4 ok) | v2.3 + v1.1 |
| Encoding | UTF-16 / Latin-1 | UTF-16 or UTF-8 | UTF-16 |
| Cover | baseline JPEG ≤ 300–500 px or none | JPEG ≤ 1000 px | baseline JPEG ≤ 300–500 px |
| Genre | single | single or multi | single |
| Album artist | important | **critical** (grouping) | important |
| Filenames | FAT32-safe, short, padded | lenient | FAT32-safe, padded |

Profiles are data (a domain record), not branching logic scattered through code. A "Universal" profile = the
strictest value of each row.

## jaudiotagger notes
- Maintained artifact on Maven Central: `net.jthink:jaudiotagger` (check latest version).
- Write version/encoding are controlled by the **global** `TagOptionSingleton` (e.g. ID3v2 version, default text
  encoding for v2.3). It's process-wide mutable state → set it only inside the adapter, in one place, and be careful
  with concurrent writes.
- Prefer the generic `Tag`/`FieldKey` API for portable fields; drop to `ID3v23Tag`/frame level only inside the adapter
  for things FieldKey can't express (picture type, encoding checks).
- jaudiotagger writes via a temp file but still verify the result (re-read) before reporting success.
- Logging is chatty (java.util.logging) — tone it down in the adapter.
- Later formats it already covers: FLAC (Vorbis comments + PICTURE block), M4A/MP4 (iTunes atoms: `©nam`, `aART`,
  `trkn`, `covr`, `cpil`), OGG. Map them in adapters; core stays format-neutral.

## MP4 video metadata (movies, TV episodes)
iTunes-style atoms in `moov/udta/meta/ilst`:
| Domain field | Atom | Notes |
|---|---|---|
| title | `©nam` | movie title or episode title |
| year / release date | `©day` | `YYYY` or ISO date |
| genre | `©gen` | text |
| description | `desc` / `ldes` | short / long |
| media kind | `stik` | 9 = Movie, 10 = TV Show |
| show | `tvsh` | |
| season | `tvsn` | integer |
| episode | `tves` | integer |
| episode ID | `tven` | e.g. `S01E02` |
| poster | `covr` | JPEG/PNG |
- Large files: writing atoms may require rewriting/moving the `moov` atom; since we always write to a copy, do it as
  part of the copy (stream, never load the file into memory). Prefer putting `moov` before `mdat` (fast start).
- Plex/Jellyfin/Kodi mostly rely on **file/folder names**, not atoms → the output naming matters as much as the tags.
