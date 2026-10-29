package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.CsvScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.ScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TableScanRenderer;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code musicstudio scan <dir>}: lists the MP3 files of a folder with their tags. It only parses the arguments,
 * calls the scan use case and renders the result; it never writes anything.
 */
@Command(name = "scan", mixinStandardHelpOptions = true,
        description = "Lists the MP3 files of a folder with their tags. Read-only: no file is changed.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {"0:Scan completed", "1:Completed, but some files could not be read",
                "2:Usage error or the folder does not exist", "3:The folder could not be read",
                "130:Cancelled"})
public final class ScanCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<dir>", description = "The folder to scan.")
    private Path dir;

    @Option(names = {"-r", "--recursive"}, description = "Also scan all subfolders.")
    private boolean recursive;

    @Option(names = "--include-hidden",
            description = "Also scan hidden files and folders (names starting with '.', including macOS '._' files). "
                    + "They are skipped and listed by default.")
    private boolean includeHidden;

    @Option(names = "--strict-v1-compare",
            description = "Count a v1 value that is only the transliterated or truncated v2 value as a difference "
                    + "between the ID3v1 and the ID3v2 tag.")
    private boolean strictV1Compare;

    @Option(names = {"-f", "--format"}, defaultValue = "table", paramLabel = "<format>",
            description = "Output format: CANDIDATES (default: VALUE).")
    private OutputFormat format;

    private final ScanMediaUseCase scan;
    private final Cancellation cancel;

    /**
     * @param scan   the scan use case; not null
     * @param cancel cancellation, triggered by Ctrl-C; not null
     */
    public ScanCommand(ScanMediaUseCase scan, Cancellation cancel) {
        this.scan = Require.nonNull(scan, "scan");
        this.cancel = Require.nonNull(cancel, "cancel");
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Path root = dir.toAbsolutePath().normalize();
        ScanRequest request = new ScanRequest(root, recursive, includeHidden, Set.of(MediaFormat.MP3), Set.of(),
                strictV1Compare ? V1Comparison.STRICT : V1Comparison.LENIENT);
        ScanRenderer renderer = switch (format) {
            case TABLE -> new TableScanRenderer(out);
            case JSON -> new JsonScanRenderer(out);
            case CSV -> new CsvScanRenderer(out, err);
        };

        renderer.begin(request);
        ScanSummary summary = scan.scan(request, renderer::item, ProgressListener.NONE, cancel);
        renderer.end(summary);
        out.flush();

        return exitCode(summary, err);
    }

    private static int exitCode(ScanSummary summary, PrintWriter err) {
        return switch (summary.status()) {
            case CANCELLED -> ExitCode.CANCELLED;
            case FAILED -> {
                summary.issues().forEach(issue -> err.println("error: " + issue.message()));
                err.flush();
                boolean missing = summary.issues().stream().anyMatch(issue -> issue.code() == IssueCode.ROOT_NOT_FOUND);
                yield missing ? ExitCode.USAGE : ExitCode.ABORTED;
            }
            case COMPLETED -> summary.failed() > 0 ? ExitCode.ITEM_ERRORS : ExitCode.OK;
        };
    }
}
