# 010 – GUI: batch tagging workspace

**As a** user processing many files **I want** a graphical workspace where I see all files in a table, define the
pattern and rules, correct single values and then export **so that** big batches are fast and I see what will happen.

## Context
First JavaFX screen. Uses exactly the same use cases as the CLI (002–005); no logic in the GUI.

## Acceptance criteria
- [ ] Open a folder (button or drag & drop); files appear in a table with current tags.
- [ ] Pattern field with live preview: new values next to old values, changed cells highlighted, no-match/ambiguous rows marked.
- [ ] Fixed values, cleanup rules, merge mode, tag versions, profile, output folder and output pattern are set in side panels.
- [ ] Cells are editable; manual edits are marked and win over rules.
- [ ] Multi-select rows → set a field for all selected (e.g. album, genre).
- [ ] "Preview export" shows target paths and warnings; "Apply" runs the export with a progress bar and can be cancelled.
- [ ] After the run: report (written, skipped, errors) and "Undo this export".
- [ ] Long runs don't freeze the UI.

## Edge cases
- 5 000 files in the table — stays responsive
- Window closed during an export → ask, then cancel cleanly (no half-written files)

## Data safety
Same as 003/004; apply always needs an explicit click, never automatic.

## Interfaces
GUI only (CLI equivalents in 002–004).

## Out of scope
Player and library view (→ 011), presets UI details (→ 007).

## Assumptions
- Layout: file table center, settings panels right, action bar bottom.
