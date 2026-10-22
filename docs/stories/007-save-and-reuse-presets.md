# 007 – Save and reuse jobs as presets

**As a** user who processes similar downloads again and again **I want** to save a complete job (input pattern, fixed
values, cleanup rules, output pattern, profile) under a name **so that** next time it's one click or one command.

## Context
Addresses "I may have to set the logic every time" — the app should remember it.

## Acceptance criteria
- [ ] After configuring a job (GUI or CLI), I can save it as a named preset.
- [ ] Loading a preset fills all settings; I can still change them before running.
- [ ] Recently used patterns are offered as suggestions in the pattern field.
- [ ] **Pattern suggestion**: the app analyses the selected filenames and proposes likely patterns
      (e.g. detects ` - ` separators, a 4-digit year, leading track numbers) — I pick or adjust one.
- [ ] Presets are stored as readable files next to the device profiles; CLI and GUI share them.

## Edge cases
- Preset references a deleted device profile → warning, fall back to defaults
- Renamed fields across app versions → preset file has a version number and is migrated

## Data safety
Presets only store configuration.

## Interfaces
- CLI: `musicstudio export <dir> --preset balkan-singles [--apply]`, `--save-preset <name>`
- GUI: preset dropdown, "Save as preset…", pattern suggestions under the pattern field.

## Out of scope
Sharing presets online.

## Assumptions
- Pattern suggestion is heuristic; it never runs without the user confirming the pattern.
