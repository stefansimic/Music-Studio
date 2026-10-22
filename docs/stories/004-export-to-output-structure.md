# 004 – Export into a defined output structure

**As a** user preparing music for a device **I want** tagged copies written into an output folder with a folder and
filename structure I define **so that** my library on each device is consistently organized and my downloads stay untouched.

## Context
Completes the main workflow: source folder → extract (002) → clean up (005) → tag (003) → **export** here.

## Output pattern
Same placeholders as 002, built from the *final* metadata, e.g.
- `{albumartist}/{year} - {album}/{track:00} - {title}`
- `{artist}/{album}/{artist} - {title}`
- `{genre}/{artist} - {title}`
`{track:00}` = zero-padded. Missing values use a configurable fallback (`Unknown Artist`, …) and produce a warning.

## Acceptance criteria
- [ ] I choose an output folder and an output pattern; the preview shows source path → target path for every file.
- [ ] Source files are **never modified**; the export writes tagged copies.
- [ ] Filenames/folders are sanitized (invalid characters, trailing dots/spaces, max length); optionally ASCII-only.
- [ ] Collisions (two files → same target, or target exists) are detected in the preview; options: skip, add suffix ` (2)`,
      or overwrite only if `--overwrite` is given explicitly.
- [ ] Dry-run is default; apply writes files and an export report (what was written where, warnings).
- [ ] An undo log allows deleting exactly the files this export created.

## Edge cases
- Output folder inside the source folder
- Output on a FAT32 USB stick (path length, forbidden chars, case-insensitive collisions `abc.mp3` vs `ABC.mp3`)
- Same song in two input folders
- Not enough disk space (checked before starting)

## Data safety
Copy only — source files are never modified, moved or deleted. No silent overwrite, undo log.

## Interfaces
- CLI: `musicstudio export ~/Downloads/BestOf2020 --pattern "{album}/{artist} - {title} - {year}"
  --out /run/media/usb --out-pattern "{albumartist}/{album}/{track:00} - {title}" --id3v2 2.3 --id3v1 1.1 [--apply]`
- GUI: output folder + output pattern fields with target path column in the preview (010).

## Out of scope
Move instead of copy (never), device presets (→ 006), copying in FAT order for old head units (later).

## Assumptions
- Confirmed: "output I define" = separate output folder, originals untouched.
