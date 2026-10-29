package dev.rs.slava.icxc.musicstudio.cli;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles.file;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonSyntax;
import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataService;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The {@code extract} command with a fake scan and the real core service: parsing, exit codes (Q2, Q4), formats,
 * and "no write path" (A12, A13, A18, A26-A28, D05, X08).
 */
class ExtractCommandTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private final List<ScanRequest> scanRequests = new ArrayList<>();
    private final List<ProposeRequest> proposeRequests = new ArrayList<>();
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private List<ScanItem> items = new ArrayList<>();
    private ScanStatus status = ScanStatus.COMPLETED;
    private List<Issue> scanIssues = List.of();

    private static ScannedFile f(String relative) {
        return file(relative);
    }

    private ScanSummary summary() {
        int read = (int) items.stream().filter(ScannedFile.class::isInstance).count();
        int failed = items.size() - read;
        if (status == ScanStatus.FAILED) {
            return new ScanSummary(ROOT, status, 0, 0, 0, 0, Map.of(), List.of(), scanIssues);
        }
        return new ScanSummary(ROOT, status, read + failed, read, failed, 0, Map.of(), List.of(), scanIssues);
    }

    private int run(String... args) {
        ScanMediaUseCase fakeScan = (request, sink, progress, token) -> {
            scanRequests.add(request);
            if (status != ScanStatus.FAILED) {
                items.forEach(sink);
            }
            return summary();
        };
        ProposeMetadataService real = new ProposeMetadataService();
        MusicStudio base = TestStudios.scanOnly(fakeScan);
        MusicStudio studio = new MusicStudio(fakeScan, request -> {
            proposeRequests.add(request);
            return real.propose(request);
        }, base.writeOptions(), base.writeTags());
        List<String> all = new ArrayList<>(List.of("extract", ROOT.toString()));
        all.addAll(List.of(args));
        return MusicStudioCommand.create(studio, Cancellation.NONE, new PrintWriter(out), new PrintWriter(err))
                .execute(all.toArray(String[]::new));
    }

    private ExtractionSpec spec() {
        assertThat(proposeRequests).hasSize(1);
        return proposeRequests.get(0).spec();
    }

    private List<String> rows() {
        return out.toString().lines().toList();
    }

    private String rowOf(String name) {
        return rows().stream().filter(l -> l.contains(name)).findFirst().orElseThrow(() -> new AssertionError(
                "no row for " + name + " in:\n" + out));
    }

    // ---- request mapping ----------------------------------------------------------------------------------------

    @Test
    void defaultsMapToTheUseCaseRequests() {
        items.add(f("A - B.mp3"));
        assertThat(run("--pattern", "{artist} - {title}")).isEqualTo(ExitCode.OK);
        ScanRequest scan = scanRequests.get(0);
        assertThat(scan.root()).isEqualTo(ROOT);
        assertThat(scan.recursive()).isFalse();
        assertThat(scan.includeHidden()).isFalse();
        assertThat(scan.formats()).containsExactly(MediaFormat.MP3);
        assertThat(scan.v1Comparison()).isEqualTo(V1Comparison.LENIENT);
        ExtractionSpec spec = spec();
        assertThat(spec.pattern().orElseThrow().source()).isEqualTo("{artist} - {title}");
        assertThat(spec.mergeMode()).isEqualTo(MergeMode.OVERWRITE);
        assertThat(spec.overrides()).isEmpty();
        assertThat(spec.autoNumbering()).isEmpty();
        assertThat(spec.acceptAmbiguous()).isFalse();
        assertThat(proposeRequests.get(0).edits()).isEqualTo(ManualEdits.NONE);
        assertThat(proposeRequests.get(0).files()).hasSize(1);
    }

    @Test
    void allOptionsReachTheRequests() {
        items.add(f("A - B.mp3"));
        run("-p", "{artist} - {title}", "-r", "--include-hidden", "--merge", "fill-empty", "--number-by", "tag",
                "--number-total", "--accept-ambiguous", "--set", "genre=Balkan", "--keep", "comment");
        assertThat(scanRequests.get(0).recursive()).isTrue();
        assertThat(scanRequests.get(0).includeHidden()).isTrue();
        ExtractionSpec spec = spec();
        assertThat(spec.mergeMode()).isEqualTo(MergeMode.FILL_EMPTY);
        assertThat(spec.acceptAmbiguous()).isTrue();
        assertThat(spec.autoNumbering()).contains(new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK, true));
        assertThat(spec.overrides()).containsEntry(Field.GENRE,
                new FieldOverride.Fixed(new FieldValue.Genres(List.of(new Genre("Balkan")))))
                .containsEntry(Field.COMMENT, new FieldOverride.KeepExisting());
    }

    @Test
    void numberByNameMapsToFileNameOrder() {
        run("--number-by", "name");
        assertThat(spec().autoNumbering()).contains(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, false));
    }

    @Test
    void setValuesAreTypedAndMayContainEquals() {
        run("--set", "year=2020", "--set", "track=3/12", "--set", "comment=a=b", "--set", "title=Ђорђе 🎵");
        Map<Field, FieldOverride> o = spec().overrides();
        assertThat(o).containsEntry(Field.YEAR, new FieldOverride.Fixed(new FieldValue.YearValue(2020)))
                .containsEntry(Field.TRACK, new FieldOverride.Fixed(new FieldValue.Position(3, OptionalInt.of(12))))
                .containsEntry(Field.COMMENT, new FieldOverride.Fixed(new FieldValue.Text("a=b")))
                .containsEntry(Field.TITLE, new FieldOverride.Fixed(new FieldValue.Text("Ђорђе 🎵")));
    }

    // ---- A12 ----------------------------------------------------------------------------------------------------

    @Test
    void a12AmbiguousRowsAreMarkedSkippedByDefaultAndAcceptedWithTheFlag() {
        items.add(f("Artist - Part - Song.mp3"));
        items.add(f("Clean - Song.mp3"));
        assertThat(run("-p", "{artist} - {title}")).isEqualTo(ExitCode.OK);
        String row = rowOf("Artist - Part - Song.mp3");
        assertThat(row).contains("AMBIGUOUS").doesNotContain("ACCEPTED").containsIgnoringCase("skipped");
        assertThat(rowOf("Clean - Song.mp3")).contains("MATCHED");
        assertThat(out.toString()).containsIgnoringCase("skipped");

        out.getBuffer().setLength(0);
        proposeRequests.clear();
        assertThat(run("-p", "{artist} - {title}", "--accept-ambiguous")).isEqualTo(ExitCode.OK);
        assertThat(rowOf("Artist - Part - Song.mp3")).contains("ACCEPTED").doesNotContain("AMBIGUOUS");
    }

    // ---- A13 ----------------------------------------------------------------------------------------------------

    @Test
    void a13FixedValuesAppearOnEveryMatchedRow() {
        items.add(f("A - B.mp3"));
        items.add(f("C - D.mp3"));
        assertThat(run("-p", "{artist} - {title}", "--set", "genre=Balkan", "--set", "albumartist=Various Artists"))
                .isEqualTo(ExitCode.OK);
        assertThat(rowOf("A - B.mp3")).contains("Balkan").contains("Various Artists");
        assertThat(rowOf("C - D.mp3")).contains("Balkan").contains("Various Artists");
    }

    @Test
    void a13BadSetValuesAreUsageErrorsBeforeAnyScan() {
        for (String bad : List.of("year=abc", "nofield=x", "genre", "=x", "track=-3", "year=", "genre=")) {
            out.getBuffer().setLength(0);
            err.getBuffer().setLength(0);
            assertThat(run("-p", "{title}", "--set", bad)).as(bad).isEqualTo(ExitCode.USAGE);
            assertThat(err.toString()).as(bad).contains("--set");
        }
        assertThat(run("-p", "{title}", "--set", "year=abc")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("year");
        assertThat(scanRequests).as("a usage error must not scan").isEmpty();
        assertThat(proposeRequests).isEmpty();
    }

    @Test
    void duplicateOrConflictingOverridesAreRejected() {
        assertThat(run("-p", "{title}", "--set", "genre=Pop", "--set", "genre=Rock")).isEqualTo(ExitCode.USAGE);
        assertThat(run("-p", "{title}", "--set", "genre=Pop", "--keep", "genre")).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void unknownKeepFieldIsAUsageError() {
        assertThat(run("-p", "{title}", "--keep", "colour")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("--keep");
    }

    // ---- A18 ----------------------------------------------------------------------------------------------------

    @Test
    void a18FillEmptyKeepsExistingValues() {
        items.add(file("New - Title.mp3", meta(Field.TITLE, "Old")));
        assertThat(run("-p", "{artist} - {title}", "--merge", "fill-empty", "-f", "csv")).isEqualTo(ExitCode.OK);
        String row = rowOf("New - Title.mp3");
        assertThat(row).contains(",Old,").contains("New").doesNotContain("Title,");
    }

    @Test
    void a18OverwriteIsTheDefault() {
        items.add(file("New - Title.mp3", meta(Field.TITLE, "Old")));
        assertThat(run("-p", "{artist} - {title}", "-f", "csv")).isEqualTo(ExitCode.OK);
        assertThat(rowOf("New - Title.mp3")).contains(",Title,").doesNotContain("Old");
    }

    @Test
    void a18BadMergeAndNumberByValuesAreUsageErrors() {
        assertThat(run("-p", "{title}", "--merge", "bogus")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("--merge");
        assertThat(run("-p", "{title}", "--number-by", "bogus")).isEqualTo(ExitCode.USAGE);
        assertThat(run("-p", "{title}", "--number-total")).isEqualTo(ExitCode.USAGE);
    }

    // ---- Q2 / Q4 ------------------------------------------------------------------------------------------------

    @Test
    void q2NoMatchRowsDoNotChangeTheExitCodeUnlessStrict() {
        items.add(f("A - B.mp3"));
        items.add(f("nothing.mp3"));
        assertThat(run("-p", "{artist} - {title}")).isEqualTo(ExitCode.OK);
        assertThat(rowOf("nothing.mp3")).contains("NO MATCH").contains("separator");
        assertThat(run("-p", "{artist} - {title}", "--strict")).isEqualTo(ExitCode.ITEM_ERRORS);
    }

    @Test
    void q2StrictAlsoFailsOnAmbiguousButNotOnAcceptedOrClean() {
        items.add(f("A - B - C.mp3"));
        assertThat(run("-p", "{artist} - {title}", "--strict")).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(run("-p", "{artist} - {title}", "--strict", "--accept-ambiguous")).isEqualTo(ExitCode.OK);
        items.clear();
        items.add(f("A - B.mp3"));
        assertThat(run("-p", "{artist} - {title}", "--strict")).isEqualTo(ExitCode.OK);
    }

    @Test
    void q4PatternIsOptionalWithSetKeepOrNumberBy() {
        items.add(f("a.mp3"));
        assertThat(run("--set", "genre=Pop")).isEqualTo(ExitCode.OK);
        assertThat(rowOf("a.mp3")).contains("Pop");
        assertThat(run("--keep", "artist")).isEqualTo(ExitCode.OK);
        assertThat(run("--number-by", "name")).isEqualTo(ExitCode.OK);
        assertThat(proposeRequests).hasSize(3);
        assertThat(proposeRequests).allSatisfy(r -> assertThat(r.spec().pattern()).isEmpty());
    }

    @Test
    void q4NothingToDoIsAUsageError() {
        assertThat(run()).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("--pattern");
        assertThat(scanRequests).isEmpty();
        assertThat(run("--merge", "fill-empty", "-r")).isEqualTo(ExitCode.USAGE);
    }

    // ---- A28 / D05 ----------------------------------------------------------------------------------------------

    @Test
    void a28InvalidPatternGivesExit2WithColumn() {
        assertThat(run("-p", "{artist} - {titel}")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("column 12").contains("titel");
        assertThat(out.toString()).isEmpty();
        assertThat(scanRequests).isEmpty();
    }

    @Test
    void a28AllPatternErrorsAreReported() {
        assertThat(run("-p", "{foo} - {bar}")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("column 1").contains("column 9");
    }

    @Test
    void a28EmptyAndAdjacentPatternsAreUsageErrors() {
        assertThat(run("-p", "")).isEqualTo(ExitCode.USAGE);
        assertThat(run("-p", "{artist}{title}")).isEqualTo(ExitCode.USAGE);
        assertThat(run("-p", "{album}//{title}")).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void a28CliTextOfColumnsCountsCodePointsAsAUserSeesThem() {
        assertThat(run("-p", "🎵 {foo}")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("column 3");
    }

    @Test
    void a28ThereIsNoApplyOption() {
        items.add(f("A - B.mp3"));
        assertThat(run("-p", "{artist} - {title}", "--apply")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("--apply");
        assertThat(scanRequests).isEmpty();
        assertThat(proposeRequests).isEmpty();
    }

    @Test
    void d05NoWriteLikeOptionsExist() {
        MusicStudio studio = TestStudios.scanOnly((request, sink, progress, token) -> summary());
        var extract = MusicStudioCommand.create(studio, Cancellation.NONE, new PrintWriter(out), new PrintWriter(err))
                .getSubcommands().get("extract");
        List<String> names = extract.getCommandSpec().options().stream().flatMap(o -> java.util.Arrays.stream(o.names()))
                .toList();
        assertThat(names).doesNotContain("--apply", "--write", "--force", "--yes", "--in-place", "--output", "-o",
                "--dry-run");
        assertThat(names).contains("--pattern", "--set", "--keep", "--merge", "--number-by", "--number-total",
                "--accept-ambiguous", "--strict", "--format");
    }

    @Test
    void patternWithExtensionPrintsAWarningButRuns() {
        items.add(f("A - B.mp3"));
        assertThat(run("-p", "{artist} - {title}.mp3")).isEqualTo(ExitCode.OK);
        assertThat(err.toString()).contains("warning").contains(".mp3");
        assertThat(rowOf("A - B.mp3")).contains("NO MATCH");
    }

    // ---- scan outcomes ------------------------------------------------------------------------------------------

    @Test
    void missingFolderIsAUsageErrorAndOtherFailuresAreAborted() {
        status = ScanStatus.FAILED;
        scanIssues = List.of(Issue.error(IssueCode.ROOT_NOT_FOUND, "no such directory: /music"));
        assertThat(run("-p", "{title}")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("no such directory");
        scanIssues = List.of(Issue.error(IssueCode.LISTING_ACCESS_DENIED, "cannot read directory: /music"));
        assertThat(run("-p", "{title}")).isEqualTo(ExitCode.ABORTED);
        assertThat(proposeRequests).isEmpty();
    }

    @Test
    void cancelledScanGivesExit130WithoutOutput() {
        status = ScanStatus.CANCELLED;
        assertThat(run("-p", "{title}")).isEqualTo(ExitCode.CANCELLED);
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void unreadableFilesAreListedAndGiveExit1() {
        items.add(f("A - B.mp3"));
        items.add(new UnreadableFile(ROOT.resolve("broken.mp3"), MediaFormat.MP3,
                Issue.error(IssueCode.READ_FAILED, "bad header")));
        assertThat(run("-p", "{artist} - {title}")).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(out.toString()).contains("broken.mp3").contains("bad header");
        assertThat(rowOf("A - B.mp3")).contains("MATCHED");
    }

    @Test
    void emptyFolderGivesAnEmptyPreview() {
        assertThat(run("-p", "{title}")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("0 file(s)");
        assertThat(run("-p", "{title}", "-f", "json")).isEqualTo(ExitCode.OK);
        JsonSyntax.assertValid(out.toString().substring(out.toString().lastIndexOf("{\"root\"")));
    }

    // ---- A26 / A27 (formats) ------------------------------------------------------------------------------------

    @Test
    void a26TableHasARowPerFileWithExtractedValuesAndTheSummary() {
        items.add(f("Best Of 2020/Artist - Song - 2020.mp3"));
        items.add(f("Best Of 2020/Other - Tune - 2019.mp3"));
        items.add(f("Best Of 2020/broken name.mp3"));
        assertThat(run("-r", "-p", "{album}/{artist} - {title} - {year}", "--set", "genre=Pop")).isEqualTo(ExitCode.OK);
        String first = rowOf("Artist - Song - 2020.mp3");
        assertThat(first).contains("MATCHED").contains("Best Of 2020").contains("Artist").contains("Song")
                .contains("2020").contains("Pop");
        assertThat(rowOf("Other - Tune - 2019.mp3")).contains("Other").contains("Tune").contains("2019");
        assertThat(rowOf("broken name.mp3")).contains("NO MATCH").doesNotContain("Pop");
        assertThat(out.toString()).contains("3 file(s): 2 matched, 0 ambiguous").contains("1 no match")
                .contains("nothing was written");
        assertThat(rows().get(0)).contains("STATUS", "PATH", "TITLE", "ARTIST", "ALBUM", "YEAR", "TRACK", "GENRE");
    }

    @Test
    void a27JsonIsValidAndHasStatusExtractedAndChanges() {
        items.add(file("Альбом/Ђорђе - Живот \"је\" море, 🎵.mp3", meta(Field.COMMENT, "keep\nme")));
        items.add(f("nothing.mp3"));
        assertThat(run("-r", "-p", "{album}/{artist} - {title}", "-f", "json", "--set", "genre=Pop"))
                .isEqualTo(ExitCode.OK);
        String json = out.toString();
        JsonSyntax.assertValid(json);
        assertThat(json).contains("\"status\": \"MATCHED\"").contains("\"status\": \"NO_MATCH\"")
                .contains("\"extracted\": {").contains("\"changes\": [").contains("\"origin\": \"FIXED\"")
                .contains("Ђорђе").contains("🎵").contains("\\\"је\\\"").contains("\"exportable\": false")
                .contains("\"matched\": 1").contains("\"noMatch\": 1");
    }

    @Test
    void a27CsvHasHeaderAndRfc4180Quoting() {
        items.add(f("Alb/Ђорђе, \"Ђ\" - Song, Live.mp3"));
        items.add(f("Alb/Plain - Simple.mp3"));
        assertThat(run("-r", "-p", "{album}/{artist} - {title}", "-f", "csv")).isEqualTo(ExitCode.OK);
        String csv = out.toString();
        assertThat(csv.lines().findFirst().orElseThrow())
                .startsWith("status,path,title,artist,albumartist,album,year,track,disc,genre,comment,detail,changed");
        assertThat(csv).contains("\"Ђорђе, \"\"Ђ\"\"\"").contains("\"Song, Live\"");
        assertThat(csv).contains("\r\n");
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains(",Simple,Plain,");
    }

    @Test
    void a27CsvQuotesEmbeddedNewlines() {
        items.add(f("Alb/A\nB - T.mp3"));
        assertThat(run("-r", "-p", "{album}/{artist} - {title}", "-f", "csv")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("\"Alb/A\nB - T.mp3\"");
    }

    @Test
    void unknownFormatIsRejected() {
        assertThat(run("-p", "{title}", "-f", "xml")).isEqualTo(ExitCode.USAGE);
        assertThat(out.toString()).isEmpty();
    }

    // ---- X08 ----------------------------------------------------------------------------------------------------

    @Test
    void x08WideAndSupplementaryCharactersAreNeverCutInsideASurrogatePair() {
        String emoji = "🎵".repeat(60);
        items.add(f(emoji + " - " + emoji + ".mp3"));
        items.add(f("Ђорђе Балашевић - Живот је море.mp3"));
        items.add(f("Μίκης Θεοδωράκης - Ένα το χελιδόνι.mp3"));
        assertThat(run("-p", "{artist} - {title}")).isEqualTo(ExitCode.OK);
        for (String line : rows()) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    assertThat(i + 1 < line.length() && Character.isLowSurrogate(line.charAt(i + 1)))
                            .as("high surrogate without low in: " + line).isTrue();
                    i++;
                } else {
                    assertThat(Character.isLowSurrogate(c)).as("lone low surrogate in: " + line).isFalse();
                }
            }
        }
    }

    @Test
    void x08CyrillicAndGreekKeepTheColumnsAligned() {
        items.add(f("Ђорђе Балашевић - Живот је море.mp3"));
        items.add(f("Μίκης Θεοδωράκης - Ένα το χελιδόνι.mp3"));
        items.add(f("Latin - Plain.mp3"));
        assertThat(run("-p", "{artist} - {title}")).isEqualTo(ExitCode.OK);
        List<String> lines = rows();
        int header = lines.get(0).indexOf("ARTIST");
        assertThat(header).isPositive();
        for (String name : List.of("Ђорђе", "Μίκης", "Latin")) {
            String line = lines.stream().filter(l -> l.contains(name)).findFirst().orElseThrow();
            // the artist cell starts at the same offset as the ARTIST header: titles are in the column before it
            int titleStart = lines.get(0).indexOf("TITLE");
            assertThat(line.indexOf(name, header)).as(line).isEqualTo(header);
        }
    }

    @Test
    void theCommandHasNoLogicOfItsOwnForPatternsOrValues() {
        // all work is delegated: the use case saw exactly the files the scan emitted, in scan order
        ScannedFile a = f("b - 2.mp3");
        ScannedFile b = f("a - 1.mp3");
        items.add(a);
        items.add(b);
        run("-p", "{artist} - {title}");
        assertThat(proposeRequests.get(0).files()).containsExactly(a, b);
    }
}
