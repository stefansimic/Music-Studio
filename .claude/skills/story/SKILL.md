---
name: story
description: Turn a feature idea for Music-Studio into a user story with acceptance criteria and edge cases, saved under docs/stories/. Use when the user describes a new feature or asks to write a story.
argument-hint: <feature idea>
---

Write a user story for: $ARGUMENTS

1. If the idea is ambiguous in a way that changes behaviour (which devices? which tags? destructive or not?),
   ask at most 3 focused questions first. Otherwise proceed with sensible defaults and list them as assumptions.
2. Find the next free number in `docs/stories/` and write `docs/stories/NNN-kebab-title.md`:

```markdown
# NNN – <Title>

**As a** <user> **I want** <capability> **so that** <benefit>.

## Context
Why this matters; which devices/formats are affected.

## Acceptance criteria
- [ ] Given … when … then …   (observable behaviour, testable, one per line)

## Edge cases
- non-ASCII text, missing tags, existing conflicting tags, multiple values, large cover art, … (only relevant ones)

## Data safety
Dry-run output, what is written/renamed, how it's undone.

## Interfaces
- CLI: example command(s) and output
- GUI: what the user sees/does (short)

## Out of scope
## Assumptions
```

3. Keep it to one deliverable increment. If it's too big, propose splitting into several stories.
4. Use the `audio-metadata` skill for correct terminology (album artist, compilation, disc number, …).
5. Finish by suggesting the next step: hand the story to the `architect` agent.
