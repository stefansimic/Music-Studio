package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
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
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Parsing and exit codes of {@code scan}, with a fake use case: the command contains no scan logic. */
class ScanCommandTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private final List<ScanRequest> requests = new ArrayList<>();
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private ScanSummary result = summary(ScanStatus.COMPLETED, 0, 0);

    private static ScanSummary summary(ScanStatus status, int read, int failed) {
        return new ScanSummary(ROOT, status, read + failed, read, failed, 0, Map.of(), List.of(), List.of());
    }

    private int run(String... args) {
        return run(Cancellation.NONE, args);
    }

    private int run(Cancellation cancel, String... args) {
        ScanMediaUseCase fake = (request, sink, progress, token) -> {
            requests.add(request);
            return result;
        };
        return MusicStudioCommand.create(TestStudios.scanOnly(fake), cancel, new PrintWriter(out), new PrintWriter(err))
                .execute(args);
    }

    @Test
    void defaultsAreFlatMp3NoHiddenLenientTable() {
        assertThat(run("scan", "/music")).isEqualTo(ExitCode.OK);
        ScanRequest request = requests.get(0);
        assertThat(request.root()).isEqualTo(ROOT);
        assertThat(request.recursive()).isFalse();
        assertThat(request.includeHidden()).isFalse();
        assertThat(request.formats()).containsExactly(MediaFormat.MP3);
        assertThat(request.v1Comparison()).isEqualTo(V1Comparison.LENIENT);
        assertThat(out.toString()).contains("PATH").contains("COMPLETED"); // table is the default format
    }

    @Test
    void optionsReachTheRequest() {
        run("scan", "/music", "--recursive", "--include-hidden", "--strict-v1-compare");
        ScanRequest request = requests.get(0);
        assertThat(request.recursive()).isTrue();
        assertThat(request.includeHidden()).isTrue();
        assertThat(request.v1Comparison()).isEqualTo(V1Comparison.STRICT);
        requests.clear();
        run("scan", "-r", "/music");
        assertThat(requests.get(0).recursive()).isTrue();
    }

    @Test
    void relativeFolderBecomesAbsoluteAndNormalised() {
        run("scan", "some/../dir");
        assertThat(requests.get(0).root()).isEqualTo(Path.of("dir").toAbsolutePath());
        assertThat(requests.get(0).root().isAbsolute()).isTrue();
    }

    @Test
    void formatSelectsTheRenderer() {
        run("scan", "/music", "--format", "json");
        assertThat(out.toString()).startsWith("{\"items\": [").contains("\"summary\"");
        out.getBuffer().setLength(0);
        run("scan", "/music", "-f", "CSV"); // case-insensitive
        assertThat(out.toString()).startsWith("path,title,artist");
    }

    @Test
    void unknownFormatIsAUsageError() {
        assertThat(run("scan", "/music", "--format", "xml")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("xml");
        assertThat(requests).isEmpty();
    }

    @Test
    void missingFolderArgumentIsAUsageError() {
        assertThat(run("scan")).isEqualTo(ExitCode.USAGE);
        assertThat(requests).isEmpty();
    }

    @Test
    void unknownOptionIsAUsageError() {
        assertThat(run("scan", "/music", "--frobnicate")).isEqualTo(ExitCode.USAGE);
    }

    @Test
    void unreadableFilesGiveExitCodeOne() {
        result = summary(ScanStatus.COMPLETED, 3, 1);
        assertThat(run("scan", "/music")).isEqualTo(ExitCode.ITEM_ERRORS);
    }

    @Test
    void missingRootIsAUsageErrorWithAMessage() {
        result = new ScanSummary(ROOT, ScanStatus.FAILED, 0, 0, 0, 0, Map.of(), List.of(),
                List.of(Issue.error(IssueCode.ROOT_NOT_FOUND, "no such directory: /music")));
        assertThat(run("scan", "/music")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("error: no such directory: /music");
    }

    @Test
    void unreadableRootAbortsWithExitCodeThree() {
        result = new ScanSummary(ROOT, ScanStatus.FAILED, 0, 0, 0, 0, Map.of(), List.of(),
                List.of(Issue.error(IssueCode.LISTING_ACCESS_DENIED, "cannot read directory: /music")));
        assertThat(run("scan", "/music")).isEqualTo(ExitCode.ABORTED);
    }

    @Test
    void cancelledScanGivesExitCode130() {
        result = summary(ScanStatus.CANCELLED, 2, 0);
        assertThat(run("scan", "/music")).isEqualTo(ExitCode.CANCELLED);
    }

    @Test
    void theSharedCancellationReachesTheUseCase() {
        CancellationSource cancel = new CancellationSource();
        List<Cancellation> seen = new ArrayList<>();
        ScanMediaUseCase fake = (request, sink, progress, token) -> {
            seen.add(token);
            return result;
        };
        MusicStudioCommand.create(TestStudios.scanOnly(fake), cancel, new PrintWriter(out), new PrintWriter(err))
                .execute("scan", "/music");
        assertThat(seen).containsExactly(cancel);
    }

    @Test
    void anUnexpectedExceptionAbortsWithExitCodeThree() {
        ScanMediaUseCase failing = (request, sink, progress, token) -> {
            throw new IllegalStateException("boom");
        };
        int code = MusicStudioCommand.create(TestStudios.scanOnly(failing), Cancellation.NONE, new PrintWriter(out),
                new PrintWriter(err)).execute("scan", "/music");
        assertThat(code).isEqualTo(ExitCode.ABORTED);
        assertThat(err.toString()).contains("error:").contains("boom");
    }

    @Test
    void helpAndVersionWork() {
        assertThat(run("--help")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("scan");
        out.getBuffer().setLength(0);
        assertThat(run("--version")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("musicstudio");
        out.getBuffer().setLength(0);
        assertThat(run("scan", "--help")).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("--recursive").contains("--include-hidden").contains("--strict-v1-compare")
                .contains("--format").contains("Exit codes");
        assertThat(out.toString()).contains("table, json or csv").contains("default: table")
                .doesNotContain("CANDIDATES").doesNotContain("default: VALUE");
        assertThat(requests).isEmpty();
    }

    @Test
    void noSubcommandPrintsUsage() {
        assertThat(run()).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("Usage: musicstudio");
    }

    @Test
    void preconditions() {
        PrintWriter writer = new PrintWriter(out);
        MusicStudio studio = TestStudios.scanOnly((request, sink, progress, token) -> result);
        assertThatThrownBy(() -> MusicStudioCommand.create(null, Cancellation.NONE, writer, writer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MusicStudioCommand.create(studio, null, writer, writer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanCommand(null, Cancellation.NONE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanCommand((r, s, p, c) -> result, null)).isInstanceOf(NullPointerException.class);
        assertThat(new UnreadableFile(ROOT.resolve("a.mp3"), MediaFormat.MP3,
                Issue.error(IssueCode.READ_FAILED, "x")).path()).isNotNull();
    }
}
