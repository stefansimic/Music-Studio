# Plan 002 – Extract metadata from file and folder names

Story: [`docs/stories/002-extract-metadata-from-names.md`](stories/002-extract-metadata-from-names.md) ·
Architecture: [`docs/architecture.md`](architecture.md) §7.2, §10 · ADRs 0009,
[0010](adr/0010-input-pattern-matching-tiers-and-normalisation.md) (new).

Every step builds and tests on its own:
`export JAVA_HOME=~/.sdkman/candidates/java/25.4.4+1-graal && ./mvnw -q verify` (or `-pl <module> -am`).
Write the Javadoc contracts (template §4.2) together with the code. Every public method starts with `Require`
checks. Records validate in their compact constructor.

Abbreviations as in plan 001: `C/` = `music-studio-core/src/main/java/dev/rs/slava/icxc/musicstudio/core/`,
`CT/` = `music-studio-core/src/test/java/dev/rs/slava/icxc/musicstudio/core/`,
`P/` = `src/main/java/dev/rs/slava/icxc/musicstudio/`, `T/` = `src/test/java/dev/rs/slava/icxc/musicstudio/`.

## Affected modules
| Module | Change |
|---|---|
| `music-studio-core` | new `core.pattern` (input side only), new `core.extract`, `FieldValueParser` in `core.model`, testkit helper, ArchUnit A12 |
| `music-studio-app` | `MusicStudio` gains `propose`; factory wires `new ProposeMetadataService()` |
| `music-studio-cli` | new `extract` subcommand, `ExtractionOptions` mixin, table/JSON proposal renderers |
| `music-studio-storage`, `music-studio-tag-jaudiotagger` | **no change.** Extraction is pure. It reads names from `Path` and existing tags from `ScannedFile`. |

No new port and no new dependency. Core stays JDK-only (`java.text.Normalizer` is already whitelisted by A1).
The mp3agic check does not apply because no tag port changes.

## Decisions taken in this plan (not or differently covered by the architecture)
| # | Decision | Why |
|---|---|---|
| D1 | **Separator tiers + possessive whitespace + NFC** ([ADR 0010](adr/0010-input-pattern-matching-tiers-and-normalisation.md)) | With §10.2 as written, `Jay-Z - Song` is ambiguous and gets split wrongly. One split must count as one solution. macOS names are NFD. |
| D2 | `MatchResult.alternative` (`Map<Field,String>`, the greedy split) replaces `int alternativeCount`. `Proposal` gains `alternative` | The story says "show the chosen split". Showing the other split as well tells the user *why* the match is ambiguous. With a count capped at 2, `alternativeCount` carried no information. |
| D3 | Placeholder names are **case-insensitive** (`{Artist}` = `{artist}`). A **repeated placeholder** is a parse error ("use `{*}` for repeated parts"). A pattern whose last segment ends with a known media extension (`.mp3`) is a parse error ("leave out the extension") | Quick pattern entry. Repeats would need cross-segment consistency rules that are not worth it now, because `{*} - {album}/{artist} - {title}` covers the case. |
| D4 | One typed-value parser, `core.model.FieldValueParser`, is shared by the matcher, CLI `--set` and later the GUI cells (010) | The typed rules (`3of12`, `(2020)`, `Pop; Rock`) must not be duplicated in frontends. |
| D5 | `{genre}` and `--set genre=` split on `;` like `Genres.display()` (§10.2 said "single Genre") | Same as D4: one parser for all inputs, and the display text round-trips. |
| D6 | `ExtractionSpec.problems(...)` returns user-level problems; the constructor throws IAE with those messages. Problems in 002: auto-numbering together with `{track}` in the pattern, or with a TRACK override | This follows `TagWriteOptions.problems` (§5.3). The CLI fails fast with exit 2 and the GUI can grey out the option. |
| D7 | Auto-numbering runs **per parent folder**, over **exportable** files only (after ambiguity acceptance, before manual edits), and it **obeys the merge mode** (FILL_EMPTY keeps an existing track) | Fills arch open questions 5/6 consistently. Numbers are positions in the folder, so they don't change when merge mode skips a file. |
| D8 | NO_MATCH ⇒ `proposed == existing`. Fixed values and manual edits are **not** applied | Story: "never written". This keeps the preview honest. |
| D9 | `FieldEdit.Set` is renamed to `FieldEdit.Assign` | Avoids a clash with `java.util.Set` in `ManualEdits`. |
| D10 | Severities: NO_MATCH = ERROR; AMBIGUOUS_MATCH = ERROR when not accepted, WARNING when accepted; POSITION_EXCEEDS_TOTAL = WARNING, only for a *changed* TRACK/DISC | Follows §11: ERROR on an item = it will not be written. |
| D11 | `PatternError.column` is a UTF-16 char index | JavaFX caret positions are char indices. The CLI converts it with `Text.width`. |
| D12 | CLI extract options live in a picocli `@Mixin` (`ExtractionOptions`) | `export` (003/004) reuses the same flags plus `--apply` without duplicating them. |
| D13 | ArchUnit **A12**: `core.extract` depends on neither `core.port` nor `core.task` | Propose must stay pure and fast (§7.2). Data that later features need (008 vocabulary) comes in as values. |

Fold D1–D13 into `docs/architecture.md` (§3, §7.2, §10) in step 9.

## Domain & API (core)

```java
// core.model – shared typed parsing (D4, D5)
public sealed interface ParsedValue {
    record Valid(FieldValue value) implements ParsedValue {}
    record Invalid(String reason) implements ParsedValue {}     // reason: English, not blank, names the input
}
public final class FieldValueParser {
    /** NFC + strip, then by field.type(): TEXT (non-empty, no ISO control chars; line breaks only if multiLine),
     *  YEAR (4 ASCII digits, optional ()/[] around, 1000..2999), POSITION (1-4 digits ≥ 1, optional
     *  "of"/"/" + total, spaces allowed around "of" and "/", case-insensitive "of"),
     *  GENRES (split on ';', strip, drop empty, dedupe case-insensitively keeping the first; ≥ 1). Pure, thread-safe. */
    public static ParsedValue parse(Field field, String raw);
}

// core.pattern (input side; OutputPattern/PathRenderer arrive in 004)
public sealed interface Token {
    record Literal(String text) implements Token {}                        // not empty, NFC
    record Placeholder(Field field, OptionalInt padWidth) implements Token {} // padWidth empty in input patterns
    record Wildcard() implements Token {}
}
public record PatternSegment(List<Token> tokens) {}          // non-empty; no two adjacent Literals
public record InputPattern(String source, List<PatternSegment> segments) {
    public List<Field> placeholders();                         // pattern order, no duplicates
}
public record PatternError(int column, String message) {}    // column: 0 ≤ column ≤ source.length()
public sealed interface PatternParseResult<P> {
    record Valid<P>(P pattern) implements PatternParseResult<P> {}
    record Invalid<P>(List<PatternError> errors) implements PatternParseResult<P> {}   // non-empty, by column
}
public final class PatternParser { public static PatternParseResult<InputPattern> parseInput(String source); }

public enum MatchKind { MATCHED, AMBIGUOUS, NO_MATCH }
/** MATCHED: values/raw for every placeholder, alternative empty, reason empty.
 *  AMBIGUOUS: values/raw = chosen (non-greedy) split, alternative = greedy split (differs), reason empty.
 *  NO_MATCH: values/raw/alternative empty, reason present ("segment 2 (folder "Best Of"): …"). */
public record MatchResult(MatchKind kind, Map<Field, FieldValue> values, Map<Field, String> raw,
                          Map<Field, String> alternative, Optional<String> reason) {}
public final class PathMatcher {
    /** Pre: pattern, file not null; file has ≥ 1 name element. Pure, no I/O, thread-safe. */
    public static MatchResult match(InputPattern pattern, Path file);
}

// core.extract – as architecture §7.2, with D2/D6/D9
public interface ProposeMetadataUseCase { ProposalSet propose(ProposeRequest request); }
public record ProposeRequest(List<ScannedFile> files, ExtractionSpec spec, ManualEdits edits) {} // unique paths
public record ExtractionSpec(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides,
                             MergeMode mergeMode, Optional<AutoNumbering> autoNumbering, boolean acceptAmbiguous) {
    public static List<String> problems(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides,
                                        Optional<AutoNumbering> autoNumbering);
    public static ExtractionSpec of(InputPattern pattern);     // no overrides, OVERWRITE, no numbering, false
}
public sealed interface FieldOverride { record Fixed(FieldValue value) … ; record KeepExisting() … }
public enum MergeMode { OVERWRITE, FILL_EMPTY }
public record AutoNumbering(NumberingOrder order, boolean setTotal) {
    public enum NumberingOrder { FILE_NAME, EXISTING_TRACK } }
public record ManualEdits(Map<Path, Map<Field, FieldEdit>> edits, Set<Path> acceptedAmbiguous) {
    public static final ManualEdits NONE; }                    // paths absolute; edits for unknown paths ignored
public sealed interface FieldEdit { record Assign(FieldValue value) … ; record Clear() … }
public enum MatchStatus { NO_PATTERN, MATCHED, AMBIGUOUS, AMBIGUOUS_ACCEPTED, NO_MATCH }
public enum ChangeOrigin { PATTERN, FIXED, AUTO_NUMBER, MANUAL }
public record FieldChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after,
                          ChangeOrigin origin) {}                // before ≠ after
public record Proposal(ScannedFile file, MatchStatus status, Optional<String> matchDetail,
                       Map<Field, String> extracted, Map<Field, String> alternative, MediaMetadata proposed,
                       List<FieldChange> changes, List<Issue> issues) {
    public MediaMetadata existing();   // file.tags().effective()
    public boolean exportable();       // NO_PATTERN, MATCHED, AMBIGUOUS_ACCEPTED
}
public record ProposalSet(List<Proposal> proposals, int matched, int ambiguous, int noMatch) {
    public int exportable();
}
public final class ProposeMetadataService implements ProposeMetadataUseCase { public ProposeMetadataService() }
```

Record invariants checked in the compact constructors (each tested):
- `Proposal`: the fields of `changes` = `existing().differingFields(proposed)` in `Field` order, and every change has
  `before`/`after` equal to the existing/proposed values. NO_MATCH ⇒ `proposed == existing`, `changes` empty,
  `matchDetail` present. NO_PATTERN ⇔ `extracted` empty and no pattern. `alternative` is non-empty only for
  AMBIGUOUS/AMBIGUOUS_ACCEPTED.
- `ProposalSet`: counters equal the status counts (`matched` = MATCHED, `ambiguous` = AMBIGUOUS +
  AMBIGUOUS_ACCEPTED, `noMatch` = NO_MATCH).
- `ExtractionSpec`: `Fixed` value type = field type; `problems(...)` empty.
- `InputPattern`: segments non-empty; no placeholder with `padWidth`; no repeated placeholder.

## Matching semantics (binding for step 3; §10.2 + ADR 0010)
- The last segment matches the file name **without its extension** (text before the last dot, unless the dot is at
  index 0). Segment *k* from the end matches the *k*-th path element from the end, including elements above the
  scan root. If the path has fewer elements than the pattern has segments → NO_MATCH ("pattern has 3 levels, path
  has 2"). Segments are independent.
- Names and literals are normalised to NFC. The DP runs over code points (`int[]`), so a capture never splits a
  surrogate pair. Whitespace = `Character.isWhitespace(cp) || Character.isSpaceChar(cp)` (includes NBSP).
- Literal = [leading ws run] core [trailing ws run]. Literal cores match case-sensitively. Every ws run is
  possessive (ADR 0010). Strict tier: every run ≥ 1, except at a segment edge. Lenient tier: edge runs ≥ 0. A
  whitespace-only literal always needs ≥ 1, except at a segment edge.
- Placeholder: a non-empty capture, stripped, then `FieldValueParser.parse`. `Invalid` prunes that split. Text
  captures with control characters are `Invalid` (Linux allows `\n` in names). `{*}`: any capture, may be empty.
- DP: `ways[i][p]` = number of matches of tokens `i..` from `p`, capped at 2 (backward). `reach[i][p]` = whether
  tokens `<i` can match `[0,p)` (forward, for diagnostics). Chosen split = smallest end position per placeholder
  that keeps `ways > 0`. Alternative split = largest end position.
- NO_MATCH reason = furthest progress: the highest `i` with a reachable `p`, the furthest `p` for it, and the
  failing token. Wording: `separator " - " not found after "Artist"`, `"20x0" is not a valid year`,
  `unexpected text " (Remix)" at the end`, `name ends before {title}`. The reason starts with the segment when the
  pattern has more than one segment (`folder "Best Of 2020": …`).
- Performance: the parse of a typed capture is bounded (≤ 16 non-space code points for YEAR/POSITION). Text
  captures are checked in O(1) with a prefix count of control characters. 5 000 names × 5 tokens must stay well
  below the GUI debounce (§12).
- A5/A6: `PatternParser`/`PathMatcher` are `final` with only static methods. DP tables are local arrays or private
  records. No mutable helper class with instance fields.

## Steps

### Step 1 – `FieldValueParser`
Files: `C/model/ParsedValue.java`, `C/model/FieldValueParser.java`

Tests (`CT/model/FieldValueParserTest.java`, parameterised):
- TEXT: `"  Song "` → `Song`; `""`/`"   "` → Invalid; `"a\nb"` → Invalid for TITLE, Valid for COMMENT; `"a\u0001"`
  → Invalid; NFD `"Đorđe"` (decomposed) → NFC value equal to the composed literal
- YEAR: `2020`, `(2020)`, `[2020]`, ` 2020 ` → 2020; `20x0`, `202`, `20200`, `0999`, `3000`, `(2020` → Invalid
  with a reason that contains the input
- POSITION: `3`, `03`, `003` → 3; `3/12`, `3 / 12`, `3of12`, `3 OF 12` → 3/12; `0`, `0/12`, `3/0`, `A1`,
  `12345`, `٣` (Arabic-Indic digit) → Invalid; `13/12` → Valid (the warning comes later)
- GENRES: `Pop` → [Pop]; `Pop; Rock` → [Pop, Rock]; `Pop;;pop; Rock ` → [Pop, Rock]; `;` → Invalid
- Preconditions: null field/raw → NPE

### Step 2 – Pattern AST + parser
Files (`C/pattern/`): `Token.java`, `PatternSegment.java`, `InputPattern.java`, `PatternError.java`,
`PatternParseResult.java`, `PatternParser.java`

Parser: a single left-to-right scan. It collects **all** errors (not just the first) with columns. It merges
adjacent literal text, unescapes `{{`/`}}`, NFC-normalises literals and keeps `source` verbatim.

Tests (`CT/pattern/PatternParserTest.java`, table-driven):
- Valid: the story patterns `{album}/{artist} - {title} - {year}` and `{album}/{artist}_{title}`; `{*} - {title}`;
  `{year} - {album}/{track}. {title}`; `{Artist} - {TITLE}` (case-insensitive); `{{live}} {title}` → literal
  `{live} `; `{track}{title}` (typed + untyped adjacent is allowed); `{year}{track}`; `{artist} {title}` (literal
  between is allowed)
- `placeholders()` order: `{album}/{artist} - {title}` → [ALBUM, ARTIST, TITLE]
- Invalid with column: `{foo}` (unknown, col 0, message lists the valid names); `{artist} - {title` (unbalanced, col
  at `{`); `title}` (col 5); `{}`; `{track:00}` ("format only in output patterns"); `{artist}{title}` (two untyped
  adjacent, col of the second); `{*}{title}`; `{artist}/{artist} - {title}` (repeated, col of the second, message
  suggests `{*}`); `a//b`, `/a`, `a/` (empty segment); `{artist} - {title}.mp3` and `.MP3` (extension); `""` and
  `"   "` (empty pattern); several errors in one pattern are all reported, sorted by column
- `InputPattern` constructor: an empty segment list, a placeholder with padWidth, or a repeated placeholder → IAE
- Non-ASCII literal: ` – ` (en dash) and Cyrillic literals are kept; a column after an emoji counts 2 chars

### Step 3 – `PathMatcher`
Files (`C/pattern/`): `MatchKind.java`, `MatchResult.java`, `PathMatcher.java`

Tests (`CT/pattern/PathMatcherTest.java`, `@ParameterizedTest` with a `(pattern, relative path, kind, expected
values)` table; paths built with `Path.of("/m", …)`):

| Case | Pattern | Name | Expect |
|---|---|---|---|
| story 1 | `{album}/{artist} - {title} - {year}` | `Best Of 2020/Artist - Song - 2020.mp3` | MATCHED album/artist/title/2020 |
| story 2 | `{album}/{artist}_{title}` | `Album/Artist_Song.mp3` | MATCHED |
| artist with ` - ` | `{artist} - {title}` | `A - B - C.mp3` | AMBIGUOUS chosen A / `B - C`, alternative `A - B` / C |
| hyphenated artist (strict tier) | `{artist} - {title}` | `Jay-Z - Song.mp3` | MATCHED Jay-Z / Song |
| missing spaces (lenient tier) | `{artist} - {title}` | `Artist-Song.mp3` | MATCHED |
| extra spaces | `{artist} - {title}` | `Artist   -    Song .mp3` | MATCHED Artist / Song (one solution, not many) |
| double separator | `{artist} - {title}` | `Artist - - Song.mp3` | AMBIGUOUS |
| separator missing | `{artist} - {title}` | `Artist Song.mp3` | NO_MATCH, reason contains `" - "` and `Artist Song` |
| typed resolves | `{artist} - {title} - {year}` | `A - B - C - 2020.mp3` | AMBIGUOUS (title `B - C` vs artist `A - B`) |
| typed resolves | `{title} - {year}` | `Song - Live - 2020.mp3` | MATCHED title `Song - Live` |
| bad year | `{artist} - {title} - {year}` | `A - B - 20x0.mp3` | NO_MATCH, reason contains `20x0` |
| year in brackets | `{artist} - {title} {year}` | `A - B (2020).mp3`, `A - B [2020].mp3` | year 2020, title `B` |
| year literal brackets | `{title} ({year})` | `Song (2020).mp3` | MATCHED |
| leading track | `{track} - {artist} - {title}` | `01 - A - B.mp3` | track 1 |
| leading track variants | `{track}. {title}` / `{track}_{title}` | `01.Song`, `01. Song` / `01_Song` | track 1, title Song |
| track forms | `{track} {title}` | `3of12 Song`, `03 Song` | 3/12, 3 |
| leading track not in pattern | `{artist} - {title}` | `01 - A - B.mp3` | AMBIGUOUS (the user should add `{track}`/`{*}`) |
| wildcard | `{*} - {artist} - {title}` | `01 - A - B.mp3` | MATCHED A / B |
| folder with year | `{year} - {album}/{track} - {title}` | `2020 - Album/01 - Song.mp3` | year 2020, album Album |
| two levels | `{artist}/{album}/{title}` | `X/Y/Z.mp3` | MATCHED; ancestors above the root are allowed |
| too few levels | `{artist}/{album}/{title}` | `/Z.mp3` (root + one name) | NO_MATCH "levels" |
| trailing text | `{title} - {year}` | `Song - 2020 (Remix).mp3` | NO_MATCH, reason "unexpected text"/not a valid year |
| case-sensitive literal | `{artist} feat. {title}` | `A Feat. B.mp3` | NO_MATCH |
| Cyrillic | `{artist} - {title}` | `Ђорђе Балашевић - Неки Нови Клинци.mp3` | MATCHED |
| NFD name | `{artist} - {title}` | NFD `Đorđe - Pesma.mp3` | values equal the NFC strings |
| emoji | `{title}` | `Song 🎵.mp3` | `Song 🎵` intact |
| NBSP | `{artist} - {title}` | `A - B.mp3` | MATCHED |
| extension | `{title}` | `My.Song.mp3` / `.mp3` / `noext` | `My.Song` / NO_MATCH (empty) / `noext` |
| control char | `{artist} - {title}` | `A - B\nC.mp3` | NO_MATCH |

Plus:
- `alternative` differs from `raw` for every AMBIGUOUS case; it is empty for MATCHED/NO_MATCH
- the result only contains placeholder fields; `{*}` never appears
- ambiguity in the folder segment alone → AMBIGUOUS; NO_MATCH in any segment → NO_MATCH, and the reason names that
  segment
- performance (`PathMatcherPerformanceTest`): 5 000 synthetic names (~60 code points, Cyrillic and ASCII mixed)
  × `{year} - {album}/{track} - {artist} - {title}` in < 1 s total; one 255-code-point name with 8 tokens < 50 ms
- preconditions: null → NPE; a root-only path (`/`) → IAE

### Step 4 – Extract records + testkit helper
Files (`C/extract/`): `ProposeMetadataUseCase`, `ProposeRequest`, `ExtractionSpec`, `FieldOverride`, `MergeMode`,
`AutoNumbering`, `ManualEdits`, `FieldEdit`, `MatchStatus`, `ChangeOrigin`, `FieldChange`, `Proposal`,
`ProposalSet`.
Test-jar: `CT/testkit/fake/ScannedFiles.java`, with `of(Path, MediaMetadata v2)` (an ID3v2.3 layer, fixed
fingerprint) and `untagged(Path)`. Core and CLI tests use it to build inputs without a reader.

Tests (`CT/extract/ExtractRecordsTest.java`): every compact-constructor rule from "Record invariants" (null → NPE,
violated invariant → IAE); `ExtractionSpec.problems` for `{track}` + auto-numbering and for a TRACK override +
auto-numbering (frontend-neutral wording, e.g. "track numbers cannot come from both the pattern and
auto-numbering"); `problems` is empty for a valid spec; a Fixed `Text` for YEAR → IAE; `ProposeRequest` with duplicate paths → IAE; `ManualEdits` with a
relative path → IAE; `ManualEdits.NONE` is empty; defensive copies (mutating input maps has no effect).

### Step 5 – `ProposeMetadataService`
Files: `C/extract/ProposeMetadataService.java` (no-arg constructor, stateless)

Algorithm per file (input order), then per batch:
1. `match` = `pattern.map(p -> PathMatcher.match(p, path))` → status NO_PATTERN / MATCHED / AMBIGUOUS / NO_MATCH;
   AMBIGUOUS becomes AMBIGUOUS_ACCEPTED if `spec.acceptAmbiguous()` or the path is in `edits.acceptedAmbiguous()`.
2. `proposed = existing`; NO_MATCH stops here (D8).
3. Pattern values (fields without `KeepExisting`; with FILL_EMPTY only where existing is absent) → origin PATTERN.
4. Fixed values (same merge rule) → origin FIXED.
5. Batch: auto-numbering over exportable proposals, grouped by `path.getParent()`. Sort by `PathOrder` (FILE_NAME)
   or by existing track number with `PathOrder` as tie-break, files without a track last (EXISTING_TRACK).
   `Position(index + 1, setTotal ? groupSize : empty)` → origin AUTO_NUMBER; merge rule as in D7.
6. Manual edits (not for NO_MATCH): `Assign` → `with`, `Clear` → `without` → origin MANUAL.
7. `changes` from `existing.differingFields(proposed)`; issues (D10, each with `forFile`, plus `forField` where it
   applies); counters; `assert` the postconditions of §7.2.

Tests (`CT/extract/ProposeMetadataServiceTest.java`, inputs from `ScannedFiles`):
- one proposal per file, in input order (input deliberately not sorted)
- story example → MATCHED, `extracted` has all 4 placeholders, `changes` origin PATTERN, `exportable`
- **per placeholder visible**: `extracted` contains a value for every placeholder, also for fields pinned by
  KeepExisting or skipped by FILL_EMPTY
- NO_MATCH: `proposed == existing`, no changes, ERROR NO_MATCH issue with the reason, not exportable; fixed values
  and manual edits are not applied
- AMBIGUOUS: chosen split in `proposed`, `alternative` filled, ERROR AMBIGUOUS_MATCH, not exportable;
  `acceptAmbiguous` → AMBIGUOUS_ACCEPTED, WARNING, exportable; acceptance via `ManualEdits.acceptedAmbiguous` for one
  path only
- fixed value: `genre=Balkan`, `albumartist=Various Artists` on all files, origin FIXED; fixed wins over a pattern
  value for the same field
- keep existing: `{artist} - {title}` + KeepExisting(ARTIST) → artist unchanged, title from the pattern;
  KeepExisting on a field without an existing value stays absent
- merge mode: OVERWRITE replaces an existing title; FILL_EMPTY keeps it and fills an absent album (pattern and fixed
  both); the default is OVERWRITE
- NO_PATTERN: no pattern + fixed genre → every file NO_PATTERN, exportable
- equal value → no FieldChange (pattern title == existing title)
- auto-numbering FILE_NAME: 3 files in folder A (given in reverse order) + 2 in B → A: 1,2,3 by `PathOrder`,
  B: 1,2; `setTotal` → 3/3 …; AMBIGUOUS (not accepted) and NO_MATCH files are not counted;
  EXISTING_TRACK: existing 5, 2, none → 2→1, 5→2, none→3; FILL_EMPTY keeps an existing track, the other files get
  their position; a manual TRACK edit wins over auto-numbering
- manual edits: `Assign` and `Clear` → origin MANUAL; an edit for an unknown path is ignored
- POSITION_EXCEEDS_TOTAL warning for an extracted `13of12`; no warning for an unchanged existing `13/12`
- `ProposalSet` counters; an empty file list → an empty set
- purity/perf: 5 000 files with pattern + 2 fixed values + auto-numbering in < 1 s; calling twice with the same
  request gives equal results
- preconditions: null request → NPE

### Step 6 – ArchUnit A12
Files:
- `CT/testkit/arch/ArchitectureRules.java` – add a package-parameterised factory
  `static ArchRule pureFeature(String packagePattern)` (`noClasses().that().resideInAPackage(pattern).should()
  .dependOnClassesThat().resideInAnyPackage(core.port.., core.task..)`) and the constant
  `A12_EXTRACT_IS_PURE = pureFeature(CORE + ".extract..")`
- `CT/CoreArchitectureTest.java` – apply A12
- `CT/testkit/arch/violations/ImpureExtract.java` (references `Cancellation`) + `ArchitectureRulesSelfTest`:
  `pureFeature("..violations..")` fails on it, the same way the existing self-tests prove A1/A2/A6/A7/A11

No change to A3 is needed, because `pattern` and `extract` are already defined layers. A1/A2/A5/A6 must pass for the
new packages. If A5 fails on a DP helper, change the helper (local arrays or private records), never the rule.

### Step 7 – Composition root
Files: `music-studio-app/P/app/MusicStudio.java` (add `ProposeMetadataUseCase propose`, `Require`d),
`MusicStudioFactory.java` (`new ProposeMetadataService()`), `music-studio-app/T/app/MusicStudioFactoryTest.java`.

Update every `new MusicStudio(scan)` call (CLI tests) to pass a propose lambda.

Tests: `create().propose()` is non-null. End to end: copy `V23_LATIN1` to `@TempDir/Best Of 2020/Artist - Song -
2020.mp3` and `NO_TAGS` to `…/Other - Name.mp3`, scan, propose with the story pattern → both MATCHED, title `Song`
replaces `Café Song` (OVERWRITE), and the SHA-256 of both files is unchanged.

### Step 8 – CLI `extract`
Files:
- `music-studio-cli/P/cli/ExtractionOptions.java` – picocli `@Mixin` (D12): `--pattern <p>`, `--set <field>=<value>`
  (repeatable), `--keep <field>[,<field>…]`, `--merge overwrite|fill-empty` (kebab-case converter, default
  overwrite), `--auto-number filename|tag`, `--track-total`, `--accept-ambiguous`. The method
  `ExtractionSpec toSpec(PrintWriter err)` returns empty after printing a usage error. Field names go through
  `Field.byPlaceholder(name.toLowerCase(ROOT))` and values through `FieldValueParser`. The same field in `--set` and
  `--keep`, an `--set` without `=`, an empty value, `--track-total` without `--auto-number`, and
  `ExtractionSpec.problems` → usage error
- `music-studio-cli/P/cli/ExtractCommand.java` – `extract <dir> [-r] [--include-hidden] [ExtractionOptions]
  [--format table|json]`. It needs at least one of `--pattern`, `--set`, `--auto-number` (otherwise exit 2). Flow:
  parse the pattern (Invalid → prints the pattern, a `^` under each error column and the message → exit 2) → scan
  (collect `ScannedFile`s and `UnreadableFile`s; root missing → exit 2, Ctrl-C → exit 130) → `propose` → render. No
  `--apply` option exists. The command writes nothing (structural: it has no write use case).
- `music-studio-cli/P/cli/ExitCode.java` – `of(ScanSummary, ProposalSet)`: 130 cancelled, 2 failed scan, 1 if any
  unreadable / NO_MATCH / AMBIGUOUS (not accepted), else 0
- `music-studio-cli/P/cli/render/ProposalRenderer.java` (interface `render(ProposalSet, List<UnreadableFile>,
  ScanSummary)`), `TableProposalRenderer`, `JsonProposalRenderer`
  - Table: `PATH`, `STATUS` (`ok`, `accepted`, `AMBIGUOUS`, `NO MATCH`; upper case = will be skipped; colour via
    `CommandLine.Help.Ansi` AUTO in production, OFF in tests), then one column per **touched field** (pattern
    placeholders ∪ `--set` fields ∪ TRACK if auto-numbered) in `Field` order with the scan table's widths, showing
    the proposed value with `*` = changed. Last column `NOTES`: the NO_MATCH reason, `also possible: artist="A - B",
    title="C"` for ambiguous rows, warnings. After the table: the legend `* = differs from current tag`, an
    "Unreadable (not processed)" section, ignored/skipped counts as in `scan`, the summary
    `12 files: 9 matched, 2 ambiguous (skipped – use --accept-ambiguous), 1 no match`, and finally
    `Preview only – no file was changed.`
  - JSON: `{"root", "pattern", "items":[{path, status, detail, extracted{}, alternative{}, existing{}, proposed{},
    changes:[{field, before, after, origin}], issues:[{severity, code, message, field}]}], "unreadable":[{path,
    message}], "summary":{files, matched, ambiguous, noMatch, exportable, ignored, skipped}}`, using the existing
    `Json.string` escaper
  - Move the column widths into `Rows` so that the scan and extract tables share them
- `MusicStudioCommand.commandLine` adds `new ExtractCommand(studio.scan(), studio.propose(), cancel)`

Tests:
- `T/cli/ExtractionOptionsTest.java` – every option → expected `ExtractionSpec` (`--set genre=Pop --set
  "albumartist=Various Artists" --set track=3/12`, `--keep artist,album`, `--merge fill-empty`, `--auto-number
  tag --track-total`); each usage error → exit 2 with a message naming the option
- `T/cli/ExtractCommandTest.java` – fake scan (lambda emitting `ScannedFiles` items) + fake propose (lambda that
  records the request): relative dir → absolute; invalid pattern → exit 2 and the caret line at the right column
  (also after a Cyrillic prefix); no pattern/set/auto-number → exit 2; exit codes 0/1/2/130; there is no
  `--apply` option (picocli rejects it → exit 2)
- `T/cli/render/TableProposalRendererTest.java`, `JsonProposalRendererTest.java` – golden output for a fixed
  `ProposalSet` built with the real service (MATCHED, AMBIGUOUS with alternative, AMBIGUOUS_ACCEPTED, NO_MATCH,
  Cyrillic, emoji, a value with quotes/control chars in JSON)
- `T/cli/ExtractEndToEndTest.java` – real `MusicStudioFactory`. In `@TempDir`: `Best Of 2020/` with fixture copies
  named `Artist - Song - 2020.mp3`, `Jay-Z - Track - 2019.mp3`, `A - B - C - 2020.mp3`, `Bad Name.mp3`,
  `Ђорђе - Песма - 1986.mp3`, plus a corrupt copy. Run `extract <dir> --pattern "{album}/{artist} - {title} -
  {year}" --set genre=Pop`. Expected rows: matched ×3, `A - B - C` AMBIGUOUS + skipped hint, `Bad Name` NO MATCH,
  corrupt in the unreadable section, exit code 1. With `--accept-ambiguous` the row shows `accepted`. JSON output
  parses (a small structural check). **The tree snapshot (names + SHA-256 + mtime) is identical before and after**,
  and no file or folder was created anywhere under `@TempDir` (config/data dirs included)
- `CliArchitectureTest` unchanged (A9 already allows `core.extract`/`core.pattern`); it must stay green

Native: extend `music-studio-cli/src/test/native/scan-smoke.sh` with one `extract … --format json` run against
the native binary (compare with `expected-extract.json`, checksums unchanged). The new picocli classes are covered
by `picocli-codegen`. No new reflection.

### Step 9 – Docs and story done
- `docs/architecture.md`: §3 (new types: `ParsedValue`, `FieldValueParser`, `MatchKind`), §7.2 (D2, D6–D10), §10.1/10.2
  (D1, D3, D5, D11, link to ADR 0010), §14.4 (A12), §18 questions 5 and 6 → resolved as D7 (after user confirmation)
- tick the acceptance criteria in `docs/stories/002-extract-metadata-from-names.md`; update `docs/stories/README.md`
- run the `tester` agent, then the `reviewer` agent

## Acceptance criteria → tests

| Criterion | Proven by |
|---|---|
| per file, the extracted value of every placeholder | `PathMatcherTest` (values), `ProposeMetadataServiceTest` ("per placeholder visible"), renderer golden tests, `ExtractEndToEndTest` |
| no match marked with reason, never written | `PathMatcherTest` reasons, service NO_MATCH test (`!exportable`, `proposed == existing`, ERROR issue), table `NO MATCH` + NOTES |
| ambiguous marked, chosen split shown; GUI accept/fix, CLI skip unless `--accept-ambiguous` | matcher AMBIGUOUS + `alternative`, service tests (`acceptAmbiguous`, `ManualEdits.acceptedAmbiguous`, manual `Assign`), `ExtractEndToEndTest` with/without the flag |
| fixed value for all files | service fixed-value tests, `ExtractionOptionsTest` `--set`, `FieldValueParserTest` |
| keep from existing tag | service KeepExisting tests, `--keep` option test |
| merge mode fill-empty / overwrite (default overwrite) | service merge-mode tests, `--merge` option test |
| auto-numbering by file name or existing tag order | service auto-numbering tests, `--auto-number` option test |
| extraction writes nothing; proposals feed 003/004 | A12 (no ports in extract), `ProposalSet`/`Proposal.exportable` API, end-to-end tree snapshot, native smoke checksums |
| edge cases: extra spaces, double separators, missing separator, `(2020)`/`[2020]`, `01 - `/`01.`/`01_`, `2020 - Album`, non-ASCII | `PathMatcherTest` table rows of the same names, NFD/emoji/NBSP rows |
| assumption: left-to-right non-greedy, typed first | "typed resolves" rows, `alternative` = greedy |
| assumption: case-sensitive literals, separator spaces trimmed | "case-sensitive literal", "extra spaces", tier rows |

## Data safety
- **Pure by construction:** `PatternParser`, `PathMatcher` and `ProposeMetadataService` take no ports. A2 forbids
  I/O in core, and the new A12 forbids `core.port`/`core.task` in `core.extract`. The only I/O in `extract` is the
  read-only scan from 001.
- **Dry-run is the only mode in 002:** the CLI `extract` has no `--apply`, and it is wired only to the `scan` and
  `propose` use cases. Every run ends with "Preview only – no file was changed." Writing arrives with
  `export … --apply` (003/004), which consumes `Proposal.exportable()`.
- **Never written:** NO_MATCH and unaccepted AMBIGUOUS proposals are `exportable() == false` with an ERROR issue,
  which 004's planner turns into SKIP.
- **No silent data loss in proposals:** fields that the pattern or `--set` don't touch keep their existing value.
  KeepExisting/FILL_EMPTY never clear. Only a manual `Clear` removes a value, and it shows as a change.
- **Undo:** not applicable (nothing is written). Proven by the end-to-end tree snapshot (names, SHA-256, mtime, no
  new files or folders) and the native smoke checksums.

## Risks to verify while implementing
- Tier/possessive whitespace edge cases (ws runs that touch a capture with no literal ws). Keep the table in
  `PathMatcherTest` as the spec and add every surprising name as a new row.
- Performance on the GUI path (5 000 names per keystroke): if the 1 s budget is tight, precompute literal match
  positions per segment once per name before optimising anything else.
- `Character.isSpaceChar` vs `String.strip()`: `FieldValue.Text` requires `value.equals(value.strip())`. Stripping
  with the wider set is a superset, so it is safe; the test with an NBSP edge covers it.
- Picocli kebab-case enum values in the native image: covered by the smoke run.

## Open questions (need the user's decision; the plan assumes the first option)
1. **Auto-numbering and merge mode (D7):** in `fill-empty` mode, keep existing track numbers and number only the
   empty ones (assumed), *or* treat `--auto-number` as explicit and always overwrite?
2. **Auto-numbering scope (arch Q5):** per parent folder with total = exportable files in that folder (assumed),
   *or* across the whole batch?
3. **Repeated placeholders (D3):** a parse error with the hint to use `{*}` (assumed), *or* allow
   `{artist} - {album}/{artist} - {title}` with a "values must be equal" check?
4. **CLI output formats:** `table` and `json` only (assumed; `scan` also has `csv`). Is CSV needed for `extract`?
