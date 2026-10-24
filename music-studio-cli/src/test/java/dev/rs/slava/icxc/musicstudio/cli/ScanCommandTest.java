package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Argument parsing and exit codes, with a fake use case (no file system). */
class ScanCommandTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final AtomicReference<ScanRequest> request = new AtomicReference<>();

    private int run(Function<ScanRequest, ScanSummary> behaviour, String... args) {
        ScanMediaUseCase fake = (scanRequest, sink, progress, cancel) -> {
            request.set(scanRequest);
            return behaviour.apply(scanRequest);
        };
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudio(fake, request -> ProposalSet.of(List.of())), Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    private static ScanSummary completed(ScanRequest r) {
        return new ScanSummary(r.root(), ScanStatus.COMPLETED, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of());
    }

    @Test
    void defaultsAreTableNonRecursiveLenient() {
        assertThat(run(ScanCommandTest::completed, "scan", "/music")).isEqualTo(ExitCode.OK);
        ScanRequest r = request.get();
        assertThat(r.root()).isEqualTo(Path.of("/music").toAbsolutePath());
        assertThat(r.recursive()).isFalse();
        assertThat(r.includeHidden()).isFalse();
        assertThat(r.formats()).containsExactly(MediaFormat.MP3);
        assertThat(r.v1Comparison()).isEqualTo(V1Comparison.LENIENT);
        assertThat(out.toString()).contains("No MP3 files found.");
    }

    @Test
    void optionsArePassedThrough() {
        run(ScanCommandTest::completed, "scan", "--recursive", "--include-hidden", "--strict-v1-compare", "/music");
        ScanRequest r = request.get();
        assertThat(r.recursive()).isTrue();
        assertThat(r.includeHidden()).isTrue();
        assertThat(r.v1Comparison()).isEqualTo(V1Comparison.STRICT);
        run(ScanCommandTest::completed, "scan", "-r", "/music");
        assertThat(request.get().recursive()).isTrue();
    }

    @Test
    void relativeDirectoryIsResolvedToAbsolute() {
        run(ScanCommandTest::completed, "scan", "some/relative/../dir");
        assertThat(request.get().root()).isEqualTo(Path.of("some/dir").toAbsolutePath());
    }

    @Test
    void formatIsCaseInsensitive() {
        assertThat(run(ScanCommandTest::completed, "scan", "--format", "JSON", "/music")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).startsWith("{\"root\":");
        assertThat(run(ScanCommandTest::completed, "scan", "--format", "csv", "/music")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("path,title,artist");
    }

    @Test
    void unknownFormatIsAUsageError() {
        assertThat(run(ScanCommandTest::completed, "scan", "--format", "xml", "/music")).isEqualTo(ExitCode.USAGE);
        assertThat(request.get()).isNull();
        assertThat(err.toString()).contains("xml");
    }

    @Test
    void missingDirectoryArgumentIsAUsageError() {
        assertThat(run(ScanCommandTest::completed, "scan")).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void failedScanExitsWithUsageError() {
        int code = run(r -> new ScanSummary(r.root(), ScanStatus.FAILED, 0, 0, 0, 0, 0, Map.of(), List.of(),
                List.of(Issue.error(IssueCode.ROOT_NOT_FOUND, "folder not found: /music"))), "scan", "/music");
        assertThat(code).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("Error: folder not found: /music");
    }

    @Test
    void itemErrorsExitWithOne() {
        int code = run(r -> new ScanSummary(r.root(), ScanStatus.COMPLETED, 2, 1, 1, 0, 0, Map.of(), List.of(),
                List.of()), "scan", "/music");
        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
    }

    @Test
    void filesWithErrorIssuesExitWithOne() {
        int code = run(r -> new ScanSummary(r.root(), ScanStatus.COMPLETED, 2, 2, 0, 1, 0, Map.of(), List.of(),
                List.of()), "scan", "/music");
        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS);
    }

    @Test
    void rootIoFailureExitsWithThree() {
        int code = run(r -> new ScanSummary(r.root(), ScanStatus.FAILED, 0, 0, 0, 0, 0, Map.of(), List.of(),
                List.of(Issue.error(IssueCode.LISTING_ACCESS_DENIED, "cannot read folder"))), "scan", "/music");
        assertThat(code).isEqualTo(ExitCode.ABORTED);
    }

    @Test
    void cancelledExitsWith130() {
        int code = run(r -> new ScanSummary(r.root(), ScanStatus.CANCELLED, 2, 1, 0, 0, 0, Map.of(), List.of(),
                List.of()), "scan", "/music");
        assertThat(code).isEqualTo(ExitCode.CANCELLED);
    }

    @Test
    void itemsAreRenderedAsTheyArrive() {
        Path root = Path.of("/music").toAbsolutePath();
        ScanMediaUseCase fake = (scanRequest, sink, progress, cancel) -> {
            sink.accept(new UnreadableFile(root.resolve("bad.mp3"), MediaFormat.MP3,
                    Issue.error(IssueCode.READ_FAILED, "not a readable MP3 file").forFile(root.resolve("bad.mp3"))));
            return new ScanSummary(root, ScanStatus.COMPLETED, 1, 0, 1, 0, 0, Map.of(), List.of(), List.of());
        };
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudio(fake, request -> ProposalSet.of(List.of())), Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        assertThat(commandLine.execute("scan", root.toString())).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(out.toString()).contains("bad.mp3").contains("ERROR: not a readable MP3 file");
    }

    @Test
    void topLevelWithoutSubcommandPrintsUsage() {
        assertThat(run(ScanCommandTest::completed)).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("scan");
        assertThat(run(ScanCommandTest::completed, "--version")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("musicstudio");
    }
}
