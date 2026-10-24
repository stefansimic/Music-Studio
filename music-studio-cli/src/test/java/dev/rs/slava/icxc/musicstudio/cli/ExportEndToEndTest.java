package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.AppConfig;
import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.app.MusicStudioFactory;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** {@code export} with the real wiring over real files: dry-run changes nothing, --apply writes new copies only. */
class ExportEndToEndTest {

    @TempDir
    Path tempDir;

    private Path src;
    private Path out;
    private StringWriter stdout;
    private StringWriter stderr;

    @BeforeEach
    void files() {
        src = tempDir.toAbsolutePath().resolve("src");
        out = tempDir.toAbsolutePath().resolve("out");
        Fixtures.copyTo(Fixture.V24_UTF8, src, "Artist - Song.mp3");
        Fixtures.copyTo(Fixture.NO_TAGS, src, "Ђорђе - Песма.mp3");
        Fixtures.copyTo(Fixture.V11_V23_DIFF, src, "Another - Tune.mp3");
        Fixtures.copyTo(Fixture.CORRUPT, src, "Broken - X.mp3");
    }

    private MusicStudio studio() {
        return new MusicStudioFactory(new AppConfig(tempDir.resolve("config").toAbsolutePath(),
                tempDir.resolve("data").toAbsolutePath(), Clock.systemUTC())).create();
    }

    private int run(String... args) {
        stdout = new StringWriter();
        stderr = new StringWriter();
        CommandLine commandLine = MusicStudioCommand.commandLine(studio(), Cancellation.NONE,
                java.util.Optional.of(tempDir.resolve("reports")));
        commandLine.setOut(new PrintWriter(stdout, true));
        commandLine.setErr(new PrintWriter(stderr, true));
        return commandLine.execute(args);
    }

    private Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        if (!Files.exists(root)) {
            return snapshot;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                snapshot.put(root.relativize(path).toString(), Files.isRegularFile(path)
                        ? Fixtures.sha256(path) + "@" + Files.getLastModifiedTime(path) : "dir");
            }
        }
        return snapshot;
    }

    private List<ScannedFile> scan(Path root) {
        List<ScanItem> items = new ArrayList<>();
        studio().scan().scan(new ScanRequest(root, false, false, Set.of(MediaFormat.MP3), Set.of(),
                V1Comparison.LENIENT), items::add, ProgressListener.NONE, Cancellation.NONE);
        return items.stream().filter(ScannedFile.class::isInstance).map(ScannedFile.class::cast).toList();
    }

    @Test
    void dryRunShowsDifferencesAndChangesNothing() throws IOException {
        Map<String, String> before = snapshot(tempDir);

        int code = run("export", src.toString(), "--out", out.toString(), "--pattern", "{artist} - {title}");

        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(stdout.toString()).contains("ID3v2.3").contains("ID3v1.0 (new)").contains("V1_TRANSLITERATED")
                .contains("Unreadable (not processed):").contains("Broken - X.mp3")
                .contains("3 files: 3 to write, 0 skipped").contains("Preview only – no file was changed.");
        assertThat(out).doesNotExist();
        assertThat(snapshot(tempDir)).isEqualTo(before);
    }

    @Test
    void applyWritesV22CopiesAndNeverOverwritesOnRepeat() throws IOException {
        Map<String, String> sources = snapshot(src);

        int code = run("export", src.toString(), "--out", out.toString(), "--pattern", "{artist} - {title}",
                "--id3v2", "2.2", "--apply");

        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(stdout.toString()).contains("Written files:").contains("COMPLETED: 3 written");
        try (Stream<Path> reports = Files.list(tempDir.resolve("reports"))) {
            Path report = reports.findFirst().orElseThrow();
            assertThat(stdout.toString()).contains("Report: " + report);
            assertThat(Files.readString(report)).contains("\"status\":\"completed\"");
        }
        assertThat(snapshot(src)).isEqualTo(sources);
        List<ScannedFile> written = scan(out);
        assertThat(written).hasSize(3);
        assertThat(written).allSatisfy(file -> {
            assertThat(file.tags().versions()).contains(TagVersion.ID3V2_2);
            assertThat(file.tags().layer(TagFamily.ID3V1)).isPresent();
        });
        assertThat(written).extracting(f -> f.tags().effective().display(Field.TITLE).orElse(""))
                .contains("Song", "Tune");
        Map<String, String> exported = snapshot(out);

        int again = run("export", src.toString(), "--out", out.toString(), "--pattern", "{artist} - {title}",
                "--id3v2", "2.2", "--apply");

        assertThat(again).isEqualTo(ExitCode.USAGE);
        assertThat(stdout.toString()).contains("the target already exists").contains("0 to write, 3 skipped");
        assertThat(snapshot(out)).isEqualTo(exported);
    }

    @Test
    void v1OnlyKeepsOrRemovesTheOtherTag() throws IOException {
        run("export", src.toString(), "--out", out.toString(), "--id3v2", "none", "--id3v1", "1.1");
        assertThat(stdout.toString()).contains("OTHER_VERSION_KEPT");

        run("export", src.toString(), "--out", out.toString(), "--id3v2", "none", "--id3v1", "1.1",
                "--remove-other-versions", "--verbose");
        assertThat(stdout.toString()).contains("OTHER_VERSION_REMOVED").contains("(removed)");
    }

    @Test
    void impossibleOptionsFailBeforeAnythingIsRead() {
        assertThat(run("export", src.toString(), "--out", out.toString(), "--id3v2", "none", "--id3v1", "none"))
                .isEqualTo(ExitCode.USAGE);
        assertThat(stderr.toString()).contains("nothing to write");
    }
}
