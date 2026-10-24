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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Cases from the 002 review. */
class ExtractReviewTest {

    @TempDir
    Path tempDir;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        AppConfig config = new AppConfig(tempDir.resolve("c").toAbsolutePath(), tempDir.resolve("d").toAbsolutePath(),
                Clock.systemUTC());
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudioFactory(config).create(),
                Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    @Test
    void problemsOfTheCurrentTagsAreShown() {
        Path dir = tempDir.toAbsolutePath().resolve("m");
        Fixtures.copyTo(Fixture.V23_BAD_TRACK, dir, "Artist - Song.mp3");
        Fixtures.copyTo(Fixture.V11_V23_DIFF, dir, "Other - Name.mp3");
        run("extract", dir.toString(), "--pattern", "{artist} - {title}");
        assertThat(out.toString()).contains("current tags: TRACK: 'A1'").contains("artist: v1 'Artist B'");
        out.getBuffer().setLength(0);
        run("extract", dir.toString(), "--pattern", "{artist} - {title}", "--format", "json");
        assertThat(out.toString()).contains("\"readIssues\":[{\"severity\":\"warning\",\"code\":\"READ_VALUE_INVALID\"")
                .contains("\"v1Conflicts\":[\"artist\"]");
    }

    @Test
    void allOptionErrorsAreReportedTogether() {
        int code = run("extract", tempDir.toString(), "--pattern", "{foo}", "--set", "year=20x0");
        assertThat(code).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("unknown placeholder {foo}").contains("not a valid year");
    }

    @Test
    void repeatedKeepIsNoConflict() {
        int code = run("extract", tempDir.toString(), "--set", "genre=Pop", "--keep", "artist,artist");
        assertThat(err.toString()).doesNotContain("both --set and --keep");
        assertThat(code).isEqualTo(ExitCode.OK);
    }
}
