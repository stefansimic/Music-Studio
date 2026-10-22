---
name: implement
description: Implement a Music-Studio story or architect plan end to end in the main session, following the ports-and-adapters rules, then hand off to the tester and reviewer agents. Use when the user asks to implement a story, plan, or feature.
argument-hint: <story file or plan>
---

Implement: $ARGUMENTS

1. **Read** `CLAUDE.md`, the story in `docs/stories/`, and the architect's plan/ADR. If there's no plan and the
   change touches ports, modules, or a new format, run the `architect` agent first.
2. **Inside out**: domain records and ports in `core` → use case with unit tests against fakes → adapter →
   frontends (CLI first, GUI after). Build after each step: `mvn -q -pl <module> -am verify`.
3. **Rules while coding**
   - No library/UI type in `core`; no library type in a port signature.
   - Frontends only parse, call a use case, render the result.
   - Every mutating use case takes a dry-run flag and returns a change list; writes are atomic and preserve
     untouched tag data (see Data safety in `CLAUDE.md`).
   - Load the `audio-metadata` skill for any tag, genre, cover art, or filename/folder logic.
   - Match existing code style; records, `Optional`, no nulls across boundaries.
4. **Hand off**: run the `tester` agent with the story path. Fix real bugs it finds (not the tests, unless the test
   contradicts the story). Then run the `reviewer` agent and address blockers and should-fixes.
5. **Report**: what changed per module, test results, open questions. Tick the satisfied acceptance criteria in the
   story file. Don't commit unless asked.
