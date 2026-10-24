# Plan 003 – Write tags with selectable ID3 versions

Story: [`docs/stories/003-write-tags-with-version-selection.md`](stories/003-write-tags-with-version-selection.md) ·
Architecture: [`docs/architecture.md`](architecture.md) §5.3, §6.3, §6.4, §7.3–7.5, §8, §9 · ADRs 0003, 0004,
[0011](adr/0011-mp3-tag-writing-by-composing-the-copy.md) (new). Story 004 is read for the shared export pipeline.

Every step builds and tests on its own:
`export JAVA_HOME=~/.sdkman/candidates/java/25.4.4+1-graal && ./mvnw -q verify` (or `-pl <module> -am`).
Write the Javadoc contracts (template §4.2) together with the code. Every public method starts with `Require`
checks. Records validate in their compact constructor.

Abbreviations as in plans 001/002: `C/` = `music-studio-core/src/main/java/dev/rs/slava/icxc/musicstudio/core/`,
`CT/` = `music-studio-core/src/test/java/dev/rs/slava/icxc/musicstudio/core/`,
`P/` = `src/main/java/dev/rs/slava/icxc/musicstudio/`, `T/` = `src/test/java/dev/rs/slava/icxc/musicstudio/`.

## Scope: how much of the export pipeline 003 builds

The story only makes sense end to end: "dry-run shows old → new", "apply writes safely and re-reads". Writing is
always part of an export (story assumption, ADR 0004). A port-only 003 (options + `TagWriter` + adapter) would
leave every user-facing criterion untestable until 004. So 003 builds the **real** plan/apply use cases with their
final interfaces, but with the smallest export spec:

| Built in 003 | Left for 004 |
|---|---|
| `WriteOptionsUseCase`, `PlanExportUseCase`, `ApplyExportUseCase` (final interfaces) | output pattern, `PathRenderer`, `FileNameSanitizer`/`NamingRules`, fallbacks |
| `ExportSpec(outputRoot)` – **flat** targets: `outputRoot/<source file name>` | `ExportSpec` gains `pattern`, `naming`, `collisions`, `fallbacks` |
| collisions: batch (case/NFC-insensitive) → later file SKIP; existing file → SKIP (`TARGET_EXISTS`); `TARGET_IS_SOURCE` | SUFFIX / OVERWRITE policies, backups |
| `ExportTarget` port **subset** (6 methods) + storage adapter + contract | `storeInfo`/space check, `moveToBackup`, `deleteIfUnchanged`, `deleteDirectoryIfEmpty` |
| safe write per file: fingerprint → temp → `writeCopy` → re-read + verify → `moveNoReplace` → temp always deleted | undo journal (`ExportJournalStore`), `UndoExportUseCase`, saved report |
| `ExportReport` returned and printed (lists every created file and directory) | `ExportId`, persisted report |
| CLI `export <dir> --out <dir> [extraction options] [write options] [--apply]` | `--out-pattern`, `--overwrite`, `--suffix`, `undo` |

**Data safety without the journal (open question 1):** in 003 apply never replaces, moves or deletes anything
except its own temp files. Every visible change is a *new* file (or the output root directory). The report lists
them all. The worst case after a crash is extra files in the output folder, never a lost or changed file. The
journal + undo arrive with 004, which should be done before the tool is used on real libraries.

## Affected modules
| Module | Change |
|---|---|
| `music-studio-core` | new `core.tag` (options, capabilities, request, ID3v1 projection, write expectation, verifier); ports `TagWriter`, `ExportTarget` (subset); new `core.export` (write options, plan, apply); testkit: `Id3Inspector`, `TagWriterContract`, `Id3WriterContract`, `ExportTargetContract`, `FakeTagWriter`, `FakeExportTarget`, `ExportInputs`; ArchUnit A13 |
| `music-studio-tag-jaudiotagger` | `JaudiotaggerTagWriter` (ADR 0011), shared library lock, learning tests, native metadata re-trace |
| `music-studio-storage` | `LocalExportTarget` |
| `music-studio-app` | `MusicStudio` gains `writeOptions`, `planExport`, `applyExport`; factory wires writer, export target, services |
| `music-studio-cli` | `export` subcommand, `WriteOptions` mixin, plan/report renderers |

Core stays JDK-only. No new library. **mp3agic check:** `TagWriter` takes core types only (`TagWriteRequest`,
`FieldChanges`, `Id3v1Values`). mp3agic can implement it: `Mp3File(source)`, `ID3v23Tag`/`ID3v24Tag` setters +
`getFrameSets()` for preflight and preservation, `toBytes()` + payload copy + `ID3v1Tag.toBytes()` (or
`save(target)`); it declares `writableVersions = {V1_0, V1_1, V2_3, V2_4}`, so v2.2 gets rejected before writing (the
contract test with a v2.2-less fake proves it). `ExportTarget` is plain `java.nio.file`.

## Decisions taken in this plan (not or differently covered by the architecture)
| # | Decision | Why |
|---|---|---|
| D1 | 003 builds plan/apply with a **flat** `ExportSpec(outputRoot)`, no journal (see Scope) | User-facing criteria testable now. Final interfaces, so 004 only adds spec components and steps. |
| D2 | **Writer strategy: compose the copy** (new v2 bytes + byte-copied payload + adapter-encoded v1 block) ([ADR 0011](adr/0011-mp3-tag-writing-by-composing-the-copy.md)) | Payload identical by construction, own cancel/progress loop, no library temp files, exact ID3v1 bytes. |
| D3 | `OTHER_VERSION_KEPT`/`OTHER_VERSION_REMOVED` are computed by **core** (`WriteExpectation.slotIssues`). `preflight` reports only `FRAME_NOT_REPRESENTABLE` (changes §6.3) | Same slot rule and wording for every adapter; dry-run cannot drift. |
| D4 | `PlannedFile` carries `currentLayers` + `expectedLayers` (instead of `MediaMetadata expected`) and derives `slotChanges()` | The story's dry-run is "field, old → new" **per tag** (v1 shows the truncated/transliterated text). The verifier needs per-slot expectations anyway. |
| D5 | `WriteVerifier` compares versions per family, values per field, **GENRES by `display()`**, and the stream duration (±50 ms) against the source | v2.2/v2.3 store several genres joined with `"; "` and read back as one genre. A truncated payload must not pass. |
| D6 | Contract split `TagWriterContract` (semantic, fakes too) / `Id3WriterContract` (bytes, real writers). The library-independent `Id3Inspector` replaces the hooks `addUnknownData`/`hasUnknownData` (§14.1) | Same pattern as `TagReaderContract`/`TagReaderEdgeCaseContract`. Preservation is proven without the library under test. |
| D7 | Files whose scan has an ERROR issue (`TAG_DAMAGED`) are **SKIP**ped by the plan. The adapter rejects a damaged v2 tag that must be rewritten with `CORRUPT` | Rewriting a damaged tag silently drops the unreadable frames. |
| D8 | `v2Encoding` applies to every text frame of a **modelled field** in the written v2 tag (AUTO per value: Latin-1 if it fits, else UTF-16 for v2.2/2.3, UTF-8 for v2.4). Other text frames keep their encoding unless the target version cannot hold it (UTF-8/UTF-16BE in v2.2/2.3 → UTF-16) | Deterministic and testable. The point of the option is device compatibility of the visible fields. |
| D9 | ID3v1 genre: number of the primary genre via `Id3v1Genres.number`; a genre without a number → **12 (Other)** + `V1_FIELD_NOT_REPRESENTABLE` WARNING; no genre → 255 | `audio-metadata` skill ("unknown → 12"). |
| D10 | ID3v1 severities: `V1_TRANSLITERATED`, `V1_TRUNCATED`, `V1_CHAR_DROPPED`, genre → Other, track > 255 = **WARNING**. Structural limits (album artist, disc, track total, extra genres, track in v1.0) = **INFO** | The story names truncation/transliteration as warnings. Album artist on every file would bury them. |
| D11 | `NO_SPACE` during apply **aborts** the run (status ABORTED); the remaining items become skipped with `WRITE_FAILED` "not attempted: disk full" | Every further file would fail after a partial copy. `written + skipped + failed == files` still holds. |
| D12 | Apply re-fingerprints the source **after** the write as well; a difference fails the item (`SOURCE_CHANGED`) | A one-`stat` guard for the non-negotiable rule. |
| D13 | ArchUnit **A13**: in core only `ApplyExportService` calls `TagWriter.writeCopy` and the mutating `ExportTarget` methods | "Dry-run is structural" (§8.1) becomes an enforced rule. |
| D14 | The encoding issue code is reused: an unsupported `V2Encoding` is reported as `UNSUPPORTED_WRITE_VERSION` with a message naming the encoding | No enum churn; frontends grey out via `FormatCapabilities` directly. |

Fold D1–D14 into `docs/architecture.md` in step 14.

## Domain & API (core)

```java
// core.tag – options (§5.3, unchanged semantics)
public enum Id3v2Choice { NONE, V2_2, V2_3, V2_4;  public Optional<TagVersion> version(); }
public enum Id3v1Choice { NONE, V1_0, V1_1;        public Optional<TagVersion> version(); }
public enum V2Encoding { AUTO, LATIN1, UTF16, UTF8 }
public enum V1TextMode { ASCII, LATIN1;  public boolean asciiOnly(); }   // maps onto Id3v1Text.fit(…, asciiOnly, …)
public record TagWriteOptions(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding v2Encoding,
                              V1TextMode v1TextMode, boolean removeOtherVersions) {
    public static final TagWriteOptions DEFAULT;                     // V2_3, V1_1, AUTO, ASCII, false
    /** Frontend-neutral English messages; empty = valid. The constructor throws IAE with them joined. */
    public static List<String> problems(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding enc,
                                        V1TextMode mode, boolean removeOtherVersions);
    public Set<TagVersion> selectedVersions();
}
/** writableVersions non-empty ⊆ format's families; writableEncodings contains AUTO; writableFields non-empty. */
public record FormatCapabilities(MediaFormat format, Set<TagVersion> writableVersions,
                                 Set<V2Encoding> writableEncodings, Set<Field> writableFields, boolean frontCover) {
    /** ERROR UNSUPPORTED_WRITE_VERSION per unsupported selected version/encoding (D14); empty = OK. Pure. */
    public List<Issue> unsupported(TagWriteOptions options);
}

// core.tag – request
/** set and clear disjoint; set values fit their field. */
public record FieldChanges(Map<Field, FieldValue> set, Set<Field> clear) {
    public static final FieldChanges NONE;
    /** set = fields of desired that differ from current; clear = fields only current has; both ∩ writable. */
    public static FieldChanges between(MediaMetadata current, MediaMetadata desired, Set<Field> writable);
    public MediaMetadata applyTo(MediaMetadata current);
    public boolean isEmpty();
}
public sealed interface CoverChange { record Keep() implements CoverChange {} }   // Remove/Replace arrive in 009
/** Byte-exact ID3v1 content (§5.3). ASCII mode: chars 0x20–0x7E; LATIN1: ≤ 0xFF; title/artist/album ≤ 30,
 *  comment ≤ 30 (V1_0) / 28 (V1_1); track 0..255, 0 for V1_0; genre 0..255 (255 = none); year 1..9999. */
public record Id3v1Values(Id3v1Choice version, V1TextMode mode, String title, String artist, String album,
                          OptionalInt year, String comment, int track, int genre) {
    /** What a TagReader reports for this block: empty strings absent, track 0 absent, genre 255 absent. */
    public MediaMetadata readBack();
}
/** Invariants: id3v1 present ⇔ options.id3v1() != NONE (MP3); id3v1.version/mode == options';
 *  options.id3v2() == NONE ⇒ changes.isEmpty(). */
public record TagWriteRequest(MediaFormat format, FieldChanges changes, CoverChange cover,
                              TagWriteOptions options, Optional<Id3v1Values> id3v1) {}

// core.tag – pure rules (final classes, static methods; A5)
public final class Id3v1Projection {
    public record Result(Id3v1Values values, List<Issue> issues) {}
    /** Pre: version != NONE. Issues per D9/D10, each forField. */
    public static Result project(MediaMetadata metadata, Id3v1Choice version, V1TextMode mode);
}
public final class WriteExpectation {
    /** The layers a conforming TagWriter leaves in the target, as a TagReader reports them (§6.3 slot rules). */
    public static List<TagLayer> layersAfter(List<TagLayer> current, TagWriteRequest request);
    /** OTHER_VERSION_KEPT (WARNING) / OTHER_VERSION_REMOVED (INFO) for families set to NONE that have a tag (D3). */
    public static List<Issue> slotIssues(List<TagLayer> current, TagWriteOptions options);
}
public record ValueChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after) {}   // before ≠ after
/** One per family present before or after, in TagFamily order; values in Field order. */
public record SlotChange(TagFamily family, Optional<TagVersion> before, Optional<TagVersion> after,
                         List<ValueChange> values) {
    public static List<SlotChange> between(List<TagLayer> before, List<TagLayer> after);
}
public final class WriteVerifier {
    /** VERIFY_MISMATCH (ERROR) per difference (D5); empty = the write is as planned. Also TAG_DAMAGED in actual. */
    public static List<Issue> verify(List<TagLayer> expected, Optional<StreamInfo> sourceStream, TagReadResult actual);
}

// core.port
public interface TagWriter {                    // §6.3, with D3: preflight returns FRAME_NOT_REPRESENTABLE only
    Set<MediaFormat> writableFormats();
    FormatCapabilities capabilities(MediaFormat format);
    List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException;
    void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException;
}
public interface ExportTarget {                 // §6.4 subset; the rest arrives with 004/009/012
    Optional<Path> findCollision(Path target) throws MediaAccessException;
    List<Path> createDirectories(Path directory) throws MediaAccessException;
    Path reserveTempPath(Path finalTarget) throws MediaAccessException;
    void moveNoReplace(Path temp, Path target) throws MediaAccessException;
    void deleteTemp(Path temp) throws MediaAccessException;
    FileFingerprint fingerprint(Path file) throws MediaAccessException;
}

// core.export
public interface WriteOptionsUseCase {          // §7.3 unchanged
    FormatCapabilities capabilities(MediaFormat format);
    Set<MediaFormat> writableFormats();
    List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats);
}
public record ExportSpec(Path outputRoot) {}    // absolute. 004 adds pattern, naming, collisions, fallbacks
public record PlanExportRequest(List<Proposal> proposals, Set<Path> sourceRoots, ExportSpec spec,
                                TagWriteOptions options) {}           // unique sources; roots absolute, non-empty
public enum PlannedAction { WRITE, SKIP }
/** WRITE ⇔ target and writeRequest present and no ERROR issue. SKIP ⇒ ≥ 1 ERROR issue and expected == current. */
public record PlannedFile(Path source, FileFingerprint sourceFingerprint, MediaFormat format, PlannedAction action,
                          Optional<Path> target, List<FieldChange> changes, Optional<TagWriteRequest> writeRequest,
                          List<TagLayer> currentLayers, List<TagLayer> expectedLayers, List<Issue> issues) {
    public List<SlotChange> slotChanges();      // SlotChange.between(currentLayers, expectedLayers)
}
public record ExportPlan(Instant createdAt, ExportSpec spec, TagWriteOptions options, List<PlannedFile> files,
                         List<Issue> issues) {  // SpaceEstimate arrives in 004
    public boolean executable();                // no global ERROR and writeCount() > 0
    public int writeCount(); public int skipCount();
}
public interface PlanExportUseCase { ExportPlan plan(PlanExportRequest request, ProgressListener progress,
                                                     Cancellation cancel); }
public interface ApplyExportUseCase { ExportReport apply(ExportPlan plan, ProgressListener progress,
                                                         Cancellation cancel); }
public enum ExportStatus { COMPLETED, COMPLETED_WITH_ERRORS, CANCELLED, ABORTED }
public record ExportedFile(Path source, Path target, List<Issue> issues) {}
public record SkippedFile(Path source, List<Issue> reasons) {}
public record FailedFile(Path source, Optional<Path> target, Issue error) {}
/** written + skipped + failed == plan.files().size(); createdDirectories outermost first. 004 adds ExportId. */
public record ExportReport(ExportStatus status, Path outputRoot, List<ExportedFile> written,
                           List<SkippedFile> skipped, List<FailedFile> failed, List<Path> createdDirectories,
                           List<Issue> issues, Duration elapsed) {}

public final class WriteOptionsService implements WriteOptionsUseCase { public WriteOptionsService(TagWriter w) }
public final class PlanExportService implements PlanExportUseCase {
    public PlanExportService(TagWriter writer, ExportTarget target, Clock clock) }
public final class ApplyExportService implements ApplyExportUseCase {
    public ApplyExportService(MediaFileSource source, TagWriter writer, TagReader reader, ExportTarget target,
                              Clock clock) }
```

`TagWriteOptions.problems` (each tested, frontend-neutral wording):
- `UTF8` with `id3v2 != V2_4` → "UTF-8 text encoding needs ID3v2.4"
- encoding ≠ AUTO with `id3v2 == NONE` → "a text encoding needs an ID3v2 version"
- `NONE` + `NONE` without `removeOtherVersions` → "nothing to write: choose an ID3v2 or ID3v1 version, or remove
  other versions to strip all tags"

## Slot and value rules (binding for steps 3–6)
- **v2 slot**, `id3v2` selected: version = selected; values = `changes.applyTo(current v2 values or empty)`. An
  existing v2 tag of another version is converted (frames mapped; frames without an equivalent dropped exactly as
  preflight reported). `id3v2 == NONE`: the existing v2 tag is copied byte-for-byte (`!remove`, WARNING
  OTHER_VERSION_KEPT naming the stale fields that differ from the proposal) or absent (`remove`, INFO
  OTHER_VERSION_REMOVED).
- **v1 slot**, `id3v1` selected: exactly `request.id3v1()`, projected from the **proposed** metadata (so a stale v1
  is rewritten from v2 values; it shows in `slotChanges`). `NONE`: kept byte-for-byte or removed, as for v2.
- **FieldChanges** are computed against the source's **v2 slot**, not the effective values: a value that only v1
  had (effective fallback) is copied into the new v2 tag. `clear` only happens for a manual `FieldEdit.Clear`,
  because proposals never drop values otherwise (plan 002).
- **Values written by the adapter:** TRCK/TPOS `"3/12"` or `"3"` (no padding); TCON plain text, never `(nn)`;
  several genres NUL-separated in v2.4, `"; "`-joined in v2.2/2.3; other multi-value text frames from v2.4 joined
  with `"; "` in v2.2/2.3; COMMENT = the COMM frame with empty description (language kept, else `eng`), other COMM
  frames untouched; YEAR = TYER (v2.3), TDRC (v2.4), TYE (v2.2). An unchanged YEAR keeps the finer date (`TDRC
  2020-05-01` → v2.3 `TYER 2020` + `TDAT 0105`).
- **V2_NOT_ENCODABLE** (ERROR, SKIP): `v2Encoding == LATIN1` and a modelled value of the expected v2 layer has a
  character outside Latin-1.

## Steps

### Step 1 – jaudiotagger learning tests (de-risk ADR 0011 first)
Files: `music-studio-tag-jaudiotagger/T/tag/jaudiotagger/JaudiotaggerLearningTest.java` (kept: it documents
library behaviour and fails loudly on a library upgrade).

Inputs built with `Id3Bytes`; outputs checked with plain byte reads (the inspector arrives in step 5). Pin down:
- `ID3v23Tag.write(OutputStream)` writes header + frames + padding; how much padding; no unsynchronisation by default
- `new ID3v23Tag(ID3v24Tag)` / `new ID3v22Tag(ID3v24Tag)`: what happens to PRIV, TXXX, USLT, APIC→PIC, an unknown
  `XABC` frame, multi-value `TPE1 "A\0B"`, `TDRC 2020-05-01`, `TCON "Pop\0Rock"`
- whether per-frame `setTextEncoding` survives `write` or is overridden by `TagOptionSingleton`
  (`resetTextEncodingForExistingFrames`, `id3v23DefaultTextEncoding`, `id3v24UnicodeTextEncoding`)
- `FieldKey.GENRE` writes `(17)` unless `writeMp3GenresAsText` → confirms: write TCON at frame level

Done when every assumption in "Slot and value rules" is confirmed or the plan's adapter notes are corrected. If
`write(OutputStream)` is unusable, fall back to `write(WritableByteChannel, 0)` into an in-memory channel (same
strategy).

### Step 2 – Write options and capabilities
Files (`C/tag/`): `Id3v2Choice`, `Id3v1Choice`, `V2Encoding`, `V1TextMode`, `TagWriteOptions`,
`FormatCapabilities`.

Tests (`CT/tag/TagWriteOptionsTest`, `FormatCapabilitiesTest`):
- DEFAULT = V2_3 + V1_1 + AUTO + ASCII + keep; `selectedVersions()` = {ID3V2_3, ID3V1_1}; NONE+V1_1 → {ID3V1_1}
- the full cross product (4 × 3 × 4 × 2 × 2 = 192) against a hand-written predicate: `problems` empty ⇔ the
  constructor succeeds; every problem message is not blank and names the option
- each problem case from the list above; NONE+NONE+remove is valid ("strip all tags")
- `unsupported`: capabilities without V2_2 + options V2_2 → one ERROR UNSUPPORTED_WRITE_VERSION mentioning
  "ID3v2.2"; without UTF8 + UTF8 → one issue naming UTF-8; NONE choices never produce an issue
- `FormatCapabilities` invariants (empty sets, MP4 version for MP3, AUTO missing → IAE); defensive copies

### Step 3 – ID3v1 projection
Files: `C/tag/Id3v1Values.java`, `C/tag/Id3v1Projection.java`

Tests (`CT/tag/Id3v1ValuesTest`, `Id3v1ProjectionTest`, table-driven):
- `Ђорђе Балашевић` (ASCII) → `Djordje Balasevic`, WARNING V1_TRANSLITERATED forField(ARTIST)
- 41-char title → ≤ 30 at a word boundary, WARNING V1_TRUNCATED; a single 40-char word → hard cut
- emoji in title → dropped, WARNING V1_CHAR_DROPPED (never `?`)
- LATIN1 mode keeps `Café`; ASCII mode → `Cafe` + V1_TRANSLITERATED
- comment 29 chars: V1_0 keeps it, V1_1 truncates to 28; multi-line comment → spaces
- track 7/12 → 7 + INFO (total); track 300 → 0 + WARNING; V1_0 with track → 0 + INFO; no track → 0
- genres `[Rock, Pop]` → 17 + INFO (extra genre); `[Balkan]` → 12 + WARNING (D9); none → 255
- album artist / disc present → INFO V1_FIELD_NOT_REPRESENTABLE each
- year 999 → `OptionalInt.of(999)` (written as `0999`); absent → empty
- `Id3v1Values` constructor: non-ASCII in ASCII mode, 31-char title, track ≠ 0 for V1_0, genre 256, NONE version
  → IAE. `readBack()`: empty strings absent, track 0 absent, genre 12 → `Other`, 255 → absent

### Step 4 – Request, expectation, slot diff, verifier
Files (`C/tag/`): `FieldChanges`, `CoverChange`, `TagWriteRequest`, `WriteExpectation`, `ValueChange`, `SlotChange`,
`WriteVerifier`

Tests:
- `FieldChangesTest`: set/clear/unchanged; restricted to writable; disjointness → IAE; `applyTo` round trip
  (`between(a, b, all).applyTo(a) == b`, property-style over 200 random metadata pairs with a fixed seed)
- `TagWriteRequestTest`: each invariant → IAE (id3v1 present with NONE, version/mode mismatch, changes with v2 NONE)
- `WriteExpectationTest` (table, current layers × options → expected layers + slot issues):
  v2.4-only → V2_3 = one v2.3 layer with changed values; v2.4-only → V2_2 likewise; v2.3 + v1.1 with V2_NONE/keep →
  v2.3 unchanged + new v1 + OTHER_VERSION_KEPT naming the stale field; same with remove → v1 only +
  OTHER_VERSION_REMOVED; untagged + DEFAULT → v2.3 + v1.1; NONE+NONE+remove → no layers; v1.0 source + V1_1 →
  converted v1.1
- `SlotChangeTest`: version change only, value change only, family added/removed, unchanged family → empty values
- `WriteVerifierTest`: equal → empty; wrong version, missing family, extra family, wrong value, missing value →
  one VERIFY_MISMATCH each, message `ID3v2.3 title: expected "X", found "Y"`; Genres `[Pop, Rock]` vs
  `["Pop; Rock"]` → equal (D5); duration differs by 1 s → mismatch; TAG_DAMAGED in actual → mismatch

### Step 5 – `TagWriter` port + testkit (inspector, contracts, fake)
Main: `C/port/TagWriter.java` (Javadoc = §6.3 with D3/D8).

Test-jar (`CT/testkit/`):
- `fixture/Id3Inspector.java` – parses bytes into `Id3File(Optional<V2Tag> v2, byte[] payload,
  Optional<byte[]> v1Block)`; `V2Tag(major, flags, List<Frame(id, flags, body)>)`; handles v2.2/2.3/2.4 frame
  headers, v2.3 whole-tag unsynchronisation, v2.4 per-frame unsync flag, extended header, footer; `text(Frame)`
  decodes encodings 0–3 (+BOM); `v1Fields(byte[])`. Payload = bytes between the v2 end and a trailing `TAG` block.
- `fixture/Id3FrameIds.java` – equivalence table v2.2 ↔ v2.3 ↔ v2.4 for the frames used in the contracts (TT2/TIT2,
  TYE/TYER/TDRC, COM/COMM, ULT/USLT, TXX/TXXX, PIC/APIC, PRIV → none in v2.2, …)
- `contract/TagWriterContract.java` – hooks `subject()`, `reader()`, `capabilitiesUnderTest()`
- `contract/Id3WriterContract.java` – hooks `subject()`, `reader()`
- `fake/FakeTagWriter.java` – `new FakeTagWriter(FakeTagReader reader, Set<TagVersion> writable)`; `writeCopy`
  copies the bytes (cancellable in 64 KiB steps, progress) and registers `reader.answer(target, …layersAfter…)`.
  Hooks: `failOn(source, Reason)`, `writeWrongValue(source)` (→ VERIFY_MISMATCH), `cancelDuring(source)`,
  `frameLoss(source, List<Issue>)` (preflight answer), `writeCount()`
- `fake/ExportInputs.java` – builds real source files + `ScannedFile` + `Proposal` for service tests (copies a
  fixture to `@TempDir`, registers size/mtime in `InMemoryMediaFileSource` and the answer in `FakeTagReader`)

`TagWriterContract` (every writer, fixtures copied into `@TempDir`):
- `writableFormats` non-empty/unmodifiable; `capabilities` constant, valid, contains AUTO; unknown format → IAE
- parameterised over readable fixtures × supported option combinations (v2 ∈ caps ∪ NONE, v1 ∈ {NONE, 1.0, 1.1},
  remove ∈ {false, true}, invalid combos skipped): changes {TITLE = `Ђорђе – Song 🎵` (Latin-1 title when the
  encoding is LATIN1), ARTIST set, COMMENT cleared} → `WriteVerifier.verify(layersAfter(…), …, reader.read(target))`
  is empty
- plus hand-written expectations (no core oracle): `V23_LATIN1` → V2_2 + V1_1 with title `Song`; `V24_UTF8` →
  V2_3 + V1_NONE/keep; `V11_V23_DIFF` → V2_NONE/remove + V1_0
- source unchanged (SHA-256 + mtime) after `preflight` and after `writeCopy`, also after a failure; `preflight`
  creates no file (directory listing unchanged)
- stream info of the target equals the source (duration ± 50 ms, bitrate)
- progress received a final `(total, total)`; cancelled before start → `OperationCancelledException`
- preconditions: null → NPE; relative source/target, existing target, missing parent, target == source,
  unsupported options → IAE
- failures: missing source → NOT_FOUND; `CORRUPT`/`EMPTY` fixtures → CORRUPT; read-only target dir →
  ACCESS_DENIED (`@DisabledOnOs(WINDOWS)`, skipped as root)
- concurrency: 4 threads × different files → all verify
- a writer without V2_2 in its capabilities rejects V2_2 with IAE (run only when `capabilitiesUnderTest` lacks it)

`Id3WriterContract` (real MP3 writers; inputs from `Id3Bytes`, outputs via `Id3Inspector`):
- payload byte-identical for every option combination (incl. an APE-like trailer before `TAG`)
- preservation, same version, only TITLE changed: PRIV, unknown `XABC`, APIC bodies byte-identical; TXXX
  `REPLAYGAIN_TRACK_GAIN`/`_PEAK`, USLT, the `iTunNORM` COMM equal after decoding; frame set = before
- v2.4 → v2.3: TDRC `2020-05-01` → TYER `2020` (+ TDAT `0105`); TPE1 `A\0B` → `A; B`; TCON `Pop\0Rock` →
  `Pop; Rock`; no frame missing that preflight did not report
- v2.4 → v2.2 (story edge case): 3-char IDs, APIC → PIC `JPG`, PRIV dropped **and** reported as
  FRAME_NOT_REPRESENTABLE by preflight (`preflight` frame set == frames missing after `writeCopy`, via
  `Id3FrameIds`)
- encodings: AUTO + ASCII → byte 0; AUTO + Cyrillic: v2.3 → 1 with BOM, v2.4 → 3; UTF16 in v2.4 → 1; LATIN1 +
  `Café` → 0 and byte 0xE9; untouched TXXX in UTF-8 (v2.4) → UTF-16 in v2.3 (D8)
- TCON is `Rock` (never `(17)`); TRCK `3/12` unpadded; COMM with empty description
- v1 block byte-exact: `TAG`, NUL padding, v1.1 byte 125 = 0 + track at 126, genre byte, v1.0 30-byte comment
- NONE + keep → original v2 bytes / original 128 v1 bytes identical; NONE + remove → absent
- no extra files in the target directory after `writeCopy` (no library temp files)
- damaged v2 tag that must be rewritten → CORRUPT (D7)

Concrete tests in core: `CT/testkit/fake/FakeTagWriterContractTest extends TagWriterContract` (fake writer + fake
reader), `CT/testkit/fixture/Id3InspectorTest` (round trip of every `Id3Bytes` builder incl. unsync and v2.2).

### Step 6 – jaudiotagger writer
Files (`music-studio-tag-jaudiotagger/P/tag/jaudiotagger/`):
- `LibraryLock.java` – package-private holder of the `ReentrantLock` (moved from `JaudiotaggerTagReader.LIBRARY_LOCK`;
  the reader uses it unchanged)
- `JaudiotaggerTagWriter.java` – public; capabilities MP3: all five ID3 versions, all four encodings, all 9 fields,
  `frontCover = false` until 009
- `SourceLayout.java` – v2 tag size from the header (+ footer flag), v1 presence (`TAG` in the last 128 bytes),
  payload offsets
- `V2Conversion.java` – one function used by `preflight` and `writeCopy`: parse → convert to the target version
  → list frames without equivalent (D2/ADR 0011); damaged tag → CORRUPT
- `V2Fields.java` – applies `FieldChanges` at frame level (rules above) and the encodings (D8)
- `Id3v1Block.java` – `byte[128] encode(Id3v1Values)`
- `PayloadCopy.java` – 1 MiB buffer, progress per buffer, `cancel.throwIfCancelled()` per buffer (≤ 8 MiB rule),
  `FileChannel` source opened `READ` only, target opened `CREATE_NEW, WRITE`, `force(true)` before close
- `LibraryExceptions.java` – add write reasons: `FileSystemException` "No space left" → NO_SPACE, `FileAlreadyExists`
  → IAE (precondition), `AccessDenied` → ACCESS_DENIED

Notes: hold the lock only while parsing/converting/serialising (not during the payload copy). Touch
`TagOptionSingleton` only under the lock and restore every option you change in `finally`. Never call
`AudioFileIO.write`/`MP3File.save`.

Tests (`music-studio-tag-jaudiotagger/T/tag/jaudiotagger/`):
- `JaudiotaggerTagWriterTest extends TagWriterContract`, `JaudiotaggerId3WriterTest extends Id3WriterContract`
- `JaudiotaggerWriterSpecificsTest`: v2.2 source → v2.4 target; 8-MiB-plus payload (synthesised by repeating
  `Id3Bytes.audio()` frames) → progress events and cancellation mid-copy leave a target the caller can delete; read
  and write concurrently (reader on file A while writer copies file B) → both succeed; `TagOptionSingleton` options
  are the same before and after a write
- `JaudiotaggerArchitectureTest` unchanged (A8, A10, A11) – must stay green

### Step 7 – `ExportTarget` port + storage adapter
Main: `C/port/ExportTarget.java` (Javadoc = §6.4 for the six methods).
Test-jar: `CT/testkit/contract/ExportTargetContract.java`, `CT/testkit/fake/FakeExportTarget.java` (real files under
`@TempDir`; check-then-move; failure hooks `failNext(Operation, Reason)`, `appearBeforeMove(target)`; `calls()` log
for ordering assertions) + `FakeExportTargetContractTest`.
Storage: `music-studio-storage/P/storage/LocalExportTarget.java`, `music-studio-storage/T/storage/LocalExportTargetTest
extends ExportTargetContract`, `LocalExportTargetLinkFallbackTest`.

`ExportTargetContract`:
- `findCollision`: same name, different case, NFD vs NFC → found; parent missing → empty; nothing modified
- `createDirectories`: returns exactly the created dirs, outermost first; existing → empty; a file in the way →
  ALREADY_EXISTS
- `reserveTempPath`: same parent, name `.<name>.<6 hex>.musicstudio-tmp`, does not exist, nothing created; a
  250-char name still yields a name ≤ 255 chars
- `moveNoReplace`: content and mtime kept, temp gone; existing target → ALREADY_EXISTS, **target bytes unchanged**,
  temp still there; different parent → IAE
- `deleteTemp`: deletes; missing → no error; a user-file name (`song.mp3`) → IAE and the file still exists
- `fingerprint` like `MediaFileSource.fingerprint`; preconditions (null/relative)

Adapter: hard link `Files.createLink(target, temp)` + delete temp; on `UnsupportedOperationException` or a
`FileSystemException` from the link (FAT/exFAT) fall back to check-then-`Files.move` without `REPLACE_EXISTING`.
`LocalExportTargetLinkFallbackTest` forces the fallback through a package-private constructor flag.

### Step 8 – Write options + plan
Files (`C/export/`): `WriteOptionsUseCase`, `WriteOptionsService`, `ExportSpec`, `PlanExportRequest`,
`PlannedAction`, `PlannedFile`, `ExportPlan`, `PlanExportUseCase`, `PlanExportService`.

Algorithm: global checks first (`capabilities.unsupported(options)` for MP3 → global ERROR; then every file SKIP
without per-file work; output root inside a source root → WARNING OUTPUT_INSIDE_SOURCE). Then per proposal, in
order, with a cancel check and a "plan" progress event: not exportable → SKIP (carry the proposal's ERROR); scan
ERROR (`TAG_DAMAGED`) → SKIP (D7); target = `outputRoot/fileName`; equals any source (case/NFC) →
TARGET_IS_SOURCE; batch duplicate (case/NFC) → COLLISION_SKIPPED (ERROR); `findCollision` → TARGET_EXISTS (ERROR);
`FieldChanges.between(v2 slot, proposed, writableFields)` (UNSUPPORTED_FIELD WARNING for dropped changed fields);
`Id3v1Projection`; V2_NOT_ENCODABLE; `TagWriteRequest`; `preflight` (MediaAccessException → SKIP, READ_FAILED);
`slotIssues`; `expectedLayers`; carry the proposal's WARNINGs. `ExportTarget` is used only for `findCollision`.

Tests (`CT/export/PlanExportServiceTest`, inputs from `ExportInputs`, `FakeTagWriter`, `FakeExportTarget`):
- one PlannedFile per proposal in order; WRITE has target `out/<name>` and a request
- Cyrillic artist + DEFAULT → v1 warnings in issues; slotChanges show `ID3v1.1 artist: (none) → Djordje Balasevic`
- v2.4 source + V2_3 → slotChanges `ID3v2.4 → ID3v2.3`; V2_NONE keep → OTHER_VERSION_KEPT; remove → REMOVED
- unsupported version (fake writer without V2_2) → global ERROR, not executable, **writer never called**
- NO_MATCH / AMBIGUOUS proposals → SKIP with their issue; TAG_DAMAGED scan → SKIP
- two sources `a/Song.mp3`, `b/song.mp3` → second COLLISION_SKIPPED; existing `out/Song.mp3` → TARGET_EXISTS;
  outputRoot == source folder → TARGET_IS_SOURCE for all, not executable
- LATIN1 encoding + Cyrillic title → V2_NOT_ENCODABLE, SKIP
- preflight FRAME_NOT_REPRESENTABLE copied into the item's issues; preflight NOT_FOUND → SKIP READ_FAILED
- `executable()` false with zero WRITE items; counters; `createdAt` from the fixed `Clock`
- **writes nothing**: `FakeExportTarget.calls()` contains only `findCollision`; `FakeTagWriter.writeCount() == 0`;
  `@TempDir` tree snapshot unchanged
- cancellation mid-plan → OperationCancelledException; preconditions → NPE/IAE
- `WriteOptionsServiceTest`: capabilities pass-through; `validate` = union of `unsupported` per format; empty
  formats → IAE

### Step 9 – Apply
Files (`C/export/`): `ApplyExportUseCase`, `ApplyExportService`, `ExportStatus`, `ExportedFile`, `SkippedFile`,
`FailedFile`, `ExportReport`.

Algorithm: Pre `plan.executable()` (IAE); output root not already being applied (ISE, concurrent key set).
`createDirectories(outputRoot)` → failure ABORTED, nothing written. Per WRITE item in plan order: cancel check →
fingerprint source (NOT_FOUND or different → SOURCE_CHANGED) → `reserveTempPath` → `writeCopy` → fingerprint source
again (D12) → `reader.read(temp)` + `WriteVerifier` → `moveNoReplace` (ALREADY_EXISTS → TARGET_EXISTS) →
`ExportedFile`; `finally deleteTemp` if the temp still exists. NO_SPACE → ABORTED (D11); cancel → CANCELLED, the
rest skipped with CANCELLED. Progress phase "write" with byte progress.

Tests (`CT/export/ApplyExportServiceTest`, fakes):
- happy path: 3 files → COMPLETED, 3 targets exist, re-read equals the expected layers, no temp left, order of
  `calls()` per file = fingerprint, reserve, write, read, move, (deleteTemp only if needed)
- **originals:** SHA-256 + mtime of every source unchanged in every test (shared `@AfterEach` assertion)
- verify mismatch (`writeWrongValue`) → failed VERIFY_MISMATCH, target absent, temp deleted, run continues
- source changed between plan and apply → SOURCE_CHANGED, nothing written for it; source deleted → same
- target appears before the move (`appearBeforeMove`) → TARGET_EXISTS, the other file's bytes unchanged
- writer ACCESS_DENIED / IO → WRITE_FAILED, run continues → COMPLETED_WITH_ERRORS
- NO_SPACE on file 2 of 4 → ABORTED, file 1 written, 2 failed, 3–4 skipped "not attempted", temps gone (D11)
- read-only source file (`chmod 444`) → written normally
- cancellation during file 2 → CANCELLED, file 1 stays, temp of file 2 deleted, 3 skipped
- `createDirectories` fails → ABORTED, nothing written; created output root listed in `createdDirectories`
- counters `written + skipped + failed == files`; SKIP items of the plan appear in `skipped` with their reasons
- non-executable plan → IAE; concurrent apply to the same root → ISE (second thread, latch)

### Step 10 – ArchUnit A13
Files: `CT/testkit/arch/ArchitectureRules.java` (`A13_ONLY_APPLY_WRITES`: no class in `core..` other than
`ApplyExportService` calls `TagWriter.writeCopy`, `ExportTarget.createDirectories/reserveTempPath/moveNoReplace/
deleteTemp`), `CT/CoreArchitectureTest.java` (apply A13), `CT/testkit/arch/violations/PlanThatWrites.java` +
`ArchitectureRulesSelfTest` (the rule fails on it).
Also: A3 and A5 must pass for `core.tag`/`core.export` (A5: `Id3v1Projection`, `WriteExpectation`, `WriteVerifier`
are final classes with static methods only). If a rule fails, change the code, never the rule.

### Step 11 – Composition root + real-file end-to-end
Files: `music-studio-app/P/app/MusicStudio.java` (add `writeOptions`, `planExport`, `applyExport`, `Require`d),
`MusicStudioFactory.java` (the only `new JaudiotaggerTagWriter()`, `new LocalExportTarget()`, services with
`config.clock()`), `music-studio-app/T/app/ExportRealFilesTest.java`, `MusicStudioFactoryTest` (update).
Update every `new MusicStudio(…)` in CLI tests.

`ExportRealFilesTest` (real adapters, `@TempDir/src` + `@TempDir/out`): fixtures `V24_UTF8`, `V23_UTF16_CYRILLIC`,
`V11_V23_DIFF`, `NO_TAGS`, plus an `Id3Bytes` file with PRIV/TXXX ReplayGain/APIC → scan → propose (`--set
genre=Pop` equivalent) → plan DEFAULT → apply → COMPLETED; re-scan `out/` with the real reader: v2.3 + v1.1 everywhere,
genre Pop, Cyrillic transliterated in v1, PRIV/TXXX/APIC still present (`Id3Inspector`); sources' SHA-256 and
mtimes unchanged; no `*.musicstudio-tmp` left; second apply of a fresh plan → every file TARGET_EXISTS (nothing
overwritten).

### Step 12 – CLI `export`
Files:
- `music-studio-cli/P/cli/WriteOptions.java` – picocli `@Mixin`: `--id3v2 none|2.2|2.3|2.4` (default 2.3),
  `--id3v1 none|1.0|1.1` (default 1.1), `--v2-encoding auto|latin1|utf16|utf8`, `--v1-text ascii|latin1`,
  `--remove-other-versions`; `Optional<TagWriteOptions> toOptions(PrintWriter err)` prints
  `TagWriteOptions.problems` → usage error
- `music-studio-cli/P/cli/ExportCommand.java` – `export <dir> --out <dir> [-r] [--include-hidden]
  [ExtractionOptions] [WriteOptions] [--format table|json] [--apply]`. Extraction options are optional here (no
  pattern = re-tag with the chosen versions). Flow: options → `writeOptions.validate(options, {MP3})` (issues →
  exit 2 **before** scanning) → scan → propose → plan → render plan → without `--apply`: `Preview only – no file
  was changed. Run again with --apply to write N files to <out>.` → with `--apply`: not executable → exit 2; else
  apply → render report
- `ExitCode.of(ExportPlan)` (2 not executable, 1 any SKIP with ERROR or unreadable, 0) and
  `ExitCode.of(ExportReport)` (0 COMPLETED, 1 COMPLETED_WITH_ERRORS, 3 ABORTED, 130 CANCELLED)
- `P/cli/render/PlanRenderer` (+ `TablePlanRenderer`, `JsonPlanRenderer`), `ReportRenderer` (+ table/JSON)
  - Table per WRITE file: `<source> → <target>`, then per `SlotChange` a line `ID3v2.4 → ID3v2.3` / `ID3v1.1 (new)`
    / `ID3v2.4 (kept, unchanged)` / `ID3v1.0 (removed)` and one line per value `title   Café Song → Song`; issues
    as `! warning V1_TRUNCATED title: …` (INFO only with `--verbose`). SKIP rows: `SKIP <source>: <reason>`. Then
    global issues, the "Unreadable (not processed)" section, summary `5 files: 3 to write, 2 skipped`
  - Report: every created file and directory (this is the manual undo list until 004), failures with reasons,
    status line
  - JSON mirrors the records (`files[{source, target, action, slots[{family, before, after, values[{field, before,
    after}]}], issues[]}]`, `issues`, `summary`), via `Json.string`
- `MusicStudioCommand.commandLine` adds `new ExportCommand(studio.scan(), studio.propose(), studio.writeOptions(),
  studio.planExport(), studio.applyExport(), cancel)`

Tests:
- `T/cli/WriteOptionsTest` – every flag → expected `TagWriteOptions`; `--id3v2 2.3 --v2-encoding utf8` → exit 2
  naming UTF-8 and v2.4; `--id3v2 none --id3v1 none` → exit 2 suggesting `--remove-other-versions`; unknown value
  `--id3v2 3` → exit 2
- `T/cli/ExportCommandTest` – fake use cases (lambdas): missing `--out` → 2; unsupported options (fake
  `validate` returns an issue) → 2 and **scan never called**; dry-run never calls `apply`; `--apply` with a
  non-executable plan → 2 and no `apply`; exit codes for every `ExportStatus`
- `T/cli/render/*PlanRendererTest`, `*ReportRendererTest` – golden output (slot lines, Cyrillic, emoji, kept/removed
  slots, SKIP reasons, JSON escaping)
- `T/cli/ExportEndToEndTest` – real factory, `@TempDir/src` with fixture copies named `Artist - Song.mp3`,
  `Ђорђе - Песма.mp3`, a 40-char title, `V24_UTF8`, a corrupt copy; `export src --out out --pattern "{artist} -
  {title}"`: (1) dry-run → slot diffs incl. `Djordje` + truncation warning, `out/` does not exist afterwards, tree
  snapshot of `src` identical, exit 1 (corrupt); (2) `--id3v2 2.2 --apply` → files in `out/` read back as v2.2 +
  v1.1, sources unchanged, report lists the files; (3) the same command again → TARGET_EXISTS for all, exit 1, `out/`
  bytes unchanged; (4) `--id3v2 none --id3v1 1.1` without/with `--remove-other-versions` → kept warning / v1-only
- `CliArchitectureTest` unchanged (A9: the CLI uses only use cases) – must stay green

### Step 13 – Native image
- Re-run the tracing agent over the adapter tests including the writer contracts
  (`./mvnw -Pnative -Dagent=true -pl music-studio-tag-jaudiotagger test`), merge into the committed
  `reachability-metadata.json` (frame bodies created during conversion: `FrameBodyTYER`, `FrameBodyTDAT`, v2.2
  bodies, `FrameBodyPIC`, …). Keep only `org.jaudiotagger` entries.
- Extend `music-studio-cli/src/test/native/scan-smoke.sh`: `export … --apply` into `mktemp -d`, then `scan --format
  json` of the output compared with `expected-export-scan.json`; source checksums unchanged.
- If writing fails only natively, add metadata; never add reflection to our code.

### Step 14 – Docs and story done
- `docs/architecture.md`: §3 (`core.tag` contents incl. `WriteExpectation`, `SlotChange`, `ValueChange`), §5.3
  (`FieldChanges.applyTo`, `Id3v1Values.readBack`, `TagWriteRequest` invariants), §6.3 (D3, D8, ADR 0011), §6.4
  (subset in 003), §7.4/7.5 (003 shapes: D1, D4, D7, D11, D12; what 004 adds), §14.1 (D6), §14.4 (A13), §17, ADR
  0011 status → accepted
- tick the criteria in `docs/stories/003-write-tags-with-version-selection.md`; `docs/stories/README.md` status
- run the `tester` agent, then the `reviewer` agent

**Cut line** if the session runs long: steps 1–11 prove every criterion at use-case level with real files; 12 (CLI)
and 13 (native) can follow in the next session.

## Acceptance criteria → tests

| Criterion | Proven by |
|---|---|
| choose ID3v2 none/2.2/2.3/2.4, ID3v1 none/1.0/1.1, defaults 2.3 + 1.1 | `TagWriteOptionsTest` (DEFAULT, cross product), `WriteOptionsTest` (CLI flags/defaults), `TagWriterContract` (every combination) |
| choose v2 encoding and v1 text mode | `Id3WriterContract` encodings, `Id3v1ProjectionTest` ASCII/LATIN1, `WriteOptionsTest` |
| invalid combinations rejected before writing, clear message | `TagWriteOptionsTest.problems`, `WriteOptionsTest` (exit 2 before scan), `ExportCommandTest` |
| remove other versions: on → replaced/removed; off → warning | `WriteExpectationTest` (KEPT/REMOVED), `Id3WriterContract` (kept bytes identical / absent), `PlanExportServiceTest`, `ExportEndToEndTest` (4) |
| dry-run default: per file field old → new, warnings (truncated, transliterated, not representable) | `SlotChangeTest`, `PlanExportServiceTest`, plan renderer golden tests, `ExportEndToEndTest` (1) incl. "out/ not created" |
| apply writes safely, re-reads, mismatches are errors | `ApplyExportServiceTest` (order, verify mismatch, temp cleanup), `WriteVerifierTest`, `ExportTargetContract` (no-replace), `ExportRealFilesTest`, `ExportEndToEndTest` (2) |
| untouched fields stay (unknown frames, cover, ReplayGain) | `Id3WriterContract` preservation (PRIV, XABC, APIC, TXXX, USLT, COMM), `ExportRealFilesTest` |
| adapter can't write a chosen version → reported before writing | `FormatCapabilitiesTest`, `TagWriterContract` (v2.2-less writer), `PlanExportServiceTest` (writer never called), `ExportCommandTest` (scan never called) |
| edge: read-only file, file disappears, disk full | `ApplyExportServiceTest` (chmod 444, SOURCE_CHANGED, NO_SPACE → ABORTED), `TagWriterContract` (read-only target dir) |
| edge: Cyrillic in v1, title > 30 chars | `Id3v1ProjectionTest`, `ExportEndToEndTest` (1) |
| edge: v2.4 only → v2.2 | `Id3WriterContract` (PRIV reported + dropped, PIC), `WriteExpectationTest`, `ExportEndToEndTest` (2) |
| originals never modified | SHA-256/mtime in `TagWriterContract`, `ApplyExportServiceTest` (`@AfterEach`), `ExportRealFilesTest`, `ExportEndToEndTest`, native smoke |

## Data safety
- **Originals:** the writer opens the source read-only (contract: SHA-256 + mtime, also after failures), requires
  target ≠ source, the plan rejects TARGET_IS_SOURCE, apply re-fingerprints the source before and after each
  write (D12). `ExportTarget` has no method that can delete or replace a user file (`deleteTemp` rejects non-temp
  names).
- **Dry-run default:** the CLI applies only with `--apply`; `PlanExportService` cannot write: it gets no write
  calls (A13) and the tests assert an unchanged tree and an empty write log.
- **Safe write:** temp in the target directory → `writeCopy` (payload copied byte-for-byte, `force` before close) →
  re-read + `WriteVerifier` → `moveNoReplace` (hard link, never replaces) → temp deleted in `finally`.
- **Never overwrite:** 003 has no overwrite at all: existing targets and batch duplicates are SKIPped in the plan,
  and a target that appears later fails the move without touching it.
- **No silent tag loss:** fields outside the change set and every unmodelled frame are preserved (byte-level
  contract). Frames that cannot exist in the target version are listed by preflight before anything is written
  (FRAME_NOT_REPRESENTABLE). A kept stale tag of another family is a WARNING. Damaged tags are not rewritten (D7).
- **Undo:** in 003 every visible change is a new file or the new output root, all listed in the report. The
  journal + `undo` command arrive in 004 (open question 1).

## Risks to verify while implementing
- jaudiotagger conversion (step 1): multi-value frames into v2.3 (NUL kept?), unknown frames across versions,
  TDRC → TYER/TDAT, per-frame encodings overridden at write time. Every surprise becomes adapter code plus a
  learning test, never a weaker contract.
- `TagOptionSingleton` defaults (`padNumbers`, `writeMp3GenresAsText`, unsync, padding) leak into writes via
  `FieldKey`. Writing at frame level avoids most; assert "options unchanged after write".
- Payload boundaries: ID3v2 footer, padding with garbage, two ID3v2 tags, APE/Lyrics3, a `TAG` false positive in
  audio. All stay in the payload; only the leading v2 tag and the trailing 128-byte `TAG` block are replaced.
- Hard links on FAT/exFAT sticks and on Windows; long names on FAT (255 UTF-16 units) for temp names.
- `Id3Inspector` must be correct independently. Its round-trip test against `Id3Bytes` and the read side of the
  existing fixtures cover that.
- Native image: conversion instantiates frame bodies reflectively; only the re-traced metadata covers them.
- Session size: 14 steps. Respect the cut line rather than skipping contract tests.

## Open questions (need the user's decision; the plan assumes the first option)
1. **Undo before 004 (D1):** ship `export --apply` in 003 without the journal (nothing is ever replaced; the
   report lists created files) (assumed), *or* pull `ExportJournalStore` + `undo` from 004 into 003, *or*
   allow `--apply` in 003 only into a new or empty output folder?
2. **Target names in 003 (D1):** flat `out/<source file name>` with duplicates skipped (assumed), *or* mirror the
   source folder structure under `out/`? And in 004: is flat the default when no `--out-pattern` is given?
3. **Damaged tags (D7):** skip files with `TAG_DAMAGED` (assumed), *or* export them and warn that the unreadable
   frames are lost?
4. **ID3v1 genre without a number (D9):** write 12 "Other" with a warning (assumed, per the skill), *or* leave it
   empty (255)?
5. **ID3v1 structural limits (D10):** album artist/disc/track total as INFO, shown only with `--verbose`
   (assumed), *or* as a WARNING on every file?
6. **Disk full (D11):** abort the run (assumed), *or* continue and let every remaining file fail?
