# Music-Studio – Architecture

Status: accepted baseline, 2026-10-22 · Scope: stories 001–012 · Binding rules: `CLAUDE.md`.
Decisions are recorded in [`docs/adr/`](adr/). The implementation plan for the skeleton and story 001 is
[`docs/plan-001.md`](plan-001.md).

Contents
1. Goals and constraints
2. Modules and dependency rules
3. Core package layout
4. Design-by-contract conventions
5. Domain model
6. Driven ports (SPI implemented by adapters)
7. Use cases (API called by frontends)
8. Dry-run, change plan, safe export, undo
9. Adapter capabilities and write-option validation
10. Pattern language (input and output patterns)
11. Errors, results, warnings
12. Threading, progress, cancellation
13. Persistence and application directories
14. Testing: contract tests, fakes, fixtures, ArchUnit
15. Maven build (parent POM, native image, JavaFX/jpackage)
16. Library choices
17. Story → component map
18. Open questions

---

## 1. Goals and constraints

| Goal | Consequence |
|---|---|
| **Swappable technology**: GUI toolkit, tag library (jaudiotagger → maybe mp3agic), MP4 tag library, playback backend, persistence | Interfaces in `core` are the stable layer. Every port and use case has a full Javadoc contract. Each technology sits in its own adapter module. |
| **Originals are never modified** | There is no in-place write. The only write path is *export*: source → temp file in the output folder → verify → no-replace atomic move ([ADR 0004](adr/0004-export-only-no-in-place-writes.md)). |
| **Dry-run by default** | Every mutating workflow is split into *plan* (pure, no writes) and *apply* (runs a plan). The CLI only calls apply when `--apply` is given. |
| **Native-image friendly** | No reflection frameworks. Wiring is manual. Library reflection config lives in the adapter modules. |
| **Small** | Seams exist only for the agreed axes: tag library, media format, frontend, playback, persistence. Everything else is plain core code. |

## 2. Modules and dependency rules

```
                        ┌────────────────────────────┐
                        │      music-studio-core     │  domain, ports, use cases, Require
                        │  (no dependencies at all)  │  + test-jar: contract tests, fakes, fixtures, ArchUnit rules
                        └─────────────▲──────────────┘
        ┌───────────────┬─────────────┼────────────────┬──────────────────┬───────────────────┐
        │               │             │                │                  │                   │
 tag-jaudiotagger    storage     tag-mp4 (012)   image-imageio (009) player-javafx (011)     │
 (MP3 tags)        (local disk,   (MP4 atoms)     (cover transcode)   (playback)             │
                    JSON stores)                                                             │
        ▲               ▲             ▲                ▲                  ▲                   │
        └───────────────┴──────┬──────┴────────────────┘                  │                   │
                               │                                          │                   │
                     ┌─────────┴──────────┐                               │                   │
                     │  music-studio-app  │  shared composition root      │                   │
                     │ (wiring, no logic) │  (no JavaFX, native-safe)     │                   │
                     └───▲────────────▲───┘                               │                   │
                         │            │                                   │                   │
                ┌────────┴───┐   ┌────┴─────────┐                         │                   │
                │    cli     │   │     gui      │─────────────────────────┘                   │
                │ (picocli)  │   │  (JavaFX)    │  passes the player into the app root        │
                └────────────┘   └──────────────┘                                             │
              both also depend on core (use-case interfaces, records) ────────────────────────┘
```

| Module | Package | Depends on | Created in | Purpose |
|---|---|---|---|---|
| `music-studio-core` | `…musicstudio.core` | – (JDK only) | skeleton | Domain records, ports, use-case interfaces and their implementations (`*Service`), `Require`, pattern language, transliteration, ID3v1 rules. Also produces a **test-jar** with contract tests, fakes, fixtures and ArchUnit rules. |
| `music-studio-tag-jaudiotagger` | `…musicstudio.tag.jaudiotagger` | core, `net.jthink:jaudiotagger` | 001 | `TagReader`/`TagWriter` for MP3 (ID3v1/v1.1, v2.2/2.3/2.4). |
| `music-studio-storage` | `…musicstudio.storage` | core, Parsson (JSON) | 001 | All local-disk access except tag content: directory listing, fingerprints, temp/no-replace moves, free space, export journal, and the JSON stores for profiles, presets, genres and the library index. |
| `music-studio-app` | `…musicstudio.app` | core + all non-UI adapters | 001 | **One** composition root shared by CLI and GUI. Builds the `MusicStudio` record of use cases. It contains no logic, which guarantees that CLI and GUI run the same wiring. |
| `music-studio-cli` | `…musicstudio.cli` | core, app, picocli | 001 | Thin picocli frontend. Built as a GraalVM native image (profile `native`). |
| `music-studio-gui` | `…musicstudio.gui` | core, app, player-javafx, JavaFX | 010 | Thin JavaFX frontend, packaged with jpackage. |
| `music-studio-player-javafx` | `…musicstudio.player.javafx` | core, `javafx-media` | 011 | `PlaybackEngine` implemented with JavaFX `MediaPlayer` ([ADR 0007](adr/0007-playback-port-and-backend.md)). It is kept out of `app`, so the native CLI never pulls in JavaFX. |
| `music-studio-tag-mp4` | `…musicstudio.tag.mp4` | core, library chosen by spike | 012 | `TagReader`/`TagWriter` for MP4 video atoms ([ADR 0006](adr/0006-mp4-video-tagging-library.md)). |
| `music-studio-image-imageio` | `…musicstudio.image.imageio` | core, JDK ImageIO (+ WebP plugin) | 009 | `ImageTranscoder` (baseline JPEG, resize, square). Native-image AWT support gets verified in 009. |

**Decisions on the questions asked** (details in [ADR 0001](adr/0001-ports-and-adapters-module-layout.md)):
- **Separate `app` wiring module: yes.** CLI and GUI need the same adapter graph. Two hand-written roots would drift
  apart, and the rule "the CLI can do anything the GUI can, through the same use case" would erode.
  "Adapters are chosen in exactly one place" then means `MusicStudioFactory`. The only frontend-specific adapter is the
  player, and the GUI's root passes it into the factory.
- **Player adapter module: yes** (011). Playback is an agreed seam, and JavaFX Media must not end up in the native CLI.
- **Video tag adapter module: yes** (012). Its I/O profile (5–20 GB files, streaming copy) and its library choice
  are independent of MP3 tagging.
- **Filesystem access behind ports: yes** (`storage`). Core does no I/O. As a result, every use case can be
  unit-tested with in-memory fakes, and the export safety rules live in one adapter that has its own contract test.
- **No JPMS `module-info` for now.** Encapsulation is enforced by ArchUnit. jpackage builds a jlink runtime that
  contains the JavaFX modules, and our jars stay on the classpath ([ADR 0005](adr/0005-graalvm-native-cli-and-jpackage-gui.md)).

**Allowed dependency directions** (enforced by Maven and ArchUnit, see §14.4):
- `core` → JDK only (whitelisted packages).
- adapter → `core` and its own library. Adapters never depend on other adapters, on `app` or on frontends.
- `app` → `core` + adapters. Only `app` may instantiate `*Service` classes and adapters.
- `cli`/`gui` → `core` (use-case interfaces, records, enums, pattern parser) + `app`. They never reference ports,
  `*Service` classes or adapter packages. The single exception is `gui.GuiMain`, which constructs the player adapter.

## 3. Core package layout

Root: `dev.rs.slava.icxc.musicstudio.core`. Layering goes from bottom to top. A package may only use packages listed
above it in this table.

| Package | Contents | Story |
|---|---|---|
| `contract` | `Require` (always-on preconditions) | skeleton |
| `text` | `Transliterator` (Serbian Cyrillic → Gaj Latin → ASCII), `Id3v1Text` (fit to N bytes), `FileNameSanitizer`, `NamingRules` | 001/004 |
| `model` | `MediaFormat`, `Field`, `FieldType`, `FieldValue` (+ `Text`, `YearValue`, `Position`, `Genres`), `Genre`, `MediaMetadata`, `TagFamily`, `TagVersion`, `TagLayer`, `TagReadResult`, `StreamInfo`, `CoverInfo`, `CoverArt`, `PictureType`, `FileFingerprint`, `SkippedEntry`, `SkipReason`, `Issue`, `Severity`, `IssueCode`, `PathOrder`, `V1Comparison`, `Id3v1Genres` (genre table is domain data) | 001 |
| `tag` | `TagWriteOptions`, `Id3v2Choice`, `Id3v1Choice`, `V2Encoding`, `V1TextMode`, `FormatCapabilities`, `TagWriteRequest`, `FieldChanges`, `CoverChange`, `Id3v1Values`, `Id3v1Projection`, `WriteVerifier` | 003 |
| `pattern` | `InputPattern`, `OutputPattern`, `PatternSegment`, `Token` (+ `Literal`, `Placeholder`, `Wildcard`), `NumberFormat`, `PatternParser`, `PatternParseResult`, `PatternError`, `PathMatcher`, `MatchResult`, `PathRenderer`, `RenderResult` | 002/004 |
| `task` | `Cancellation`, `CancellationSource`, `OperationCancelledException`, `ProgressListener`, `ProgressEvent`, `ByteProgress` | 001 |
| `port` | `MediaFileSource`, `ListingOptions`, `DirectoryListing`, `TagReader`, `TagWriter`, `ExportTarget`, `StoreInfo`, `ExportJournalStore`, `ExportJournal`, `JournalEntry`, `MediaAccessException`, `StoreException`; later `ProfileStore`, `PresetStore`, `GenreStore`, `LibraryIndexStore`, `ImageTranscoder`, `PlaybackEngine` | 001–011 |
| `scan` | `ScanMediaUseCase`, `ScanRequest`, `ScanItem` (`ScannedFile`, `UnreadableFile`), `ScanSummary`, `ScanStatus`, `ScanMediaService` | 001 |
| `extract` | `ProposeMetadataUseCase`, `ProposeRequest`, `ExtractionSpec`, `FieldOverride`, `MergeMode`, `AutoNumbering`, `ManualEdits`, `FieldEdit`, `Proposal`, `ProposalSet`, `MatchStatus`, `FieldChange`, `ChangeOrigin`, `ProposeMetadataService` | 002 |
| `export` | `WriteOptionsUseCase`, `PlanExportUseCase`, `ApplyExportUseCase`, `UndoExportUseCase`, `ExportSpec`, `CollisionPolicy`, `PlanExportRequest`, `ExportPlan`, `PlannedFile`, `PlannedAction`, `SpaceEstimate`, `ExportId`, `ExportReport`, `ExportStatus`, `UndoPlan`, `UndoStep`, `UndoReport`, `ExportSummary`, `*Service`, `CollisionResolver` | 003/004 |
| `cleanup` | `CleanupSpec`, `CleanupRule` (sealed), `CleanupEngine` | 005 |
| `profile` | `DeviceProfile`, `ProfileId`, `BuiltInProfiles`, `OptionOverrides`, `ResolvedOptions`, `ProfileUseCase`, `ProfileService` | 006 |
| `preset` | `JobSpec`, `JobPreset`, `PresetUseCase`, `PatternSuggestionUseCase`, services | 007 |
| `genre` | `GenreVocabulary`, `GenreEntry`, `GenreMatch`, `GenreUseCase`, `GenreService` | 008 |
| `cover` | `CoverSource`, `CoverPolicy`, `CoverUseCase`, `CoverService` | 009 |
| `library` | `LibraryTrack`, `LibraryIndex`, `LibraryQuery`, `LibraryUseCase`, `LibraryService` | 011 |
| `playback` | `PlaybackQueue`, `PlayerUseCase`, `PlayerService`, `PlaybackState` | 011 |

Feature packages (`scan` → `extract` → `export` → `profile`/`preset`/…) each hold one use case's interface, its
request/result records and its `final` `*Service` implementation. A feature may use earlier features' records (for
example, `export` uses `extract.Proposal`), never later ones.

## 4. Design-by-contract conventions

See [ADR 0002](adr/0002-design-by-contract.md).

### 4.1 `Require` (always on)

```java
package dev.rs.slava.icxc.musicstudio.core.contract;

/** Always-active precondition checks. Never disabled, also not in native images. */
public final class Require {
    private Require() {}

    /** @throws NullPointerException "{name} must not be null" */
    public static <T> T nonNull(T value, String name);
    /** @throws IllegalArgumentException with {@code message} if {@code condition} is false */
    public static void that(boolean condition, String message);
    /** Same, message formatted with {@link String#format} only on failure. */
    public static void that(boolean condition, String template, Object... args);
    /** nonNull + not blank. @throws NullPointerException, IllegalArgumentException */
    public static String nonBlank(String value, String name);
    /** nonNull + every element nonNull. */
    public static <C extends java.util.Collection<?>> C noNullElements(C values, String name);
    /** nonNull + every key and value nonNull. */
    public static <M extends java.util.Map<?, ?>> M noNullEntries(M map, String name);
    /** @throws IllegalArgumentException unless min <= value <= max */
    public static int inRange(int value, int min, int max, String name);
    public static long notNegative(long value, String name);
    /** nonNull + {@code path.isAbsolute()}. */
    public static java.nio.file.Path absolute(java.nio.file.Path path, String name);
    /** @throws IllegalStateException – for calls that are invalid in the object's current state */
    public static void state(boolean condition, String message);
}
```

Rules:
- **Every public method of every public class** in every module starts with its precondition checks. Records do the
  checks in their compact constructor. The checks run in the same order as the `@param` list.
- `Require` throws `NullPointerException` for nulls, `IllegalArgumentException` for invalid values and
  `IllegalStateException` for invalid state. These signal **programming or contract errors**, never user mistakes
  (see §11).
- `assert` is used **in addition**, for internal invariants and postconditions (e.g.
  `assert sorted(result) : "listing must be sorted"`). Tests run with `-ea` (§15). An `assert` is never the only check
  for anything a caller can influence.
- Adapters also use `Require` (they depend on core).

### 4.2 Javadoc template (ports, use cases, public records)

```java
/**
 * <One sentence: what it does, in domain terms.>
 * <Optional paragraph: semantics, ordering, idempotency.>
 *
 * <p><b>Preconditions:</b> <list; each violation names the exception>
 * <p><b>Postconditions/Guarantees:</b> <list, observable state after normal return>
 * <p><b>On failure:</b> <state after an exception, e.g. "no file was created">
 * <p><b>Thread-safety:</b> <safe for concurrent use | confined | serialised internally>
 *
 * @param x <allowed values, "not null", units, absolute/relative>
 * @return <never null; empty means …>
 * @throws NullPointerException     if …
 * @throws IllegalArgumentException if …
 * @throws MediaAccessException     <reason codes that may occur>
 */
```

An implementer must be able to write a conforming implementation from the Javadoc alone. The contract test (§14.1)
is the executable form of the same Javadoc.

## 5. Domain model

All types are immutable records or enums. Absent values are `Optional`/`OptionalInt`, never `null`. Collections are
defensively copied (`List.copyOf`, unmodifiable `EnumMap`). Every compact constructor validates via `Require`.

### 5.1 Fields and values (001)

```java
public enum MediaFormat {
    MP3(Set.of("mp3"));                     // 012 adds MP4(Set.of("mp4", "m4v"))
    public Set<String> extensions();        // lower-case, without dot
    /** Case-insensitive extension match on the file name. */
    public static Optional<MediaFormat> ofFileName(String fileName);
}

public enum FieldType { TEXT, YEAR, POSITION, GENRES }   // 012 adds NUMBER, EPISODES, KIND

/** What the user means – never a frame ID. Placeholder name = pattern-language name. */
public enum Field {
    TITLE("title", TEXT), ARTIST("artist", TEXT), ALBUM_ARTIST("albumartist", TEXT), ALBUM("album", TEXT),
    YEAR("year", FieldType.YEAR), TRACK("track", POSITION), DISC("disc", POSITION),
    GENRE("genre", GENRES), COMMENT("comment", TEXT);
    // 012 adds: SHOW("show"), SEASON("season", NUMBER), EPISODE("episode", EPISODES),
    //           EPISODE_TITLE("episodetitle"), DESCRIPTION(no placeholder), MEDIA_KIND(no placeholder, KIND)
    public String placeholder();
    public FieldType type();
    public boolean multiLine();                         // true only for COMMENT (and later DESCRIPTION)
    public static Optional<Field> byPlaceholder(String name);   // exact, lower-case
}

public sealed interface FieldValue {
    FieldType type();
    /** Canonical display text: "Song", "2020", "3/12" or "3", "Pop; Rock". */
    String display();

    /** value: stripped, not empty, no U+0000. Line breaks allowed only for multi-line fields (checked by MediaMetadata). */
    record Text(String value) implements FieldValue {}
    /** year in 1..9999. Finer dates (TDRC 2020-05-01) are preserved by adapters while YEAR is unchanged. */
    record YearValue(int year) implements FieldValue {}
    /** number 1..9999; total 1..9999 if present. number > total is allowed (real files), reported as a warning by core. */
    record Position(int number, OptionalInt total) implements FieldValue {}
    /** non-empty, no duplicates (case-insensitive), order = priority (first = primary genre). */
    record Genres(List<Genre> values) implements FieldValue { public Genre primary(); }
}

/** name: stripped, not blank, single line. */
public record Genre(String name) {}

/**
 * Format-neutral metadata of one tag layer or of a proposal. Only fields that have a value are present.
 * Invariants: no null keys or values; value.type() == field.type(); single-line unless field.multiLine().
 */
public record MediaMetadata(Map<Field, FieldValue> values) {
    public static MediaMetadata empty();
    public Optional<FieldValue> get(Field field);
    public Optional<String> display(Field field);
    public MediaMetadata with(Field field, FieldValue value);   // Require: value.type() == field.type()
    public MediaMetadata without(Field field);
    public boolean isEmpty();
    /** Fields whose presence or value differs between this and other. */
    public Set<Field> differingFields(MediaMetadata other);
}
```

### 5.2 Tag layers and read results (001)

```java
/** One slot per family in a file. Order = precedence for "effective" values. */
public enum TagFamily { ID3V2, MP4, ID3V1 }

public enum TagVersion {
    ID3V1_0(ID3V1, "ID3v1.0"), ID3V1_1(ID3V1, "ID3v1.1"),
    ID3V2_2(ID3V2, "ID3v2.2"), ID3V2_3(ID3V2, "ID3v2.3"), ID3V2_4(ID3V2, "ID3v2.4"),
    MP4_ILST(MP4, "MP4");
    public TagFamily family();
    public String label();
}

public record TagLayer(TagVersion version, MediaMetadata metadata) {}

/** Duration > 0 or zero if unknown; bitrateKbps >= 0 (0 = unknown); average for VBR. */
public record StreamInfo(Duration duration, int bitrateKbps, boolean variableBitrate) {}

public enum PictureType { FRONT_COVER, OTHER }
/** Describes an embedded picture without holding its bytes. sizeBytes >= 0; mimeType not blank. */
public record CoverInfo(PictureType type, String mimeType, int sizeBytes) {}
/** Picture bytes (defensive copies in and out; equals/hashCode by content). Used from 009/011. */
public record CoverArt(byte[] data, String mimeType, PictureType type) {}

/** Size >= 0. Used to detect "source changed since the plan" and "exported file changed since export". */
public record FileFingerprint(long sizeBytes, Instant lastModified) {}

/**
 * Everything a TagReader found in one file.
 * Invariants: at most one layer per TagFamily; layers sorted by TagFamily order; every layer's version belongs to
 * a family valid for format (MP3: ID3V2, ID3V1; MP4: MP4).
 */
public record TagReadResult(MediaFormat format, List<TagLayer> layers, Optional<StreamInfo> stream,
                            Optional<CoverInfo> cover, List<Issue> issues) {
    public Set<TagVersion> versions();
    public Optional<TagLayer> layer(TagFamily family);
    /** Per field: the value of the first layer (by precedence) that has it. "v2 primary, v1 as fallback." */
    public MediaMetadata effective();
    /**
     * Fields where an ID3v1 value disagrees with the ID3v2 value.
     * LENIENT (default): not a conflict if equal, or v1 equal to Id3v1Text.fit(v2) (pure transliteration/truncation),
     * or the v2 genre has no ID3v1 number. STRICT: every inequality is a conflict.
     * @param mode not null
     */
    public Set<Field> conflicts(V1Comparison mode);
}

/** Settings checkbox "Count transliteration/truncation as v1/v2 difference": unchecked = LENIENT. */
public enum V1Comparison { LENIENT, STRICT }
```

`cover` describes the front cover (type 3). If no front cover exists it describes the first picture of any type,
with `type = OTHER`. This is the "has cover" column of story 001.

### 5.3 Write options and requests (003)

```java
public enum Id3v2Choice { NONE, V2_2, V2_3, V2_4 }
public enum Id3v1Choice { NONE, V1_0, V1_1 }
public enum V2Encoding { AUTO, LATIN1, UTF16, UTF8 }
public enum V1TextMode { ASCII, LATIN1 }

/**
 * User-selected tag write options (MP3/ID3; ignored for formats without versions).
 * removeOtherVersions is the settings checkbox "Remove other tag versions" (default unchecked; CLI
 * --remove-other-versions). Unchecked: a tag family set to NONE keeps its existing tag (OTHER_VERSION_KEPT warning).
 * Checked: it is removed (OTHER_VERSION_REMOVED). A selected version always converts the existing tag of its family.
 * Invariants (problems(...) must be empty):
 *  - UTF8 only with V2_4;  encoding other than AUTO requires id3v2 != NONE
 *  - id3v2 == NONE && id3v1 == NONE only if removeOtherVersions (= explicit "strip all tags")
 */
public record TagWriteOptions(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding v2Encoding,
                              V1TextMode v1TextMode, boolean removeOtherVersions) {
    public static final TagWriteOptions DEFAULT;   // V2_3, V1_1, AUTO, ASCII, false
    /** Pure validation, used by the GUI to grey out combinations; the constructor throws IAE with these messages. */
    public static List<String> problems(Id3v2Choice id3v2, Id3v1Choice id3v1, V2Encoding enc,
                                        V1TextMode mode, boolean removeOtherVersions);
    public Set<TagVersion> selectedVersions();
}

/** Field changes relative to the target slot's current content. set and clear are disjoint. */
public record FieldChanges(Map<Field, FieldValue> set, Set<Field> clear) {
    public static FieldChanges between(MediaMetadata current, MediaMetadata desired, Set<Field> writable);
    public boolean isEmpty();
}

public sealed interface CoverChange {
    record Keep() implements CoverChange {}
    record Remove() implements CoverChange {}          // 009
    record Replace(CoverArt cover) implements CoverChange {}   // 009
}

/**
 * Byte-exact ID3v1 content derived by core (transliteration, truncation, genre number), so every adapter writes
 * exactly what the dry-run showed. Strings fit their byte length in the charset of mode (title/artist/album 30,
 * comment 30 for V1_0 / 28 for V1_1); track 0..255 (0 = none, must be 0 for V1_0); genre 0..255 (255 = none).
 */
public record Id3v1Values(Id3v1Choice version, V1TextMode mode, String title, String artist, String album,
                          OptionalInt year, String comment, int track, int genre) {}

/**
 * Everything a TagWriter needs for one file.
 * Invariants: id3v1 present iff format == MP3 && options.id3v1() != NONE; changes only contain fields that
 * FormatCapabilities.writableFields() of format allows.
 */
public record TagWriteRequest(MediaFormat format, FieldChanges changes, CoverChange cover,
                              TagWriteOptions options, Optional<Id3v1Values> id3v1) {}
```

`Id3v1Projection` (pure, core) turns final metadata into `Id3v1Values` plus warnings (`V1_TRANSLITERATED`,
`V1_TRUNCATED`, `V1_CHAR_DROPPED`, `V1_FIELD_NOT_REPRESENTABLE` for album artist/disc). Truncation prefers a word
boundary. Unmappable characters are dropped (never `?`) and reported. This follows the `audio-metadata` skill.

### 5.4 Issues (all stories)

```java
public enum Severity { INFO, WARNING, ERROR }

/** Stable codes so frontends can render/localise and tests can assert without string matching. */
public enum IssueCode {
    // read / scan
    READ_FAILED, TAG_DAMAGED, READ_VALUE_INVALID, LISTING_ACCESS_DENIED, ROOT_NOT_FOUND, V1_V2_CONFLICT,
    // extract
    NO_MATCH, AMBIGUOUS_MATCH, VALUE_INVALID_FOR_FIELD, POSITION_EXCEEDS_TOTAL,
    // write options / tags
    UNSUPPORTED_WRITE_VERSION, UNSUPPORTED_FIELD, V1_TRANSLITERATED, V1_TRUNCATED, V1_CHAR_DROPPED,
    V1_FIELD_NOT_REPRESENTABLE, V2_NOT_ENCODABLE, FRAME_NOT_REPRESENTABLE, OTHER_VERSION_KEPT, OTHER_VERSION_REMOVED,
    // export
    FALLBACK_USED, NAME_SANITIZED, PATH_TOO_LONG, COLLISION_SUFFIXED, COLLISION_SKIPPED, TARGET_EXISTS,
    TARGET_IS_SOURCE, OUTPUT_INSIDE_SOURCE, INSUFFICIENT_SPACE, FILE_TOO_LARGE_FOR_TARGET_FS,
    SOURCE_CHANGED, WRITE_FAILED, VERIFY_MISMATCH, CANCELLED,
    // undo
    UNDO_FILE_CHANGED, UNDO_FILE_MISSING, UNDO_DIR_NOT_EMPTY
}

/** message: human-readable English, not blank. file absolute if present. */
public record Issue(Severity severity, IssueCode code, String message, Optional<Path> file, Optional<Field> field) {
    public static Issue error(IssueCode code, String message);
    public static Issue warning(IssueCode code, String message);
    public static Issue info(IssueCode code, String message);
    public Issue forFile(Path file);
    public Issue forField(Field field);
}
```

### 5.5 Path ordering

`PathOrder.COMPARATOR` compares paths element by element: first `String.compareToIgnoreCase`, then exact `compareTo`
as a tie-break. It is deterministic on every OS and keeps folders grouped. "Sorted by path" (001) and
"auto-number by filename" (002) both use this comparator.

## 6. Driven ports (SPI implemented by adapters)

Ports live in `core.port`, take and return only core/JDK value types (`Path`, `Instant`, `Duration`), and never expose
library types. Expected I/O failures are reported as the checked `MediaAccessException` (media/file ports) or
`StoreException` (persistence ports). An adapter must not let any library or unchecked exception escape, except the
`Require` exceptions and `OperationCancelledException`.

```java
/** Expected failure of a media/file operation. reason never null; message human-readable; path absolute. */
public final class MediaAccessException extends Exception {
    public enum Reason { NOT_FOUND, NOT_A_DIRECTORY, ACCESS_DENIED, CORRUPT, UNSUPPORTED_FORMAT,
                         ALREADY_EXISTS, NO_SPACE, FILE_TOO_LARGE, IO }
    public MediaAccessException(Reason reason, Path path, String message, Throwable cause /* nullable */);
    public Reason reason();
    public Path path();
}

/** Expected failure of a persistence store (unreadable/invalid file, I/O). */
public final class StoreException extends Exception {
    public enum Reason { INVALID_CONTENT, UNSUPPORTED_SCHEMA_VERSION, ACCESS_DENIED, IO }
    // constructor/accessors analogous
}
```

### 6.1 `MediaFileSource` (001) – full contract

```java
/**
 * Read-only access to the user's source folders: listing candidate files and fingerprinting them.
 * Implemented by music-studio-storage (local disk) and by in-memory fakes.
 *
 * <p><b>Side effects:</b> none. Never creates, modifies, renames or deletes anything; does not change access/modify
 * times beyond what the OS does for directory reads.
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent use.
 */
public interface MediaFileSource {

    /**
     * Lists the regular files under {@code root}.
     *
     * <p><b>Preconditions:</b> root not null (NPE) and absolute (IAE); options not null (NPE);
     * cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code files} contains every regular file directly in {@code root} and, if {@code options.recursive()},
     *       in all subdirectories – absolute, normalised, unique, each starting with {@code root}. All file types are listed;
     *       filtering by format is the caller's job.</li>
     *   <li>Symbolic links to regular files are included (as the link path). Symbolic links to directories are
     *       <em>not</em> followed (no loops, no escaping the tree).</li>
     *   <li>If {@code !options.includeHidden()}: entries whose name starts with "." (and, on Windows, entries with the
     *       hidden attribute) are not listed and not descended into; each one is recorded once in {@code skipped}
     *       with reason HIDDEN (a hidden directory as one entry, its content is not enumerated). This applies to every
     *       entry found in a listed directory, also in a non-recursive listing.</li>
     *   <li>Symbolic links to directories are recorded in {@code skipped} with reason DIRECTORY_SYMLINK; excluded
     *       directories with reason EXCLUDED (only when the listing is recursive, because only then would they be entered).</li>
     *   <li>Directories in {@code options.excludedDirectories()} (and their content) are skipped.</li>
     *   <li>A subdirectory that cannot be read is reported once as {@code LISTING_ACCESS_DENIED} (WARNING) in
     *       {@code issues}; listing continues.</li>
     *   <li>The order of {@code files} is unspecified (callers sort with PathOrder).</li>
     * </ul>
     * <p><b>Cancellation:</b> checked at least once per directory; throws OperationCancelledException.
     *
     * @param root    absolute path of the directory to list; not null
     * @param options recursion/hidden/exclusion options; not null
     * @param cancel  cooperative cancellation; not null (use Cancellation.NONE)
     * @return listing, never null
     * @throws MediaAccessException NOT_FOUND if root does not exist; NOT_A_DIRECTORY; ACCESS_DENIED if root itself is
     *         unreadable; IO for other failures of root
     * @throws OperationCancelledException if cancelled
     */
    DirectoryListing list(Path root, ListingOptions options, Cancellation cancel) throws MediaAccessException;

    /**
     * Returns size and last-modified time of a file, following symlinks.
     *
     * <p><b>Preconditions:</b> file not null (NPE), absolute (IAE).
     * <p><b>Postconditions:</b> sizeBytes equals the current file size; lastModified has the precision the
     * filesystem provides (callers compare fingerprints only from the same source).
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, IO
     */
    FileFingerprint fingerprint(Path file) throws MediaAccessException;
}

/** excludedDirectories: absolute paths, no nulls. Defaults: recursive=false, includeHidden=false, none excluded. */
public record ListingOptions(boolean recursive, boolean includeHidden, Set<Path> excludedDirectories) {}
/** files, skipped: absolute, no nulls, unique, disjoint; issues: no nulls. */
public record DirectoryListing(Path root, List<Path> files, List<SkippedEntry> skipped, List<Issue> issues) {}

// In core.model, not core.port: ScanSummary exposes them to the frontends, which may not use core.port (rule A9).
/** A file or directory deliberately not scanned. path absolute. */
public record SkippedEntry(Path path, SkipReason reason) {}
public enum SkipReason { HIDDEN, DIRECTORY_SYMLINK, EXCLUDED }
```

### 6.2 `TagReader` (001) – full contract

```java
/**
 * Reads the tags and stream properties of media files. Implemented per library (jaudiotagger now, mp3agic possible)
 * and per format family (MP3; MP4 in 012).
 *
 * <p><b>Side effects:</b> none. Files are opened read-only and closed before return; content and last-modified time
 * stay byte-for-byte unchanged (verified by checksum in the contract test).
 * <p><b>Thread-safety:</b> implementations must be safe for concurrent calls on different files.
 */
public interface TagReader {

    /**
     * @return the formats this reader can read; non-empty, unmodifiable, constant for the instance's lifetime
     */
    Set<MediaFormat> readableFormats();

    /**
     * Reads all tag layers, stream info and a description of the embedded cover of one file.
     *
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>file not null (NPE), absolute (IAE)</li>
     *   <li>{@code MediaFormat.ofFileName(file)} is present and contained in {@link #readableFormats()} (IAE)</li>
     * </ul>
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code result.format()} equals the file's format.</li>
     *   <li>One {@link TagLayer} per tag slot present in the file, with the exact version found
     *       (e.g. ID3v1.0 vs ID3v1.1 decided by the track byte; ID3v2.2 with 3-character frame IDs is supported).
     *       A file without tags yields an empty layer list, never an exception.</li>
     *   <li>Text values are decoded from their declared encoding (Latin-1, UTF-16 with/without BOM, UTF-16BE, UTF-8),
     *       stripped of padding, trailing NULs and surrounding whitespace; empty values are absent.</li>
     *   <li>Track/disc "3/12", "03", "3" become {@code Position}; ID3v1 track 0 is absent.</li>
     *   <li>Genre references "(17)", "17", "(17)Rock" and ID3v1 genre bytes are resolved to text via
     *       {@code Id3v1Genres}; 255 / unknown numbers are absent. Multiple v2.4 genres (NUL-separated) become
     *       several {@code Genre}s in order.</li>
     *   <li>A value that cannot be mapped to its field type (e.g. TRCK "A1") is omitted from the metadata and
     *       reported as {@code READ_VALUE_INVALID} (WARNING, with field and raw value in the message).</li>
     *   <li>A damaged tag in an otherwise readable file: the readable layers are returned, the damage is reported as
     *       {@code TAG_DAMAGED} (ERROR). Exception {@code CORRUPT} only when nothing usable can be read.</li>
     *   <li>{@code stream()} is present if the audio/video headers can be parsed.</li>
     *   <li>{@code cover()} describes the front cover (or the first picture, with type OTHER); bytes are not loaded.</li>
     *   <li>Fields the format has no slot for are simply absent.</li>
     * </ul>
     * <p><b>On failure:</b> the file is unchanged.
     *
     * @param file absolute path of the file to read; not null
     * @return the read result; never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT (not a parsable file of this format, incl.
     *         0-byte files), IO
     */
    TagReadResult read(Path file) throws MediaAccessException;

    /**
     * (009/011) Loads the bytes of the picture described by {@code read(file).cover()}.
     * Preconditions as {@link #read}. Postcondition: empty iff the file has no picture. Same exceptions.
     */
    Optional<CoverArt> readCover(Path file) throws MediaAccessException;
}
```

### 6.3 `TagWriter` (003) – full contract

```java
/**
 * Produces a tagged copy of a media file. There is deliberately no in-place write: the source is only ever read,
 * the result is written to a new path chosen by the caller (a temp file in the export folder).
 * This shape is native to both jaudiotagger (copy, then write the copy) and mp3agic (Mp3File.save(newPath)).
 *
 * <p><b>Thread-safety:</b> implementations must be safe to call from several threads; they may serialise internally
 * (jaudiotagger does, because of its global TagOptionSingleton). Calls may block.
 */
public interface TagWriter {

    /** @return formats this writer can write; non-empty, unmodifiable, constant. */
    Set<MediaFormat> writableFormats();

    /**
     * Declares what this writer can produce for a format (see §9).
     * <p><b>Preconditions:</b> format not null (NPE), in writableFormats() (IAE).
     * <p><b>Postconditions:</b> constant for the instance's lifetime; never null.
     */
    FormatCapabilities capabilities(MediaFormat format);

    /**
     * Dry-run of {@link #writeCopy}: reports, without writing anything, what the write would lose or change beyond
     * {@code request.changes()} – frames with no equivalent in the target version, tags that will be removed
     * because of removeOtherVersions, tags of other families that will be kept stale.
     *
     * <p><b>Preconditions:</b> source not null, absolute, a readable file of {@code request.format()};
     * request not null; {@code capabilities(request.format()).unsupported(request.options())} is empty (IAE).
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Every frame/atom that writeCopy will not carry over is listed as {@code FRAME_NOT_REPRESENTABLE}
     *       (WARNING) – the list is complete: writeCopy drops nothing that preflight did not report.</li>
     *   <li>{@code OTHER_VERSION_REMOVED} / {@code OTHER_VERSION_KEPT} for tag slots affected by the options.</li>
     *   <li>No file is created or modified.</li>
     * </ul>
     * @return issues (WARNING/INFO only; fatal problems are exceptions); never null
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT, IO
     */
    List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException;

    /**
     * Writes a copy of {@code source} to {@code target} with the requested tag state.
     *
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>source not null, absolute, existing regular file of request.format() (NPE/IAE)</li>
     *   <li>target not null, absolute (NPE/IAE); target does not exist; its parent directory exists;
     *       target is not the same file as source (IAE)</li>
     *   <li>request not null; options supported per capabilities (IAE)</li>
     *   <li>cancel, progress not null (NPE)</li>
     * </ul>
     * <p><b>Postconditions/Guarantees (normal return):</b>
     * <ul>
     *   <li>source is byte-for-byte unchanged, including its last-modified time.</li>
     *   <li>target exists; its audio/video payload is identical to source's.</li>
     *   <li>One slot per family: for every family with a selected version, target has exactly that version.
     *       An existing tag of the same family in another version is converted (frames mapped; frames without an
     *       equivalent dropped exactly as reported by preflight). For a family set to NONE: the source's tag of that
     *       family is copied unchanged if !removeOtherVersions, and absent if removeOtherVersions.</li>
     *   <li>ID3v2/MP4 slot: fields in {@code changes.set()} have the given values, fields in {@code changes.clear()}
     *       are absent, every other frame/atom (unknown frames, TXXX incl. ReplayGain, PRIV, lyrics, pictures unless
     *       {@code cover} says otherwise) is preserved.</li>
     *   <li>Text encoding: as options.v2Encoding(); AUTO = Latin-1 if every character fits, otherwise UTF-16 (v2.2/
     *       v2.3) or UTF-8 (v2.4).</li>
     *   <li>ID3v1 slot (if selected): exactly {@code request.id3v1()} – no further transformation.</li>
     *   <li>Multi-value genres: joined with "; " for v2.2/v2.3, NUL-separated for v2.4 (the profile restricts to one
     *       genre before this point if needed).</li>
     *   <li>Before returning, {@code progress} has received at least one final call (total, total).</li>
     * </ul>
     * <p><b>On failure or cancellation:</b> source unchanged; target may exist in an unspecified state – the caller
     * deletes it (it is a temp path).
     * <p><b>Cancellation:</b> checked before starting and at least every 8 MiB of copied payload.
     *
     * @param source   absolute path of the original; read only; not null
     * @param target   absolute path of the new file; must not exist; not null
     * @param request  what to write; not null
     * @param cancel   cooperative cancellation; not null
     * @param progress byte progress of this file; not null (ByteProgress.NONE)
     * @throws MediaAccessException NOT_FOUND, ACCESS_DENIED, CORRUPT, NO_SPACE, FILE_TOO_LARGE, IO
     * @throws OperationCancelledException if cancelled
     */
    void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException;
}
```

**mp3agic sanity check:** `Mp3File(source)` reads, `setId3v1Tag`/`setId3v2Tag` + `save(target)` writes to a new
path. That is exactly `writeCopy`, and mp3agic cannot even save in place. Custom frames are reachable via
`getFrameSets()` (preflight). Capabilities: v1.1, v2.3, v2.4, but no v2.2 writing, so it declares
`writableVersions = {ID3V1_0, ID3V1_1, ID3V2_3, ID3V2_4}` and core greys out v2.2. Nothing in the port depends on
jaudiotagger's `Tag`/`FieldKey` model.

### 6.4 `ExportTarget` (004) – full contract

> **Status (story 003):** only a four-method subset exists so far: `exists`, `reserveTempPath`, `moveNoReplace` and
> `deleteTemp` (`LocalExportTarget` in `music-studio-storage`). Temp names are `.<stem>.<random>.musicstudio-tmp<.ext>`
> (`ExportTarget.TEMP_MARKER`, extension kept so a reader can verify the temp file); `deleteTemp` accepts only such names.
> The contract below is the target state; story 004 adds the rest.

```java
/**
 * Write side of the local filesystem, restricted to what a safe export needs. It cannot modify or delete arbitrary
 * files: deletes are limited to its own temp files and to fingerprint-verified files during undo.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use on different paths.
 */
public interface ExportTarget {

    /**
     * Describes the filesystem that would hold {@code directory} (the nearest existing ancestor is used if it does not
     * exist yet).
     * <p><b>Preconditions:</b> directory not null, absolute.
     * <p><b>Postconditions:</b> usableBytes >= 0; type is the OS store type ("ext4", "vfat", "exfat", "ntfs", …);
     * maxFileSize present for FAT32 (4 GiB − 1); caseInsensitive true if the store is known to fold case.
     * @throws MediaAccessException ACCESS_DENIED, IO
     */
    StoreInfo storeInfo(Path directory) throws MediaAccessException;

    /**
     * Finds an existing directory entry that a new file at {@code target} would collide with: the same name, or a name
     * equal ignoring case and Unicode normalisation (NFC), in target's parent directory.
     * <p><b>Preconditions:</b> target not null, absolute.
     * <p><b>Postconditions:</b> empty if the parent does not exist or nothing collides; never modifies anything.
     * @throws MediaAccessException ACCESS_DENIED, IO
     */
    Optional<Path> findCollision(Path target) throws MediaAccessException;

    /**
     * Creates {@code directory} and missing parents.
     * <p><b>Preconditions:</b> directory not null, absolute.
     * <p><b>Postconditions:</b> directory exists; result lists exactly the directories this call created,
     * outermost first (empty if it already existed).
     * @throws MediaAccessException ALREADY_EXISTS (a non-directory is in the way), ACCESS_DENIED, NO_SPACE, IO
     */
    List<Path> createDirectories(Path directory) throws MediaAccessException;

    /**
     * Returns a unique, not-yet-existing temp path in the same directory as {@code finalTarget}, named
     * {@code ".<stem>.<random>.musicstudio-tmp<.ext>"}. Same directory ⇒ same filesystem ⇒ the later move is a rename.
     * <p><b>Preconditions:</b> finalTarget not null, absolute; parent exists.
     * <p><b>Postconditions:</b> the returned path does not exist; nothing is created.
     */
    Path reserveTempPath(Path finalTarget) throws MediaAccessException;

    /**
     * Moves {@code temp} to {@code target} <b>without ever replacing</b> an existing file.
     * Implementation strategy: hard link target→temp (atomic, fails if target exists), then delete temp; where hard
     * links are unsupported (FAT/exFAT): check absence, then rename without REPLACE_EXISTING.
     * <p><b>Preconditions:</b> temp is a path returned by reserveTempPath and exists; target absolute; same parent.
     * <p><b>Postconditions:</b> target has temp's content and last-modified time; temp no longer exists.
     * <p><b>On failure:</b> an existing target is untouched; temp still exists.
     * @throws MediaAccessException ALREADY_EXISTS, ACCESS_DENIED, IO
     */
    void moveNoReplace(Path temp, Path target) throws MediaAccessException;

    /**
     * Moves an existing export target out of the way before an explicit overwrite (no-replace move into
     * {@code backup}, creating backup's parents).
     * <p><b>Preconditions:</b> existing exists; backup absolute and does not exist.
     * @throws MediaAccessException ALREADY_EXISTS, ACCESS_DENIED, IO
     */
    void moveToBackup(Path existing, Path backup) throws MediaAccessException;

    /** Fingerprint of a file in the export area. Same contract as MediaFileSource.fingerprint. */
    FileFingerprint fingerprint(Path file) throws MediaAccessException;

    /**
     * Deletes a temp file. <b>Precondition:</b> the file name matches the reserveTempPath naming scheme (IAE otherwise) –
     * this method cannot be used to delete user files. Postcondition: the path does not exist. Missing file is not an error.
     */
    void deleteTemp(Path temp) throws MediaAccessException;

    /**
     * Undo support: deletes {@code file} only if its current fingerprint equals {@code expected}.
     * @return DELETED, MISSING (nothing to do) or CHANGED (file kept)
     */
    DeleteOutcome deleteIfUnchanged(Path file, FileFingerprint expected) throws MediaAccessException;

    /** Undo support: deletes {@code directory} only if it is empty. @return true if deleted. */
    boolean deleteDirectoryIfEmpty(Path directory) throws MediaAccessException;

    /** (009/012) Plain copy of a companion file (cover.jpg, .srt) to a not-existing target, with progress/cancel. */
    void copy(Path source, Path target, Cancellation cancel, ByteProgress progress) throws MediaAccessException;

    enum DeleteOutcome { DELETED, MISSING, CHANGED }
}

public record StoreInfo(String type, long usableBytes, OptionalLong maxFileSize, boolean caseInsensitive) {}
```

### 6.5 `ExportJournalStore` (004) – full contract

```java
/**
 * Durable, append-only journal per export (the undo log) plus the final export report.
 * Stored in the application data directory (§13), keyed by ExportId.
 * <p><b>Thread-safety:</b> safe for concurrent use with different ExportIds; one ExportJournal is confined to one thread.
 */
public interface ExportJournalStore {

    /**
     * Creates a new journal and writes the header durably.
     * <p><b>Preconditions:</b> header not null; no journal with header.id() exists (IAE).
     * <p><b>Postconditions:</b> load(header.id()) returns the header with no entries.
     * @throws StoreException ACCESS_DENIED, IO
     */
    ExportJournal create(JournalHeader header) throws StoreException;

    /** @return the journal incl. all entries durably appended so far (also after a crash); empty if unknown. */
    Optional<JournalContent> load(ExportId id) throws StoreException;

    /** @return headers of all journals, newest first. Invalid files are skipped (not an exception). */
    List<JournalHeader> list() throws StoreException;

    /** Stores the final report next to the journal (human-readable JSON). Overwrites an earlier report of the same id. */
    void saveReport(ExportReport report) throws StoreException;
}

/** Open journal. append is durable on return (flushed and fsync'ed). */
public interface ExportJournal extends AutoCloseable {
    void append(JournalEntry entry) throws StoreException;   // Require: not closed (ISE)
    @Override void close() throws StoreException;            // idempotent
}

public record JournalHeader(ExportId id, Instant startedAt, Path outputRoot, int plannedFiles) {}
public sealed interface JournalEntry {
    record DirectoryCreated(Path directory) implements JournalEntry {}
    record FileCreated(Path target, FileFingerprint fingerprint) implements JournalEntry {}
    record FileBackedUp(Path original, Path backup) implements JournalEntry {}
    record Finished(ExportStatus status, Instant at) implements JournalEntry {}
    record Undone(Instant at) implements JournalEntry {}
}
public record JournalContent(JournalHeader header, List<JournalEntry> entries) {}
```

### 6.6 Ports for later stories (signature + short contract)

```java
// 006 – user profiles (built-ins are core data: BuiltInProfiles). Invalid files → issues, built-ins still work.
public interface ProfileStore {
    LoadResult<DeviceProfile> loadAll() throws StoreException;       // valid profiles + issues for invalid ones
    void save(DeviceProfile profile) throws StoreException;          // Require: !profile.builtIn(); atomic file replace
    void delete(ProfileId id) throws StoreException;                 // missing = no-op
}
public record LoadResult<T>(List<T> items, List<Issue> issues) {}

// 007 – presets and recent patterns; schemaVersion in file, migrated on load.
public interface PresetStore {
    LoadResult<JobPreset> loadAll() throws StoreException;
    void save(JobPreset preset) throws StoreException;
    void delete(String name) throws StoreException;
    List<String> recentPatterns(int max) throws StoreException;      // newest first, unique
    void rememberPattern(String pattern) throws StoreException;
}

// 008 – user genre vocabulary (merged with the built-in ID3v1/Winamp + regional list by core).
public interface GenreStore {
    GenreVocabulary load() throws StoreException;                    // empty vocabulary if no file yet
    void save(GenreVocabulary vocabulary) throws StoreException;
}

// 009 – image conversion; pure function of its input, no file access.
public interface ImageTranscoder {
    ImageInfo probe(byte[] image) throws ImageException;             // format, width, height, progressive
    CoverArt toDeviceJpeg(byte[] image, CoverPolicy policy) throws ImageException; // baseline JPEG within policy limits
}
// plus on MediaFileSource (009): List<Path> listImages(Path directory); byte[] readBytes(Path file, int maxBytes)

// 010 – persisted user settings (checkboxes). CLI flags override them for one run; built-in defaults if no file.
public interface SettingsStore {
    AppSettings load() throws StoreException;          // defaults if the file is missing; INVALID_CONTENT if broken
    void save(AppSettings settings) throws StoreException;   // atomic replace
}
/** removeOtherVersions default false; v1Comparison default LENIENT. Grows with later settings (schemaVersion). */
public record AppSettings(boolean removeOtherVersions, V1Comparison v1Comparison) {
    public static final AppSettings DEFAULTS;
}

// 011 – library index persistence (whole snapshot; 50 000 tracks ≈ 15 MB JSON lines).
public interface LibraryIndexStore {
    LibraryIndex load() throws StoreException;                       // empty index if none
    void save(LibraryIndex index) throws StoreException;             // atomic replace (temp + move)
}

// 011 – playback. Asynchronous; events delivered on an implementation thread (GUI marshals to its UI thread).
public interface PlaybackEngine extends AutoCloseable {
    Set<MediaFormat> playableFormats();
    void open(Path file) throws MediaAccessException;   // stops current; state → READY or exception
    void play(); void pause(); void stop();             // Require.state(opened)
    void seek(Duration position);                       // 0 <= position <= duration
    void setVolume(double volume);                      // 0.0..1.0
    PlaybackStatus status();                            // state, position, duration
    void setListener(PlaybackListener listener);        // single listener; PlaybackListener.NONE to clear
    @Override void close();
}
```

## 7. Use cases (API called by frontends)

All use cases are interfaces in their feature package. Each is implemented by a `final` `*Service` class that takes
its ports and collaborators through the constructor. The services are stateless (thread-safe), synchronous and
blocking. They never print. They return result records. They throw only the `Require` exceptions (contract violations)
and `OperationCancelledException`, and only where documented. Per-file problems become `Issue`s in the result.

### 7.1 `ScanMediaUseCase` (001) – full contract

```java
/**
 * Lists the media files under a folder and reads their tags, streaming one result per file in path order.
 * Read-only: no file is created or modified.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; call it from a worker thread (it blocks on I/O).
 */
public interface ScanMediaUseCase {

    /**
     * <p><b>Preconditions:</b> request, sink, progress, cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>{@code sink} receives exactly one {@link ScanItem} per file whose format is in {@code request.formats()},
     *       in {@code PathOrder} order, on the calling thread, before scan returns. Files with tags, without tags
     *       (empty layers) and unreadable files ({@link UnreadableFile} with READ_FAILED) are all delivered –
     *       none is skipped.</li>
     *   <li>Memory: results are not retained; only the path list is held (streaming, 10 000+ files).</li>
     *   <li>Files with other extensions are not read; they are counted in {@code ignoredFiles} and
     *       {@code ignoredByExtension}.</li>
     *   <li>Hidden files/directories (unless includeHidden), directory symlinks and excluded directories are listed
     *       in {@code skipped}, sorted by PathOrder, so the user sees what was not scanned.</li>
     *   <li>{@link ScannedFile#conflicts()} = {@code tags.conflicts(request.v1Comparison())}.</li>
     *   <li>{@code status} = COMPLETED, CANCELLED (sink received a prefix of the order) or FAILED (root missing/unreadable;
     *       issue ROOT_NOT_FOUND / LISTING_ACCESS_DENIED; sink received nothing).</li>
     *   <li>Counters: found == read + failed (if COMPLETED).</li>
     *   <li>{@code progress} receives phase "list" then "read" events with done/total files.</li>
     * </ul>
     * <p><b>On sink failure:</b> an exception thrown by sink aborts the scan and propagates unchanged.
     *
     * @param request what to scan; not null
     * @param sink    receives results in order; not null; must not block for long
     * @param progress progress callback; not null (ProgressListener.NONE)
     * @param cancel  cooperative cancellation, checked between files; not null
     * @return summary; never null
     */
    ScanSummary scan(ScanRequest request, Consumer<? super ScanItem> sink, ProgressListener progress,
                     Cancellation cancel);
}

/** root absolute; formats non-empty. Defaults: formats {MP3}, includeHidden false, v1Comparison LENIENT. */
public record ScanRequest(Path root, boolean recursive, boolean includeHidden, Set<MediaFormat> formats,
                          Set<Path> excludedDirectories, V1Comparison v1Comparison) {}

public sealed interface ScanItem permits ScannedFile, UnreadableFile { Path path(); }
/** conflicts ⊆ fields present in both an ID3v1 and the ID3v2 layer. */
public record ScannedFile(Path path, MediaFormat format, FileFingerprint fingerprint, TagReadResult tags,
                          Set<Field> conflicts) implements ScanItem {}
public record UnreadableFile(Path path, MediaFormat format, Issue error) implements ScanItem {}

public enum ScanStatus { COMPLETED, CANCELLED, FAILED }
public record ScanSummary(Path root, ScanStatus status, int found, int read, int failed, int ignoredFiles,
                          Map<String, Integer> ignoredByExtension, List<SkippedEntry> skipped, List<Issue> issues) {}
```

### 7.2 `ProposeMetadataUseCase` (002; extended by 005, 008, 009) – full contract

> **Status (story 002):** implemented as below, with these details: `FieldChange` is `ProposedChange`; a NO_MATCH file gets
> nothing (no fixed value, number or edit); `FieldValue.Position` allows 0; matching follows ADR 0012 (NFC, code points,
> ASCII digits, shortest-first, ambiguity = distinct capture boundaries). The parser handles input **and** output patterns
> (the renderer, `FileNameSanitizer` and `NamingRules` come with story 004). The CLI `extract` command is a preview with no
> write option.

```java
/**
 * Computes the proposed final metadata for a batch: pattern extraction, fixed values, keep-existing, merge mode,
 * auto-numbering and manual edits (later also cleanup 005 and genre normalisation 008). Pure: no I/O, fast enough to
 * run on every keystroke of the GUI pattern field (5 000 files).
 *
 * <p><b>Thread-safety:</b> stateless; safe for concurrent use.
 */
public interface ProposeMetadataUseCase {

    /**
     * <p><b>Preconditions:</b> request not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>One {@link Proposal} per input file, in input order.</li>
     *   <li>Per field, the value is derived in this order (later wins): existing effective value → pattern value
     *       (subject to merge mode) → fixed value (subject to merge mode) → auto-number → manual edit (always wins).
     *       KeepExisting pins a field to its existing value and ignores pattern/fixed for that field.</li>
     *   <li>MergeMode.FILL_EMPTY: a pattern/fixed value is only used if the existing value is absent.</li>
     *   <li>status: NO_PATTERN (no pattern given), MATCHED, AMBIGUOUS (several splits; the non-greedy left-to-right
     *       split is chosen and shown), AMBIGUOUS_ACCEPTED (spec.acceptAmbiguous or a manual accept), NO_MATCH
     *       (proposed == existing; matchDetail says why). Only NO_PATTERN, MATCHED and AMBIGUOUS_ACCEPTED are
     *       exportable.</li>
     *   <li>{@code changes} lists exactly the fields where proposed differs from existing, with origin.</li>
     *   <li>Typed placeholder values that cannot be parsed never reach proposed (the match fails instead).</li>
     *   <li>Auto-numbering (if configured) numbers exportable files per parent folder in the configured order,
     *       starting at 1; total = count in that folder if setTotal.</li>
     * </ul>
     */
    ProposalSet propose(ProposeRequest request);
}

public record ProposeRequest(List<ScannedFile> files, ExtractionSpec spec, ManualEdits edits) {}

/** overrides: no nulls. A Fixed value's type must match its field. */
public record ExtractionSpec(Optional<InputPattern> pattern, Map<Field, FieldOverride> overrides, MergeMode mergeMode,
                             Optional<AutoNumbering> autoNumbering, boolean acceptAmbiguous) {}
public sealed interface FieldOverride {
    record Fixed(FieldValue value) implements FieldOverride {}
    record KeepExisting() implements FieldOverride {}
}
public enum MergeMode { OVERWRITE, FILL_EMPTY }              // default OVERWRITE
public record AutoNumbering(NumberingOrder order, boolean setTotal) {
    public enum NumberingOrder { FILE_NAME, EXISTING_TRACK }
}
/** Per-file manual edits (GUI cells) and manual acceptance of ambiguous matches. */
public record ManualEdits(Map<Path, Map<Field, FieldEdit>> edits, Set<Path> acceptedAmbiguous) {
    public static final ManualEdits NONE;
}
public sealed interface FieldEdit { record Set(FieldValue value) implements FieldEdit {} record Clear() implements FieldEdit {} }

public enum MatchStatus { NO_PATTERN, MATCHED, AMBIGUOUS, AMBIGUOUS_ACCEPTED, NO_MATCH }
public enum ChangeOrigin { PATTERN, FIXED, AUTO_NUMBER, MANUAL }   // 005: CLEANUP, 008: GENRE_MAP
public record ProposedChange(Field field, Optional<FieldValue> before, Optional<FieldValue> after, ChangeOrigin origin) {}
// (named ProposedChange because tag.FieldChange from story 003 already exists)

public record Proposal(ScannedFile file, MatchStatus status, Optional<String> matchDetail,
                       Map<Field, String> extracted, MediaMetadata proposed, List<ProposedChange> changes,
                       List<Issue> issues) {
    public MediaMetadata existing();   // = file.tags().effective()
    public boolean exportable();
}
public record ProposalSet(List<Proposal> proposals, int matched, int ambiguous, int noMatch) {}
```

### 7.3 `WriteOptionsUseCase` (003) – full contract

```java
/** Answers what can be written, so frontends can grey out options before planning. Pure, thread-safe. */
public interface WriteOptionsUseCase {
    /** @return capabilities of the active writer for format. Pre: format writable (IAE). */
    FormatCapabilities capabilities(MediaFormat format);
    /** @return formats the active writer supports; non-empty. */
    Set<MediaFormat> writableFormats();
    /**
     * Validates options against the writer for every format in {@code formats}.
     * Pre: not null; formats non-empty. Post: empty list ⇔ a plan with these options will not fail with
     * UNSUPPORTED_WRITE_VERSION/UNSUPPORTED_FIELD.
     */
    List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats);
}
```

### 7.4 `PlanExportUseCase` (003 + 004) – full contract

```java
/**
 * Builds the export plan – the dry-run. Computes per file the target path, the tag diff, all warnings and
 * collisions, and checks free space. Reads source files (preflight) but never writes anything.
 *
 * <p><b>Thread-safety:</b> stateless; safe for concurrent use. Blocking; call from a worker thread.
 */
public interface PlanExportUseCase {

    /**
     * <p><b>Preconditions:</b> request, progress, cancel not null (NPE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>One {@link PlannedFile} per proposal, in proposal order.</li>
     *   <li>Non-exportable proposals → action SKIP with NO_MATCH/AMBIGUOUS_MATCH.</li>
     *   <li>Unsupported write options for a format present in the batch → global ERROR UNSUPPORTED_WRITE_VERSION;
     *       plan not executable. Checked before any per-file work.</li>
     *   <li>Target = outputRoot / render(outputPattern, proposed) sanitised per NamingRules + source extension
     *       (lower-case). Missing values use spec.fallbacks() → FALLBACK_USED (WARNING).</li>
     *   <li>No target equals (ignoring case/NFC) any source path → otherwise ERROR TARGET_IS_SOURCE, SKIP –
     *       regardless of collision policy.</li>
     *   <li>Collisions are resolved in proposal order, case-insensitively, within the batch and against existing
     *       files: SKIP → COLLISION_SKIPPED; SUFFIX → " (2)", " (3)" … → COLLISION_SUFFIXED; OVERWRITE → only for
     *       existing files outside the batch, {@code replaces} set (backup on apply); two batch files never overwrite
     *       each other (the later one is SKIPPED).</li>
     *   <li>writeRequest = FieldChanges between the source's ID3v2/MP4 slot and proposed (restricted to writable
     *       fields), Id3v1Values from Id3v1Projection, CoverChange.Keep (until 009). {@code changes} is the user-facing
     *       diff against effective existing values.</li>
     *   <li>Item issues include Id3v1Projection warnings, TagWriter.preflight issues, V2_NOT_ENCODABLE (ERROR, SKIP)
     *       when a forced Latin-1 encoding cannot hold a value, PATH_TOO_LONG (ERROR, SKIP) if still too long after
     *       shortening, FILE_TOO_LARGE_FOR_TARGET_FS.</li>
     *   <li>Output root inside a source root → OUTPUT_INSIDE_SOURCE (WARNING).</li>
     *   <li>SpaceEstimate: required = Σ size of WRITE sources + 64 KiB per file; if required + 50 MiB > usable →
     *       global ERROR INSUFFICIENT_SPACE.</li>
     *   <li>{@code executable()} ⇔ no global ERROR and at least one WRITE item.</li>
     *   <li>No file or directory is created, modified or deleted.</li>
     * </ul>
     * @throws OperationCancelledException if cancelled (no partial plan is returned)
     */
    ExportPlan plan(PlanExportRequest request, ProgressListener progress, Cancellation cancel);
}

/** sourceRoots: absolute, non-empty (used for TARGET_IS_SOURCE / OUTPUT_INSIDE_SOURCE). */
public record PlanExportRequest(List<Proposal> proposals, Set<Path> sourceRoots, ExportSpec spec,
                                TagWriteOptions options) {}
/** outputRoot absolute; fallbacks: placeholder field → non-blank text. */
public record ExportSpec(Path outputRoot, OutputPattern pattern, NamingRules naming, CollisionPolicy collisions,
                         Map<Field, String> fallbacks) {}
public enum CollisionPolicy { SKIP, SUFFIX, OVERWRITE }   // CLI default SUFFIX; OVERWRITE only via --overwrite
/** maxSegmentLength 1..255, maxPathLength 32..4096 (whole target path), replacement must be a safe char. */
public record NamingRules(boolean asciiOnly, int maxSegmentLength, int maxPathLength, char replacement) {
    public static final NamingRules DEFAULT;   // false, 120, 240, '_' – always FAT32/Windows-safe characters
}

public enum PlannedAction { WRITE, SKIP }
public record PlannedFile(Path source, FileFingerprint sourceFingerprint, MediaFormat format, PlannedAction action,
                          Optional<Path> target, Optional<Path> replaces, List<FieldChange> changes,
                          Optional<TagWriteRequest> writeRequest, MediaMetadata expected, List<Issue> issues) {}
public record SpaceEstimate(long requiredBytes, long usableBytes, String storeType) {}
public record ExportPlan(Instant createdAt, ExportSpec spec, TagWriteOptions options, List<PlannedFile> files,
                         List<Issue> issues, SpaceEstimate space) {
    public boolean executable();
    public int writeCount(); public int skipCount();
}
```

### 7.5 `ApplyExportUseCase` (003 + 004) – full contract

```java
/**
 * Executes an export plan: writes verified, tagged copies and journals every step for undo.
 * The only use case in the system that writes media files.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use with different output roots; a second concurrent apply into the
 * same output root is rejected (IllegalStateException).
 */
public interface ApplyExportUseCase {

    /**
     * <p><b>Preconditions:</b> plan, progress, cancel not null (NPE); plan.executable() (IAE);
     * no other apply running for plan.spec().outputRoot() (ISE).
     * <p><b>Postconditions/Guarantees:</b>
     * <ul>
     *   <li>Source files are never opened for writing, moved or deleted.</li>
     *   <li>A journal is created before the first write. If that fails → status ABORTED, nothing written.</li>
     *   <li>Per WRITE item, in plan order: (1) re-fingerprint source, mismatch → SOURCE_CHANGED, item failed;
     *       (2) create target dirs (journaled); (3) temp = reserveTempPath; writeCopy(source, temp);
     *       (4) re-read temp with TagReader and compare with plan expectations (WriteVerifier) → VERIFY_MISMATCH
     *       on difference; (5) if {@code replaces}: journal + moveToBackup; (6) journal FileCreated(target, fingerprint
     *       of temp); (7) moveNoReplace(temp, target). A failing item never stops the run; its temp is always deleted.</li>
     *   <li>No existing file is ever replaced; with OVERWRITE the old file is moved to
     *       {@code <outputRoot>/.musicstudio-backup/<exportId>/<relative path>} first.</li>
     *   <li>Cancellation: checked between files and during copies; the current temp is deleted; files already
     *       moved stay and are undoable; status CANCELLED.</li>
     *   <li>Journal ends with Finished(status); report saved via ExportJournalStore.saveReport.</li>
     *   <li>Status COMPLETED (all WRITE items written), COMPLETED_WITH_ERRORS, CANCELLED, ABORTED.</li>
     *   <li>written + skipped + failed == plan.files().size().</li>
     * </ul>
     */
    ExportReport apply(ExportPlan plan, ProgressListener progress, Cancellation cancel);
}

/** value matches \d{8}T\d{6}Z-[0-9a-f]{6} (UTC start time + random). */
public record ExportId(String value) {}
public enum ExportStatus { COMPLETED, COMPLETED_WITH_ERRORS, CANCELLED, ABORTED }
public record ExportedFile(Path source, Path target, List<Issue> issues) {}
public record SkippedFile(Path source, List<Issue> reasons) {}
public record FailedFile(Path source, Optional<Path> target, Issue error) {}
public record ExportReport(ExportId id, ExportStatus status, Path outputRoot, List<ExportedFile> written,
                           List<SkippedFile> skipped, List<FailedFile> failed, List<Issue> issues, Duration elapsed) {}
```

### 7.6 `UndoExportUseCase` (004) – full contract

```java
/** Reverts an export: deletes exactly the files it created (if unchanged), restores backups, removes empty dirs it created. */
public interface UndoExportUseCase {

    /** @return summaries of all journaled exports, newest first. Never throws for unreadable journals (skipped). */
    List<ExportSummary> listExports();

    /**
     * Dry-run of an undo.
     * <p><b>Preconditions:</b> id not null; journal exists (IAE).
     * <p><b>Postconditions:</b> steps in reverse journal order: DELETE_FILE (fingerprint matches), KEEP_CHANGED
     * (UNDO_FILE_CHANGED), ALREADY_GONE (UNDO_FILE_MISSING), RESTORE_BACKUP, REMOVE_DIR_IF_EMPTY. Nothing is modified.
     * If the journal already contains Undone → empty steps + INFO issue.
     */
    UndoPlan planUndo(ExportId id);

    /**
     * Executes an UndoPlan. Pre: plan not null; created by planUndo. Post: files deleted only via
     * ExportTarget.deleteIfUnchanged (re-checked at execution), backups restored with moveNoReplace (never
     * overwriting), directories removed only if empty; Undone appended to the journal. Idempotent.
     */
    UndoReport applyUndo(UndoPlan plan, ProgressListener progress, Cancellation cancel);
}
public record ExportSummary(ExportId id, Instant startedAt, Path outputRoot, int filesCreated,
                            Optional<ExportStatus> status, boolean undone) {}
public record UndoStep(UndoAction action, Path path, Optional<Path> backup) {
    public enum UndoAction { DELETE_FILE, RESTORE_BACKUP, REMOVE_DIR_IF_EMPTY, KEEP_CHANGED, ALREADY_GONE }
}
public record UndoPlan(ExportId id, List<UndoStep> steps, List<Issue> issues) {}
public record UndoReport(ExportId id, int deleted, int restored, int kept, List<Issue> issues) {}
```

### 7.7 Use cases for later stories (signature + short contract)

```java
// 005 – cleanup runs inside ProposeMetadataUseCase: ExtractionSpec gains `CleanupSpec cleanup`.
public record CleanupSpec(List<CleanupRule> rules) {}   // order = application order, shown in preview
public sealed interface CleanupRule {   // all pure String → String on selected fields
    record Separators(boolean underscores, boolean dots) implements CleanupRule {}
    record CollapseSpaces() implements CleanupRule {}
    record RemoveJunk(List<String> patterns) implements CleanupRule {}         // literal or glob-like, editable list
    record Capitalize(CapitalizationMode mode, Set<String> exceptions) implements CleanupRule {}
    record NormalizeFeat(FeatMove move) implements CleanupRule {}
    record StripLeadingTrackNumber() implements CleanupRule {}
    record Replace(Optional<Field> field, String find, String replacement, boolean regex) implements CleanupRule {}
}
// Guarantee: a rule that would leave a value empty keeps the previous value + WARNING; manual edits win.

// 006
public interface ProfileUseCase {
    List<DeviceProfile> list();                                    // built-ins first, then user profiles
    Optional<DeviceProfile> find(ProfileId id);
    DeviceProfile save(DeviceProfile profile);                     // Require !builtIn
    DeviceProfile duplicate(ProfileId source, String newName);
    void delete(ProfileId id);                                     // Require: not built-in
    ResolvedOptions resolve(Optional<ProfileId> profile, OptionOverrides overrides); // explicit > profile > default
    List<Issue> loadIssues();                                      // invalid hand-edited files
}
public record DeviceProfile(ProfileId id, String name, boolean builtIn, TagWriteOptions tagOptions,
                            CoverPolicy cover, GenreMode genreMode, NamingRules naming, boolean zeroPadTrack,
                            Optional<String> defaultOutputPattern) {}
// Multi-profile export: PlanExportRequest per profile; PlanExportUseCase.planAll(List<PlanExportRequest>) in 006.

// 007
public interface PresetUseCase {
    List<JobPreset> list(); Optional<JobPreset> load(String name);
    JobPreset save(String name, JobSpec spec); void delete(String name);
    List<String> recentPatterns(int max);
}
public record JobSpec(ExtractionSpec extraction, Optional<ProfileId> profile, OptionOverrides overrides,
                      Optional<String> outputPattern, CollisionPolicy collisions) {}
public record JobPreset(int schemaVersion, String name, JobSpec spec) {}
public interface PatternSuggestionUseCase {
    List<PatternSuggestion> suggest(List<Path> files, int max);    // pure heuristics; never applied automatically
}
public record PatternSuggestion(String pattern, double matchRatio, int sampleSize) {}

// 008
public interface GenreUseCase {
    GenreVocabulary vocabulary();
    GenreMatch normalize(String raw);                 // KNOWN(name) | ALIAS(name) | UNKNOWN(raw) + ID3v1 number
    GenreVocabulary addGenre(String name, int id3v1Number);
    GenreVocabulary addAlias(String genre, String alias);
}
public record GenreEntry(String name, Set<String> aliases, int id3v1Number) {}  // id3v1Number 0..191, unknown → 12

// 009 – resolves CoverChange per file/album; used by PlanExportUseCase (ExportSpec gains CoverPolicy + CoverSource).
public sealed interface CoverSource { record FromFile(Path image) {} record FromFolder() {} record KeepEmbedded() {} record Remove() {} }
public record CoverPolicy(CoverMode mode, int maxPixels, int maxBytes, SquareMode square, boolean writeFolderJpg) {}

// 011
public interface LibraryUseCase {
    List<Path> roots(); void addRoot(Path root); void removeRoot(Path root);
    RescanReport rescan(ProgressListener progress, Cancellation cancel);  // uses ScanMediaUseCase; marks missing
    List<LibraryTrack> query(LibraryQuery query);                         // browse by artist/album/genre, full-text
}
public interface PlayerUseCase {          // queue logic in core (PlaybackQueue), engine via port
    void playNow(List<Path> tracks, int startIndex); void enqueue(List<Path> tracks);
    void togglePause(); void stop(); void next(); void previous();
    void seek(Duration position); void setVolume(double volume);
    PlayerState state(); void setListener(PlayerListener listener);       // skips corrupt files with an Issue
}

// 012 – no new use case: MP4 goes through scan/propose/plan/apply with MediaFormat.MP4,
// ExtractionSpec gains MediaKind; ExportSpec gains companion extensions (".srt").
```

## 8. Dry-run, change plan, safe export, undo

### 8.1 Workflow

```
 ScanMediaUseCase ──► ScannedFile* ──► ProposeMetadataUseCase ──► ProposalSet
                                                                  │
                         PlanExportUseCase (dry-run, read-only) ◄─┘
                                   │
                              ExportPlan ──render──► CLI table / GUI preview          (default: stop here)
                                   │  --apply / "Apply" click
                                   ▼
                         ApplyExportUseCase ──► ExportReport + journal ──► UndoExportUseCase
```

- **Dry-run is structural**: `PlanExportUseCase` cannot write, because it has no write port (`ExportTarget` is only
  used for read-only queries like `storeInfo`/`findCollision`). The CLI calls `apply` only with `--apply`. The GUI
  calls it only from the "Apply" button.
- **The plan is the contract between preview and apply**: apply executes exactly the plan's targets and write requests.
  It re-checks each source fingerprint (SOURCE_CHANGED), so a stale preview cannot write something other than what was
  shown.

### 8.2 Safe write per file (temp → verify → no-replace move)

```
reserveTempPath(target)          ".Song.4f9a1c.musicstudio-tmp.mp3"  (same dir ⇒ same FS ⇒ rename)
TagWriter.writeCopy(src, temp)   source opened read-only
TagReader.read(temp)             WriteVerifier: v2 slot fields == expected (writable fields), v1 == Id3v1Values,
                                 versions == selected (+ kept other families)
journal FileCreated(target, fp(temp))      durable before the file becomes visible
ExportTarget.moveNoReplace(temp, target)   hard-link + unlink, or check-then-rename on FAT/exFAT
finally: deleteTemp(temp) if it still exists
```

`Files.move(…, ATOMIC_MOVE)` alone is **not** used for the final step, because on POSIX `rename(2)` silently replaces
an existing target. The hard-link variant is atomic *and* no-replace. On filesystems without hard links (FAT32
sticks), a check-then-rename leaves a tiny race. That is acceptable there because the export folder is not shared
with other writers. This is documented in `ExportTarget.moveNoReplace`.

### 8.3 Undo journal

- Written by `ApplyExportService` through `ExportJournalStore`. It is append-only JSON lines, and every append is
  `fsync`'ed, so the journal survives a crash or power loss mid-export.
- Ordering makes undo complete: `DirectoryCreated` is appended right after `createDirectories` returns (a crash in
  between leaves only empty folders). `FileCreated` is appended *before* the move. After a crash between append and
  move, undo sees `ALREADY_GONE` and continues. The orphaned temp file has a recognisable name, and the next plan
  for that output folder lists it (INFO).
- Undo deletes only files whose fingerprint (size + mtime; a rename keeps both) still matches. Files changed later
  are kept and reported. Backups taken for `--overwrite` are restored with `moveNoReplace`.
- Undo is itself dry-run-first (`planUndo` → `applyUndo`).

### 8.4 Originals

Enforced on four levels: (1) `MediaFileSource` and `TagReader` have read-only contracts, verified by checksum;
(2) `TagWriter.writeCopy` requires target ≠ source; (3) the plan rejects targets equal to any source (TARGET_IS_SOURCE)
regardless of the collision policy; (4) `ExportTarget` has no general delete/overwrite method.

## 9. Adapter capabilities and write-option validation

```java
/**
 * What a TagWriter can produce for one format. writableVersions non-empty for MP3 (subset of ID3 versions), {MP4_ILST}
 * for MP4; writableFields non-empty; writableEncodings non-empty for MP3.
 */
public record FormatCapabilities(MediaFormat format, Set<TagVersion> writableVersions,
                                 Set<V2Encoding> writableEncodings, Set<Field> writableFields, boolean frontCover) {
    /** Pure: issues (UNSUPPORTED_WRITE_VERSION, …) for options this writer cannot honour; empty = OK. */
    public List<Issue> unsupported(TagWriteOptions options);
}
```

| Adapter | writableVersions | Notes |
|---|---|---|
| jaudiotagger (MP3) | ID3v1.0, v1.1, v2.2, v2.3, v2.4 | all encodings |
| mp3agic (hypothetical) | ID3v1.0, v1.1, v2.3, v2.4 | no v2.2 write → greyed out |
| MP4 adapter (012) | MP4_ILST | ID3 options ignored for MP4 files |

Validation happens in three places, all before any write: `TagWriteOptions` constructor (structurally invalid combos),
`WriteOptionsUseCase.validate` (UI greying, CLI fail-fast with exit code 2), and `PlanExportUseCase` (global ERROR,
plan not executable). Device profiles (006) are checked with the same `unsupported(...)` call when selected
(WARNING).

## 10. Pattern language

Parsing and matching are pure core logic in `core.pattern`, with no `java.util.regex` exposure in the API
([ADR 0009](adr/0009-pattern-language-own-parser-and-matcher.md)).

### 10.1 Grammar

```
pattern      := segment ( "/" segment )*
segment      := token+
token        := literal | placeholder | wildcard
placeholder  := "{" name ( ":" format )? "}"      name ∈ Field.placeholder();   format := "0"+  (output only)
wildcard     := "{*}"                               (input only)
literal      := ( any char except "{" "}" "/"  |  "{{"  |  "}}" )+
```

- Input patterns (002): the **last segment** matches the file's base name without extension, the segment before it
  matches the parent folder name, and so on. Ancestors above the scan root may be matched, e.g. the scan root's own
  name as `{album}`. If the path has fewer elements than segments → NO_MATCH.
- Output patterns (004): segments become folders, and the last becomes the file name; the extension is appended.
  `{track:00}` zero-pads to the number of zeros.
- Parse errors (unknown placeholder, `{*}` in output, format in input, unbalanced brace, empty segment, two
  **untyped** placeholders adjacent without a literal) are returned with a 0-based column for GUI highlighting.

```java
public sealed interface Token { record Literal(String text) implements Token {}
                                record Placeholder(Field field, OptionalInt padWidth) implements Token {}
                                record Wildcard() implements Token {} }
public record PatternSegment(List<Token> tokens) {}
public record InputPattern(String source, List<PatternSegment> segments) {}
public record OutputPattern(String source, List<PatternSegment> segments) {}
public record PatternError(int column, String message) {}
public sealed interface PatternParseResult<P> { record Valid<P>(P pattern) … ; record Invalid<P>(List<PatternError> errors) … }

public final class PatternParser {          // pure, stateless; static methods are fine for pure functions
    public static PatternParseResult<InputPattern> parseInput(String source);
    public static PatternParseResult<OutputPattern> parseOutput(String source);
}
public final class PathMatcher {
    public static MatchResult match(InputPattern pattern, Path file);
}
/** MATCHED: values; AMBIGUOUS: chosen values + alternativeCount (>=1); NO_MATCH: reason incl. segment index. */
public record MatchResult(MatchKind kind, Map<Field, FieldValue> values, Map<Field, String> raw,
                          int alternativeCount, Optional<String> reason) {}
public final class PathRenderer {
    public static RenderResult render(OutputPattern pattern, MediaMetadata metadata, Map<Field, String> fallbacks,
                                      NamingRules naming, String extension);
}
public record RenderResult(List<String> segments, List<Issue> issues) { public Path resolveAgainst(Path root); }
```

### 10.2 Matching semantics (per segment; segments are independent)

- **Literals**: case-sensitive. Leading/trailing spaces of a literal match zero or more whitespace characters, and
  inner runs of spaces match one or more. So `" - "` matches `-`, `" - "` and `"  -   "`. This covers "extra spaces"
  and "separator spaces trimmed".
- **Placeholders** capture a non-empty string, which is stripped. Typed placeholders must also parse:
  `{year}` = 4 digits, optionally in `()` or `[]` (brackets removed), range 1000–2999;
  `{track}`/`{disc}` = `\d{1,4}` optionally followed by `of\d+` or `/\d+` → `Position`; `{genre}` → single `Genre`;
  text placeholders → `Text`. `{*}` captures anything (it may also be empty).
- **Algorithm**: dynamic programming over (token index, string position). It counts complete matches, capped at 2.
  The chosen solution is the lexicographically shortest-capture-first one (non-greedy, left to right), reconstructed
  from the table. Cost is O(tokens × length²) per segment, which is trivial for file names. A count of 1 gives
  MATCHED; ≥2 gives AMBIGUOUS. Typed placeholders prune splits during the DP ("typed placeholders tried first to
  resolve ambiguity").
- **NO_MATCH reason**: the furthest position reached and the token that failed there
  (e.g. `separator " - " not found after "Artist"`, or `"20x0" is not a 4-digit year`).
- 012 extends the typed set: `{season}` digits, `{episode}` digits with optional double episode (`E01E02`, `E01-E02`).
  Dots as separators already work as literals (`{show}.S{season}E{episode}.{episodetitle}.{*}`).

### 10.3 Rendering

Values come from `MediaMetadata.display`, except: `TRACK`/`DISC` render the number only (no total);
`GENRE` renders the primary genre; `padWidth` zero-pads numbers. Each rendered segment is passed through
`FileNameSanitizer`: replace `\ / : * ? " < > |` and control characters with the replacement character, strip
trailing dots and spaces, rename Windows reserved names (`CON`, `NUL`, …) with a `_` suffix, optionally convert to
ASCII via `Transliterator`, then cut to maxSegmentLength at a word boundary. Every change produces a NAME_SANITIZED
INFO issue. Path length is checked last (§7.4).

## 11. Errors, results, warnings

| Kind | Mechanism | Example |
|---|---|---|
| Contract violation (bug in caller) | `NullPointerException` / `IllegalArgumentException` / `IllegalStateException` from `Require` | null path, relative path, executing a non-executable plan |
| Broken internal invariant | `assert` (tests, `-ea`) | sort order, counters add up |
| Expected per-item problem | `Issue` with `Severity` + `IssueCode` inside the result | corrupt file, no match, truncated in v1, collision |
| Expected global problem | `Issue` at result level + status enum | root not found, unsupported write version, insufficient space |
| Expected I/O failure at a port | checked `MediaAccessException` / `StoreException` with `Reason` | caught by the service, converted to an Issue |
| Cancellation | `OperationCancelledException` (unchecked) from ports, `CANCELLED` status from apply | user pressed Cancel |
| User input syntax | result type (`PatternParseResult`, `TagWriteOptions.problems`) | invalid pattern, UTF-8 with v2.3 |

Severity semantics: **ERROR** on an item means the item will not be written (SKIP or failed). **ERROR** on the
plan means the plan is not executable. **WARNING** means it will be written, but the user should look at it.
**INFO** is shown in verbose output.

CLI exit codes: `0` success; `1` completed with item errors; `2` usage/invalid input/not executable; `3` aborted
(I/O failure of the journal or root); `130` cancelled (Ctrl-C).

## 12. Threading, progress, cancellation

```java
public interface Cancellation {
    Cancellation NONE;
    boolean isCancelled();
    /** @throws OperationCancelledException if cancelled */
    default void throwIfCancelled();
}
/** Thread-safe (AtomicBoolean). The frontend owns it and passes it as Cancellation. */
public final class CancellationSource implements Cancellation { public void cancel(); }

public interface ProgressListener {
    ProgressListener NONE;
    /** Called on the use case's thread; must be fast and must not call back into the use case. */
    void onProgress(ProgressEvent event);
}
/** done >= 0; total >= done or -1 if unknown; phase e.g. "list", "read", "plan", "write". */
public record ProgressEvent(String phase, long done, long total, Optional<Path> current,
                            long currentBytes, long currentBytesTotal) {}
public interface ByteProgress { ByteProgress NONE; void onBytes(long done, long total); }
```

- **Use cases are synchronous and blocking.** Frontends decide the thread. The CLI calls them on the main thread and
  installs a shutdown hook (Ctrl-C) that triggers the `CancellationSource` and waits for the call to return, so the
  current temp file is removed.
- **GUI (010)**: every call runs in a `javafx.concurrent.Task` on a single-thread executor for mutating work (one
  export at a time) and a separate one for scans/plans. `ProgressListener` → `Task.updateProgress/updateMessage`
  (thread-safe and coalesced). Results come back via `Task.succeeded` on the FX thread. Cancel → `CancellationSource
  .cancel()`. **Never `Task.cancel(true)`**, because the interrupt would close NIO channels mid-write
  (`ClosedByInterruptException`). Closing the window during an export asks first, cancels, and waits for the task.
- `ProposeMetadataUseCase` is pure and fast. The GUI runs it on the background executor with a ~150 ms debounce
  and drops stale results (sequence number).
- Concurrency inside use cases: sequential per file in v1. `TagReader` is contractually safe for concurrent use, so
  parallel reading with ordered emission can be added to scans later without changing the API.
- jaudiotagger's global `TagOptionSingleton` is set only inside the adapter, under a lock held for the whole
  `writeCopy`/`preflight` call.
- jaudiotagger is not thread-safe even for **reads**: it has process-wide mutable state, and concurrent reads of
  different files return wrong durations and audio offsets (reproduced with plain library calls, no project code).
  The adapter therefore runs every library call under one static lock (`LibraryLock`); the writer of story 003 must
  use the same lock. The reader contract test (8 threads x all fixtures) guards this.

## 13. Persistence and application directories

[ADR 0008](adr/0008-local-persistence-json-files.md): plain JSON files written via Eclipse Parsson's streaming API
and hand-written mappers (no reflection binding). Every file has `"schemaVersion"`. Writes are atomic
(temp + move).

| Data | Location (Linux / Windows / macOS) | Port |
|---|---|---|
| settings (010), profiles, presets, genres | `$XDG_CONFIG_HOME/music-studio/` (`~/.config/music-studio`) · `%APPDATA%\MusicStudio` · `~/Library/Application Support/MusicStudio` | `SettingsStore`, `ProfileStore`, `PresetStore`, `GenreStore` |
| export journals + reports | `$XDG_DATA_HOME/music-studio/exports/<id>.journal.jsonl`, `<id>.report.json` | `ExportJournalStore` |
| library index | `$XDG_DATA_HOME/music-studio/library.jsonl` | `LibraryIndexStore` |
| overwrite backups | `<outputRoot>/.musicstudio-backup/<exportId>/…` (same FS → rename) | `ExportTarget` |

`AppConfig` (in `app`) resolves the directories from environment and system properties once. It is passed to the
storage adapters' constructors. Core never sees these paths.

## 14. Testing

### 14.1 Contract tests (core test-jar)

`music-studio-core` builds a `test-jar` (classifier `tests`) containing package `…core.testkit`:

| Class | Contract for | Hooks the concrete test implements |
|---|---|---|
| `MediaFileSourceContract` | `MediaFileSource` | `subject()`; filesystem created in `@TempDir` |
| `TagReaderContract` | `TagReader` | `subject()` |
| `TagWriterContract` (003) | `TagWriter` | `subject()`, `reader()`, `addUnknownData(Path)`, `hasUnknownData(Path)` (e.g. a PRIV frame – neutral wording, library-specific implementation) |
| `ExportTargetContract` (004) | `ExportTarget` | `subject()` |
| `ExportJournalStoreContract` (004) | `ExportJournalStore` | `subject(Path dir)`, `simulateCrash()` (reopen without close) |
| later: `ProfileStoreContract`, `PresetStoreContract`, `GenreStoreContract`, `LibraryIndexStoreContract`, `PlaybackEngineContract` (state machine only) | | |

- Contract tests are JUnit 5 abstract classes. Every adapter test class `extends` the matching contract, so a
  future mp3agic adapter proves the same behaviour with one `extends`.
- **Fixtures** (`Fixture` enum + `FixtureCatalog` with the expected `TagReadResult` per fixture) live in the
  test-jar (`/fixtures/mp3/*.mp3`) and are always copied into `@TempDir` before use. They are generated by an
  **independent tool** (ffmpeg for 1 s of silence, Python `mutagen` for v1/v2.3/v2.4, a tiny byte-writer for v2.2,
  `head -c` for corrupt files). The script `music-studio-core/src/test/fixtures-src/generate-fixtures.sh` is
  committed alongside the binaries. Expectations are written by hand from the script, never by running our own
  reader.
- Every contract asserts the precondition behaviour too (null → NPE, relative path → IAE), and read-only ports
  assert unchanged SHA-256 + mtime.
- **Fakes** (`InMemoryMediaFileSource`, `FakeTagReader`, `FakeTagWriter`, `InMemoryExportTarget`,
  `InMemoryJournalStore`) live in the test-jar, back all core service tests, and **pass the same contract tests**,
  so fakes cannot drift from real behaviour. Fakes keep files in memory under `@TempDir`-rooted absolute paths
  (absolute on every OS). There is no mocking library.

### 14.2 Unit tests in core

Pure logic (pattern DP, rendering, sanitising, transliteration, ID3v1 projection, collision resolution, merge
precedence, verifier) gets table-driven tests. Services are tested against the fakes (ordering, streaming, cancel
mid-run, failure in one file continues, SOURCE_CHANGED, crash-safe journal ordering).

### 14.3 Adapter integration tests

They use real fixture copies in `@TempDir`. jaudiotagger: contract tests plus adapter-specific ones (v2.4→v2.3
conversion of TDRC, UTF-16 BOM, JUL logging silenced). Storage: contract tests on the real FS incl. symlinks,
hidden files, no-permission directories (skipped on Windows via `@DisabledOnOs`), and hard-link vs fallback move.
Large-file MP4 tests (012) are tagged `@Tag("large")` and run in Failsafe, not on every build.

### 14.4 ArchUnit rules

Rules are defined once in `…core.testkit.arch.ArchitectureRules` (test-jar). Each module has a small
`ArchitectureTest` that applies the rules relevant to it.
Analysed package: `dev.rs.slava.icxc.musicstudio`; test classes are excluded by the shared import option
`ProductionClassesOnly`, which also leaves out the core `-tests.jar` that downstream modules have on their classpath.

| # | Rule | Checked in |
|---|---|---|
| A1 | `core..` depends only on `core..` and whitelisted JDK packages: `java.lang`, `java.util..`, `java.time..`, `java.text`, `java.nio.file` (types `Path` only – see A2), `java.nio.charset`, `java.math` | core |
| A2 | `core..` does not use `java.nio.file.Files`, `java.io.File*`, `RandomAccessFile`, `java.nio.channels..`, `java.net..`, `java.awt..`, `javax..`, `java.sql..`, `java.util.logging..`, `java.lang.reflect..`, `Class.forName`, `System.out/err`, `Path.toFile`, `Path.toRealPath` | core |
| A3 | Core layering as in §3 (`contract` ← `text` ← `model` ← `tag`/`pattern`/`task` ← `port` ← `scan` ← `extract` ← `export` ← later features) | core |
| A4 | `core.port` contains only interfaces, records, enums and the two exception classes | core |
| A5 | In `core.model`, `core.tag`, `core.pattern` every class is a record, enum, interface, or `final` class with only `static final` fields. In `core.task` every class is a record, enum, interface or `final` class (`CancellationSource` holds an `AtomicBoolean`, §12) | core |
| A6 | All fields in `core..` are `final` | core |
| A7 | Classes named `*Service` are `final`, public, implement exactly one `*UseCase` interface, and are only referenced from `core..` (tests) and `app..` | core + every module |
| A8 | Adapters (`tag..`, `storage..`, `image..`, `player..`) do not depend on each other, on `app..`, `cli..`, `gui..`, or on `*Service` | each adapter |
| A9 | `cli..` and `gui..` do not depend on `core.port..`, `*Service`, or adapter packages (exception: `gui.GuiMain` → `player.javafx`) | cli, gui |
| A10 | Library containment: `org.jaudiotagger..` only in `tag.jaudiotagger..`; `picocli..` only in `cli..`; `javafx..` only in `gui..`/`player.javafx..`; `jakarta.json..`/`org.eclipse.parsson..` only in `storage..` | every module |
| A11 | No class in our code uses `java.lang.reflect..` or `Class.forName` (native-image friendliness) | every module |

Rules are never weakened to make code compile (`CLAUDE.md`). There is no freezing/baseline of violations.

## 15. Maven build

### 15.1 Layout

```
pom.xml                              music-studio-parent (packaging pom)
music-studio-core/pom.xml            no dependencies; builds test-jar; enforcer bans compile/runtime deps
music-studio-tag-jaudiotagger/pom.xml
music-studio-storage/pom.xml
music-studio-app/pom.xml
music-studio-cli/pom.xml             profile "native"
music-studio-gui/pom.xml             (010) javafx-maven-plugin, profile "package"
music-studio-player-javafx/pom.xml   (011)
music-studio-tag-mp4/pom.xml         (012)
music-studio-image-imageio/pom.xml   (009)
.mvn/wrapper/…, mvnw, mvnw.cmd       Maven wrapper (Maven 3.9.x)
```

### 15.2 Parent POM (essentials; all versions: **verify latest** before use)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.rs.slava.icxc</groupId>
  <artifactId>music-studio-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>music-studio-core</module>
    <module>music-studio-tag-jaudiotagger</module>
    <module>music-studio-storage</module>
    <module>music-studio-app</module>
    <module>music-studio-cli</module>
    <!-- added by their stories: music-studio-gui (010), music-studio-player-javafx (011),
         music-studio-tag-mp4 (012), music-studio-image-imageio (009) -->
  </modules>

  <properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <!-- libraries (verify latest) -->
    <jaudiotagger.version>3.0.1</jaudiotagger.version>
    <picocli.version>4.7.7</picocli.version>
    <javafx.version>25.0.1</javafx.version>
    <parsson.version>1.1.7</parsson.version>
    <jakarta.json-api.version>2.1.3</jakarta.json-api.version>
    <!-- tests (verify latest; ArchUnit must support class-file version 69 = Java 25) -->
    <junit.version>5.13.4</junit.version>           <!-- or JUnit 6.0.x, see open questions -->
    <assertj.version>3.27.6</assertj.version>
    <archunit.version>1.4.1</archunit.version>
    <!-- plugins (verify latest) -->
    <maven-compiler-plugin.version>3.14.1</maven-compiler-plugin.version>
    <maven-surefire-plugin.version>3.5.4</maven-surefire-plugin.version>
    <maven-jar-plugin.version>3.4.2</maven-jar-plugin.version>
    <maven-enforcer-plugin.version>3.6.2</maven-enforcer-plugin.version>
    <maven-dependency-plugin.version>3.8.1</maven-dependency-plugin.version>
    <native-maven-plugin.version>0.11.1</native-maven-plugin.version>
    <javafx-maven-plugin.version>0.0.8</javafx-maven-plugin.version>
    <jpackage-maven-plugin.version>1.7.1</jpackage-maven-plugin.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency><groupId>org.junit</groupId><artifactId>junit-bom</artifactId>
        <version>${junit.version}</version><type>pom</type><scope>import</scope></dependency>
      <!-- internal -->
      <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-core</artifactId>
        <version>${project.version}</version></dependency>
      <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-core</artifactId>
        <version>${project.version}</version><type>test-jar</type><scope>test</scope></dependency>
      <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-tag-jaudiotagger</artifactId>
        <version>${project.version}</version></dependency>
      <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-storage</artifactId>
        <version>${project.version}</version></dependency>
      <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-app</artifactId>
        <version>${project.version}</version></dependency>
      <!-- libraries -->
      <dependency><groupId>net.jthink</groupId><artifactId>jaudiotagger</artifactId>
        <version>${jaudiotagger.version}</version></dependency>
      <dependency><groupId>info.picocli</groupId><artifactId>picocli</artifactId>
        <version>${picocli.version}</version></dependency>
      <dependency><groupId>info.picocli</groupId><artifactId>picocli-codegen</artifactId>
        <version>${picocli.version}</version></dependency>
      <dependency><groupId>jakarta.json</groupId><artifactId>jakarta.json-api</artifactId>
        <version>${jakarta.json-api.version}</version></dependency>
      <dependency><groupId>org.eclipse.parsson</groupId><artifactId>parsson</artifactId>
        <version>${parsson.version}</version></dependency>
      <dependency><groupId>org.openjfx</groupId><artifactId>javafx-controls</artifactId>
        <version>${javafx.version}</version></dependency>
      <dependency><groupId>org.openjfx</groupId><artifactId>javafx-media</artifactId>
        <version>${javafx.version}</version></dependency>
      <!-- tests -->
      <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId>
        <version>${assertj.version}</version><scope>test</scope></dependency>
      <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId>
        <version>${archunit.version}</version><scope>test</scope></dependency>
    </dependencies>
  </dependencyManagement>

  <!-- every module gets the test stack -->
  <dependencies>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope></dependency>
    <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId><scope>test</scope></dependency>
  </dependencies>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
          <version>${maven-compiler-plugin.version}</version>
          <configuration>
            <release>${maven.compiler.release}</release>
            <parameters>true</parameters>
            <compilerArgs><arg>-Xlint:all,-processing,-serial</arg></compilerArgs>
            <failOnWarning>true</failOnWarning>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>
          <version>${maven-surefire-plugin.version}</version>
          <configuration>
            <enableAssertions>true</enableAssertions>        <!-- -ea: asserts are part of the test oracle -->
            <argLine>-ea -Duser.language=en -Duser.country=US</argLine>
            <excludedGroups>large</excludedGroups>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId><artifactId>maven-jar-plugin</artifactId>
          <version>${maven-jar-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.graalvm.buildtools</groupId><artifactId>native-maven-plugin</artifactId>
          <version>${native-maven-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId>
          <version>${javafx-maven-plugin.version}</version>
        </plugin>
        <plugin>
          <groupId>org.panteleyev</groupId><artifactId>jpackage-maven-plugin</artifactId>
          <version>${jpackage-maven-plugin.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId><artifactId>maven-enforcer-plugin</artifactId>
        <version>${maven-enforcer-plugin.version}</version>
        <executions><execution><id>enforce</id><goals><goal>enforce</goal></goals><configuration><rules>
          <requireJavaVersion><version>[25,)</version></requireJavaVersion>
          <requireMavenVersion><version>[3.9,)</version></requireMavenVersion>
          <dependencyConvergence/>
          <banDuplicatePomDependencyVersions/>
        </rules></configuration></execution></executions>
      </plugin>
    </plugins>
  </build>
</project>
```

### 15.3 Module specifics

**core** – no `<dependencies>` besides the inherited test stack, plus:

```xml
<plugin><artifactId>maven-jar-plugin</artifactId>
  <executions><execution><goals><goal>test-jar</goal></goals></execution></executions></plugin>
<plugin><artifactId>maven-enforcer-plugin</artifactId>
  <executions><execution><id>core-has-no-dependencies</id><goals><goal>enforce</goal></goals><configuration><rules>
    <bannedDependencies><excludes>
      <exclude>*:*:*:*:compile</exclude><exclude>*:*:*:*:runtime</exclude><exclude>*:*:*:*:provided</exclude>
    </excludes></bannedDependencies></rules></configuration></execution></executions></plugin>
```

The test-jar needs JUnit/AssertJ/ArchUnit at *compile* time of the test classes only. Consumers declare them as test
dependencies (inherited from the parent).

**adapters / app** depend on `music-studio-core` + `music-studio-core:test-jar` (test). Adapters add their library.
`app` depends on core and the adapters.

**cli**:

```xml
<dependencies>
  <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-app</artifactId></dependency>
  <dependency><groupId>${project.groupId}</groupId><artifactId>music-studio-core</artifactId></dependency>
  <dependency><groupId>info.picocli</groupId><artifactId>picocli</artifactId></dependency>
</dependencies>
<build><plugins>
  <plugin><artifactId>maven-compiler-plugin</artifactId><configuration>
    <annotationProcessorPaths><path><groupId>info.picocli</groupId><artifactId>picocli-codegen</artifactId>
      <version>${picocli.version}</version></path></annotationProcessorPaths>
    <compilerArgs combine.children="append"><arg>-Aproject=${project.groupId}/${project.artifactId}</arg></compilerArgs>
  </configuration></plugin>
</plugins></build>
<profiles><profile><id>native</id><build><plugins>
  <plugin><groupId>org.graalvm.buildtools</groupId><artifactId>native-maven-plugin</artifactId>
    <extensions>true</extensions>
    <executions><execution><id>build-native</id><goals><goal>compile-no-fork</goal></goals><phase>package</phase></execution></executions>
    <configuration>
      <imageName>musicstudio</imageName>
      <mainClass>dev.rs.slava.icxc.musicstudio.cli.CliMain</mainClass>
      <buildArgs>
        <buildArg>--no-fallback</buildArg>
        <buildArg>-march=compatibility</buildArg>
        <buildArg>-H:+ReportExceptionStackTraces</buildArg>
      </buildArgs>
      <!-- agent config used to (re)generate adapter metadata: mvn -Pnative -Dagent=true test native:metadata-copy -->
      <agent><enabled>false</enabled></agent>
    </configuration></plugin>
</plugins></build></profile></profiles>
```

- picocli's annotation processor generates the picocli reflection config at compile time.
- jaudiotagger's metadata is produced by `music-studio-tag-jaudiotagger/native-image-metadata/generate-reachability-metadata.sh`:
  the tracing agent output (JVM CLI run over varied files) merged with an enumeration of the classes jaudiotagger
  creates by name or copies through copy constructors (all `FrameBody*`, data types, frames/tags, ASF chunk readers).
  The agent alone is not enough: it only sees the frame types of the files it ran on, and a real file with another
  frame would fail in the native binary. `music-studio-cli/src/test/native/scan-smoke.sh` runs the native binary
  against a reviewed JSON snapshot. The result is committed to `music-studio-tag-jaudiotagger/src/main/resources/META-INF/native-image/dev.rs.slava.icxc/music-studio-tag-jaudiotagger/reachability-metadata.json`
  (single-file format of GraalVM for JDK 23+).
- Assertions are **off** in the shipped native image (they would need `-ea` at build time). Preconditions stay
  active through `Require`. An optional `native-test` run (`mvn -Pnative test`) builds the tests natively with `-ea`.

**gui** (010):

```xml
<dependencies>
  <dependency>music-studio-app</dependency><dependency>music-studio-core</dependency>
  <dependency>music-studio-player-javafx</dependency>   <!-- 011 -->
  <dependency><groupId>org.openjfx</groupId><artifactId>javafx-controls</artifactId></dependency>
</dependencies>
<plugin><groupId>org.openjfx</groupId><artifactId>javafx-maven-plugin</artifactId>
  <configuration><mainClass>dev.rs.slava.icxc.musicstudio.gui.GuiMain</mainClass></configuration></plugin>
<!-- profile "package": maven-dependency-plugin copies jars to target/lib; jpackage-maven-plugin builds an
     installer with a jlink runtime: java.base, java.logging, java.desktop (ImageIO, 009), java.prefs(if needed),
     plus javafx.controls/javafx.media from the JavaFX jmods (env JAVAFX_JMODS). App jars stay on the classpath. -->
```

GraalVM JDK does not ship JavaFX. `org.openjfx` artifacts (with OS classifiers resolved by the JavaFX Maven artifacts)
cover development and `javafx:run`. jpackage uses the JavaFX **jmods** download for the runtime image. GluonFX
native GUI stays a later experiment.

## 16. Library choices

| Purpose | Coordinates (verify latest) | Where | Notes |
|---|---|---|---|
| MP3 tags | `net.jthink:jaudiotagger:3.0.1` | tag-jaudiotagger | Behind `TagReader`/`TagWriter` ([ADR 0003](adr/0003-tag-library-behind-port-jaudiotagger-first.md)). Global `TagOptionSingleton` → serialised writes. Needs native-image metadata. |
| MP3 tags (alternative) | `com.mpatric:mp3agic:0.9.1` | – | Candidate replacement; fits the port (writes to new file); no v2.2 write. |
| MP4 video atoms | decided by spike in 012: jaudiotagger MP4 support first; fallback own streaming ISO-BMFF `ilst` writer; alternative `org.mp4parser:isoparser:1.9.56` | tag-mp4 | [ADR 0006](adr/0006-mp4-video-tagging-library.md) |
| CLI | `info.picocli:picocli:4.7.7` + `picocli-codegen` | cli | Annotation processor emits native config. |
| GUI | `org.openjfx:javafx-controls:25.0.1` | gui | jpackage + jlink runtime. |
| Playback | `org.openjfx:javafx-media:25.0.1` | player-javafx | [ADR 0007](adr/0007-playback-port-and-backend.md) |
| JSON | `jakarta.json:jakarta.json-api:2.1.3` + `org.eclipse.parsson:parsson:1.1.7` | storage | Streaming API, no reflection; instantiate the provider directly to avoid ServiceLoader surprises. |
| Images (009) | JDK ImageIO (+ `com.twelvemonkeys.imageio:imageio-webp` for WebP input) | image-imageio | Verify AWT/ImageIO in native image in 009. |
| Tests | `org.junit:junit-bom:5.13.4`, `org.assertj:assertj-core:3.27.6`, `com.tngtech.archunit:archunit-junit5:1.4.1` | all | No mocking library (fakes). |
| Native | `org.graalvm.buildtools:native-maven-plugin:0.11.1`, GraalVM for JDK 25 | cli | |
| Packaging | `org.openjfx:javafx-maven-plugin:0.0.8`, `org.panteleyev:jpackage-maven-plugin:1.7.1` | gui | |

## 17. Story → component map

| Story | Core | Ports | Adapters / frontends |
|---|---|---|---|
| 001 | model (incl. Id3v1Genres), text (v1 fit), task, scan | MediaFileSource, TagReader | storage (listing), tag-jaudiotagger (read), app, cli `scan` |
| 002 | pattern (parse/match), extract | – | cli `extract` |
| 003 | tag (options, capabilities, projection, verifier), export.WriteOptions | TagWriter | tag-jaudiotagger (write), cli `export` options |
| 004 | pattern (render), text (sanitizer), export (plan/apply/undo) | ExportTarget, ExportJournalStore | storage, cli `export`, `undo` |
| 005 | cleanup (inside propose) | – | cli `--clean`, `--replace` |
| 006 | profile | ProfileStore | storage, cli `profiles`, `--profile` |
| 007 | preset, pattern suggestion | PresetStore | storage, cli `--preset`, `--save-preset` |
| 008 | genre (inside propose + v1 projection) | GenreStore | storage, cli `genres` |
| 009 | cover (inside plan) | ImageTranscoder, MediaFileSource images | image-imageio, cli `--cover` |
| 010 | – (uses 002–004 use cases) | – | gui workspace |
| 011 | library, playback | LibraryIndexStore, PlaybackEngine | storage, player-javafx, gui library view |
| 012 | Field/MediaKind/pattern extensions, companions | TagReader/TagWriter for MP4 | tag-mp4, storage `copy` |

## 18. Open questions

See the hand-off summary. The answers will be folded into the stories and this document:
1. **Resolved 2026-10-22:** "Remove other tag versions" is a settings checkbox (`AppSettings.removeOtherVersions`,
   default off; CLI `--remove-other-versions`). It keeps the one-slot-per-family semantics of §5.3/§6.3.
2. **Resolved 2026-10-22:** hidden files/directories (incl. `._*`) are skipped and reported in the scan's
   `skipped` list (`SkipReason.HIDDEN`); `--include-hidden` opts in.
3. Undo journals and reports go to the app data dir, not onto the device. OK?
4. **Resolved 2026-10-22:** effective value = v2 with v1 fallback. Whether transliteration/truncation counts as a
   difference is a settings checkbox (`AppSettings.v1Comparison`, default LENIENT; CLI `--strict-v1-compare`).
5. Auto-numbering is per parent folder with total = files in that folder. OK?
6. Merge mode applies to fixed values too (`--set` + `fill-empty` only fills empty fields). OK?
7. Overwrite backups go to `<output>/.musicstudio-backup/<id>/` on the target device (they use space on the stick). OK?
8. JUnit 5.13 vs JUnit 6.0: JUnit 6 is fine for Java 25. Choose on first build.
