# 003 – Write tags with selectable ID3 versions

**As a** user with devices that understand different ID3 versions **I want** to write the proposed metadata and choose
which ID3v1 and ID3v2 versions are written **so that** every device — including my old car — shows the right info.

## Context
Turns a change set (from 002, manual edits, or fixed values) into tags — always on the **exported copy** (004),
never on the original file. Version selection, encodings, and
conversion rules are defined in the `audio-metadata` skill ("Tag version selection", "ID3v1.1").

## Acceptance criteria
- [x] I can choose ID3v2: none / 2.2 / 2.3 / 2.4 and ID3v1: none / 1.0 / 1.1 (defaults: 2.3 + 1.1).
- [x] I can choose v2 text encoding (auto / Latin-1 / UTF-16 / UTF-8 for 2.4) and v1 text mode (ASCII / Latin-1).
- [x] Invalid combinations are rejected before anything is written, with a clear message.
- [x] Option "remove other tag versions": when on, e.g. an existing v2.4 tag is replaced by v2.3; when off, a warning is shown.
- [x] Dry-run (default) shows per file: field, old value → new value, plus warnings (truncated in v1, transliterated,
      frame not representable in target version).
- [x] With apply, files are written safely; afterwards each file is re-read and compared; mismatches are reported as errors.
- [x] Fields not part of the change set stay untouched (incl. unknown frames, cover, ReplayGain).
- [x] If the adapter can't write a chosen version, the use case reports it before writing.

## Edge cases
- Read-only files, file disappears during the run, disk full
- Writing v1 for Cyrillic titles (→ transliterated ASCII, warning)
- Title longer than 30 characters in v1 (→ truncated, warning)
- File had v2.4 only, target is v2.2
- Greek titles/artists (tonos, final sigma, `ευ`/`ου`) and Serbian Cyrillic are very common and must **stay in their
  script**: v2 keeps the original text (UTF-16 / UTF-8). For v1 the write option `V1NonLatinPolicy` decides, per device
  profile (006): skip v1 for the file with a warning (default), Windows-1253 / Windows-1251 bytes, or Latin transliteration.
  Never `?`, never an empty field.
- Characters with no mapping at all (emoji, CJK) are never written as `?`

## Data safety
Originals are never modified. Tags are written to the copy in the output folder: write to temp, verify by re-reading,
then atomic move into place.

## Interfaces
- CLI: options of `musicstudio export` (004): `--id3v2 2.3 --id3v1 1.1`
- GUI: version/encoding selectors in the batch workspace (010).

## Out of scope
Cover art (→ 009), genre mapping beyond plain text (→ 008).

## Assumptions
- There is no in-place tagging; writing tags is always part of an export.
- Decided 2026-10-22: "Remove other tag versions" is a settings checkbox (default off; CLI `--remove-other-versions`).
  A file has one ID3v2 and one ID3v1 slot: a selected version always converts the existing tag of that family;
  the checkbox decides whether a tag of a family set to "none" is kept (warning) or removed.
