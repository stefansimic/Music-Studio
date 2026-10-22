# Plan 001 – Maven skeleton + story 001 (scan a folder and read tags)

Story: [`docs/stories/001-scan-and-read-tags.md`](stories/001-scan-and-read-tags.md) ·
Architecture: [`docs/architecture.md`](architecture.md) · ADRs 0001–0005, 0008.

Each step builds and tests on its own with `mvn -q verify` (or `mvn -q -pl <module> -am verify`). Write the
Javadoc contracts from architecture §5–7 together with the code, not afterwards. Every public method starts with
`Require` checks.

Abbreviations: `C/` = `music-studio-core/src/main/java/dev/rs/slava/icxc/musicstudio/core/`,
`CT/` = `music-studio-core/src/test/java/dev/rs/slava/icxc/musicstudio/core/`,
`P/` = `src/main/java/dev/rs/slava/icxc/musicstudio/`, `T/` = `src/test/java/dev/rs/slava/icxc/musicstudio/`.

## Affected modules
`music-studio-core` (new), `music-studio-storage` (new, listing + fingerprint only), `music-studio-tag-jaudiotagger`
(new, reader only), `music-studio-app` (new), `music-studio-cli` (new). No GUI module yet (010).
Core gains no dependency.

## Steps

### Step 0 – Build skeleton
Files:
- `pom.xml` – parent as in architecture §15.2, `<modules>` = `music-studio-core` only for now
- `music-studio-core/pom.xml` – test-jar execution and enforcer `bannedDependencies` (§15.3)
- `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd` (`mvn wrapper:wrapper -Dmaven=3.9.x`)
- `.gitignore` (`target/`, `*.iml`, `.idea/`, `.vscode/`)
- `CT/AssertionsEnabledTest.java`

Tests:
- `AssertionsEnabledTest`: `assertThatThrownBy(() -> { assert false; }).isInstanceOf(AssertionError.class)`. This
  fails the build if `-ea` is ever lost.

Done when: `./mvnw -q verify` is green on Java 25. Adding any compile dependency to core fails the enforcer (try it
once, then revert).

### Step 1 – `Require`
Files: `C/contract/Require.java` (API in architecture §4.1)

Tests (`CT/contract/RequireTest.java`): one test per method for the pass case (returns its argument) and the fail
case (exception type + message contains the name). Also: `that(false, "x=%d", 5)` formats lazily (a template with
a bad format string is not evaluated when the condition holds); `absolute(relative)` → IAE; `state(false)` → ISE.

### Step 2 – Text helpers for ID3v1 comparison
Files: `C/text/Transliterator.java`, `C/text/Id3v1Text.java`

- `Transliterator.toAscii(String) → Transliteration(String text, boolean changed, boolean droppedChars)`
  (Serbian Cyrillic → Gaj Latin → ASCII: č/ć→c, š→s, ž→z, đ→dj, dž→dz, ä→ae, ö→oe, ü→ue, ß→ss, NFD + strip marks,
  drop the rest).
- `Transliterator.toLatin1(String)` (same, but keep Latin-1 characters).
- `Id3v1Text.fit(String value, boolean asciiOnly, int maxBytes) → FittedText(String text, boolean transliterated,
  boolean truncated, boolean dropped)`: truncates at a word boundary when possible. (`core.text` sits below
  `model`/`tag`, so it takes a boolean. `V1TextMode` arrives in 003 in `core.tag` and maps onto it.)

Tests (`TransliteratorTest`, `Id3v1TextTest`, parameterised): `Ђорђе Балашевић` → `Djordje Balasevic`;
`Љубав` → `Ljubav`; `Džez` → `Dzez`; `Müller` → `Mueller`; `Straße` → `Strasse`; emoji dropped with
`droppedChars`; pure ASCII unchanged with `changed=false`; 31-char title cut at a word boundary to ≤ 30; a single
40-char word cut hard at 30; LATIN1 mode keeps `é`.

### Step 3 – Domain model
Files (`C/model/`): `MediaFormat`, `FieldType`, `Field`, `FieldValue` (with nested `Text`, `YearValue`,
`Position`, `Genres`), `Genre`, `MediaMetadata`, `TagFamily`, `TagVersion`, `TagLayer`, `StreamInfo`,
`PictureType`, `CoverInfo`, `FileFingerprint`, `Severity`, `IssueCode` (the 001 subset is enough: `READ_FAILED`,
`TAG_DAMAGED`, `READ_VALUE_INVALID`, `LISTING_ACCESS_DENIED`, `ROOT_NOT_FOUND`, `V1_V2_CONFLICT`), `Issue`,
`TagReadResult`, `V1Comparison`, `PathOrder`.
Plus `C/model/Id3v1Genres.java` (Winamp table 0–191; `name(int)`, `number(String)` case-insensitive,
`resolveReference(String raw) → Optional<List<Genre>>` for `"17"`, `"(17)"`, `"(17)Rock"`, `"(17)(20)"`, plain
text).

Tests (one class per record/enum, `CT/model/…`):
- every compact-constructor rule (null → NPE, blank text / year 0 / track 0 / duplicate genres / wrong value type for
  field / line break in TITLE → IAE; line break in COMMENT ok)
- `MediaMetadata` is immutable (the input map is changed after construction → no effect; `values().put` → UOE);
  `with`/`without`/`differingFields`
- `MediaFormat.ofFileName`: `a.MP3`, `a.mp3`, `.mp3` (hidden, still mp3), `a.mp3.txt` (empty), `a` (empty)
- `TagReadResult`: duplicate family → IAE; wrong order → IAE; `effective()` = v2 value, v1 as fallback;
  `conflicts(LENIENT)`: equal → none; v1 `Djordje` vs v2 `Ђорђе` → none (transliteration); 30-char cut → none;
  different artist → `{ARTIST}`; v2 genre `Balkan` (no v1 number) vs v1 `Other` → none.
  `conflicts(STRICT)`: the same transliteration/truncation/genre cases → reported; equal values → none
- `Id3v1Genres`: `17`→Rock, `(17)`→Rock, `(17)Rock`→Rock, `(17)(20)`→Rock, Alternative; `255`→empty;
  `(999)`→empty; `Rock`→Rock; number lookup case-insensitive
- `PathOrder`: `a/B.mp3` < `a/c.mp3` < `b/a.mp3`; `A.mp3` vs `a.mp3` deterministic; folders stay grouped

### Step 4 – Task primitives
Files (`C/task/`): `Cancellation`, `CancellationSource`, `OperationCancelledException`, `ProgressListener`,
`ProgressEvent`, `ByteProgress`.

Tests: `CancellationSourceTest` (initially not cancelled; `cancel()` visible from another thread; `throwIfCancelled`);
`ProgressEventTest` (done ≤ total unless total = -1, negative → IAE); `NONE` instances are no-ops.

### Step 5 – Ports + testkit (contract tests, fixtures, fakes)
Main files (`C/port/`): `MediaAccessException`, `ListingOptions`, `DirectoryListing`, `SkippedEntry`, `SkipReason`, `MediaFileSource`,
`TagReader` (Javadoc contracts verbatim from architecture §6.1/6.2; `readCover` may be declared now and
implemented in 009, but the contract test for it is not added before 009).

Test-jar files (`CT/testkit/`):
- `fixture/Fixture.java` – enum of fixture files
- `fixture/FixtureCatalog.java` – hand-written expected `TagReadResult` per fixture
- `fixture/Fixtures.java` – `Path copyTo(Fixture, Path dir)`, `String sha256(Path)`
- `contract/MediaFileSourceContract.java`, `contract/TagReaderContract.java`
- `fake/InMemoryMediaFileSource.java`, `fake/FakeTagReader.java` (answers from `FixtureCatalog` by file name;
  configurable failures)

Fixture binaries in `music-studio-core/src/test/resources/fixtures/mp3/`, generated by
`music-studio-core/src/test/fixtures-src/generate-fixtures.sh` (ffmpeg: 1 s silence 128 kbps CBR; mutagen for
v1/v2.3/v2.4; a 30-line Python byte-writer for v2.2). All files are committed:

| Fixture | Content |
|---|---|
| `no-tags.mp3` | audio only |
| `v1_0.mp3` | ID3v1.0, 30-byte comment, no track |
| `v1_1.mp3` | ID3v1.1, track 7, genre 17 |
| `v23-latin1.mp3` | v2.3 Latin-1, `TRCK 3/12`, `TPOS 1/2`, `TPE2`, `COMM` |
| `v23-utf16-cyrillic.mp3` | v2.3 UTF-16 BOM: `Ђорђе Балашевић`, `čćšžđ`, an emoji |
| `v24-utf8.mp3` | v2.4 UTF-8, `TDRC 2020-05-01`, two genres NUL-separated |
| `v22.mp3` | v2.2 (`TT2`, `TP1`, `TAL`, `TRK`, `TYE`, `TCO`) |
| `v11-v23-same.mp3` | both tags, consistent (v1 = transliterated/truncated v2) |
| `v11-v23-diff.mp3` | both tags, different artist |
| `v23-genre-ref.mp3` | `TCON (17)Rock` |
| `v23-genre-num.mp3` | `TCON 17` |
| `v23-bad-track.mp3` | `TRCK A1` |
| `v23-cover.mp3` | APIC type 3, JPEG 1×1 |
| `v23-other-picture.mp3` | APIC type 0 only |
| `corrupt.mp3` | 4 KiB random bytes |
| `empty.mp3` | 0 bytes |

`TagReaderContract` tests (each fixture copied into `@TempDir`):
- `readableFormats()` non-empty and unmodifiable
- per fixture: layers/versions, values, stream info (duration ≈ 1 s ± 0.1, bitrate 128), cover info as in
  `FixtureCatalog`
- untagged → empty layers, no exception
- `v23-bad-track` → TRACK absent + `READ_VALUE_INVALID` warning naming the raw value
- `corrupt`/`empty` → `MediaAccessException(CORRUPT)`; missing file → `NOT_FOUND`;
  `chmod 000` → `ACCESS_DENIED` (`@DisabledOnOs(WINDOWS)`, skipped when running as root)
- read-only guarantee: SHA-256 and last-modified unchanged for every fixture
- non-ASCII file names: copy `v23-latin1` to `Ђорђе – 🎵 čćž.mp3` at test time → readable
- preconditions: null → NPE, relative path → IAE, `.txt` → IAE
- concurrency: 8 threads × all fixtures → same results as sequential

`MediaFileSourceContract` tests (tree built in `@TempDir`):
- flat vs recursive; result absolute, unique, under root
- hidden file/dir not listed unless `includeHidden`; each appears once in `skipped` with `HIDDEN` (a hidden
  directory as one entry, its content not enumerated); `._song.mp3` counts as hidden
- directory symlink → `skipped` with `DIRECTORY_SYMLINK`; excluded dir → `EXCLUDED`; `files` and `skipped` disjoint
- file symlink included, directory symlink not followed, symlink loop terminates (`@DisabledOnOs(WINDOWS)`)
- unreadable subdir → one `LISTING_ACCESS_DENIED` warning, rest listed
- root missing → `NOT_FOUND`; root is a file → `NOT_A_DIRECTORY`
- `excludedDirectories` honoured
- cancellation before start → `OperationCancelledException`
- `fingerprint` matches `Files.size`/mtime; missing → `NOT_FOUND`
- nothing in the tree changed (snapshot of names + mtimes before/after)

Concrete tests in core: `CT/testkit/fake/InMemoryMediaFileSourceContractTest`, `FakeTagReaderContractTest`
(the fakes pass the contracts).

### Step 6 – Scan use case
Files (`C/scan/`): `ScanRequest`, `ScanItem`, `ScannedFile`, `UnreadableFile`, `ScanStatus`, `ScanSummary`,
`ScanMediaUseCase` (contract §7.1), `ScanMediaService` (constructor `(MediaFileSource, TagReader)`, both `Require`d).

Algorithm: `list` → partition by `MediaFormat.ofFileName` ∩ `request.formats()` (count the rest by extension) →
sort with `PathOrder` (also `listing.skipped()`) → for each path: `throwIfCancelled`-style check → `fingerprint` +
`read` → emit `ScannedFile` with `conflicts = tags.conflicts(request.v1Comparison())`,
or on `MediaAccessException` emit `UnreadableFile(READ_FAILED, reason + message)` → progress event.
`assert found == read + failed` when COMPLETED.

Tests (`CT/scan/ScanMediaServiceTest`, with fakes):
- results sorted by `PathOrder` although the fake lists in random order
- untagged file is emitted with empty layers; corrupt file emitted as `UnreadableFile`; scan continues after it
- `ignoredFiles`/`ignoredByExtension` (`.jpg`, `.txt`, `.flac`)
- recursive flag passed through; hidden flag passed through
- `summary.skipped()` = the listing's skipped entries, sorted
- `v1Comparison` STRICT vs LENIENT changes `ScannedFile.conflicts` for a transliterated v1 tag
- root missing → `FAILED` + `ROOT_NOT_FOUND`, sink never called
- cancellation after 3 of 10 files → `CANCELLED`, sink got exactly the first 3 in order
- streaming: 20 000 fake files; the sink observes that `FakeTagReader.readCount` grows with the emitted count
  (results are not buffered)
- sink throws → exception propagates, no further reads
- progress: "list" then "read" events, last event done == total
- preconditions: each null argument → NPE

### Step 7 – ArchUnit rules
Files: `CT/testkit/arch/ArchitectureRules.java` (A1–A11 from architecture §14.4 as `public static final ArchRule`;
rules about packages that do not exist yet use `allowEmptyShould(true)`), `CT/CoreArchitectureTest.java`
(applies A1–A7, A11).

Tests: the rules themselves. Also add a negative self-test in `CT/testkit/arch/ArchitectureRulesSelfTest`: analyse
two tiny classes under `CT/testkit/arch/violations/` (e.g. one calling `java.nio.file.Files`) and assert the rule
*fails*. The production analysis excludes that package.

### Step 8 – Storage adapter: local listing
Files:
- add `<module>music-studio-storage</module>` to the root POM
- `music-studio-storage/pom.xml` – core + core:test-jar (test). No Parsson yet (it arrives with the journal in 004).
- `music-studio-storage/P/storage/LocalMediaFileSource.java` (`Files.walkFileTree` with
  `EnumSet.noneOf(FileVisitOption.class)`, so directory symlinks are not followed; `visitFileFailed` → issue;
  hidden = name starts with `.` or `Files.isHidden` on Windows; cancellation per `preVisitDirectory`)
- `music-studio-storage/T/storage/LocalMediaFileSourceTest.java` `extends MediaFileSourceContract`
- `music-studio-storage/T/storage/StorageArchitectureTest.java` (A8, A10, A11)

Extra test: `LocalMediaFileSourceLargeTreeTest` – 10 000 empty files in 100 folders, recursive listing < 5 s and
correct count.

### Step 9 – jaudiotagger adapter: reader
Files:
- add `<module>music-studio-tag-jaudiotagger</module>`
- `music-studio-tag-jaudiotagger/pom.xml` – core, `net.jthink:jaudiotagger`, core:test-jar (test)
- `P/tag/jaudiotagger/JaudiotaggerTagReader.java` – public; implements `TagReader`
- `P/tag/jaudiotagger/Id3Reading.java` – package-private mapping (`ID3v1Tag`/`ID3v11Tag`, `AbstractID3v2Tag`
  subclasses for 2.2/2.3/2.4 → `TagLayer`; generic `FieldKey` for portable fields; frame level only for picture
  type and the TCON/TRCK raw text; genre via core `Id3v1Genres.resolveReference`)
- `P/tag/jaudiotagger/JaudiotaggerLogging.java` – sets `org.jaudiotagger` JUL loggers to WARNING once, called from
  the reader's constructor (no static initialiser side effects elsewhere)
- `P/tag/jaudiotagger/LibraryExceptions.java` – maps `CannotReadException`, `InvalidAudioFrameException`,
  `ReadOnlyFileException`, `TagException`, `IOException`, and *any* `RuntimeException` from the library
  → `MediaAccessException` with the right `Reason` (never let library exceptions escape)
- `T/tag/jaudiotagger/JaudiotaggerTagReaderTest.java` `extends TagReaderContract`
- `T/tag/jaudiotagger/JaudiotaggerReaderSpecificsTest.java` – v1.0 vs v1.1 detection, v2.4 `TDRC` → year,
  UTF-16 without BOM (if jaudiotagger can produce the fixture, else skip), the file handle is closed after read
  (delete the file afterwards succeeds, also on Windows)
- `T/tag/jaudiotagger/JaudiotaggerArchitectureTest.java` (A8, A10, A11)

Notes: open files via `AudioFileIO.read(File)` (jaudiotagger opens read-only). Make sure that no
`TagOptionSingleton` option changes during reads. Bitrate/duration come from `MP3AudioHeader`.

### Step 10 – Composition root
Files:
- add `<module>music-studio-app</module>`
- `music-studio-app/pom.xml` – core, storage, tag-jaudiotagger
- `P/app/AppConfig.java` – record (`configDir`, `dataDir`, `Clock`) + `static AppConfig fromEnvironment(Map<String,String> env, Properties sys)`
  (pure resolution; no directories are created in 001)
- `P/app/MusicStudio.java` – record of use cases; in 001 only `ScanMediaUseCase scan`
- `P/app/MusicStudioFactory.java` – `public MusicStudioFactory(AppConfig)`, `public MusicStudio create()`;
  the only place that calls `new LocalMediaFileSource()`, `new JaudiotaggerTagReader()`, `new ScanMediaService(…)`
- `T/app/MusicStudioFactoryTest.java` – `create()` returns non-null use cases; end-to-end scan of 3 copied fixtures
  in `@TempDir` gives the expected rows
- `T/app/AppConfigTest.java` – XDG variables set/unset, Windows `APPDATA`, macOS (os.name) → expected paths
- `T/app/AppArchitectureTest.java` (A7–A11)

### Step 11 – CLI `scan`
Files:
- add `<module>music-studio-cli</module>`
- `music-studio-cli/pom.xml` – app, core, picocli; `picocli-codegen` annotation processor (§15.3)
- `P/cli/CliMain.java` – `main`: builds `MusicStudioFactory(AppConfig.fromEnvironment(System.getenv(), System.getProperties()))`,
  runs `new CommandLine(new MusicStudioCommand(studio))`, installs a Ctrl-C hook that cancels the
  `CancellationSource`, and `System.exit(code)`
- `P/cli/MusicStudioCommand.java` – top-level `musicstudio` command (`--version`, help), subcommands
- `P/cli/ScanCommand.java` – `scan <dir> [--recursive] [--include-hidden] [--strict-v1-compare] [--format table|json|csv]`. It resolves
  `<dir>` to an absolute path, calls `studio.scan().scan(…)` with a renderer as sink, and maps the summary to the
  exit code
- `P/cli/ExitCode.java` – 0/1/2/3/130 as in architecture §11
- `P/cli/render/ScanRenderer.java` (interface: `begin`, `item`, `end(summary)`), `TableScanRenderer`
  (fixed column widths with ellipsis, streaming; columns: path (relative to root), title, artist, album artist,
  album, track, disc, year, genre, comment, cover yes/no, duration m:ss, bitrate, tags e.g. `ID3v1.1 + ID3v2.3`;
  conflicting fields marked with `*` and listed in a last column `differs`; unreadable rows show `ERROR: <message>`),
  after the table a "Skipped (not scanned)" section lists each skipped path with its reason (hidden, directory
  symlink, excluded),
  `JsonScanRenderer` (streams `{"items": [ … ], "summary": {…, "skipped": [{path, reason}]}}`; every item has `path`, `format`, `effective`, `layers[]` (version +
  fields), `conflicts[]`, `stream`, `cover`, `issues[]`; a small hand-written JSON string escaper, no library),
  `CsvScanRenderer` (RFC 4180, UTF-8, header row; skipped entries go to stderr as `skipped: <reason> <path>`, so
  stdout stays a clean CSV)
- `P/cli/render/Text.java` – width/ellipsis helpers (code points, not chars, so emoji are not split)
- Tests:
  - `T/cli/ScanCommandTest.java` – picocli with a **fake** `ScanMediaUseCase` (a lambda): argument parsing, relative
    dir resolved to absolute, default format table, unknown format → exit 2, `FAILED` summary → exit 2,
    item errors → exit 1
  - `T/cli/render/*RendererTest.java` – golden outputs for a fixed set of `ScanItem`s (incl. Cyrillic, emoji,
    commas/quotes in CSV, control chars in JSON)
  - `T/cli/ScanEndToEndTest.java` – real `MusicStudioFactory` over all fixtures copied into `@TempDir` (plus a
    `.jpg` and a `.txt`, a hidden `._x.mp3` and a hidden `.cache/` folder): output contains every MP3 row sorted,
    `ignored: 2`, both hidden entries in the skipped section, corrupt rows with `ERROR`, and the SHA-256 of every
    file is unchanged; `--strict-v1-compare` flags `v11-v23-same`, the default does not
  - `T/cli/CliArchitectureTest.java` (A7, A9, A10, A11)

### Step 12 – Native CLI
Files:
- `music-studio-cli/pom.xml` – profile `native` (§15.3)
- `music-studio-tag-jaudiotagger/src/main/resources/META-INF/native-image/dev.rs.slava.icxc/music-studio-tag-jaudiotagger/reachability-metadata.json`
  – generated with the tracing agent: `./mvnw -Pnative -Dagent=true -pl music-studio-tag-jaudiotagger test`, then
  copy/merge the output (`native:metadata-copy` or manually). Review it and keep only `org.jaudiotagger` entries.
- `music-studio-cli/src/test/native/scan-smoke.sh` – builds `target/musicstudio`, copies fixtures to `mktemp -d`,
  runs `scan --recursive --format json`, compares with `expected-scan.json` (paths normalised), and checks the
  checksums

Tests: the smoke script locally on Linux (CI matrix later). If reading fails only in native mode, add the missing
metadata. Never "fix" it by adding reflection to our code.

### Step 13 – Story done
- tick the satisfied acceptance criteria in `docs/stories/001-scan-and-read-tags.md`
- update the status in `docs/stories/README.md`
- run the `tester` agent, then the `reviewer` agent (CLAUDE.md workflow)

## Acceptance criteria → tests

| Criterion | Proven by |
|---|---|
| all `.mp3` found, optional recursive, sorted | `ScanMediaServiceTest` (sort, recursive), `MediaFileSourceContract`, `ScanEndToEndTest` |
| all columns incl. tag versions | `TagReaderContract` (values), renderer golden tests |
| v1/v2 differ → both available, v2 primary, marker | `TagReadResultTest.conflicts`, `v11-v23-diff` fixture, JSON `layers[]` + `conflicts[]`, table `*` |
| files without tags listed | contract (`no-tags`), `ScanMediaServiceTest` |
| corrupt files listed with error, scan continues | contract (`corrupt`, `empty`), service + end-to-end tests |
| non-MP3 ignored, count reported | `ScanMediaServiceTest`, `ScanEndToEndTest` |
| (decision) hidden files skipped and listed as skipped | `MediaFileSourceContract`, `ScanMediaServiceTest`, `ScanEndToEndTest` |
| (decision) v1/v2 comparison mode selectable | `TagReadResultTest`, `ScanMediaServiceTest`, `ScanEndToEndTest` |
| reading never modifies a file | checksum + mtime assertions in `TagReaderContract`, `MediaFileSourceContract`, `ScanEndToEndTest`, native smoke |
| edge cases: Cyrillic/emoji, numeric genres, v2.2, 10 000+ files, symlinks/hidden/permissions | fixtures + `Id3v1GenresTest` + streaming test + `LocalMediaFileSourceLargeTreeTest` + contract symlink/hidden/permission tests |

## Data safety
Read-only. Proven by checksum/mtime assertions in the contract tests (which every reader and listing implementation,
fakes included, must pass) and in the end-to-end and native smoke tests. No write port exists yet.

## Risks to verify while implementing
- ArchUnit must read Java 25 class files (version 69). If 1.4.x cannot, upgrade ArchUnit; never drop the rules.
- jaudiotagger ID3v2.2 and UTF-16-without-BOM handling. Contract failures are bugs to work around in the adapter,
  not reasons to relax the contract.
- Native image: jaudiotagger's use of reflection and resources (genre/language tables) must be covered by the
  traced metadata.
