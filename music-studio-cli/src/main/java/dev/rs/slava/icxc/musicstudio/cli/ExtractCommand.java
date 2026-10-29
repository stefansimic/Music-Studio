package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.CsvExtractRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.ExtractRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.JsonExtractRenderer;
import dev.rs.slava.icxc.musicstudio.cli.render.TableExtractRenderer;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.pattern.FieldValueParser;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternError;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code musicstudio extract <dir> --pattern P}: previews the metadata that a naming pattern, fixed values and
 * numbering would give to the MP3 files of a folder. It only parses the arguments, calls the scan and the propose use
 * case and renders the result. It is a preview: there is no option that writes, and no file is changed.
 */
@Command(name = "extract", mixinStandardHelpOptions = true,
        description = "Previews the tags that a naming pattern would give to the MP3 files of a folder. "
                + "Preview only: no file is changed.",
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {"0:Preview completed (no-match and ambiguous rows are marked, not errors)",
                "1:Some files could not be read, or --strict and some rows are not a clean match",
                "2:Usage error: invalid pattern or value, or the folder does not exist",
                "3:The folder could not be read", "130:Cancelled"})
public final class ExtractCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<dir>", description = "The folder with the files.")
    private Path dir;

    @Option(names = {"-p", "--pattern"}, paramLabel = "<pattern>",
            description = "Naming pattern, for example \"{album}/{artist} - {title} - {year}\". Placeholders: "
                    + "{title} {artist} {albumartist} {album} {year} {track} {disc} {genre} {comment}, {*} = ignore. "
                    + "'/' steps up to the parent folder.")
    private String pattern;

    @Option(names = "--set", paramLabel = "<field=value>",
            description = "Fixed value for all files, for example genre=Balkan. Repeatable.")
    private List<String> sets = new ArrayList<>();

    @Option(names = "--keep", paramLabel = "<field>",
            description = "Keep the value the files have now for this field. Repeatable.")
    private List<String> keeps = new ArrayList<>();

    @Option(names = "--merge", defaultValue = "overwrite", paramLabel = "<mode>",
            description = "overwrite (default) or fill-empty (only set empty fields).")
    private String merge;

    @Option(names = "--number-by", paramLabel = "<order>",
            description = "Number the tracks per folder when the name has no track: name or tag (order of the "
                    + "existing track tags).")
    private String numberBy;

    @Option(names = "--number-total", description = "With --number-by: also set the number of files as track total.")
    private boolean numberTotal;

    @Option(names = "--accept-ambiguous",
            description = "Use ambiguous matches (the shortest split) instead of skipping them.")
    private boolean acceptAmbiguous;

    @Option(names = "--strict", description = "Exit with code 1 if some row is a no-match or ambiguous.")
    private boolean strict;

    @Option(names = {"-r", "--recursive"}, description = "Also scan all subfolders.")
    private boolean recursive;

    @Option(names = "--include-hidden", description = "Also scan hidden files and folders.")
    private boolean includeHidden;

    @Option(names = {"-f", "--format"}, defaultValue = "table", paramLabel = "<format>",
            description = "Output format: table, json or csv (default: ${DEFAULT-VALUE}).")
    private OutputFormat format;

    private final ScanMediaUseCase scan;
    private final ProposeMetadataUseCase propose;
    private final Cancellation cancel;

    /**
     * @param scan    the scan use case; not null
     * @param propose the propose use case; not null
     * @param cancel  cancellation, triggered by Ctrl-C; not null
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
        List<String> problems = new ArrayList<>();
        Optional<ExtractionSpec> parsedSpec = extractionSpec(problems);
        if (parsedSpec.isEmpty()) {
            problems.forEach(problem -> err.println("error: " + problem));
            err.flush();
            return ExitCode.USAGE;
        }
        Path root = dir.toAbsolutePath().normalize();
        ScanRequest request = new ScanRequest(root, recursive, includeHidden, Set.of(MediaFormat.MP3), Set.of(),
                V1Comparison.LENIENT);
        List<ScannedFile> files = new ArrayList<>();
        List<UnreadableFile> unreadable = new ArrayList<>();
        ScanSummary summary = scan.scan(request, item -> collect(item, files, unreadable), ProgressListener.NONE,
                cancel);
        if (summary.status() == ScanStatus.CANCELLED) {
            return ExitCode.CANCELLED;
        }
        if (summary.status() == ScanStatus.FAILED) {
            summary.issues().forEach(issue -> err.println("error: " + issue.message()));
            err.flush();
            boolean missing = summary.issues().stream().anyMatch(issue -> issue.code() == IssueCode.ROOT_NOT_FOUND);
            return missing ? ExitCode.USAGE : ExitCode.ABORTED;
        }
        ProposalSet proposals = propose.propose(new ProposeRequest(files, parsedSpec.get(), ManualEdits.NONE));
        ExtractRenderer renderer = switch (format) {
            case TABLE -> new TableExtractRenderer(out);
            case JSON -> new JsonExtractRenderer(out);
            case CSV -> new CsvExtractRenderer(out);
        };
        renderer.render(root, proposals, unreadable);
        out.flush();
        if (!unreadable.isEmpty()) {
            return ExitCode.ITEM_ERRORS;
        }
        boolean notClean = proposals.ambiguous() > 0 || proposals.noMatch() > 0;
        return strict && notClean ? ExitCode.ITEM_ERRORS : ExitCode.OK;
    }

    private static void collect(ScanItem item, List<ScannedFile> files, List<UnreadableFile> unreadable) {
        if (item instanceof ScannedFile file) {
            files.add(file);
        } else if (item instanceof UnreadableFile broken) {
            unreadable.add(broken);
        }
    }

    /** Parses and validates the options; every problem is added to {@code problems} and the result is empty. */
    private Optional<ExtractionSpec> extractionSpec(List<String> problems) {
        Optional<InputPattern> parsed = Optional.empty();
        if (pattern != null) {
            switch (PatternParser.parseInput(pattern)) {
                case PatternParseResult.Valid<InputPattern> valid -> {
                    parsed = Optional.of(valid.pattern());
                    valid.hints().forEach(hint -> spec.commandLine().getErr().println("warning: " + hint.message()));
                }
                case PatternParseResult.Invalid<InputPattern> invalid -> {
                    for (PatternError error : invalid.errors()) {
                        problems.add("pattern, column " + (error.column() + 1) + ": " + error.message());
                    }
                }
            }
        }
        Map<Field, FieldOverride> overrides = new EnumMap<>(Field.class);
        for (String set : sets) {
            int eq = set.indexOf('=');
            Optional<Field> field = eq <= 0 ? Optional.empty() : Field.byPlaceholder(set.substring(0, eq));
            if (field.isEmpty()) {
                problems.add("--set needs field=value with a known field, got '" + set + "'");
                continue;
            }
            var value = FieldValueParser.parse(field.get(), set.substring(eq + 1));
            if (value.error().isPresent()) {
                problems.add("--set " + field.get().placeholder() + ": " + value.error().get());
            } else if (overrides.put(field.get(), new FieldOverride.Fixed(value.value().get())) != null) {
                problems.add("--set " + field.get().placeholder() + " is given twice");
            }
        }
        for (String keep : keeps) {
            Optional<Field> field = Field.byPlaceholder(keep);
            if (field.isEmpty()) {
                problems.add("--keep needs a known field, got '" + keep + "'");
            } else if (overrides.put(field.get(), new FieldOverride.KeepExisting()) != null) {
                problems.add("--keep " + keep + " conflicts with another --set or --keep for the same field");
            }
        }
        MergeMode mode = MergeMode.OVERWRITE;
        switch (merge.toLowerCase(Locale.ROOT)) {
            case "overwrite" -> mode = MergeMode.OVERWRITE;
            case "fill-empty" -> mode = MergeMode.FILL_EMPTY;
            default -> problems.add("--merge must be overwrite or fill-empty, got '" + merge + "'");
        }
        Optional<AutoNumbering> numbering = Optional.empty();
        if (numberBy != null) {
            switch (numberBy.toLowerCase(Locale.ROOT)) {
                case "name" -> numbering = Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME,
                        numberTotal));
                case "tag" -> numbering = Optional.of(new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK,
                        numberTotal));
                default -> problems.add("--number-by must be name or tag, got '" + numberBy + "'");
            }
        } else if (numberTotal) {
            problems.add("--number-total needs --number-by");
        }
        if (pattern == null && overrides.isEmpty() && numbering.isEmpty() && problems.isEmpty()) {
            problems.add("give a --pattern, or at least one of --set, --keep and --number-by");
        }
        if (!problems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ExtractionSpec(parsed, overrides, mode, numbering, acceptAmbiguous));
    }
}
