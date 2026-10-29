package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonSyntax;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The real application (real folder listing, real jaudiotagger) driven through the command line. */
class ScanEndToEndTest {

    @TempDir
    Path tempDir;

    private Path music;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void setUp() throws IOException {
        music = Files.createDirectories(tempDir.resolve("music"));
        for (Fixture fixture : Fixture.values()) {
            Fixtures.copyTo(fixture, music);
        }
        Files.writeString(music.resolve("cover.jpg"), "not really a jpeg");
        Files.writeString(music.resolve("notes.txt"), "hello");
        Files.writeString(music.resolve("._v1_0.mp3"), "macOS resource fork");
        Files.createDirectories(music.resolve(".cache"));
        Files.writeString(music.resolve(".cache/hidden.mp3"), "x");
    }

    private int run(String... args) {
        MusicStudio studio = new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"),
                Clock.systemUTC())).create();
        return MusicStudioCommand.create(studio, new CancellationSource(), new PrintWriter(out), new PrintWriter(err))
                .execute(args);
    }

    private static Map<String, String> checksums(Path dir) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                result.put(dir.relativize(file).toString(), Fixtures.sha256(file) + " "
                        + Files.getLastModifiedTime(file).toMillis());
            }
        }
        return result;
    }

    private List<String> expectedMp3Names() {
        return java.util.Arrays.stream(Fixture.values()).map(Fixture::fileName)
                .sorted(java.util.Comparator.comparing(Path::of, PathOrder.COMPARATOR)).toList();
    }

    @Test
    void tableListsEveryMp3SortedAndReportsWhatWasNotScanned() throws Exception {
        Map<String, String> before = checksums(music);
        int code = run("scan", music.toString());
        String table = out.toString();

        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS); // corrupt.mp3 and empty.mp3
        List<String> rows = table.lines().filter(line -> line.contains(".mp3")).toList();
        List<String> shownNames = rows.stream().map(line -> line.substring(0, line.indexOf(".mp3") + 4).strip())
                .filter(name -> !name.startsWith("Skipped")).toList();
        assertThat(shownNames.subList(0, expectedMp3Names().size())).containsExactlyElementsOf(expectedMp3Names());
        assertThat(table).contains("20 files found").contains("18 read").contains("2 unreadable")
                .contains("2 ignored (jpg: 1, txt: 1)");
        assertThat(table).contains("Skipped (not scanned):").contains("hidden  ._v1_0.mp3").contains("hidden  .cache");
        assertThat(rows).filteredOn(line -> line.startsWith("corrupt.mp3") || line.startsWith("empty.mp3"))
                .hasSize(2).allMatch(line -> line.contains("ERROR:"));
        assertThat(rows).filteredOn(line -> line.startsWith("no-tags.mp3")).singleElement()
                .satisfies(line -> assertThat(line).contains("none"));
        assertThat(table).contains("Café au lait").contains("Ђорђе Балашевић").contains("ID3v1.1 + ID3v2.3");
        assertThat(checksums(music)).isEqualTo(before); // reading never modifies a file
    }

    @Test
    void v1V2ConflictIsMarkedAndTheV1ComparisonModeIsSelectable() {
        run("scan", music.toString());
        String lenient = out.toString();
        assertThat(lenient.lines().filter(l -> l.startsWith("v11-v23-diff.mp3")).findFirst().orElseThrow())
                .contains("Artist Two*").endsWith("artist");
        assertThat(lenient.lines().filter(l -> l.startsWith("v11-v23-same.mp3")).findFirst().orElseThrow())
                .doesNotContain("*");

        out.getBuffer().setLength(0);
        run("scan", music.toString(), "--strict-v1-compare");
        assertThat(out.toString().lines().filter(l -> l.startsWith("v11-v23-same.mp3")).findFirst().orElseThrow())
                .contains("*").endsWith("title,artist,album");
    }

    @Test
    void hiddenFilesAreScannedOnRequest() {
        run("scan", music.toString(), "--include-hidden");
        assertThat(out.toString()).contains("._v1_0.mp3").doesNotContain("Skipped (not scanned)");
    }

    @Test
    void recursiveScanFindsFilesInSubfolders() throws Exception {
        Path sub = Files.createDirectories(music.resolve("Artist/Album"));
        Fixtures.copyTo(Fixture.V23_LATIN1, sub, "01 - deep.mp3");
        run("scan", music.toString());
        assertThat(out.toString()).doesNotContain("01 - deep.mp3");
        out.getBuffer().setLength(0);
        run("scan", music.toString(), "--recursive");
        assertThat(out.toString()).contains("Artist/Album/01 - deep.mp3".replace('/', java.io.File.separatorChar));
    }

    @Test
    void jsonOutputIsValidAndCompleteAndChangesNothing() throws Exception {
        Map<String, String> before = checksums(music);
        run("scan", music.toString(), "--format", "json", "--recursive");
        String json = out.toString();
        JsonSyntax.assertValid(json);
        assertThat(json).contains("\"found\": 20").contains("\"read\": 18").contains("\"failed\": 2")
                .contains("\"ignoredByExtension\": {\"jpg\": 1, \"txt\": 1}")
                .contains("\"reason\": \"HIDDEN\"").contains("\"status\": \"error\"")
                .contains("\"version\": \"ID3v1.1\"").contains("\"version\": \"ID3v2.3\"")
                .contains("\"conflicts\": [\"artist\"]");
        assertThat(checksums(music)).isEqualTo(before);
    }

    @Test
    void csvIsOneRowPerMp3AndStdoutStaysClean() {
        run("scan", music.toString(), "--format", "csv");
        List<String> lines = out.toString().lines().toList();
        assertThat(lines).hasSize(1 + 20);
        assertThat(lines.get(0)).startsWith("path,title");
        assertThat(out.toString()).doesNotContain("skipped");
        assertThat(err.toString()).contains("skipped: hidden").contains("completed: 20 found, 18 read, 2 unreadable");
    }

    @Test
    void missingFolderIsAUsageError() {
        assertThat(run("scan", tempDir.resolve("nowhere").toString())).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("error:").contains("nowhere");
    }

    @Test
    void aFileInsteadOfAFolderIsAUsageError() {
        assertThat(run("scan", music.resolve("notes.txt").toString())).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void anEmptyFolderScansCleanly() throws IOException {
        Path empty = Files.createDirectories(tempDir.resolve("empty"));
        assertThat(run("scan", empty.toString())).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("0 files found");
    }

    @Test
    void theListOfExpectedNamesIsComplete() {
        assertThat(new ArrayList<>(expectedMp3Names())).hasSize(20);
    }
}
