package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.JsonProposalRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.ProposalRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TableProposalRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.Text;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code musicstudio extract <dir> --pattern …}: shows the metadata a pattern extracts from file and folder names.
 * Preview only – this command has no write use case and never changes a file.
 */
@Command(name = "extract", mixinStandardHelpOptions = true,
        description = "Shows the tags a naming pattern would produce. Preview only: no file is changed.")
public final class ExtractCommand implements Callable<Integer> {

    /** Output formats. */
    public enum Format {
        /** Human-readable table. */
        TABLE,
        /** One JSON document. */
        JSON
    }

    private final ScanMediaUseCase scan;
    private final ProposeMetadataUseCase propose;
    private final Cancellation cancel;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<dir>", description = "Folder with the files.")
    private Path dir;

    @Option(names = {"-r", "--recursive"}, description = "Also process subfolders.")
    private boolean recursive;

    @Option(names = "--include-hidden", description = "Also process hidden files and folders.")
    private boolean includeHidden;

    @Mixin
    private ExtractionOptions options;

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "table",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Format format;

    /**
     * @param scan    lists and reads the files; not null
     * @param propose computes the proposals; not null
     * @param cancel  cancelled by Ctrl-C; not null
     */
    public ExtractCommand(ScanMediaUseCase scan, ProposeMetadataUseCase propose, Cancellation cancel) {
        this.scan = Require.nonNull(scan, "scan");
        this.propose = Require.nonNull(propose, "propose");
        this.cancel = Require.nonNull(cancel, "cancel");
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!options.anyExtraction()) {
            err.println("musicstudio: give at least one of --pattern, --set or --auto-number");
            return ExitCode.USAGE;
        }
        var extraction = options.toSpec(err);
        if (extraction.isEmpty()) {
            return ExitCode.USAGE;
        }
        Path root = dir.toAbsolutePath().normalize();
        List<ScannedFile> files = new ArrayList<>();
        List<UnreadableFile> unreadable = new ArrayList<>();
        ScanSummary summary = scan.scan(new ScanRequest(root, recursive, includeHidden, Set.of(MediaFormat.MP3),
                Set.of(), V1Comparison.LENIENT), item -> collect(item, files, unreadable), ProgressListener.NONE,
                cancel);
        if (summary.status() != ScanStatus.COMPLETED) {
            summary.issues().forEach(issue -> err.println("Error: " + Text.oneLine(issue.message())));
            if (summary.status() == ScanStatus.CANCELLED) {
                err.println("Cancelled.");
            }
            return ExitCode.of(summary);
        }
        ProposalSet proposals = propose.propose(new ProposeRequest(files, extraction.get(), ManualEdits.NONE));
        ProposalRenderer renderer = switch (format) {
            case TABLE -> new TableProposalRenderer(out, root);
            case JSON -> new JsonProposalRenderer(out, root);
        };
        renderer.render(proposals, unreadable, summary, touchedFields(extraction.get()),
                extraction.get().pattern().map(p -> p.source()));
        out.flush();
        err.flush();
        return ExitCode.of(summary, proposals);
    }

    private static void collect(ScanItem item, List<ScannedFile> files, List<UnreadableFile> unreadable) {
        switch (item) {
            case ScannedFile file -> files.add(file);
            case UnreadableFile file -> unreadable.add(file);
        }
    }

    /** The columns to show: pattern placeholders, --set/--keep fields, TRACK when auto-numbered, in Field order. */
    private static List<Field> touchedFields(ExtractionSpec spec) {
        EnumSet<Field> fields = EnumSet.noneOf(Field.class);
        spec.pattern().ifPresent(p -> fields.addAll(p.placeholders()));
        fields.addAll(spec.overrides().keySet());
        spec.autoNumbering().map(AutoNumbering::order).ifPresent(order -> fields.add(Field.TRACK));
        return List.copyOf(fields);
    }
}
