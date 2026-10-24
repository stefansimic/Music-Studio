package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.CsvScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.ScanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TableScanRenderer;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
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

/** {@code musicstudio scan <dir>}: lists MP3 files and shows their tags. Read-only. */
@Command(name = "scan", mixinStandardHelpOptions = true,
        description = "Lists the MP3 files in a folder and shows their tags. Never modifies a file.")
public final class ScanCommand implements Callable<Integer> {

    /** Output formats. */
    public enum Format {
        /** Human-readable, fixed-width columns. */
        TABLE,
        /** One JSON document, streamed. */
        JSON,
        /** RFC 4180 CSV with header row. */
        CSV
    }

    private final ScanMediaUseCase scan;
    private final Cancellation cancel;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<dir>", description = "Folder to scan.")
    private Path dir;

    @Option(names = {"-r", "--recursive"}, description = "Also scan subfolders.")
    private boolean recursive;

    @Option(names = "--include-hidden", description = "Also scan hidden files and folders (names starting with '.').")
    private boolean includeHidden;

    @Option(names = "--strict-v1-compare",
            description = "Count transliterated/truncated ID3v1 values as a difference from ID3v2.")
    private boolean strictV1Compare;

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "table",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Format format;

    /**
     * @param scan   the scan use case; not null
     * @param cancel cancelled by Ctrl-C; not null
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
            case TABLE -> new TableScanRenderer(out, err, root);
            case JSON -> new JsonScanRenderer(out, root);
            case CSV -> new CsvScanRenderer(out, err, root);
        };
        renderer.begin();
        ScanSummary summary = scan.scan(request, renderer::item, ProgressListener.NONE, cancel);
        renderer.end(summary);
        out.flush();
        err.flush();
        return ExitCode.of(summary);
    }
}
