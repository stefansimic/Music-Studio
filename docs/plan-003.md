# Plan 003 – Write tags with selectable ID3 versions

Story: [`docs/stories/003-write-tags-with-version-selection.md`](stories/003-write-tags-with-version-selection.md) ·
Architecture: §5.3, §6.3, §7.3-7.5, §8.2, §9 of [`docs/architecture.md`](architecture.md) ·
ADRs 0002 (DbC), 0003 (library behind port), 0004 (export-only), [0011](adr/0011-tag-write-slice-before-export.md) (slice).
Baseline: [`docs/plan-001.md`](plan-001.md) (done, merged). Abbreviations `C/ CT/ P/ T/` as in plan-001.

## Status
- **Implemented and verified** (spec approved by the user). `mvn -q verify` is green (JDK 25): 885 tests, 0 failures. The
  native CLI still builds and its scan smoke test passes (`-H:+AddAllCharsets` added for Windows-1251/1253).
- Spec coverage: see the table at the end of this section. `tester` found six real production bugs (temp name vs reader,
  ID3v2 removal, multi-genre conversion, APIC/PIC preflight, long temp names, Cyrillic Т); all fixed. `reviewer` found no
  data-safety or architecture blocker; its should-fix items were fixed except the ones listed under "Deferred".
- Deviations from this plan: use cases live in package `export` (the ArchUnit layering has no `tagwrite` layer);
  `SafeTagWriteService` became package-private `SafeCopyWriter` (ArchUnit A7: a `*Service` must implement exactly one
  UseCase); temp names are `.<stem>.<rand>.musicstudio-tmp<.ext>` (`ExportTarget.TEMP_MARKER`) so the reader still
  recognises the format when verifying; `WriteOutcome.Written.targetFingerprint` is optional; the adapter hashes the audio
  region of source and copy and fails if they differ; the reader splits genres on `; `.
- **Deferred** (needs story 004's `export` command to exercise writing natively): re-running the native-image tracing
  agent for the write path (R3; the metadata was recorded for reading); a native smoke test that writes with
  `--v1-non-latin codepage`. Also deferred: a check that preserved frames survived (covered by D06 tests today), and an
  APE/Lyrics3-trailer fixture for the ID3v1 append.
- OpenAPI is deferred to story 013 (user decision); golden-JSON tests for the new records exist (X03).

## Scope decision (see ADR 0011)
Architecture assigns `PlanExportUseCase`/`ApplyExportUseCase` to "003 + 004". Export naming, collision policy,
journal and undo are 004. 003 therefore delivers the **tag-writing slice** that 004 will call:

| Piece | In 003 | Left to 004 |
|---|---|---|
| `TagWriteOptions`, `Id3v1Projection`, `FieldChanges`, `Id3v1Values`, `TagWriteRequest`, `FormatCapabilities` | yes | - |
| `TagWriter` port (+ jaudiotagger adapter, contract test) | yes | - |
| `WriteOptionsUseCase` | yes | - |
| `WriteTagsUseCase` `plan` / `apply` on **explicit source -> target pairs** (no pattern, no collision policy) | yes | `PlanExportUseCase`/`ApplyExportUseCase` wrap it (path rendering, collision suffix, space, journal, undo) |
| Safe write: temp -> `writeCopy` -> re-read verify -> no-replace move | yes (`SafeTagWriteService`) | journaling around it |
| `ExportTarget` port | **only** `reserveTempPath`, `moveNoReplace`, `deleteTemp`, `exists` | `createDirectories`, `storeInfo`, `moveToBackup`, ... |
| Undo | no (targets are new files; user can delete them) | `UndoExportUseCase` |
| CLI/GUI | picocli `Id3OptionsMixin` only (`--id3v2 --id3v1 --v2-encoding --v1-mode --remove-other-versions`); no new command | `export` command uses the mixin; GUI selectors in 010 |

Target already exists -> ERROR `TARGET_EXISTS`, item skipped. 003 never overwrites and never creates a backup.

## Affected modules
- `music-studio-core`: model, port `TagWriter`, `tagwrite` package, testkit (contract, fakes, fixtures). No new dependency.
- `music-studio-tag-jaudiotagger`: `JaudiotaggerTagWriter` (+ package-private mapping).
- `music-studio-storage`: `LocalExportTarget` (4 methods).
- `music-studio-app`: wires `WriteOptionsUseCase`, `WriteTagsUseCase` into `MusicStudio`.
- `music-studio-cli`: `Id3OptionsMixin` + test only.
- GUI: not yet exists (010).

Port sanity (mp3agic): `TagWriter.writeCopy(source, target, request)` maps to `Mp3File(source)` +
`setId3v1Tag/setId3v2Tag` + `save(target)`. The request uses only core types (`Field`, `FieldValue`, `Id3v1Values`,
`TagWriteOptions`); mp3agic lacks v2.2 -> declared via `FormatCapabilities.writableVersions`, so core rejects it.

## TEST SPECIFICATION (confirm before implementing)

Levels: **U** unit (core, fakes) · **C** contract (abstract class in core test-jar, run against fake AND jaudiotagger) ·
**R** real-file round-trip (jaudiotagger on fixtures copied to `@TempDir`) · **A** architecture · **G** golden-JSON.
Every test runs in `@TempDir`; "DS" lists the data-safety assertions it carries. SHA = SHA-256 + mtime of the source
unchanged.

### Acceptance criteria

| ID | Criterion | Scenario (given / when / then) | Lvl | Data safety |
|---|---|---|---|---|
| T01 | AC1 choices + defaults | given no args / `TagWriteOptions.DEFAULT` / v2.3, v1.1, AUTO, ASCII, removeOther=false; every `Id3v2Choice` x `Id3v1Choice` valid combo constructs | U | - |
| T02 | AC1 | given each of V2_2/2.3/2.4 + V1_0/1_1 / write real fixture `no-tags` / re-read: exactly the chosen versions present (`TagReadResult.layers`) | R | SHA of source |
| T03 | AC2 encodings | given Cyrillic title, v2.3, `UTF16` / write / raw frame encoding byte is UTF-16 with BOM, value round-trips | R | SHA |
| T04 | AC2 | given v2.4 + `UTF8` / write / frame encoding byte 3, round-trips | R | SHA |
| T05 | AC2 | given ASCII-only title, `AUTO` / write v2.3 / Latin-1 frame; given Cyrillic + `AUTO` / v2.3 UTF-16, v2.4 UTF-8 (per port contract) | R | SHA |
| T06 | AC2 v1 mode | given title `Müller`; `ASCII` -> `Mueller`; `LATIN1` -> `Müller` in raw v1 bytes (0xFC) | U (projection) + R (bytes) | SHA |
| T07 | AC3 invalid combos | given UTF8+V2_3, UTF8+V2_2, encoding!=AUTO+id3v2 NONE, NONE+NONE w/o removeOther / construct / IAE with message naming both options; same combos via `problems()` return the same text | U | - |
| T08 | AC3 "before anything written" | given a batch with an invalid-for-adapter option (v2.2 on fake without v2.2) / `apply`/`plan` / global ERROR `UNSUPPORTED_WRITE_VERSION`, `writeCopy` call count 0, no file or dir created | U | no write at all |
| T09 | AC3 | given same options / `WriteOptionsUseCase.validate` / non-empty issue list ⇔ plan fails with the same code | U | - |
| T10 | AC4 remove ON | given `v24-utf8`, target v2.3, removeOther=true / write / result has v2.3 only; no v2.4 left; issue `OTHER_VERSION_REMOVED` only when a *family set to NONE* is removed (v1 NONE + v1 present) | R + U | SHA |
| T11 | AC4 remove OFF | given `v11-v23-same`, id3v1=NONE, removeOther=false / write / v1 tag kept byte-identical, WARNING `OTHER_VERSION_KEPT` in plan | R + U | SHA |
| T12 | AC4 slot rule | given `v24-utf8`, target v2.3, removeOther=false / write / v2.4 is *converted* to v2.3 (one v2 slot), no second tag, no OTHER_VERSION warning | R | SHA |
| T13 | AC5 dry-run content | given a fake source with title A and desired B / `plan` / per file: `FieldChange(field, old, new)` list + issues; second `plan` identical (deterministic) | U | - |
| T14 | AC5 dry-run writes nothing | given real fixtures / `plan` / dir tree listing + SHA of every file unchanged before/after; `writeCopy` never called on fake; `ExportTarget` fake saw only `exists` | U + R | tree snapshot |
| T15 | AC5 v1 truncation | given 41-char title, v1.1 / plan / `V1_TRUNCATED` WARNING with field TITLE; `Id3v1Values.title` <= 30 bytes at a word boundary | U | - |
| T16 | AC5 transliteration | given `Ђорђе Балашевић`, ASCII / plan / `V1_TRANSLITERATED`; `Djordje Balasevic` in Id3v1Values | U | - |
| T17 | AC5 not representable | given album artist + disc number set, v1 only / plan / `V1_FIELD_NOT_REPRESENTABLE`; given `v24-utf8` -> v2.2 / `FRAME_NOT_REPRESENTABLE` listing exactly the frames later dropped (T20) | U + R | - |
| T18 | AC6 apply + verify | given real fixture / `apply` / output re-read with `TagReader` equals `PlannedFile.expected`; report `written` contains it | R | SHA; temp file gone; target is the only new file |
| T19 | AC6 mismatch reported | given a fake `TagWriter` that writes a wrong title / `apply` / item `failed` with `VERIFY_MISMATCH` naming field, target absent, temp deleted, run continues with next file | U | no half-written target |
| T20 | AC7 untouched fields | given `v23-extras` (TXXX ReplayGain, PRIV, USLT, APIC, custom TXXX) and change = title only / write v2.3 | R | SHA |
|  |  | / all other frames byte-equal in payload; only TIT2 differs; with `v24` source -> v2.3: same except frames reported by T17 | | |
| T21 | AC7 field-level | given change set {TITLE} / `FieldChanges.between` / only TITLE in `set`; unchanged fields not in set; cleared fields in `clear`; `set` and `clear` disjoint (IAE otherwise) | U | - |
| T22 | AC7 payload | given any write / compare audio payload hash (frames after tags) source vs target / equal | R | SHA |
| T23 | AC8 capability | given fake writer without V2_2 / `validate`, `plan` / `UNSUPPORTED_WRITE_VERSION` before any item work; with jaudiotagger all five versions accepted; encoding set also checked (`UNSUPPORTED_ENCODING` via same code) | U + C | no write |

### Edge cases

| ID | Edge case | Scenario | Lvl | Data safety |
|---|---|---|---|---|
| E01 | read-only source | given source `chmod 444` / apply / succeeds (source opened read-only), source stays 444 and unchanged | R (`@DisabledOnOs(WINDOWS)`) | SHA + permissions |
| E02 | read-only / unwritable target dir | given dir `chmod 555` / apply / item failed `WRITE_FAILED`(ACCESS_DENIED), no temp left, others continue (skip if root) | R | no leftover |
| E03 | source disappears | given source deleted after plan / apply / `SOURCE_CHANGED` (fingerprint) or NOT_FOUND, no target, run continues | U + R | - |
| E04 | source modified after plan | given mtime/size changed after plan / apply / `SOURCE_CHANGED`, nothing written for it | U | stale preview cannot write |
| E05 | disk full | given fake writer throwing `MediaAccessException(NO_SPACE)` after creating partial temp / apply / failed `WRITE_FAILED`, temp deleted, source SHA same; real test via tiny tmpfs only where available (skipped otherwise) | U (+ R optional) | temp cleanup |
| E06 | v1 Cyrillic | `v23-utf16-cyrillic` -> v1.1 ASCII / v1 bytes `Djordje Balasevic`, no `?`, `V1_TRANSLITERATED`, v2 keeps original Cyrillic | R | SHA |
| E07 | v1 title > 30 | 41-char title / v1 bytes exactly <= 30, v2 holds full title, `V1_TRUNCATED` | R | SHA |
| E08 | v1.1 comment 28, v1.0 no track | 30-char comment + track 7 / v1.1 comment cut to 28 + track 7; v1.0 comment 30, track field absent; `Id3v1Values` invariants (track 0 for V1_0) enforced by IAE | U + R | - |
| E09 | v2.4-only source -> v2.2 | `v24-utf8` -> v2.2 / re-read: v2.2 layer (TT2/TP1...), `TDRC` -> `TYE`, two genres joined `"; "`, unrepresentable frames warned (T17) and exactly those dropped | R | SHA |
| E10 | multi-genre | two genres -> v2.3/2.2 joined `"; "`, v2.4 NUL-separated; re-read gives two `Genre`s | R | SHA |
| E11 | forced Latin-1 can't hold value | Latin-1 + `Ђорђе` / plan / ERROR `V2_NOT_ENCODABLE`, item SKIP, nothing written | U | - |
| E12 | target exists | given target already present / plan / `TARGET_EXISTS` ERROR, SKIP; apply never replaces (bytes of existing file unchanged) | U + R | never overwrite |
| E13 | target == source (also via symlink / case) | plan -> `TARGET_IS_SOURCE`; `TagWriter.writeCopy` precondition IAE | U + C | originals untouched |
| E14 | unreadable/corrupt source | `corrupt.mp3` / plan+apply / item failed `CORRUPT`, no target | U + R | - |
| E15 | cancellation | cancel after item 2 of 5 / apply / status CANCELLED, 2 targets complete & verified, current temp deleted, no `*.musicstudio-tmp` left | U + R | temp cleanup |
| E16 | unsupported format | `.flac` in request / plan / ERROR, not writable | U | - |
| E17 | non-ASCII path | target `Ђорђе – 🎵.mp3` / apply ok | R | - |
| E18 | empty change set | only options change (convert v2.4 -> v2.3) / plan has a WRITE with empty `changes` but version conversion noted; no change and versions already equal -> SKIP "nothing to do" | U | no needless rewrite |
| E19 | `removeOther` NONE+NONE | strip all tags, explicit / plan warns `OTHER_VERSION_REMOVED`; result has no tags, audio intact; without flag -> T07 | R | SHA |
| E20 | default policy `SKIP_V1`, Greek | `Μίκης Θεοδωράκης` / `Ελευθερία Αρβανιτάκη` (tonos, final sigma), v1.1 + v2.3, policy default / plan: `V1_SKIPPED_NON_LATIN` WARNING; write: re-read has the v2.3 layer with the exact Greek text and **no** v1 layer; no `?`, no transliteration anywhere | U + R | SHA |
| E21 | default policy `SKIP_V1`, Serbian Cyrillic | `Ђорђе Балашевић`, `ЉЊЏЂЋЈ љњџђћј` and Latin `čćšžđ` / same as E20 for Cyrillic; v2 text identical after re-read in UTF-16 (v2.3) and UTF-8 (v2.4) | U + R | SHA |
| E22 | `CODEPAGE`, Greek | Greek title/artist/album, policy `CODEPAGE` / v1 raw bytes are Windows-1253; decoding them as windows-1253 gives the original text; v2 holds the same text; `V1_LEGACY_CODEPAGE` INFO names the codepage | R | SHA |
| E23 | `CODEPAGE`, Serbian Cyrillic | `Ђорђе Балашевић` / v1 raw bytes are Windows-1251 and decode back to the original; letters missing from the codepage (Latin `č ć š ž đ` inside Cyrillic text) are folded to ASCII with `V1_TRANSLITERATED`, others dropped with `V1_CHAR_DROPPED`, never `?` | R | SHA |
| E24 | `CODEPAGE`, mixed scripts | one file with a Greek title and a Cyrillic artist (needs two codepages) / v1 skipped with `V1_SKIPPED_NON_LATIN` (reason: mixed scripts), v2 written normally | U | - |
| E25 | `TRANSLITERATE` | Greek and Serbian Cyrillic examples, policy `TRANSLITERATE` / v1 ASCII Latin (`Mikis Theodorakis`, `Nikos Vertis`, `Eleftheria Arvanitaki`, `Djordje Balasevic`), never empty, no `?`, `V1_TRANSLITERATED`; v2 keeps the original script | U + R | SHA |
| E26 | Latin-only text unaffected | `Müller`, `Café` / policy irrelevant: `ASCII` -> `Mueller`; `LATIN1` -> raw 0xFC; no non-Latin warning | U + R | SHA |
| E27 | truly unmappable chars | title `Song 🎵 日本` / emoji and CJK dropped from v1 (policy `TRANSLITERATE`/`CODEPAGE`) or v1 skipped (`SKIP_V1`), never `?`; `V1_CHAR_DROPPED` names the field | U | - |
| E28 | v2 stays in the original script | Greek and Cyrillic titles with `AUTO`: v2.3 UTF-16, v2.4 UTF-8; explicit v2 `LATIN1` for them -> rejected with `V2_NOT_ENCODABLE` before writing | R | SHA |
| E29 | existing v1 on the source copy, policy `SKIP_V1` | source has a v1 tag (maybe mojibake) / treated as "v1 = none" for this file: kept with `OTHER_VERSION_KEPT` warning when `removeOther` is off, removed with `OTHER_VERSION_REMOVED` when on | R | SHA |
| E30 | fixtures with real scripts | new `v23-utf16-greek.mp3` (Greek) and `v24-utf8-greek-cyrillic.mp3` (Greek + Serbian Cyrillic): write copies per policy/version/encoding and re-read; v2 values identical; source SHA unchanged | R | SHA |

### Data-safety rules

| ID | Rule | Scenario | Lvl |
|---|---|---|---|
| D01 | Originals never modified | for every R test: SHA+mtime+permissions of source before/after (shared helper in `RoundTripSupport`); one explicit test running all fixtures x all version combos | R |
| D02 | No in-place write possible | `TagWriter` has no single-path method (reflection-free check: API has only `writeCopy(source, target, ...)`); contract: target == source -> IAE, source unchanged | C + A |
| D03 | Temp -> verify -> no-replace move | apply with fake `ExportTarget` recording calls: order = `reserveTempPath`, `writeCopy(temp)`, read(temp), `moveNoReplace`; temp in the *target's* directory; target appears only after verification | U |
| D04 | Failed verify/write leaves no target and no temp | T19, E02, E05, E15 each assert directory contains only expected files | U + R |
| D05 | Never overwrite | `LocalExportTarget.moveNoReplace` onto existing file -> `TARGET_EXISTS` exception, existing content unchanged; concurrent race test (2 threads, one winner) | storage contract (R) |
| D06 | Unknown data not discarded silently | T17+T20+E09: every dropped frame is reported before writing; a frame is dropped only if preflight listed it ("list is complete" property test over all fixtures x versions) | R |
| D07 | Dry-run is default and write-free | T14; plus: `WriteTagsUseCase.plan` has no `ExportTarget` mutating dependency (constructor takes a read-only `exists` view) | U + A |
| D08 | Tests stay in temp dirs | `Fixtures.copyTo` only; contract base class refuses non-temp output paths (assertion on `java.io.tmpdir`/`@TempDir` prefix) | C |
| D09 | Library types do not leak | A1/A2 (core free of jaudiotagger, `java.nio.file.Files`, etc.), new rule: `core.tagwrite` has no `java.io`/`java.nio.file.Files` use; adapter types package-private except `JaudiotaggerTagWriter` | A |
| D10 | jaudiotagger global state | 8 threads writing different files with different versions/encodings -> every result matches its own options (no `TagOptionSingleton` bleed); options restored after each call | R |

### Other contract/serialisation tests
| ID | What | Lvl |
|---|---|---|
| X01 | `TagWriterContract` (abstract, core test-jar): capabilities constant, preconditions (null/relative/exists/same-file), cancellation before start, progress final call, source untouched; run by `FakeTagWriterContractTest` and `JaudiotaggerTagWriterTest` | C |
| X02 | `ExportTarget` subset contract run by in-memory fake and `LocalExportTarget` | C |
| X03 | Golden JSON for `TagWriteOptions`, `WriteTagsRequest`, `WriteTagsPlan`, `WriteTagsReport` (cheap hand-written `toJson` in test sources; ensures records stay reflection-free expressible) | G |
| X04 | ArchUnit: A1-A11 still pass; `tag.jaudiotagger` only imported from adapter + app; `core.tagwrite` depends only on `model/port/task/text/contract` | A |
| X05 | Picocli `Id3OptionsMixin`: `--id3v2 2.3 --id3v1 1.1` and `--v1-non-latin skip|codepage|transliterate` parse, defaults (`skip`), `--id3v2 none`, bad value -> exit 2 with message, UTF8+2.3 -> exit 2 (message from T07) | U |
| X06 | Preconditions: each public method of new use cases/ports: null -> NPE; relative path -> IAE | U |

## Steps
Each step builds and tests alone (`./mvnw -q verify`). Javadoc contract + `Require` with the code.

**Scripts that matter (user decision):** a large part of the library is Greek and Serbian Cyrillic, and it must
**stay in the original script**. v2 tags always keep the original text (UTF-16 / UTF-8). ID3v1 has no encoding marker,
so a new write option `V1NonLatinPolicy` decides what happens to v1 when any v1 field contains non-Latin letters;
story 006 device profiles will choose it per device:
- `SKIP_V1` (**default**): no v1 tag is written for that file (v2 only), `V1_SKIPPED_NON_LATIN` warning. Nothing is
  transliterated or garbled. An existing v1 tag follows the "family set to none" rule (kept + warning, or removed).
- `CODEPAGE`: v1 bytes in Windows-1253 (Greek) or Windows-1251 (Cyrillic), chosen per file; mixed scripts in one file
  fall back to `SKIP_V1`. Correct only on devices set to that locale; the codepage is not recorded in the file.
- `TRANSLITERATE`: Latin transliteration in v1 only (Greek ELOT 743 style, Serbian Cyrillic Gaj).
`V1TextMode` (`ASCII` / `LATIN1`) applies to Latin-script text only. Truly unmappable chars (emoji, CJK) are never
written as `?`.

0. **Options + enums** (`C/tag/` : `Id3v2Choice`, `Id3v1Choice`, `V2Encoding`, `V1TextMode`, `V1NonLatinPolicy`, `TagWriteOptions` with
   `problems(...)`, `DEFAULT`, `selectedVersions()`). Tests T01, T07.
1. **Issue codes**: extend `IssueCode` (`UNSUPPORTED_WRITE_VERSION`, `UNSUPPORTED_FIELD`, `V1_TRANSLITERATED`, `V1_TRUNCATED`,
   `V1_CHAR_DROPPED`, `V1_SKIPPED_NON_LATIN`, `V1_LEGACY_CODEPAGE`, `V1_FIELD_NOT_REPRESENTABLE`, `V2_NOT_ENCODABLE`, `FRAME_NOT_REPRESENTABLE`, `OTHER_VERSION_KEPT`,
   `OTHER_VERSION_REMOVED`, `TARGET_EXISTS`, `TARGET_IS_SOURCE`, `SOURCE_CHANGED`, `WRITE_FAILED`, `VERIFY_MISMATCH`,
   `CANCELLED`). Add `FormatCapabilities` (+ `unsupported(options)`) . Tests T23 (pure part), E16.
2. **Scripts + change model**. `Transliterator` gets Greek (ELOT 743 style: α a, β v, γ g, δ d, ε e, ζ z, η i, θ th,
   ι i, κ k, λ l, μ m, ν n, ξ x, ο o, π p, ρ r, σ/ς s, τ t, υ y, φ f, χ ch, ψ ps, ω o; digraphs `ου` ou, `αυ/ευ` av/ev
   or af/ef, `γγ` ng, `γκ` g/ng, `μπ` b/mp, `ντ` d/nt; tonos and dialytika stripped), with Javadoc and `TransliteratorTest`
   updated (Serbian Cyrillic table complete incl. Ј Љ Њ Ћ Џ Ђ). Only used by policy `TRANSLITERATE`. `Id3v1Projection`
   applies `V1NonLatinPolicy` first (script detection per field, mixed-script check, `V1_SKIPPED_NON_LATIN`), then
   `V1TextMode` for Latin text; `CODEPAGE` uses `java.nio.charset` windows-1253 / windows-1251 (JDK type, no new dependency).
   Then `FieldChanges.between`, `CoverChange.Keep` only (Remove/Replace come with 009), `FieldChange`
   (field, old, new), `Id3v1Values`, `Id3v1Projection` (reuses `Id3v1Text`, `Transliterator`, `Id3v1Genres`). Tests T06, T15, T16, T21, E07, E08, E20-E27.
3. **Ports + testkit**: `TagWriter`, `TagWriteRequest`, `ExportTarget` subset (`C/port/`); `FakeTagWriter`
   (in-memory: keeps a metadata model per path, can inject failures, drops/keeps frames configurably),
   `InMemoryExportTarget`; contracts `TagWriterContract`, `ExportTargetContract`; fixtures `v23-extras.mp3`, `v23-utf16-greek.mp3`, `v24-utf8-greek-cyrillic.mp3` (+ generator
   script lines) and `RoundTripSupport`. Tests X01 (fake), X02 (fake), D02, D08.
4. **Use cases**: `WriteOptionsUseCase/Service`; `WriteTagsUseCase` + `WriteTagsService` (plan: capability check ->
   per file preflight -> `Id3v1Projection` -> issues; apply: fingerprint check -> `SafeTagWriteService`
   temp/write/verify/move with always-cleanup; `WriteVerifier` compares re-read vs expected). Records:
   `WriteTagsRequest(List<WriteItem(source, target, MediaMetadata desired)>, TagWriteOptions)`, `WriteTagsPlan`,
   `PlannedWrite`, `WriteTagsReport`. Tests T08, T09, T13-T17, T19, E03-E05, E11-E18, D03, D04, D07, X03, X06.
5. **Storage**: `LocalExportTarget` (temp name `.<name>.<rand>.musicstudio-tmp` in the target dir, `moveNoReplace` via
   hard link + unlink, fallback check+rename; `deleteTemp` only accepts names with the temp suffix). Tests X02, D05, E02.
6. **jaudiotagger writer**: `JaudiotaggerTagWriter` copies source -> target stream with progress/cancel (target is the
   only file opened for write), then edits the target; `Id3Writing` (core `Field` -> `FieldKey` + frame level for TXXX,
   encodings), `TagOptionSingleton` set/restored inside one `synchronized` section. v1 written from `Id3v1Values`
   byte-exactly (set fields on `ID3v11Tag`/`ID3v1Tag` after core did truncation). Preflight by diffing frame ids of
   source slot against what the conversion keeps. Tests X01 (real), T02-T06, T10-T12, T17, T20, T22, E01, E06-E10, E19, D01, D06, D10.
   Native-image: re-run tracing agent for write paths and commit metadata (risk R3).
7. **Wiring**: `MusicStudio` gets `writeOptions`, `writeTags`; `MusicStudioFactory` creates `JaudiotaggerTagWriter`,
   `LocalExportTarget`. Test: end-to-end plan+apply on 3 copied fixtures; `AppArchitectureTest`.
8. **CLI mixin** `Id3OptionsMixin` + X05; ArchUnit X04; golden-JSON X03 if not done in step 4.
9. **Close-out**: update `docs/architecture.md` map (§6.3 `ExportTarget` subset, §7.4/7.5 note on the slice), tick the
   spec table here and the story criteria, `tester` then `reviewer`.

## Acceptance criteria -> tests
AC1 T01,T02 · AC2 T03-T06 · AC3 T07-T09 · AC4 T10-T12,E19 · AC5 T13-T17,E06,E07 · AC6 T18,T19 · AC7 T20-T22 · AC8 T23.
Edge cases: read-only E01/E02, disappears E03/E04, disk full E05, non-Latin v1 E06 and E20-E30, >30 chars E07, v2.4->v2.2 E09.
Data safety: D01-D10 (+ "write to temp, verify, atomic move" = D03, D05).

## Open questions / risks
Decisions (answered by the user):
- Q1 No throwaway `write-tags` command; 003 ships only the `Id3OptionsMixin`.
- Q2 Partial `ExportTarget` (4 methods) in 003, completed by 004: accepted.
- Q3 v1 `LATIN1` with unmappable chars: drop + warn `V1_CHAR_DROPPED`, accepted. Greek and Serbian Cyrillic are not
  transliterated by default: policy `V1NonLatinPolicy` (default `SKIP_V1`, or `CODEPAGE`, or `TRANSLITERATE`), per
  device profile (user decision).
- Q4 Unchanged file + same versions: skip with an INFO (E18): accepted.

Risks:
- R1 jaudiotagger v2.4 -> v2.2/v2.3 conversion may silently drop or rewrite frames; the "preflight list is complete"
  test (D06) may force adapter-side workarounds (e.g. copying raw frame bodies). If jaudiotagger cannot preserve unknown frames, escalate before
  working around.
- R2 jaudiotagger may write v1 only when the v2 tag also changes, or re-add v1 by option defaults; verify by re-read in T02/T10.
- R3 Native image: write paths touch more reflection (frame classes) than read; needs a second agent run (CLI native smoke in 004).
- R4 `chmod`/tmpfs tests are POSIX-only (skipped on Windows / root); disk-full is mainly proven with the fake.
- R6 Native image: Windows-1251/1253 need the charsets registered (`-H:+AddAllCharsets` or equivalent); verify in the
  native smoke test.
- R7 `CODEPAGE` v1 bytes are read back by the story-001 reader as Latin-1 (mojibake in `scan`), and the re-read verify
  must compare the raw v1 bytes or decode with the same codepage. Follow-up for the reader (not in 003): optional
  codepage hint when reading v1.
- R5 `moveNoReplace` on FAT sticks has a documented race (architecture §8.2).
