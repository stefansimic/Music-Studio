package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderRequest;
import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderResult;
import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.export.ExportSpec;
import dev.rs.slava.icxc.musicstudio.core.export.ExportStatus;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ExportCommandTest {

    private final List<ExportFolderRequest> requests = new ArrayList<>();
    private final StringWriter err = new StringWriter();
    private List<Issue> unsupported = List.of();

    private int run(String... args) {
        Path root = Path.of("/music").toAbsolutePath();
        var command = new ExportCommand((request, progress, cancel) -> {
            requests.add(request);
            if (!unsupported.isEmpty()) {
                return new ExportFolderResult(unsupported, Optional.empty(), List.of(), Optional.empty(),
                        Optional.empty());
            }
            return new ExportFolderResult(List.of(), Optional.of(new ScanSummary(root, ScanStatus.COMPLETED, 0, 0,
                    0, 0, 0, Map.of(), List.of(), List.of())), List.of(), Optional.of(new ExportPlan(Instant.EPOCH,
                    new ExportSpec(request.outputRoot()), request.options(), List.of(), List.of())),
                    Optional.empty());
        }, Cancellation.NONE, Optional.empty());
        CommandLine commandLine = new CommandLine(command);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setOut(new PrintWriter(new StringWriter(), true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    @Test
    void missingOutIsAUsageError() {
        assertThat(run("/music")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("--out");
    }

    @Test
    void unsupportedOptionsAreReportedAsUsageError() {
        unsupported = List.of(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION, "ID3v2.2 cannot be written"));
        assertThat(run("/music", "--out", "/out", "--id3v2", "2.2")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("ID3v2.2 cannot be written");
    }

    @Test
    void applyWithANonExecutablePlanWritesNothing() {
        assertThat(run("/music", "--out", "/out", "--apply")).isEqualTo(ExitCode.USAGE);
        assertThat(requests).singleElement().satisfies(r -> assertThat(r.apply()).isTrue());
    }

    @Test
    void dryRunNeverApplies() {
        run("/music", "--out", "/out");
        assertThat(requests).singleElement().satisfies(r -> assertThat(r.apply()).isFalse());
    }

    @Test
    void reportStatusesMapToExitCodes() {
        Path out = Path.of("/out").toAbsolutePath();
        assertThat(ExitCode.of(report(ExportStatus.COMPLETED, out))).isEqualTo(ExitCode.OK);
        assertThat(ExitCode.of(report(ExportStatus.COMPLETED_WITH_ERRORS, out))).isEqualTo(ExitCode.ITEM_ERRORS);
        assertThat(ExitCode.of(report(ExportStatus.ABORTED, out))).isEqualTo(ExitCode.ABORTED);
        assertThat(ExitCode.of(report(ExportStatus.CANCELLED, out))).isEqualTo(ExitCode.CANCELLED);
        assertThat(new ExportSpec(out).outputRoot()).isEqualTo(out);
    }

    private static ExportReport report(ExportStatus status, Path out) {
        return new ExportReport(status, out, List.of(), List.of(), List.of(), List.of(), List.of(), Duration.ZERO);
    }
}
