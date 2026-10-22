# 006 – Device profiles

**As a** user with an old car stereo, an MP3 player, a phone and a PC **I want** a profile per device **so that** I don't
have to remember which tag versions, cover size and filename rules each device needs.

## Context
Profiles are presets for tag write options (003) and export rules (004). Values per device are in the
`audio-metadata` skill (Device profiles).

## Acceptance criteria
- [ ] Built-in profiles: **Car (old)**, **MP3 player**, **Phone**, **PC**, **Universal** (strictest of all).
- [ ] A profile defines: ID3v2 version + encoding, ID3v1 version + text mode, cover (none / max size / baseline JPEG),
      genre mode (single / multi), filename rules (ASCII-only, max path length, zero-padding), default output pattern.
- [ ] Choosing a profile fills all options; any option can still be overridden for this run.
- [ ] I can create, edit, duplicate and delete my own profiles; built-ins can be duplicated but not deleted.
- [ ] Profiles are stored as a readable file (e.g. JSON/YAML in the user config directory) so I can back them up.
- [ ] One export run can target several profiles at once, each into its own output folder (e.g. car + phone).

## Edge cases
- Profile file edited by hand and invalid → clear error, built-ins still work
- Profile contains an option the active adapter can't write → warning when selected

## Data safety
Profiles don't write anything themselves.

## Interfaces
- CLI: `--profile car`, `musicstudio profiles list|show <name>`
- GUI: profile dropdown + profile editor.

## Out of scope
Auto-detecting the device on USB plug-in.

## Assumptions
- Car (old): ID3v2.3 + ID3v1.1 ASCII, no or small cover, ASCII filenames, `{artist} - {title}` flat or one folder level.
