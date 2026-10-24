package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** The real wiring (jaudiotagger + local disk) over every fixture, through the command line. */
class ScanEndToEndTest {

    @TempDir
    Path tempDir;

    private Path music;
    private Map<Path, String> checksums;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void copyFixtures() throws IOException {
        music = tempDir.toAbsolutePath().resolve("music");
        for (Fixture fixture : Fixture.values()) {
            Fixtures.copyTo(fixture, music);
        }
        Files.writeString(music.resolve("cover.jpg"), "not really a jpeg");
        Files.writeString(music.resolve("notes.txt"), "notes");
        Fixtures.copyTo(Fixture.V23_LATIN1, music, "._x.mp3");
        Fixtures.copyTo(Fixture.V23_LATIN1, music.resolve(".cache"), "cached.mp3");
        checksums = checksums();
    }

    private Map<Path, String> checksums() throws IOException {
        Map<Path, String> sums = new HashMap<>();
        try (Stream<Path> files = Files.walk(music)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                sums.put(file, Fixtures.sha256(file) + "@" + Files.getLastModifiedTime(file));
            }
        }
        return sums;
    }

    private int run(String... args) {
        AppConfig config = new AppConfig(tempDir.resolve("config").toAbsolutePath(),
                tempDir.resolve("data").toAbsolutePath(), Clock.systemUTC());
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudioFactory(config).create(),
                Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    private List<String> lines() {
        return out.toString().lines().toList();
    }

    @Test
    void tableShowsEveryMp3SortedWithErrorsIgnoredAndSkippedEntries() throws IOException {
        int code = run("scan", music.toString(), "--recursive");

        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS); // corrupt + empty
        List<String> rows = lines().stream().skip(2).takeWhile(line -> !line.isBlank()).toList();
        List<String> expected = Stream.of(Fixture.values()).map(Fixture::fileName)
                .sorted(String::compareToIgnoreCase).toList();
        assertThat(rows).hasSize(expected.size());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i)).startsWith(expected.get(i));
        }
        assertThat(rows).filteredOn(r -> r.startsWith("corrupt.mp3") || r.startsWith("empty.mp3"))
                .hasSize(2).allMatch(r -> r.contains("ERROR:"));
        assertThat(rows).filteredOn(r -> r.startsWith("v11-v23-same.mp3")).singleElement()
                .asString().contains("ID3v1.1 + ID3v2.3").doesNotContain("*");
        assertThat(rows).filteredOn(r -> r.startsWith("v11-v23-diff.mp3")).singleElement()
                .asString().contains("Artist A*").contains("artist: v1 'Artist B'");
        assertThat(rows).filteredOn(r -> r.startsWith("v23-bad-track.mp3")).singleElement()
                .asString().contains("'A1'");
        assertThat(rows).filteredOn(r -> r.startsWith("v23-utf16-cyrillic.mp3")).singleElement()
                .asString().contains("Ђорђе Балашевић").contains("Љубав 🎵");
        assertThat(out.toString())
                .contains("16 MP3 files: 14 read, 2 unreadable.")
                .contains("Ignored 2 other files (jpg: 1, txt: 1).")
                .contains("Skipped (not scanned):")
                .containsPattern("hidden +\\._x\\.mp3")
                .containsPattern("hidden +\\.cache");
        assertThat(checksums()).isEqualTo(checksums);
    }

    @Test
    void strictComparisonFlagsTransliteratedV1() {
        run("scan", music.toString(), "--strict-v1-compare");
        assertThat(lines()).filteredOn(r -> r.startsWith("v11-v23-same.mp3")).singleElement()
                .asString().contains("Ђорђе Балашевић*").contains("artist: v1 'Djordje Balasevic'");
    }

    @Test
    void includeHiddenScansHiddenFiles() {
        run("scan", music.toString(), "--recursive", "--include-hidden");
        assertThat(out.toString()).contains("._x.mp3").contains(".cache/cached.mp3")
                .contains("18 MP3 files").doesNotContain("Skipped (not scanned)");
    }

    @Test
    void jsonAndCsvDoNotModifyFilesEither() throws IOException {
        assertThat(run("scan", music.toString(), "--recursive", "--format", "json")).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(out.toString()).startsWith("{\"root\":").contains("\"status\":\"completed\"")
                .contains("\"skipped\":[{\"path\":\"._x.mp3\",\"reason\":\"hidden\"}");
        assertThat(run("scan", music.toString(), "--format", "csv")).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(err.toString()).contains("skipped: hidden ._x.mp3");
        assertThat(checksums()).isEqualTo(checksums);
    }

    @Test
    void missingFolderIsAUsageError() {
        assertThat(run("scan", music.resolve("missing").toString())).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("Error:").contains("missing");
        assertThat(out.toString()).isEmpty();
    }
}
