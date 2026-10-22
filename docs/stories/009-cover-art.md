# 009 – Cover art

**As a** user who wants album pictures on phone and MP3 player **I want** to embed cover art for many files at once
**so that** devices show the right picture, in a format they can actually display.

## Acceptance criteria
- [ ] Cover sources: an image file I choose, an image in the source folder (`cover.jpg`, `folder.jpg`, `front.*`,
      or the only image), or the cover already embedded.
- [ ] Applies to all files of a batch or per album folder.
- [ ] Images are converted per profile: baseline JPEG, square (crop or pad, configurable), max size (e.g. 500 px),
      max file size.
- [ ] Option to remove covers (e.g. for the old car profile).
- [ ] Preview shows the cover per file/album.
- [ ] Optionally write `cover.jpg` into each exported album folder.

## Edge cases
- PNG/WebP/progressive JPEG input, huge images (6000 px), non-square images
- Several images in a folder → user picks; no image → no cover, warning
- Native image build: image processing must work in the GraalVM native CLI

## Data safety
Covers are only written via 003/004; originals untouched on export.

## Interfaces
- CLI: `--cover folder|<file>|keep|remove`
- GUI: cover panel with drag & drop.

## Out of scope
Online cover search (later idea).

## Assumptions
- Picture type 3 (front cover) only.
