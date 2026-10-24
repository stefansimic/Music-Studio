package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.cli.render.Text;
import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValueParser;
import dev.rs.slava.icxc.musicstudio.core.model.ParsedValue;
import dev.rs.slava.icxc.musicstudio.core.pattern.InputPattern;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternError;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParseResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PatternParser;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Option;

/**
 * The extraction options shared by {@code extract} (002) and later {@code export} (003/004): pattern, fixed values,
 * keep, merge mode, auto-numbering, ambiguity. Parsing of values and patterns is done by core
 * ({@link PatternParser}, {@link FieldValueParser}); this class only maps options to an {@link ExtractionSpec}.
 */
public final class ExtractionOptions {

    @Option(names = "--pattern", paramLabel = "<pattern>",
            description = "Naming pattern, e.g. \"{album}/{artist} - {title} - {year}\". Placeholders: {title} "
                    + "{artist} {albumartist} {album} {year} {track} {disc} {genre} {comment}, {*} = ignore.")
    private String pattern;

    @Option(names = "--set", paramLabel = "<field>=<value>",
            description = "Fixed value for all files, e.g. --set genre=Pop --set \"albumartist=Various Artists\". "
                    + "Repeatable.")
    private List<String> set = new ArrayList<>();

    @Option(names = "--keep", paramLabel = "<field>[,<field>…]", split = ",",
            description = "Keep the existing tag value of these fields.")
    private List<String> keep = new ArrayList<>();

    @Option(names = "--merge", paramLabel = "overwrite|fill-empty", defaultValue = "overwrite",
            description = "overwrite (default) replaces existing values; fill-empty only sets empty fields.")
    private String merge;

    @Option(names = "--auto-number", paramLabel = "filename|tag",
            description = "Number the tracks of each folder by file name or by the existing track order.")
    private String autoNumber;

    @Option(names = "--track-total", description = "With --auto-number: also set the total (3/12).")
    private boolean trackTotal;

    @Option(names = "--accept-ambiguous",
            description = "Use the shown split for ambiguous names instead of skipping them.")
    private boolean acceptAmbiguous;

    /** @return true if at least one of --pattern, --set or --auto-number is given */
    public boolean anyExtraction() {
        return pattern != null || !set.isEmpty() || autoNumber != null;
    }

    /**
     * Builds the spec. On invalid input prints a message to {@code err} and returns empty.
     *
     * @param err receives usage errors; not null
     * @return the spec, or empty after a printed usage error; never null
     * @throws NullPointerException if {@code err} is null
     */
    public Optional<ExtractionSpec> toSpec(PrintWriter err) {
        Require.nonNull(err, "err");
        List<String> errors = new ArrayList<>();
        Optional<InputPattern> parsed = Optional.empty();
        boolean patternInvalid = false;
        if (pattern != null) {
            switch (PatternParser.parseInput(pattern)) {
                case PatternParseResult.Valid<InputPattern> valid -> parsed = Optional.of(valid.pattern());
                case PatternParseResult.Invalid<InputPattern> invalid -> {
                    printPatternErrors(pattern, invalid.errors(), err);
                    patternInvalid = true;
                }
            }
        }
        Map<Field, FieldOverride> overrides = new EnumMap<>(Field.class);
        for (String assignment : set) {
            int equals = assignment.indexOf('=');
            if (equals <= 0) {
                errors.add("--set " + assignment + ": expected <field>=<value>");
                continue;
            }
            Optional<Field> field = field(assignment.substring(0, equals), "--set", errors);
            if (field.isEmpty()) {
                continue;
            }
            ParsedValue value = FieldValueParser.parse(field.get(), assignment.substring(equals + 1));
            switch (value) {
                case ParsedValue.Valid valid -> {
                    if (overrides.put(field.get(), new FieldOverride.Fixed(valid.value())) != null) {
                        errors.add("--set " + field.get().placeholder() + " is given twice");
                    }
                }
                case ParsedValue.Invalid invalid -> errors.add("--set " + assignment + ": " + invalid.reason());
            }
        }
        for (String name : keep) {
            field(name, "--keep", errors).ifPresent(field -> {
                FieldOverride previous = overrides.put(field, new FieldOverride.KeepExisting());
                if (previous instanceof FieldOverride.Fixed) {
                    errors.add(field.placeholder() + " cannot be both --set and --keep");
                }
            });
        }
        MergeMode mergeMode = switch (merge.toLowerCase(Locale.ROOT)) {
            case "overwrite" -> MergeMode.OVERWRITE;
            case "fill-empty" -> MergeMode.FILL_EMPTY;
            default -> {
                errors.add("--merge " + merge + ": expected overwrite or fill-empty");
                yield MergeMode.OVERWRITE;
            }
        };
        Optional<AutoNumbering> numbering = Optional.empty();
        if (autoNumber != null) {
            switch (autoNumber.toLowerCase(Locale.ROOT)) {
                case "filename" -> numbering = Optional.of(
                        new AutoNumbering(AutoNumbering.NumberingOrder.FILE_NAME, trackTotal));
                case "tag" -> numbering = Optional.of(
                        new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK, trackTotal));
                default -> errors.add("--auto-number " + autoNumber + ": expected filename or tag");
            }
        } else if (trackTotal) {
            errors.add("--track-total needs --auto-number");
        }
        if (errors.isEmpty() && !patternInvalid) {
            errors.addAll(ExtractionSpec.problems(parsed, overrides, numbering));
        }
        if (!errors.isEmpty() || patternInvalid) {
            errors.forEach(message -> err.println("musicstudio: " + message));
            return Optional.empty();
        }
        return Optional.of(new ExtractionSpec(parsed, overrides, mergeMode, numbering, acceptAmbiguous));
    }

    private static Optional<Field> field(String name, String option, List<String> errors) {
        Optional<Field> field = Field.byPlaceholder(name.strip().toLowerCase(Locale.ROOT));
        if (field.isEmpty()) {
            errors.add(option + " " + name + ": unknown field; valid: "
                    + String.join(", ", Arrays.stream(Field.values()).map(Field::placeholder).toList()));
        }
        return field;
    }

    /** Shows the pattern with a caret under every error column. */
    private static void printPatternErrors(String source, List<PatternError> errors, PrintWriter err) {
        err.println("musicstudio: invalid pattern");
        err.println("  " + Text.oneLine(source));
        for (PatternError error : errors) {
            int column = Text.width(Text.oneLine(source.substring(0, Math.min(error.column(), source.length()))));
            err.println("  " + " ".repeat(column) + "^ " + error.message());
        }
    }
}
