# Working agreement (how Claude works in this repo)

Complements CLAUDE.md (what the project is). This file is about process and token economy.

## 1. Token economy — don't read the whole repo
- Never bulk-read directories or "get familiar" with the codebase. Start from the story/plan and `docs/architecture.md`.
- Locate with `Grep`/`Glob` first, then `Read` only the needed file or line range (`offset`/`limit`). Don't re-read a file already read this session or just edited.
- Broad questions ("where is X used", "how does Y work across modules") go to an `Explore` agent, which returns a conclusion, not file dumps.
- Don't read ADRs, other stories, or other modules unless the current task touches them. Open an ADR only when a decision in it is relevant.
- Keep `docs/architecture.md` as the module/port map. If a port, use case, or module is added or changed, update that map in the same change so later sessions don't have to re-scan code.
- Build output: always `mvn -q`; on failure show only the failing tests/errors (`| tail -60` or surefire reports), never full logs.
- Replies: short. Report outcomes and decisions, not narration.

## 2. Test-first specification, test-last implementation
1. **Start of a story:** write a *test specification* before any code — a table in the story/plan file
   (`docs/stories/NNN-*.md` or `docs/plan-NNN.md`): ID, acceptance criterion, scenario (given/when/then), level
   (unit / contract / round-trip with real file / architecture / golden-JSON), data-safety checks, edge cases.
   Every acceptance criterion maps to ≥1 test case; every data-safety rule touched gets an explicit case.
   **Always get the spec confirmed by the user before implementing** — no implementation starts without that approval.
2. **Implement** against the spec and the Javadoc contracts (Require checks first). No tests yet beyond what is
   needed to compile.
3. **End of a story:** write the tests from the spec (the `tester` agent), run `mvn -q verify`, then **fix the
   production code** (tester only reports; the main session fixes) and re-run until green.
   Never weaken/delete a test or an ArchUnit rule to get green; if the spec was wrong, change the spec visibly.
4. Tick off the spec table (ID → test class/method → pass) in the story file. Then `reviewer`.

## 3. Definition of done (checklist before saying "done")
- [ ] `mvn -q verify` green, including ArchUnit and contract tests; tests run with `-ea`
- [ ] Every acceptance criterion has a passing test; spec table updated
- [ ] Data-safety rules honoured (dry-run default, originals untouched, temp-dir-only tests)
- [ ] Public API has full Javadoc + `Require` checks; records JSON-expressible
- [ ] If a use case changed: `docs/api/openapi.yaml` + golden-JSON test updated
- [ ] If a decision was made: ADR added; `docs/architecture.md` map updated
- [ ] No new reflection/dynamic loading (native-image); adapter reflection config committed if needed
- [ ] `reviewer` run, findings addressed or explicitly deferred

## 4. Decisions and scope
- Do what was asked; no drive-by refactors, extra features, or renames. Note follow-ups at the end instead.
- Small, conventional choices: decide and mention. Architecture-affecting or irreversible choices: ask first (or ADR).
- If a requirement is ambiguous, ask once with a recommendation rather than guessing silently.
- If a fix attempt fails twice, stop, state the hypothesis and evidence, and ask — don't loop.

## 5. Git
- Work on a feature branch (`feature/NNN-*`); never commit to `main`.
- Commit/push only when asked. One logical change per commit, message explains why.
- Never `--force`, never `reset --hard`, never skip hooks without explicit instruction.

## 6. Continuity across sessions / context resets
- Keep a short "Status" section at the top of the active plan/story: done, in progress, next, open questions.
  Update it at natural checkpoints so a fresh session can resume from that file alone.
- Use `/clear` or a new session between stories rather than letting context grow.
