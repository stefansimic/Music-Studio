# 0004 – Export only: originals are never written; plan → apply with verified, journaled copies

Status: accepted · Date: 2026-10-22 · Details: [architecture §7.4–7.6, §8](../architecture.md)

## Context
The tool works on the user's real library. Stories 003/004/012 and CLAUDE.md require that originals are never
modified, moved or deleted, that dry-run is the default, that nothing is ever overwritten silently, and that every
export is reversible.

## Decision
- **No in-place tagging, ever.** The only mutating workflow is export: the source is read, and a tagged copy is
  written into an output folder.
- **Plan / apply split**: `PlanExportUseCase` produces an immutable `ExportPlan` (targets, tag diffs, warnings,
  collisions, space check) with no write capability. `ApplyExportUseCase` executes exactly that plan. The CLI applies
  only with `--apply`, and the GUI only on an explicit click.
- **Per-file safe write**: re-check the source fingerprint → `TagWriter.writeCopy(source, temp-in-target-dir)` →
  re-read and verify → journal → `ExportTarget.moveNoReplace(temp, target)`, implemented with a hard link + unlink
  (atomic, never replaces) and a check-then-rename fallback on FAT/exFAT. Temp files are always deleted on failure
  or cancel.
- **Collisions**: case- and NFC-insensitive, both within the batch and against existing files. Policies SKIP, SUFFIX
  (default) and OVERWRITE (explicit flag only; the old file is first moved to `<output>/.musicstudio-backup/<id>/`).
  A target equal to a source is always an error.
- **Undo journal**: append-only, fsync'ed JSON lines per export in the app data dir, written ahead of each visible
  change. Undo deletes only files whose fingerprint still matches, restores backups, removes empty directories it
  created, and has its own dry-run.

## Alternatives
- *In-place tagging with a backup copy*: what most taggers do. Rejected by the user ("originals untouched").
- *`Files.move(ATOMIC_MOVE)`*: on POSIX it silently replaces existing targets, which violates "never overwrite".
- *Copy first, then tag the final file in place*: leaves a visible half-tagged file on failure.
- *Undo log only at the end of the run*: loses everything on a crash mid-export.

## Consequences
- Every export needs disk space for full copies. This is checked up front (`INSUFFICIENT_SPACE`) and matters most for
  videos (012), including FAT32's 4 GiB limit.
- "Fix tags of my library in place" is impossible by design. A user would export to a new folder and swap folders
  manually.
- Undo is reliable within a machine. Journals are keyed by absolute path, so a USB stick mounted elsewhere needs a
  path remap (not planned).
