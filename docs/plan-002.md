# Plan 002 – Extract metadata from file and folder names

Story: [`docs/stories/002-extract-metadata-from-names.md`](stories/002-extract-metadata-from-names.md) ·
Architecture: §3, §7.2, §10, §11, §12 of [`docs/architecture.md`](architecture.md) ·
ADRs 0002 (DbC), 0009 (own parser/matcher), 0010 (web-neutral use cases), [0012](adr/0012-pattern-matching-unicode-and-ambiguity.md) (new).
Baselines: [`plan-001.md`](plan-001.md), [`plan-003.md`](plan-003.md) (both done). Level abbreviations as in plan-003:
**U** unit · **C** contract · **R** real files (`@TempDir`) · **A** ArchUnit · **G** golden JSON · **CLI** picocli test.

## Status
- **Implemented and verified.** `mvn -q verify` is green (JDK 25): 1,254 tests, 0 failures. Spec and the ten open questions
  were approved by the user (all recommendations accepted). The `musicstudio extract` command was checked by hand in the
  native image with Greek and Serbian Cyrillic names.
- `tester` found 6 production bugs (parser double error, no-match reason, natural order of auto-numbering, `KEEP_EMPTY`,
  `ManualEdits` relative paths, ambiguous row text); `reviewer` found no blocker and 3 should-fix items (space-only
  separators falsely ambiguous, track 0 vs write verification, matcher performance). All fixed.
- Deviations/decisions made while implementing: track `0` is valid, so `FieldValue.Position` now allows 0..9999 (a stored 0 is
  read as "not set", the write verifier treats 0 like absent); the same placeholder twice in one pattern is a parse error;
  `PatternError.column` counts code points; the ANSI colour for table rows was skipped (the status column marks rows);
  the table shows `AMBIGUOUS (skipped)`.
- Open notes (not blocking): OVERWRITE with a pattern track `3` replaces an existing `3/12` by `3` (it shows as a change);
  a non-accepted ambiguous file is not counted in auto-numbering until accepted; `alternativeCount` is 0 or 1;
  `--merge`/`--number-by` are parsed as strings by hand; the scan request hardcodes MP3 (generalise with story 012).
- OpenAPI is deferred to story 013; golden JSON (X03) covers the new records.

## Scope decision: how the `pattern` package is split between 002 and 004
User decision: 002 is built before 004; 004's output pattern reuses the language.

| Piece | 002 | 004 |
|---|---|---|
| Grammar + parser for **input and output** patterns: `Token` (`Literal`, `Placeholder`, `Wildcard`), `PatternSegment`, `InputPattern`, `OutputPattern`, `PatternParser.parseInput/parseOutput`, `PatternParseResult`, `PatternError` | yes (one grammar, one parser, one set of parse-error tests; output-only rules `{track:00}` / "no `{*}`" are parser rules) | - |
| Typed value parsing `FieldValueParser` (text -> `YearValue`, `Position`, `Genres`, `Text`); also used for `--set field=value` | yes | reuses |
| Matcher `PathMatcher`, `MatchResult`, `MatchKind` | yes | - |
| `ProposeMetadataUseCase` + `extract` package | yes | consumes `Proposal` |
| Renderer `PathRenderer`, `RenderResult`, `FileNameSanitizer`, `NamingRules` | **no** | yes |

Justification: the parser is the part that is hard to change later (column errors, grammar); the renderer depends on
004-only decisions (sanitiser, collision, ASCII). Cost of the early `OutputPattern` is ~40 lines + tests; it is not a
speculative seam because 004 is the next story.

## Affected modules
- `music-studio-core`: new packages `pattern`, `extract`; `IssueCode` extended; `CoreArchitectureTest` layering extended.
  No new dependency (matcher is hand written, **no `java.util.regex`**, only JDK `java.text.Normalizer`).
- `music-studio-app`: `MusicStudio` gets `proposeMetadata`; `MusicStudioFactory` creates `ProposeMetadataService` (no ports needed).
- `music-studio-cli`: `ExtractCommand` + three renderers in `render/` (table/json/csv), reusing `OutputFormat`, `ExitCode`, `Text`, `Json`.
- `music-studio-tag-jaudiotagger`, `music-studio-storage`: **unchanged** (002 reads via story-001 `scan`, writes nothing).
- GUI: does not exist yet (010).

Naming clash: `tag.FieldChange(field, oldValue, newValue)` already exists (003). The extract record is therefore named
`ProposedChange(field, before, after, origin)` (architecture §3/§7.2 call it `FieldChange`; the map is updated in close-out).

Port sanity: 002 adds **no port**. The use case is pure (`List<ScannedFile>` in, `ProposalSet` out), so it works with any
tag library; nothing jaudiotagger-specific can leak.

Transport-neutral (ADR 0010): request/result are records, `Map<Field, …>` keys are enums (JSON object keys),
`Path` is a string, `Optional` is "absent field". The sealed `FieldOverride`/`FieldEdit` get an explicit `type` tag in the
hand-written golden JSON. Stateless service.

## Design decisions (details)
1. **Matching** is the DP of architecture §10.2 operating on code points (`int[]`), never on `char` splits, so emoji and
   other supplementary characters are safe. Segment captures start/end only at literal boundaries.
2. **Unicode**: pattern literals, file names and captured values are normalised to **NFC** before matching (macOS gives
   NFD names; `č` typed as c+caron must match `č`). Digits for typed placeholders are **ASCII 0-9 only** (no Arabic-Indic).
   Whitespace for the trimmed-space rule = `Character.isWhitespace` plus NBSP/U+3000. Literals are case-sensitive and
   not folded (`–`, `—`, `-` are three different literals). Details in ADR 0012.
3. **Empty capture** (after strip) invalidates that split (wildcards may be empty). `A - - B` with `{artist} - {title}` has no valid split with a non-empty title other than `title = "- B"`; E05 fixes the exact rule.
4. **Ambiguity** = >=2 complete splits (count capped at 2). Chosen split: shortest capture first, left to right. Typed
   placeholders prune splits first. `alternativeCount` and the chosen raw captures are returned for display.
5. **Value pipeline** in `ProposeMetadataService` exactly as §7.2: existing -> pattern -> fixed -> auto-number -> manual.
   `KeepExisting` wins over pattern/fixed for that field. `FILL_EMPTY` only fills absent fields. Every difference between
   `existing` and `proposed` is listed as a `ProposedChange` with `before` (so overwriting is always visible).
6. **Status** per file: `NO_PATTERN`, `MATCHED`, `AMBIGUOUS`, `AMBIGUOUS_ACCEPTED`, `NO_MATCH`. `exportable()` is true only
   for `NO_PATTERN`, `MATCHED`, `AMBIGUOUS_ACCEPTED`. A NO_MATCH file still gets fixed values / auto-number? **No**: a
   no-match file is `proposed == existing` (§7.2), so partial writes of "half-matched" files cannot happen. (Q3)
7. **Auto-numbering** numbers exportable files per parent folder starting at 1; order `FILE_NAME` (the `PathOrder` natural
   order from 001, so `2` before `10`) or `EXISTING_TRACK` (tag track, files without one last, then by name). Only fills
   `TRACK` when the pattern/fixed/keep gave no track (a track from the name wins; documented, test A07b).
7a. **CLI** `musicstudio extract <dir> --pattern P [--set field=value]... [--keep field]... [--merge overwrite|fill-empty]
   [--number-by name|tag [--number-total]] [--accept-ambiguous] [-r] [--include-hidden] [-f table|json|csv]`.
   Dry-run only: there is **no** `--apply` and no write path in the command. It calls `ScanMediaUseCase` then
   `ProposeMetadataUseCase` (the same two calls the GUI will make). Rows: MATCHED / AMBIGUOUS (shows chosen split) /
   NO_MATCH (reason) / SKIPPED-ambiguous (CLI default), marked by a status column and (table only) ANSI colour when a TTY.
   Exit codes: 0 ok, 2 usage/invalid pattern/bad `--set`; no-match rows do not change the exit code (Q2).

## TEST SPECIFICATION (confirm before implementing)

Every test runs on `@TempDir` copies or pure in-memory data. Real files only where a command/scan path is involved.
"DS" = data-safety assertion carried by the test. `P` = parser, `M` = matcher, `V` = value parser, `S` = service.

### Acceptance criteria

| ID | Criterion | Scenario (given / when / then) | Lvl | DS |
|---|---|---|---|---|
| A01 | AC1 per-file values for every placeholder | given `Best Of 2020/Artist - Song - 2020.mp3` + `{album}/{artist} - {title} - {year}` / propose / `extracted` has album `Best Of 2020`, artist, title, year `2020`, status MATCHED; `proposed` holds typed values (`YearValue(2020)`) | U | - |
| A02 | AC1 all nine placeholders | given pattern with `{title}{artist}{albumartist}{album}{year}{track}{disc}{genre}{comment}` over a 3-level path / propose / each field extracted, typed (`Position`, `Genres`, `YearValue`) | U | - |
| A03 | AC1 `{*}` ignores | given `{*} - {artist} - {title}` on `Junk - A - B` / `extracted` has no key for `{*}`; proposed has artist/title only | U | - |
| A04 | AC1 folder levels | given `{artist}/{album}/{track} - {title}` on 3 levels / fields from each level; `{album}/…` = file's folder; scan-root name usable as `{album}` (ancestor above scan root) | U | - |
| A05 | AC1 path with fewer elements than segments | given `{artist}/{album}/{title}` on a file directly in `/` (or a 2-element relative-to-root path) / NO_MATCH, reason names the missing segment index | U | - |
| A06 | AC2 no match marked with reason | given file `Artist Song.mp3` + `{artist} - {title}` / propose / status NO_MATCH, `matchDetail` = `separator " - " not found after "Artist Song"`, `NO_MATCH` WARNING issue, `exportable()==false` | U | never exportable |
| A07 | AC2 no-match file untouched in proposal | given NO_MATCH file with `--set genre=Pop` and auto-number configured / propose / `proposed == existing`, `changes` empty (no half-applied values) | U | not written |
| A07b | AC7 name beats auto-number | given pattern provides `{track}` for file X, auto-numbering on / X keeps the name's track; others numbered | U | - |
| A08 | AC3 ambiguous marked | given `Artist - Part - Song.mp3` + `{artist} - {title}` / status AMBIGUOUS, chosen split artist=`Artist`, title=`Part - Song` shown, `alternativeCount>=1`, `AMBIGUOUS_MATCH` WARNING, `exportable()==false` | U | not exportable until accepted |
| A09 | AC3 accept via spec | same with `acceptAmbiguous=true` / AMBIGUOUS_ACCEPTED, exportable, same chosen split | U | - |
| A10 | AC3 accept per file (GUI) | `ManualEdits.acceptedAmbiguous = {path}` / only that file AMBIGUOUS_ACCEPTED, others stay AMBIGUOUS | U | - |
| A11 | AC3 fix by edit (GUI) | manual `FieldEdit.Set(artist,"Artist - Part")` on an ambiguous file / proposed artist = edit, origin MANUAL; status unchanged until accepted | U | - |
| A12 | AC3 CLI skips ambiguous by default | CLI over a dir with one ambiguous file / row marked `AMBIGUOUS (skipped)`; with `--accept-ambiguous` marked accepted | CLI | - |
| A13 | AC4 fixed value | `--set genre=Balkan`, `albumartist=Various Artists` for all files / every MATCHED proposal has both with origin FIXED; `Fixed` with wrong type (year="abc") rejected in CLI (exit 2) and in `ExtractionSpec` (IAE: type mismatch) | U + CLI | - |
| A14 | AC4 fixed beats pattern | pattern gives genre `X`, fixed genre `Y`, OVERWRITE / proposed `Y` | U | - |
| A15 | AC5 keep existing | `KeepExisting(ARTIST)` with pattern giving artist / proposed artist = existing tag artist, no change listed; `KeepExisting` for a field with no existing value -> field stays empty (+ INFO `KEEP_EMPTY`) | U | existing data kept |
| A16 | AC6 overwrite default | existing title `Old`, pattern `New` / default spec / proposed `New`, change before=`Old` after=`New` | U | change visible |
| A17 | AC6 fill-empty | same with `FILL_EMPTY` / proposed `Old`; empty title gets `New`; applies to fixed values too | U | no silent loss |
| A18 | AC6 CLI flag | `--merge fill-empty` / equals A17 via CLI; bad value -> exit 2 | CLI | - |
| A19 | AC7 auto number by name | 12 files in a folder, no track in names, `FILE_NAME` / 1..12 in natural order (`2` before `10`), per folder restart at 1 | U | - |
| A20 | AC7 auto number by existing tag | tags have tracks 5,3,9, one without / numbered 1..4 in tag order, no-track file last | U | - |
| A21 | AC7 total | `setTotal=true` / `Position(n, total=count in folder)`; false -> total absent | U | - |
| A22 | AC7 numbers only exportable files | NO_MATCH/unaccepted-ambiguous files are not counted and not numbered | U | - |
| A23 | AC8 extraction writes nothing | propose over real fixture copies via the app facade / SHA-256+mtime+permissions of every file and directory listing identical before/after | R | originals untouched |
| A24 | AC8 produces change set | `changes` = exactly fields where proposed differs from existing, with origin PATTERN/FIXED/AUTO_NUMBER/MANUAL, deterministic (second call equal) | U | - |
| A25 | precedence | one field set by existing, pattern, fixed, auto-number and manual edit in turn / later wins in §7.2 order; manual `Clear` removes the value (change after=empty) | U | - |
| A26 | CLI happy path | story command `extract <dir> --pattern "{album}/{artist} - {title} - {year}" --set genre=Pop` on fixtures / table has a row per file with all extracted values + genre, summary `matched/ambiguous/no match`, exit 0 | CLI | tree unchanged |
| A27 | CLI formats | `-f json` valid JSON array incl. status/extracted/changes; `-f csv` has header + RFC-4180 quoting (comma, quote, newline, Cyrillic) | CLI | - |
| A28 | CLI usage errors | missing `--pattern` allowed? (see Q4) / invalid pattern -> exit 2 + message with column marker; unknown field in `--set`/`--keep` -> exit 2; no `--apply` option exists (`--apply` -> picocli unknown option, exit 2) | CLI | no write path |

### Pattern language: parser (P) and value parser (V)

| ID | Case | Scenario | Lvl |
|---|---|---|---|
| P01 | valid input patterns | the two story patterns, `{*}`, `{track} - {title}`, `{year} - {album}/{track}. {title}` parse; `source` kept verbatim | U |
| P02 | tokens | `{artist} - {title}` -> [Placeholder(ARTIST), Literal(" - "), Placeholder(TITLE)] ; `/` splits segments; `{{` and `}}` are literal braces | U |
| P03 | errors with column | unknown placeholder `{foo}` (col of `{`), unbalanced `{title`, stray `}`, empty segment `a//b` or leading/trailing `/`, empty pattern, `{artist}{title}` adjacent untyped (rejected), `{year}{track}` adjacent typed allowed? (see Q5); multiple errors all returned, ordered by column | U |
| P04 | input-only / output-only | `{*}` in output -> error; `{track:00}` in input -> error; `{track:00}` in output ok -> padWidth 2; `{title:00}` (format on non-number) -> error; `{track:0a}` error | U |
| P05 | case and whitespace | `{Title}` -> error "unknown placeholder" with suggestion of `title`; `{ title }` -> error; names lowercase only | U |
| P06 | extension never in pattern | pattern ending `.mp3` is parsed as literal but yields a `PATTERN_EXTENSION` WARNING hint (the extension is stripped before matching, so it would never match) | U |
| P07 | non-ASCII literals | pattern `{artist} – {title}` (en dash), `{artist} ・ {title}`, Greek `{artist} Και {title}`, emoji `🎵 {title}` parse; literals NFC-normalised | U |
| P08 | robustness | 10 000 random strings (seeded) never throw, always Valid or Invalid; 5 000-char pattern parses in < 100 ms | U |
| P09 | immutability | records unmodifiable lists; null args NPE | U |
| V01 | year | `2020`, `(2020)`, `[2020]` -> `YearValue(2020)` (brackets removed); `20x0`, `99`, `0999`, `3000`, `２０２０` (full-width digits), `٢٠٢٠` (Arabic-Indic) -> reject with message | U |
| V02 | track/disc | `3`, `03`, `003`, `3of12`, `3 of 12`, `3/12`, `03/12` -> `Position`; `0`? (accepted as 0? see Q6), `12345`, `3of`, `of12`, `3/`, `-3` -> reject | U |
| V03 | text | strip; collapse inner runs of whitespace? **No** (kept as is, cleanup is 005); NFC normalised; empty -> reject | U |
| V04 | genre | `Pop` -> `Genres([Genre("Pop")])`; `Pop; Rock` -> two genres; empty -> reject | U |
| V05 | `--set` text round trip | `FieldValueParser.parse(Field, String)` for every field type accepts what `FieldValue.display` produces (round-trip property over all fields) | U |

### Matcher (M) – literals, spaces, typed placeholders

| ID | Case | Scenario | Lvl |
|---|---|---|---|
| M01 | basic split | `Artist - Song - 2020` with `{artist} - {title} - {year}` -> MATCHED | U |
| M02 | underscore separator | `Album/Artist_Song.mp3` with `{album}/{artist}_{title}` | U |
| M03 | case-sensitive literals | literal `Feat.` does not match `feat.` -> NO_MATCH with reason | U |
| M04 | extra spaces | `Artist  -   Song` and ` Artist - Song ` match `{artist} - {title}`; captured values stripped | U |
| M05 | missing separator in some files | 3 files, one lacks ` - ` / two MATCHED, one NO_MATCH naming the separator; batch result keeps order | U |
| M06 | double separator | `Artist - - Song` / chosen rule documented in E05 | U |
| M07 | non-greedy left to right | `A - B - C` with `{artist} - {title}` -> artist `A`, title `B - C`, AMBIGUOUS (2 splits), `alternativeCount==1` | U |
| M08 | typed placeholder resolves ambiguity | `Artist - Song - 2020` with `{title} - {year}`: only one split has a valid year -> MATCHED not AMBIGUOUS, title `Artist - Song` | U |
| M09 | typed resolves track | `01 - Artist - Song` with `{track} - {title}` -> MATCHED, track 1, title `Artist - Song`; `Artist - 01 - Song` -> NO_MATCH (`"Artist" is not a track number`) | U |
| M10 | no match reason points at token | `Artist - 20x0` with `{artist} - {year}` -> reason `"20x0" is not a 4-digit year`, segment index given | U |
| M11 | wildcard may be empty | `{*}` captures empty/anything; two wildcards ok | U |
| M12 | extension handling | `Song.mp3` -> `{title}`: title `Song`; `Song.v2.mp3` -> `Song.v2`; file with no extension; `.hidden.mp3`; uppercase `.MP3` | U |
| M13 | folder segments independent | wrong folder, right file name -> NO_MATCH naming the folder segment; reason carries segment index | U |
| M14 | path fewer than segments | see A05 | U |
| M15 | pathological | 300-char name, 6 placeholders, many repeated separators / returns within 100 ms, count capped at 2 | U |
| M16 | determinism | 1 000 runs same result; no static state; 8 threads in parallel same results | U |
| M17 | long list | 5 000 files propose in < 1 s on CI (generous bound, tagged slow) | U |

### Edge cases from the story and real-world names

| ID | Edge case | Scenario | Lvl |
|---|---|---|---|
| E01 | year in brackets | `Song (2020).mp3` with `{title} ({year})` and with `{title} {year}`; `[2020]` likewise -> year 2020, title `Song`, status MATCHED (not AMBIGUOUS) | U |
| E02 | leading track `01 - ` | `01 - Song`, `1 - Song`, `001 - Song` with `{track} - {title}` -> track 1 | U |
| E03 | leading track `01.` and `01_` | `01. Song`, `01.Song`, `01_Song`, `01 Song` with `{track}. {title}` / `{track}_{title}` / `{track} {title}` | U |
| E04 | `3of12`, `3/12` | name `03of12 Song` with `{track} {title}` -> `Position(3,12)`; `3 of 12 - Song`; the `3/12` form via V02 and as `--set track=3/12` (a `/` cannot occur in a file name) | U |
| E05 | double separator / empty capture | `Artist -  - Song`, `- Song`, `Artist - ` with `{artist} - {title}` -> an empty-after-strip capture invalidates that split; rule + resulting status asserted (NO_MATCH for the last two) | U |
| E06 | `2020 - Album` folder | `2020 - Album/01 - Song.mp3` with `{year} - {album}/{track} - {title}` -> year 2020, album `Album`; folder `2020 Album` -> NO_MATCH | U |
| E07 | ` - ` inside artist name | `AC - DC - Thunderstruck` with `{artist} - {title}` -> AMBIGUOUS chosen `AC`/`DC - Thunderstruck`; accept+manual fix gives `AC - DC`; with `{artist} - {title} - {year}` and `AC - DC - Thunderstruck - 1990` also AMBIGUOUS; `Artist - Song` stays MATCHED | U |
| E08 | underscore ambiguity | `Artist_Name_Song` with `{artist}_{title}` -> AMBIGUOUS | U |
| E09 | Greek | `Μίκης Θεοδωράκης - Ένα το χελιδόνι.mp3` in folder `Ελευθερία Αρβανιτάκη/…`; final sigma `ς`, tonos, dialytika `ΐ`, capital sigma; pattern with Greek literal `{artist} και {title}` | U + R (file name on disk) |
| E10 | Serbian Cyrillic | `Ђорђе Балашевић - Живот је море.mp3`, `ЉЊЏЂЋЈ`, folder `Теразије`; pattern `{artist} – {title}` with en dash; mixed Cyrillic + Latin `čćšžđ` in one name | U + R |
| E11 | Latin diacritics | `Đorđe Balašević - Ćevapi, Šljivovica.mp3`; `Müller`, `Café`; values keep exact characters | U + R |
| E12 | NFC vs NFD | file name given as `c`+U+030C (NFD) and pattern literal in NFC (and vice versa) -> match; extracted value is NFC; `Ђ` unaffected | U |
| E13 | emoji | `🎵 Artist - Song 🎶.mp3`, ZWJ sequence `👨‍👩‍👧`, flag, skin-tone modifier, in a literal and in a capture: matches; no split inside a surrogate pair; emoji kept in `extracted` | U + R |
| E14 | exotic whitespace | NBSP / ideographic space around ` - ` matches the trimmed-space rule; tab does too; newline in name -> treated as ordinary char of capture | U |
| E15 | dash look-alikes | `–` (en), `—` (em), `‐` (U+2010) are not folded to `-`: `A – B` against `{artist} - {title}` -> NO_MATCH with a reason that mentions the character (hint, Q7) | U |
| E16 | digits from other scripts | `٢٠٢٠`/`２０２０` not accepted as year (V01); as text in title ok | U |
| E17 | dots in names | `A.B.C - Song.Part 2.mp3` -> extension stripped only once | U |
| E18 | very long names / 260+ chars | match works; no exception | U |
| E19 | windows-style root | path of `C:\Music\Album\a.mp3` shape handled by `Path` elements (test uses `Path.of` on current OS; Windows-only case `@EnabledOnOs`) | U |
| E20 | conflicting existing tag | file has v1/v2 conflict (`ScannedFile.conflicts`) / proposal uses `effective()` as `existing` and carries an INFO `TAG_CONFLICT` | U |
| E21 | duplicate target names | two files extract the same artist/title / both proposed, no dedupe (collision is 004) | U |
| E22 | empty file list / one file | `ProposalSet` empty, counts 0; no NPE | U |
| E23 | non-ASCII path on disk via scan -> propose | real file `Ђорђе – 🎵 ČĆŠŽĐ.mp3` copied to temp, scan, propose with `{artist} – {title}` -> correct values | R |
| E24 | genre placeholder | `Pop` and `Pop; Rock` and unknown genre text -> `Genres`; v1 genre table not required here | U |
| E25 | disc/track cross | `CD1/03 - Song` with `CD{disc}/{track} - {title}` -> disc 1 track 3 | U |

### Data-safety rules

| ID | Rule | Scenario | Lvl |
|---|---|---|---|
| D01 | Originals never touched | A23 plus CLI A26: SHA+mtime+permissions+directory listing before/after for all fixtures, including NO_MATCH and ambiguous | R + CLI |
| D02 | No write path in 002 | ArchUnit: `core.extract` and `core.pattern` depend on none of `core.port`, `java.io`, `java.nio.file.Files`, `core.tag` write classes, `java.util.regex`; `ProposeMetadataService` constructor has no collaborators | A |
| D03 | No-match / unaccepted-ambiguous never exportable | A06, A08; property test over random names: `exportable() ⇒ status in {NO_PATTERN, MATCHED, AMBIGUOUS_ACCEPTED}` | U |
| D04 | Existing tag data never discarded silently | A15-A17, A25: every existing non-empty value that differs in `proposed` appears in `changes` with `before`; FILL_EMPTY never overwrites; unmapped/unknown frames are not part of the proposal (not touched) - asserted: `proposed` is derived from `existing` by copy, other fields equal | U |
| D05 | Dry-run default in CLI | A28: the `extract` command class has no apply/write option, does not accept `--apply`, does not reference any writer type (ArchUnit on `cli`) | CLI + A |
| D06 | Tests stay in temp dirs | fixtures copied via `Fixtures.copyTo` only (existing helper); R tests assert the working dir is under `java.io.tmpdir` | R |
| D07 | Stale preview | proposal carries the `ScannedFile` (incl. `FileFingerprint`) so 003/004 can detect changes; asserted `Proposal.file()` is the same instance/fingerprint passed in | U |
| D08 | Library types do not leak | core ArchUnit A1/A2 still pass; extract/pattern only import `contract/model/text/pattern/scan/task` | A |

### Contract / serialisation / architecture

| ID | What | Lvl |
|---|---|---|
| X01 | Preconditions (`Require`): `propose(null)`, null lists/maps/elements, relative paths, `Fixed` type mismatch, `AutoNumbering` null order, negative counts -> NPE/IAE, messages name the argument; every public method of the new types | U |
| X02 | `ProposeMetadataUseCaseContract` (abstract, core test-jar): precedence, status rules, input order, immutability of results, thread-safety (8 threads), determinism; run against `ProposeMetadataService` (so an alternative implementation, e.g. server-side, can prove itself) | C |
| X03 | Golden JSON (hand-written `toJson` in test sources, like plan-003 X03): `ExtractionSpec`, `ProposeRequest`, `Proposal`, `ProposalSet`, `MatchResult`, `PatternParseResult.Invalid`; includes Cyrillic, emoji, sealed `FieldOverride`/`FieldEdit` with `type` tag, `Map<Field,…>` keys, `Optional` absent/present | G |
| X04 | ArchUnit: layering extended (`pattern` above `text`/`model`, `extract` above `scan`/`pattern`); `export` may use `extract`, not the reverse; records only in `extract`/`pattern` (no mutable classes, no setters); services `final` and implement exactly one use case (existing A7) | A |
| X05 | Records `ProposalSet` counts consistent (`matched+ambiguous+noMatch <= size`, `NO_PATTERN` counted separately) | U |
| X06 | App wiring: `MusicStudioFactory` yields working `proposeMetadata` over scan result of 3 copied fixtures; `AppArchitectureTest` still green | U |
| X07 | Native smoke: the native CLI runs `extract` on a fixture folder (no reflection added; `java.text.Normalizer` works in native) - run manually / in the `native` profile | CLI |
| X08 | CLI table rendering: wide characters (Cyrillic, Greek, emoji) do not break column alignment more than the existing `scan` table (same helper), and are never truncated mid surrogate pair | CLI |

**Count: 101 spec rows** (A: 29 incl. A07b, P: 9, V: 5, M: 17, E: 25, D: 8, X: 8); several rows are parameterised over many inputs.

## Acceptance criteria -> tests
AC1 A01-A05,M01-M04,E09-E13,E25 · AC2 A06,A07,M05,M10,M13 · AC3 A08-A12,M07,M08,E07,E08 · AC4 A13,A14 · AC5 A15 ·
AC6 A16-A18 · AC7 A07b,A19-A22 · AC8 A23,A24,D01-D04 · Interface (CLI) A12,A18,A26-A28,X07,X08 ·
Edge cases (story): extra spaces M04, double separators M06/E05, missing separator M05, bracket year E01, leading track E02/E03,
`2020 - Album` E06, non-ASCII E09-E16,E23. Data safety: D01-D08.

## Steps
Each step builds and tests alone (`mvn -q verify`). Javadoc contract and `Require` with the code.

0. **ADR 0012** + `pattern` model: `Token`, `PatternSegment`, `InputPattern`, `OutputPattern`, `PatternError`,
   `PatternParseResult`, `PatternParser` (input + output). `IssueCode` additions: `PATTERN_NO_MATCH`, `AMBIGUOUS_MATCH`,
   `PATTERN_EXTENSION`, `KEEP_EMPTY`, `TAG_CONFLICT` (reuse existing codes where present). Tests P01-P09.
1. **`FieldValueParser`** (typed parsing, ASCII digits, NFC, brackets, `3of12`). Tests V01-V05, E16.
2. **Matcher**: `MatchKind`, `MatchResult`, `PathMatcher` (code-point DP, NFC, trimmed spaces, counted capped at 2,
   reason with segment index). Tests M01-M17, E01-E19, E24, E25.
3. **`extract` records** with compact-constructor validation: `ExtractionSpec`, `FieldOverride`, `MergeMode`,
   `AutoNumbering`, `ManualEdits`, `FieldEdit`, `MatchStatus`, `ChangeOrigin`, `ProposedChange`, `Proposal`,
   `ProposalSet`, `ProposeRequest`. Tests X01 (records), X05.
4. **`ProposeMetadataUseCase` + `ProposeMetadataService`** (pipeline §7.2) without auto-number. Testkit:
   `ProposeMetadataUseCaseContract`, `ScannedFiles` builder. Tests A01-A11, A13-A18, A24, A25, E20-E22, D03, D04, D07, X02.
5. **Auto-numbering** (uses `PathOrder`). Tests A07b, A19-A22.
6. **ArchUnit + golden JSON**: layering, D02, D08, X03, X04.
7. **Wiring** in `music-studio-app` (`MusicStudio.proposeMetadata`, factory). Tests X06, A23, E09-E11/E13/E23 (real files), D01, D06.
8. **CLI** `ExtractCommand`, `ExtractRenderer` (+ table/json/csv), registered in `CliMain`/`MusicStudioCommand`;
   `--set/--keep` parsed via `FieldValueParser` (no logic in the CLI). Tests A12, A18, A26-A28, D05, X08; X07 manual.
9. **Close-out**: update `docs/architecture.md` (§3 `pattern`/`extract` rows: renderer left to 004, `ProposedChange` name;
   §7.2 note; §10 NFC/ASCII-digit rules), tick spec table here and in the story, `tester` then `reviewer`.
   OpenAPI stays deferred to story 013 (user decision); golden JSON X03 covers the new records.

## Open questions and risks

Decisions the user must make (recommendation first):
- **Q1 Pattern split** (above): 002 ships parser for input+output, matcher, use case; 004 ships renderer/sanitiser. Recommended; please confirm.
- **Q2 CLI exit code** when some rows are NO_MATCH/AMBIGUOUS: recommend `0` (it is a preview; rows are marked) and `1` only with
  a new `--strict`. Alternative: `1` whenever any row is not matched (§11 "completed with item errors").
- **Q3 NO_MATCH files and fixed values**: recommend a no-match file gets **nothing** (not even `--set genre=`),
  so a batch cannot be half-tagged. Alternative: apply fixed values to all files regardless of match.
- **Q4 Pattern optional?** Story lists "fixed value" and "auto-number" without a pattern; architecture allows
  status `NO_PATTERN`. Recommend: `--pattern` optional if at least one of `--set/--keep/--number-by` is given, else exit 2.
- **Q5 Adjacent placeholders** (`{track}{title}`, `{year}{track}`): recommend reject all adjacent placeholders without a literal between (as in architecture §10.1, which only exempts typed ones; here stricter: typed+typed also rejected, users write `{track} {title}`).
- **Q6 Track `0` / `00`** accepted (some albums start with intro track 0)? Recommend accept `0`.
- **Q7 Dash look-alikes** (`–` `—` `-`): recommend **no folding** (literal means literal) but a hint in the no-match reason.
  Alternative: treat all dashes as one in literals.
- **Q8 NFC normalisation** of extracted values (ADR 0012): recommend yes; the value then differs byte-wise from an NFD file
  name on macOS but is what tags should contain.
- **Q9 Underscores**: `Artist_Name_Song` stays AMBIGUOUS (no "replace `_` with space" here; that is cleanup 005). Confirm.
- **Q10 `ProposedChange` instead of `FieldChange`** (name clash with 003): accepted by default, say if you prefer renaming 003's.

Risks:
- R1 DP on code points with NFC: performance for 5 000 files while typing (GUI). Mitigated by M15/M17; if too slow, cache
  normalised names per `ScannedFile` in the GUI layer, not in core.
- R2 `java.text.Normalizer` in GraalVM native image: believed fine (JDK built-in data); X07 verifies.
- R3 Ambiguity count semantics ("what is a different split") must stay stable because story 007 will suggest patterns from
  it; ADR 0012 fixes the definition (distinct capture-boundary vectors).
- R4 Early `OutputPattern` may need small grammar tweaks when 004 starts; parser tests are the safety net, grammar changes
  are allowed in 004 as long as 002's tests stay green.
- R5 Table renderer column widths with wide glyphs (CJK/emoji): same limitation as `scan`; X08 documents it.
