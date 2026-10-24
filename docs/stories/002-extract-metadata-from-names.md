# 002 – Extract metadata from file and folder names

**As a** user who downloads files with consistent but varying naming schemes **I want** to describe the naming
scheme once with a pattern **so that** artist, title, album, year etc. are extracted automatically for many files at once.

## Context
The core helper feature. Examples from real use:
- `Best Of 2020/Artist - Song - 2020.mp3` → pattern `{album}/{artist} - {title} - {year}`
- `Album/Artist_Song.mp3` → pattern `{album}/{artist}_{title}`
The pattern changes from batch to batch; defining it must be quick.

## Pattern language
- Placeholders: `{title}` `{artist}` `{albumartist}` `{album}` `{year}` `{track}` `{disc}` `{genre}` `{comment}`,
  and `{*}` = ignore this part.
- Literal text between placeholders is the separator (` - `, `_`, ` (`, …).
- `/` steps up to the parent folder: `{album}/…` = the file's folder name, `{artist}/{album}/…` = two levels.
- The extension is never part of the pattern.
- Typed placeholders validate and help disambiguate: `{year}` = 4 digits, `{track}` / `{disc}` = digits
  (also `03`, `3of12`, `3/12`).

## Acceptance criteria
- [x] Given files and a pattern, when I preview, then I see per file the extracted value for every placeholder.
- [x] Files that don't match the pattern are clearly marked as "no match" with the reason; they are never written.
- [x] Ambiguous matches (e.g. artist itself contains ` - `) are marked "ambiguous" and show the chosen split;
      the user can accept or fix them (GUI) or they are skipped (CLI unless `--accept-ambiguous`).
- [x] Besides the pattern, a field can get a **fixed value** for all files (e.g. `genre=Balkan`, `albumartist=Various Artists`).
- [x] A field can be **kept from the existing tag** instead.
- [x] Merge mode per run: `fill-empty` (only set empty fields) or `overwrite` (default: overwrite).
- [x] Track numbers can be **auto-numbered** by sort order (filename or existing tag order) when not in the name.
- [x] Extraction itself writes nothing; it produces a proposed change set used by 003/004.

## Edge cases
- Extra spaces, double separators, separator missing in a few files
- Year in brackets `(2020)` / `[2020]`
- Leading track numbers `01 - `, `01.`, `01_`
- Folder names with year: `2020 - Album`
- Non-ASCII names

## Data safety
Preview only — no file is touched.

## Interfaces
- CLI: `musicstudio extract ~/Downloads/BestOf2020 --pattern "{album}/{artist} - {title} - {year}" --set genre=Pop`
  → table with extracted values, no-match and ambiguous rows highlighted.
- GUI: pattern input field with live preview table (see 010).

## Out of scope
Regex input for power users (possible later), cleanup of values (→ 005), saving patterns (→ 007).

## Assumptions
- Matching is left-to-right, non-greedy, with typed placeholders tried first to resolve ambiguity.
- Pattern matching is case-sensitive for literals except the separator spaces, which are trimmed.
- Decided 2026-10-23 (implementation, see plan-002 D1–D13 and ADR 0010): spaces around a separator are required
  first and only optional if no file split works with them (`Jay-Z - Song` is not ambiguous, `Artist-Song` still
  matches); ambiguous rows show the other possible split; placeholder names are case-insensitive; a placeholder may
  occur only once (use `{*}`); auto-numbering counts per folder and keeps existing numbers with `--merge fill-empty`;
  `extract` has table and JSON output.
- Decided 2026-10-23 (after testing): auto-numbering by file name uses natural order (`2 - x` before `10 - y`);
  the scan order of 001 stays alphabetical. A pattern without any placeholder (only `{*}` or text) is rejected,
  because it would extract nothing.
