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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** {@code extract} with the real wiring over real files: preview only, nothing changes anywhere. */
class ExtractEndToEndTest {

    private static final String PATTERN = "{album}/{artist} - {title} - {year}";

    @TempDir
    Path tempDir;

    private Path album;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void files() {
        album = tempDir.toAbsolutePath().resolve("music").resolve("Best Of 2020");
        Fixtures.copyTo(Fixture.V23_LATIN1, album, "Artist - Song - 2020.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, album, "Jay-Z - Track - 2019.mp3");
        Fixtures.copyTo(Fixture.V1_1, album, "A - B - C - 2020.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, album, "Bad Name.mp3");
        Fixtures.copyTo(Fixture.V23_UTF16_CYRILLIC, album, "Ђорђе - Песма - 1986.mp3");
        Fixtures.copyTo(Fixture.CORRUPT, album, "Broken - X - 2000.mp3");
    }

    /** Names, SHA-256 and mtime of everything below the temp dir (config/data folders included). */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(tempDir)) {
            for (Path path : paths.toList()) {
                snapshot.put(tempDir.relativize(path).toString(), Files.isRegularFile(path)
                        ? Fixtures.sha256(path) + "@" + Files.getLastModifiedTime(path) : "dir");
            }
        }
        return snapshot;
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

    private String row(String file) {
        return out.toString().lines().filter(line -> line.startsWith(file)).findFirst().orElseThrow();
    }

    @Test
    void previewShowsEveryStatusAndChangesNothing() throws IOException {
        Map<String, String> before = snapshot();

        int code = run("extract", album.toString(), "--pattern", PATTERN, "--set", "genre=Pop");

        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(row("Artist - Song - 2020.mp3")).contains(" ok ").contains("Song*").contains("Best Of 2020*");
        assertThat(row("Jay-Z - Track - 2019.mp3")).contains(" ok ").contains("Jay-Z*").contains("2019*");
        assertThat(row("Ђорђе - Песма - 1986.mp3")).contains(" ok ").contains("Песма*");
        assertThat(row("A - B - C - 2020.mp3")).contains("AMBIGUOUS").contains("also possible")
                .contains("--accept-ambiguous");
        assertThat(row("Bad Name.mp3")).contains("NO MATCH").contains("\" - \"");
        assertThat(out.toString()).contains("Unreadable (not processed):").contains("Broken - X - 2000.mp3")
                .contains("5 files: 3 matched, 1 ambiguous (1 skipped – use --accept-ambiguous), 1 no match.")
                .endsWith("Preview only – no file was changed." + System.lineSeparator());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void acceptAmbiguous() throws IOException {
        Map<String, String> before = snapshot();
        run("extract", album.toString(), "--pattern", PATTERN, "--accept-ambiguous");
        assertThat(row("A - B - C - 2020.mp3")).contains("accepted").doesNotContain("--accept-ambiguous");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void jsonIsWellFormedAndComplete() throws IOException {
        Map<String, String> before = snapshot();
        run("extract", album.toString(), "--pattern", PATTERN, "--format", "json");
        String json = out.toString();
        assertThat(json).startsWith("{\"root\":").contains("\"items\":[")
                .contains("\"status\":\"ambiguous\"").contains("\"status\":\"no_match\"")
                .contains("\"unreadable\":[{\"path\":\"Broken - X - 2000.mp3\"")
                .contains("\"summary\":{\"files\":5,\"matched\":3,\"ambiguous\":1,\"noMatch\":1,\"exportable\":3");
        assertThat(balanced(json)).isTrue();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void autoNumberingInTheRealWiring() {
        run("extract", album.toString(), "--auto-number", "filename", "--track-total");
        List<String> rows = out.toString().lines().filter(l -> l.endsWith("*") || l.contains("/6")).toList();
        assertThat(out.toString()).contains("1/5*").contains("5/5*");
        assertThat(rows).isNotEmpty();
    }

    /** Braces and brackets outside strings are balanced. */
    private static boolean balanced(String json) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inString;
    }
}
