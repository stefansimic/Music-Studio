package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/** X05: {@link Id3OptionsMixin} through a tiny test command. */
class Id3OptionsMixinTest {

    @Command(name = "probe")
    static class Probe implements Callable<Integer> {
        @Mixin
        Id3OptionsMixin id3;

        TagWriteOptions parsed;

        @Override
        public Integer call() {
            parsed = id3.toOptions();
            return 0;
        }
    }

    private record Run(int exit, TagWriteOptions options, String err) {}

    private static Run run(String... args) {
        Probe probe = new Probe();
        StringWriter err = new StringWriter();
        CommandLine cli = new CommandLine(probe);
        cli.setErr(new PrintWriter(err));
        cli.setOut(new PrintWriter(new StringWriter()));
        int exit = cli.execute(args);
        return new Run(exit, probe.parsed, err.toString());
    }

    @Test
    void defaultsAreTheStoryDefaults() {
        Run run = run();
        assertThat(run.exit()).isZero();
        assertThat(run.options()).isEqualTo(TagWriteOptions.DEFAULT);
        assertThat(run.options().v1NonLatinPolicy()).isEqualTo(V1NonLatinPolicy.SKIP_V1);
    }

    @Test
    void versionsParse() {
        Run run = run("--id3v2", "2.3", "--id3v1", "1.1");
        assertThat(run.options().id3v2()).isEqualTo(Id3v2Choice.V2_3);
        assertThat(run.options().id3v1()).isEqualTo(Id3v1Choice.V1_1);
        Run v24 = run("--id3v2", "2.4", "--id3v1", "1.0", "--v2-encoding", "utf8", "--v1-mode", "latin1");
        assertThat(v24.exit()).isZero();
        assertThat(v24.options().id3v2()).isEqualTo(Id3v2Choice.V2_4);
        assertThat(v24.options().id3v1()).isEqualTo(Id3v1Choice.V1_0);
        assertThat(v24.options().v2Encoding()).isEqualTo(V2Encoding.UTF8);
        assertThat(v24.options().v1TextMode()).isEqualTo(V1TextMode.LATIN1);
        assertThat(run("--id3v2", "2.2").options().id3v2()).isEqualTo(Id3v2Choice.V2_2);
    }

    @Test
    void nonLatinPolicyValuesParseCaseInsensitively() {
        assertThat(run("--v1-non-latin", "skip").options().v1NonLatinPolicy()).isEqualTo(V1NonLatinPolicy.SKIP_V1);
        assertThat(run("--v1-non-latin", "codepage").options().v1NonLatinPolicy())
                .isEqualTo(V1NonLatinPolicy.CODEPAGE);
        assertThat(run("--v1-non-latin", "TRANSLITERATE").options().v1NonLatinPolicy())
                .isEqualTo(V1NonLatinPolicy.TRANSLITERATE);
    }

    @Test
    void noneIsAcceptedAndRemoveFlagWorks() {
        Run run = run("--id3v2", "none", "--remove-other-versions");
        assertThat(run.exit()).isZero();
        assertThat(run.options().id3v2()).isEqualTo(Id3v2Choice.NONE);
        assertThat(run.options().removeOtherVersions()).isTrue();
        assertThat(run("--id3v1", "none").options().id3v1()).isEqualTo(Id3v1Choice.NONE);
    }

    @Test
    void badValueIsAUsageErrorNamingTheOptionAndTheChoices() {
        Run run = run("--id3v2", "2.5");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("--id3v2").contains("2.5").contains("2.3");
        Run policy = run("--v1-non-latin", "guess");
        assertThat(policy.exit()).isEqualTo(2);
        assertThat(policy.err()).contains("--v1-non-latin").contains("transliterate");
    }

    @Test
    void invalidCombinationsAreAUsageErrorWithTheCoreMessage() {
        Run run = run("--id3v2", "2.3", "--v2-encoding", "utf8");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("UTF8").contains("V2_4");
        Run none = run("--id3v2", "none", "--id3v1", "none");
        assertThat(none.exit()).isEqualTo(2);
        assertThat(none.err()).contains("removeOtherVersions");
        Run encodingWithoutV2 = run("--id3v2", "none", "--v2-encoding", "utf16");
        assertThat(encodingWithoutV2.exit()).isEqualTo(2);
        assertThat(encodingWithoutV2.err()).contains("UTF16");
    }
}
