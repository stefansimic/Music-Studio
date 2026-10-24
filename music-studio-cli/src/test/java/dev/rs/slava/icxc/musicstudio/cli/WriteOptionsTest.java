package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

class WriteOptionsTest {

    @Command(name = "probe")
    private static final class Probe implements Callable<Integer> {
        @Mixin
        WriteOptions options;

        @Override
        public Integer call() {
            return 0;
        }
    }

    private final StringWriter err = new StringWriter();

    private Optional<TagWriteOptions> parse(String... args) {
        Probe probe = new Probe();
        new CommandLine(probe).parseArgs(args);
        return probe.options.toOptions(new PrintWriter(err, true));
    }

    @Test
    void defaultsAreV23AndV11() {
        assertThat(parse()).contains(TagWriteOptions.DEFAULT);
    }

    @Test
    void everyFlagIsMapped() {
        assertThat(parse("--id3v2", "2.4", "--id3v1", "1.0", "--v2-encoding", "utf8", "--v1-text", "latin1",
                "--remove-other-versions")).contains(new TagWriteOptions(Id3v2Choice.V2_4, Id3v1Choice.V1_0,
                V2Encoding.UTF8, V1TextMode.LATIN1, true));
        assertThat(parse("--id3v2", "none", "--id3v1", "none", "--remove-other-versions")).isPresent();
    }

    @Test
    void impossibleCombinationsAreExplained() {
        assertThat(parse("--id3v2", "2.3", "--v2-encoding", "utf8")).isEmpty();
        assertThat(err.toString()).contains("UTF-8").contains("2.4");
        err.getBuffer().setLength(0);
        assertThat(parse("--id3v2", "none", "--id3v1", "none")).isEmpty();
        assertThat(err.toString()).contains("remove other versions");
    }

    @Test
    void unknownValueIsRejected() {
        assertThat(parse("--id3v2", "3")).isEmpty();
        assertThat(err.toString()).contains("--id3v2 3");
    }
}
