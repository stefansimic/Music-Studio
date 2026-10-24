package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Story 001 criterion "for each file I see: path, title, artist, album artist, album, track (n/total), disc (n/total),
 * year, genre, comment, has-cover, duration, bitrate, tag versions" on real files, in all three output formats.
 */
class ScanColumnsEndToEndTest {

    @TempDir
    Path tempDir;

    private Path music;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void copyFixtures() {
        music = tempDir.toAbsolutePath().resolve("music");
        for (Fixture fixture : List.of(Fixture.V23_LATIN1, Fixture.V23_COVER, Fixture.NO_TAGS, Fixture.V11_V23_DIFF,
                Fixture.CORRUPT, Fixture.V1_1)) {
            Fixtures.copyTo(fixture, music);
        }
    }

    private String run(String format) {
        AppConfig config = new AppConfig(tempDir.resolve("config").toAbsolutePath(),
                tempDir.resolve("data").toAbsolutePath(), Clock.systemUTC());
        CommandLine cli = MusicStudioCommand.commandLine(new MusicStudioFactory(config).create(), Cancellation.NONE);
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        cli.execute("scan", music.toString(), "--format", format);
        return out.toString();
    }

    private static String row(String output, String prefix) {
        return output.lines().filter(l -> l.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no row " + prefix + " in\n" + output));
    }

    @Test
    void tableHasEveryColumnAndValues() {
        String table = run("table");
        assertThat(table.lines().findFirst().orElseThrow()).contains("PATH", "TITLE", "ARTIST", "ALBUM ARTIST",
                "ALBUM", "TRACK", "DISC", "YEAR", "GENRE", "COMMENT", "COVER", "TIME", "KBPS", "TAGS");
        assertThat(row(table, "v23-latin1.mp3")).contains("Café Song", "Album Artist", "3/12", "1/2", "2001", "Pop",
                "A comment", "0:01", "128", "ID3v2.3");
        assertThat(row(table, "v23-cover.mp3")).containsPattern("\\byes\\b");
        assertThat(row(table, "v23-latin1.mp3")).containsPattern("\\bno\\b");
        assertThat(row(table, "v1_1.mp3")).contains("ID3v1.1", "7", "Rock");
        assertThat(row(table, "no-tags.mp3")).as("untagged file listed with empty fields").contains("0:01", "128")
                .doesNotContain("ERROR");
        assertThat(row(table, "corrupt.mp3")).contains("ERROR:");
        assertThat(row(table, "v11-v23-diff.mp3")).contains("ID3v1.1 + ID3v2.3", "Artist A*");
    }

    @Test
    void jsonCarriesEveryFieldBothLayersAndTheConflict() {
        String json = run("json");
        String latin1 = row(json, "{\"path\":\"v23-latin1.mp3\"");
        assertThat(latin1).contains("\"title\":\"Café Song\"", "\"artist\":\"Artist\"",
                "\"albumartist\":\"Album Artist\"", "\"album\":\"Album\"", "\"year\":2001",
                "\"track\":{\"number\":3,\"total\":12}", "\"disc\":{\"number\":1,\"total\":2}",
                "\"genre\":[\"Pop\"]", "\"comment\":\"A comment\"", "\"bitrateKbps\":128",
                "\"versions\":[\"ID3v2.3\"]", "\"cover\":null");
        assertThat(row(json, "{\"path\":\"v23-cover.mp3\"")).doesNotContain("\"cover\":null");
        assertThat(row(json, "{\"path\":\"v11-v23-diff.mp3\"")).contains("\"conflicts\":[\"artist\"]",
                "\"effective\":{\"title\":\"Same Title\",\"artist\":\"Artist A\"", "\"artist\":\"Artist B\"");
        assertThat(row(json, "{\"path\":\"no-tags.mp3\"")).contains("\"layers\":[]", "\"effective\":{}");
        assertThat(row(json, "{\"path\":\"corrupt.mp3\"")).contains("\"error\":{");
    }

    @Test
    void csvHasAHeaderWithEveryColumnAndEmptyFieldsForUntaggedFiles() {
        String csv = run("csv");
        List<String> header = List.of(csv.lines().findFirst().orElseThrow().split(","));
        assertThat(header).contains("path", "title", "artist", "albumartist", "album", "track", "disc", "year",
                "genre", "comment", "cover", "duration", "bitrate", "tags");
        String noTags = row(csv, "no-tags.mp3,");
        int fieldColumns = header.indexOf("comment");
        assertThat(noTags).startsWith("no-tags.mp3" + ",".repeat(fieldColumns));
        assertThat(row(csv, "v23-latin1.mp3,")).contains("Café Song", "3/12", "1/2");
        assertThat(csv).as("CRLF per RFC 4180").contains("\r\n");
    }
}
