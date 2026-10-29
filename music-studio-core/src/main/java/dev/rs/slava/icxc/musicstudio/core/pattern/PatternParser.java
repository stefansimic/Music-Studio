package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Parses pattern text into {@link InputPattern} or {@link OutputPattern}. One grammar for both:
 * <pre>
 * pattern     := segment ( "/" segment )*
 * segment     := token+
 * token       := literal | placeholder | wildcard
 * placeholder := "{" name ( ":" "0"+ )? "}"     name = Field.placeholder(); the format only for track and disc, output only
 * wildcard    := "{*}"                          input only
 * literal     := ( any code point except "{" "}" "/" | "{{" | "}}" )+
 * </pre>
 * Literals are normalised to NFC; the {@code source} of the result is kept verbatim. A pattern cannot have two
 * placeholders (or wildcards) next to each other, and no placeholder twice. The extension is never part of a
 * pattern; a pattern that ends in one parses with an {@code PATTERN_EXTENSION} hint.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use. Never throws for bad pattern text.
 */
public final class PatternParser {

    private PatternParser() {}

    /**
     * @param source the pattern text; not null
     * @return a valid input pattern or the errors; never null
     * @throws NullPointerException if {@code source} is null
     */
    public static PatternParseResult<InputPattern> parseInput(String source) {
        Require.nonNull(source, "source");
        return parse(source, true);
    }

    /**
     * @param source the pattern text; not null
     * @return a valid output pattern or the errors; never null
     * @throws NullPointerException if {@code source} is null
     */
    public static PatternParseResult<OutputPattern> parseOutput(String source) {
        Require.nonNull(source, "source");
        return parse(source, false);
    }

    @SuppressWarnings("unchecked")
    private static <P> PatternParseResult<P> parse(String source, boolean input) {
        List<PatternError> errors = new ArrayList<>();
        List<PatternSegment> segments = new ArrayList<>();
        int[] cps = source.codePoints().toArray();
        if (source.isBlank()) {
            errors.add(new PatternError(0, "The pattern is empty"));
            return new PatternParseResult.Invalid<>(errors);
        }
        List<Token> tokens = new ArrayList<>();
        List<Integer> columns = new ArrayList<>(); // start column of each token
        StringBuilder literal = new StringBuilder();
        int literalStart = 0;
        Set<Field> used = java.util.EnumSet.noneOf(Field.class);
        int segmentStart = 0;
        int errorsAtSegmentStart = 0;
        for (int i = 0; i < cps.length; ) {
            int c = cps[i];
            if (c == '{') {
                if (i + 1 < cps.length && cps[i + 1] == '{') {
                    if (literal.length() == 0) {
                        literalStart = i;
                    }
                    literal.append('{');
                    i += 2;
                    continue;
                }
                flush(literal, literalStart, tokens, columns);
                int close = -1;
                for (int j = i + 1; j < cps.length; j++) {
                    if (cps[j] == '}' || cps[j] == '{') {
                        close = cps[j] == '}' ? j : -1;
                        break;
                    }
                }
                if (close < 0) {
                    errors.add(new PatternError(i, "Unbalanced '{': a placeholder needs a closing '}'"));
                    i++;
                    continue;
                }
                String content = new String(cps, i + 1, close - i - 1);
                Token token = placeholder(content, i, input, used, errors);
                if (token != null) {
                    tokens.add(token);
                    columns.add(i);
                }
                i = close + 1;
            } else if (c == '}') {
                if (i + 1 < cps.length && cps[i + 1] == '}') {
                    if (literal.length() == 0) {
                        literalStart = i;
                    }
                    literal.append('}');
                    i += 2;
                } else {
                    errors.add(new PatternError(i, "Unexpected '}' (write '}}' for a literal brace)"));
                    i++;
                }
            } else if (c == '/') {
                flush(literal, literalStart, tokens, columns);
                endSegment(tokens, columns, segments, errors, errorsAtSegmentStart, segmentStart);
                tokens = new ArrayList<>();
                columns = new ArrayList<>();
                segmentStart = i + 1;
                errorsAtSegmentStart = errors.size();
                i++;
            } else {
                if (literal.length() == 0) {
                    literalStart = i;
                }
                literal.appendCodePoint(c);
                i++;
            }
        }
        flush(literal, literalStart, tokens, columns);
        endSegment(tokens, columns, segments, errors, errorsAtSegmentStart, segmentStart);
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(PatternError::column));
            return new PatternParseResult.Invalid<>(errors);
        }
        List<Issue> hints = new ArrayList<>();
        extensionHint(segments.get(segments.size() - 1), hints);
        Object pattern = input ? new InputPattern(source, segments) : new OutputPattern(source, segments);
        return new PatternParseResult.Valid<>((P) pattern, hints);
    }

    private static void flush(StringBuilder literal, int start, List<Token> tokens, List<Integer> columns) {
        if (literal.length() > 0) {
            tokens.add(new Token.Literal(Normalizer.normalize(literal.toString(), Normalizer.Form.NFC)));
            columns.add(start);
            literal.setLength(0);
        }
    }

    private static void endSegment(List<Token> tokens, List<Integer> columns, List<PatternSegment> segments,
                                   List<PatternError> errors, int errorsAtSegmentStart, int segmentStart) {
        if (tokens.isEmpty()) {
            if (errors.size() > errorsAtSegmentStart) {
                return; // the segment is empty because its placeholders were invalid; that is already reported
            }
            errors.add(new PatternError(Math.max(segmentStart, 0), "Empty folder or file name part: "
                    + "remove the extra '/' or add a placeholder"));
            return;
        }
        boolean adjacent = false;
        for (int k = 1; k < tokens.size(); k++) {
            if (!(tokens.get(k - 1) instanceof Token.Literal) && !(tokens.get(k) instanceof Token.Literal)) {
                errors.add(new PatternError(columns.get(k), "Two placeholders next to each other cannot be told "
                        + "apart: put a separator between them"));
                adjacent = true;
            }
        }
        if (!adjacent) {
            segments.add(new PatternSegment(tokens));
        }
    }

    private static Token placeholder(String content, int column, boolean input, Set<Field> used,
                                     List<PatternError> errors) {
        if (content.equals("*")) {
            if (!input) {
                errors.add(new PatternError(column, "{*} is only allowed in input patterns"));
                return null;
            }
            return new Token.Wildcard();
        }
        String name = content;
        String format = null;
        int colon = content.indexOf(':');
        if (colon >= 0) {
            name = content.substring(0, colon);
            format = content.substring(colon + 1);
        }
        Optional<Field> field = Field.byPlaceholder(name);
        if (field.isEmpty()) {
            String lower = name.toLowerCase(Locale.ROOT);
            String hint = Field.byPlaceholder(lower).isPresent() ? "; placeholder names are lower-case, use '{" + lower + "}'" : "";
            errors.add(new PatternError(column, "Unknown placeholder '{" + name + "}'" + hint));
            return null;
        }
        OptionalInt pad = OptionalInt.empty();
        if (format != null) {
            if (input) {
                errors.add(new PatternError(column, "A format like ':" + format + "' is only allowed in output patterns"));
                return null;
            }
            if (field.get() != Field.TRACK && field.get() != Field.DISC) {
                errors.add(new PatternError(column, "A format is only allowed for {track} and {disc}"));
                return null;
            }
            if (format.length() < 2 || format.length() > 4 || !format.chars().allMatch(ch -> ch == '0')) {
                errors.add(new PatternError(column, "The format must be 2 to 4 zeros, for example {track:00}"));
                return null;
            }
            pad = OptionalInt.of(format.length());
        }
        if (!used.add(field.get())) {
            errors.add(new PatternError(column, "'{" + name + "}' is used twice"));
            return null;
        }
        return new Token.Placeholder(field.get(), pad);
    }

    private static void extensionHint(PatternSegment last, List<Issue> hints) {
        Token tail = last.tokens().get(last.tokens().size() - 1);
        if (!(tail instanceof Token.Literal literal)) {
            return;
        }
        String text = literal.text().toLowerCase(Locale.ROOT);
        for (MediaFormat format : MediaFormat.values()) {
            for (String extension : format.extensions()) {
                if (text.endsWith("." + extension)) {
                    hints.add(Issue.warning(IssueCode.PATTERN_EXTENSION, "The pattern ends with '." + extension
                            + "'. The file extension is never part of a pattern, so this would never match"));
                    return;
                }
            }
        }
    }
}
