package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonSyntax;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A26/D01 with the real application: the story command on copied fixtures; the tree stays byte-identical. */
class ExtractEndToEndTest {

    @TempDir
    Path tempDir;

    private Path album;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void setUp() throws IOException {
        album = Files.createDirectories(tempDir.resolve("music/Best Of 2020"));
        Fixtures.copyTo(Fixture.V23_LATIN1, album, "Artist - Song - 2020.mp3");
        Fixtures.copyTo(Fixture.V24_UTF8, album, "Ђорђе Балашевић - Живот је море - 2019.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, album, "AC - DC - Thunderstruck - 1990.mp3");
        Fixtures.copyTo(Fixture.V1_0, album, "no separator.mp3");
        Fixtures.copyTo(Fixture.V23_COVER, album, "🎵 Emoji - Tune - 2021.mp3");
    }

    private int run(String... args) {
        MusicStudio studio = new MusicStudioFactory(new AppConfig(tempDir.resolve("cfg"), tempDir.resolve("data"),
                Clock.systemUTC())).create();
        return MusicStudioCommand.create(studio, new CancellationSource(), new PrintWriter(out), new PrintWriter(err))
                .execute(args);
    }

    private Map<String, Object> state() throws IOException {
        Map<String, Object> state = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(tempDir.resolve("music"))) {
            for (Path path : walk.toList()) {
                state.put(tempDir.relativize(path).toString(), Files.isDirectory(path)
                        ? Files.getLastModifiedTime(path).toMillis() : RoundTripSupport.snapshot(path));
            }
        }
        return state;
    }

    @Test
    void theStoryCommandShowsOneRowPerFileAndChangesNothing() throws IOException {
        Map<String, Object> before = state();
        int exit = run("extract", album.toString(), "--pattern", "{album}/{artist} - {title} - {year}", "--set",
                "genre=Pop");
        assertThat(exit).isEqualTo(ExitCode.OK);
        String table = out.toString();
        assertThat(table).contains("Artist - Song - 2020.mp3").contains("Best Of 2020").contains("Pop")
                .contains("Ђорђе Балашевић").contains("Живот је море").contains("NO MATCH")
                .contains("AMBIGUOUS").contains("5 file(s): 3 matched, 1 ambiguous").contains("1 no match")
                .contains("nothing was written");
        assertThat(err.toString()).isEmpty();
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void everyFormatAndOptionLeavesTheTreeUntouched() throws IOException {
        Map<String, Object> before = state();
        for (String format : new String[] {"table", "json", "csv"}) {
            out.getBuffer().setLength(0);
            int exit = run("extract", album.toString(), "-p", "{album}/{artist} - {title} - {year}", "-f", format,
                    "--accept-ambiguous", "--merge", "fill-empty", "--number-by", "name", "--number-total", "--set",
                    "genre=Balkan", "--strict");
            assertThat(exit).as(format).isEqualTo(ExitCode.ITEM_ERRORS); // the "no separator" file does not match
            if (format.equals("json")) {
                JsonSyntax.assertValid(out.toString());
            }
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test
    void jsonCarriesExistingTagValuesAsBeforeOfTheChanges() {
        assertThat(run("extract", album.toString(), "-p", "{album}/{artist} - {title} - {year}", "-f", "json"))
                .isEqualTo(ExitCode.OK);
        String json = out.toString();
        JsonSyntax.assertValid(json);
        assertThat(json).contains("\"before\": ").contains("\"after\": ").contains("\"origin\": \"PATTERN\"")
                .contains("\"matched\": 3").contains("\"ambiguous\": 1").contains("\"noMatch\": 1");
    }

    @Test
    void missingFolderIsAUsageError() {
        assertThat(run("extract", tempDir.resolve("nope").toString(), "-p", "{title}")).isEqualTo(ExitCode.USAGE);
    }
}
