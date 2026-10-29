# 0011 – Tag-writing slice (003) delivered before the export workflow (004)

Status: proposed, 2026-10-29

## Context
Architecture §7.4/7.5 assign `PlanExportUseCase` and `ApplyExportUseCase` to "003 + 004". Story 003 (write tags with
version selection) needs the safe write path (temp, verify, no-replace move), but not path rendering, collision policy,
space estimation, journal or undo, which are story 004. Building all of it in 003 makes the story too large; building
tag writing without a safe path would risk the "no unsafe write" rule.

## Decision
003 delivers a self-contained, safe **tag-write slice**: `TagWriter` port, `TagWriteOptions`, `Id3v1Projection`,
`WriteOptionsUseCase`, and `WriteTagsUseCase` (plan/apply on explicit source -> target pairs) built on
`SafeTagWriteService` (reserve temp in the target directory, `writeCopy`, re-read verify, `moveNoReplace`, always clean
up). Only the four `ExportTarget` methods this needs are introduced (`reserveTempPath`, `moveNoReplace`, `deleteTemp`,
`exists`); 004 extends the port and wraps the slice in `PlanExportUseCase`/`ApplyExportUseCase` with journaling.
An existing target is an error (`TARGET_EXISTS`); 003 never overwrites and takes no backups.

## Alternatives
- Implement full export in 003: too large, mixes two stories, delays tag-version feedback.
- Write tags in a temporary in-core helper doing `java.nio` I/O: breaks the core no-I/O rule.
- In-place tagging for 003 and export later: violates ADR 0004.

## Consequences
- 003 has no runnable CLI command; options are exposed as a reusable picocli mixin consumed by 004.
- 004 must reuse `SafeTagWriteService` rather than re-implement the write sequence.
- No undo in 003: outputs are new files only; undo arrives with the journal in 004.
- Small risk of rework if the `ExportTarget` subset needs signature changes in 004.
