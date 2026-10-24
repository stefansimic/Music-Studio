package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.JsonPlanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonReportRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.PlanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.ReportRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TablePlanRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TableReportRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.Text;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderRequest;
import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderResult;
import dev.rs.slava.icxc.musicstudio.core.export.ExportFolderUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code musicstudio export <dir> --out <dir>}: writes tagged <b>copies</b> of the files into an output folder.
 * Without {@code --apply} it only shows what would happen; the originals are never changed.
 */
@Command(name = "export", mixinStandardHelpOptions = true,
        description = "Writes tagged copies into an output folder. Without --apply: preview only.")
public final class ExportCommand implements Callable<Integer> {

    private final ExportFolderUseCase exportFolder;
    private final Cancellation cancel;
    private final Optional<Path> reportDir;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<dir>", description = "Folder with the files.")
    private Path dir;

    @Option(names = "--out", required = true, paramLabel = "<dir>",
            description = "Output folder; created if missing. Existing files are never overwritten.")
    private Path out;

    @Option(names = {"-r", "--recursive"}, description = "Also process subfolders.")
    private boolean recursive;

    @Option(names = "--include-hidden", description = "Also process hidden files and folders.")
    private boolean includeHidden;

    @Mixin
    private ExtractionOptions extraction;

    @Mixin
    private WriteOptions write;

    @Option(names = "--format", paramLabel = "<format>", defaultValue = "table",
            description = "Output format: CANDIDATES (default: VALUE).")
    private ExtractCommand.Format format;

    @Option(names = {"-v", "--verbose"}, description = "Also show informational notes.")
    private boolean verbose;

    @Option(names = "--apply", description = "Write the files. Without it nothing is written.")
    private boolean apply;

    /**
     * @param exportFolder the folder export flow; not null
     * @param cancel       cancelled by Ctrl-C; not null
     * @param reportDir    where the JSON report of an applied export is saved; not null (empty = not saved)
     */
    public ExportCommand(ExportFolderUseCase exportFolder, Cancellation cancel, Optional<Path> reportDir) {
        this.exportFolder = Require.nonNull(exportFolder, "exportFolder");
        this.cancel = Require.nonNull(cancel, "cancel");
        this.reportDir = Require.nonNull(reportDir, "reportDir");
    }

    @Override
    public Integer call() {
        PrintWriter stdout = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Optional<ExtractionSpec> extractionSpec = extraction.anyExtraction() ? extraction.toSpec(err) : Optional.empty();
        Optional<TagWriteOptions> options = write.toOptions(err);
        if (options.isEmpty() || extraction.anyExtraction() && extractionSpec.isEmpty()) {
            return ExitCode.USAGE;
        }
        Path root = dir.toAbsolutePath().normalize();
        Path output = out.toAbsolutePath().normalize();
        ExportFolderResult result = exportFolder.export(new ExportFolderRequest(root, recursive, includeHidden,
                extractionSpec, output, options.get(), apply), ProgressListener.NONE, cancel);
        if (!result.optionIssues().isEmpty()) {
            result.optionIssues().forEach(issue -> err.println("musicstudio: " + Text.oneLine(issue.message())));
            return ExitCode.USAGE;
        }
        ScanSummary summary = result.scan().orElseThrow();
        if (result.plan().isEmpty()) {
            summary.issues().forEach(issue -> err.println("Error: " + Text.oneLine(issue.message())));
            if (summary.status() == ScanStatus.CANCELLED) {
                err.println("Cancelled.");
            }
            return ExitCode.of(summary);
        }
        ExportPlan plan = result.plan().get();
        PlanRenderer planRenderer = switch (format) {
            case TABLE -> new TablePlanRenderer(stdout, root, verbose);
            case JSON -> new JsonPlanRenderer(stdout, root);
        };
        planRenderer.render(plan, result.unreadable());
        int planCode = ExitCode.of(plan);
        boolean problems = planCode != ExitCode.OK || !result.unreadable().isEmpty();
        if (!apply) {
            if (plan.executable() && format == ExtractCommand.Format.TABLE) {
                stdout.println("Preview only – no file was changed. Run again with --apply to write "
                        + plan.writeCount() + " file" + (plan.writeCount() == 1 ? "" : "s") + " to " + output + ".");
            }
            stdout.flush();
            return planCode == ExitCode.OK && problems ? ExitCode.ITEM_ERRORS : planCode;
        }
        if (result.report().isEmpty()) {
            err.println("musicstudio: nothing to write; see the notes above");
            stdout.flush();
            err.flush();
            return ExitCode.USAGE;
        }
        ExportReport report = result.report().get();
        stdout.println();
        ReportRenderer reportRenderer = switch (format) {
            case TABLE -> new TableReportRenderer(stdout, root);
            case JSON -> new JsonReportRenderer(stdout, root);
        };
        reportRenderer.render(report);
        saveReport(report, plan, root, stdout, err);
        stdout.flush();
        err.flush();
        int code = ExitCode.of(report);
        return code == ExitCode.OK && problems ? ExitCode.ITEM_ERRORS : code;
    }

    /** Saves the JSON report as a new file (never overwriting) and prints its path. */
    private void saveReport(ExportReport report, ExportPlan plan, Path root, PrintWriter stdout, PrintWriter err) {
        if (reportDir.isEmpty()) {
            return;
        }
        StringWriter json = new StringWriter();
        new JsonReportRenderer(new PrintWriter(json), root).render(report);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
                .format(plan.createdAt());
        try {
            Files.createDirectories(reportDir.get());
            for (int n = 0;; n++) {
                Path file = reportDir.get().resolve("export-" + stamp + (n == 0 ? "" : "-" + n) + ".json");
                try {
                    Files.writeString(file, json.toString(), StandardOpenOption.CREATE_NEW);
                    stdout.println("Report: " + file);
                    return;
                } catch (FileAlreadyExistsException e) {
                    continue;
                }
            }
        } catch (IOException e) {
            err.println("musicstudio: could not save the report: " + Text.oneLine(String.valueOf(e.getMessage())));
        }
    }
}
