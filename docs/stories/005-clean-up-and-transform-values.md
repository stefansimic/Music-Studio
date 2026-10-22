# 005 – Clean up and transform values

**As a** user downloading music from the internet **I want** extracted values cleaned automatically **so that** junk
like `_`, `(Official Video)` or `[320kbps]` doesn't end up in my tags.

## Context
Downloaded filenames are messy. Cleanup runs between extraction (002) and tagging (003) and applies to all values,
or per field.

## Acceptance criteria
- [ ] Built-in rules, each on/off:
  - underscores / dots → spaces, collapse multiple spaces, trim
  - remove junk patterns: `(Official Video)`, `(Official Audio)`, `(Lyrics)`, `[HD]`, `[320kbps]`, `www.*.com`, …
    (editable list)
  - capitalization: Title Case / Sentence case / as-is (with exceptions list, e.g. `feat.`, `DJ`, `AC/DC`)
  - normalize featured artists: `ft.`, `feat`, `Feat.` → `feat.`; option to move `feat. X` from title to artist or vice versa
  - strip leading track numbers from titles (`01 - Song` → `Song`)
- [ ] Search & replace per field, optionally regex.
- [ ] Rules apply in a defined, visible order; the preview shows the value before and after cleanup.
- [ ] Per file, the user can override a value manually (GUI) — manual edits win over rules.

## Edge cases
- Band names that look like junk or have special casing (`AC/DC`, `t.A.T.u.`, `MØ`)
- Cyrillic capitalization
- A rule leaves a value empty → warning, keep original

## Data safety
Only changes the proposed values; nothing is written until 003/004 apply.

## Interfaces
- CLI: `--clean underscores,junk,titlecase,feat` and `--replace "title:Remix=Mix"`
- GUI: cleanup panel with checkboxes, live preview.

## Out of scope
Online spell checking against databases.

## Assumptions
- Default enabled: underscores, spaces/trim, junk removal, feat. normalization. Capitalization default: as-is.
