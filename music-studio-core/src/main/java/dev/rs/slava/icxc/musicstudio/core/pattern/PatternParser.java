package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldType;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Parses the pattern language (architecture §10.1, ADR 0009/0010).
 *
 * <pre>
 * pattern     := segment ( "/" segment )*
 * segment     := token+
 * token       := literal | placeholder | wildcard
 * placeholder := "{" name "}"     name = a field placeholder, case-insensitive
 * wildcard    := "{*}"
 * literal     := ( any char except "{" "}" "/"  |  "{{"  |  "}}" )+
 * </pre>
 *
 * Input-pattern errors (all are reported, sorted by column): unknown placeholder, empty {@code {}}, a format such as
 * {@code {track:00}}, unbalanced braces, empty segment, empty pattern, two untyped placeholders (text, genre or
 * {@code {*}}) without a literal between them, a repeated placeholder, a file extension at the end (also with
 * trailing spaces), control characters, and a pattern without any placeholder (only {@code {*}} or text).
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class PatternParser {

    private static final String VALID_NAMES = Arrays.stream(Field.values())
            .map(f -> "{" + f.placeholder() + "}").collect(Collectors.joining(" ")) + " {*}";

    private PatternParser() {
    }

    private record Positioned(Token token, int column) {
    }

    /**
     * Parses an input pattern.
     *
     * <p><b>Preconditions:</b> source not null (NPE).
     * <p><b>Postconditions/Guarantees:</b> Valid: {@code pattern.source()} equals {@code source} verbatim, literals
     * are NFC, adjacent literal text is merged, {@code {{}}/{@code }}} are unescaped. Invalid: every error found,
     * sorted by column; columns are 0-based UTF-16 char indices into {@code source} (≤ its length). Pure.
     *
     * @param source the pattern text; not null
     * @return the pattern or all errors; never null
     * @throws NullPointerException if {@code source} is null
     */
    public static PatternParseResult<InputPattern> parseInput(String source) {
        Require.nonNull(source, "source");
        List<PatternError> errors = new ArrayList<>();
        if (source.isBlank()) {
            return new PatternParseResult.Invalid<>(List.of(new PatternError(0, "the pattern is empty")));
        }
        List<List<Positioned>> segments = new ArrayList<>();
        List<Positioned> current = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int literalStart = -1;
        int segmentStart = 0;
        int segmentErrors = 0;
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/') {
                literalStart = flush(literal, literalStart, current);
                if (current.isEmpty() && errors.size() == segmentErrors) {
                    errors.add(new PatternError(i, "empty folder level: nothing between two \"/\""));
                }
                segments.add(current);
                current = new ArrayList<>();
                segmentStart = i + 1;
                segmentErrors = errors.size();
                i++;
            } else if (c == '{' && source.startsWith("{{", i)) {
                literalStart = literalStart < 0 ? i : literalStart;
                literal.append('{');
                i += 2;
            } else if (c == '}' && source.startsWith("}}", i)) {
                literalStart = literalStart < 0 ? i : literalStart;
                literal.append('}');
                i += 2;
            } else if (c == '}') {
                errors.add(new PatternError(i, "\"}\" without \"{\" (write \"}}\" for a literal brace)"));
                i++;
            } else if (c == '{') {
                int close = closingBrace(source, i);
                if (close < 0) {
                    errors.add(new PatternError(i, "\"{\" is not closed (write \"{{\" for a literal brace)"));
                    i = nextSlash(source, i);
                    continue;
                }
                literalStart = flush(literal, literalStart, current);
                String name = source.substring(i + 1, close);
                Optional<Token> token = placeholder(name);
                if (token.isPresent()) {
                    current.add(new Positioned(token.get(), i));
                } else {
                    errors.add(placeholderError(name, i));
                }
                i = close + 1;
            } else {
                literalStart = literalStart < 0 ? i : literalStart;
                literal.append(c);
                i++;
            }
        }
        flush(literal, literalStart, current);
        if (current.isEmpty() && errors.size() == segmentErrors) {
            errors.add(new PatternError(Math.min(segmentStart, source.length()),
                    segments.isEmpty() ? "the pattern is empty" : "empty folder level after the last \"/\""));
        }
        segments.add(current);

        checkAdjacency(segments, errors);
        checkRepeats(segments, errors);
        checkExtension(source, segments, errors);
        checkControlCharacters(source, errors);
        if (errors.isEmpty() && segments.stream().flatMap(List::stream)
                .noneMatch(positioned -> positioned.token() instanceof Token.Placeholder)) {
            errors.add(new PatternError(0, "the pattern has no placeholder, so it extracts nothing; valid: "
                    + VALID_NAMES));
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(PatternError::column));
            return new PatternParseResult.Invalid<>(errors);
        }
        List<PatternSegment> parsed = segments.stream()
                .map(tokens -> new PatternSegment(tokens.stream().map(Positioned::token).toList()))
                .toList();
        return new PatternParseResult.Valid<>(new InputPattern(source, parsed));
    }

    /** Adds the collected literal text as one NFC literal; returns the reset start index. */
    private static int flush(StringBuilder literal, int start, List<Positioned> tokens) {
        if (!literal.isEmpty()) {
            tokens.add(new Positioned(new Token.Literal(Normalizer.normalize(literal, Normalizer.Form.NFC)), start));
            literal.setLength(0);
        }
        return -1;
    }

    private static int closingBrace(String source, int open) {
        for (int j = open + 1; j < source.length(); j++) {
            char c = source.charAt(j);
            if (c == '}') {
                return j;
            }
            if (c == '{' || c == '/') {
                return -1;
            }
        }
        return -1;
    }

    private static int nextSlash(String source, int from) {
        int slash = source.indexOf('/', from);
        return slash < 0 ? source.length() : slash;
    }

    private static Optional<Token> placeholder(String name) {
        String trimmed = name.strip().toLowerCase(Locale.ROOT);
        if (trimmed.equals("*")) {
            return Optional.of(new Token.Wildcard());
        }
        return Field.byPlaceholder(trimmed).map(Token.Placeholder::of);
    }

    private static PatternError placeholderError(String name, int column) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return new PatternError(column, "empty placeholder {}; valid: " + VALID_NAMES);
        }
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && (Field.byPlaceholder(trimmed.substring(0, colon).strip().toLowerCase(Locale.ROOT))
                .isPresent())) {
            return new PatternError(column, "a format such as {" + trimmed
                    + "} is only allowed in output patterns");
        }
        return new PatternError(column, "unknown placeholder {" + trimmed + "}; valid: " + VALID_NAMES);
    }

    private static boolean untyped(Token token) {
        return token instanceof Token.Wildcard
                || token instanceof Token.Placeholder p && p.field().type() != FieldType.YEAR
                && p.field().type() != FieldType.POSITION;
    }

    private static void checkAdjacency(List<List<Positioned>> segments, List<PatternError> errors) {
        for (List<Positioned> tokens : segments) {
            for (int k = 1; k < tokens.size(); k++) {
                Token previous = tokens.get(k - 1).token();
                Token token = tokens.get(k).token();
                if (untyped(previous) && untyped(token)) {
                    errors.add(new PatternError(tokens.get(k).column(),
                            "two placeholders without text between them cannot be told apart; add a separator"));
                }
            }
        }
    }

    private static void checkRepeats(List<List<Positioned>> segments, List<PatternError> errors) {
        Map<Field, Integer> seen = new EnumMap<>(Field.class);
        for (List<Positioned> tokens : segments) {
            for (Positioned positioned : tokens) {
                if (positioned.token() instanceof Token.Placeholder p && seen.putIfAbsent(p.field(), 1) != null) {
                    errors.add(new PatternError(positioned.column(), "{" + p.field().placeholder()
                            + "} occurs twice; use {*} for a part that repeats"));
                }
            }
        }
    }

    /** A pattern must not end with a media extension (also not followed by spaces); column = the dot in source. */
    private static void checkExtension(String source, List<List<Positioned>> segments, List<PatternError> errors) {
        List<Positioned> last = segments.getLast();
        if (last.isEmpty() || !(last.getLast().token() instanceof Token.Literal)) {
            return;
        }
        String trimmed = source.stripTrailing();
        int dot = trimmed.lastIndexOf('.');
        if (dot > trimmed.lastIndexOf('/') && dot > trimmed.lastIndexOf('}')
                && MediaFormat.ofFileName(trimmed.substring(dot)).isPresent()) {
            errors.add(new PatternError(dot, "leave out the file extension; it is never part of the pattern"));
        }
    }

    /** Control characters can never be typed into a file name pattern on purpose (and NUL never matches). */
    private static void checkControlCharacters(String source, List<PatternError> errors) {
        for (int i = 0; i < source.length(); i++) {
            if (Character.isISOControl(source.charAt(i))) {
                errors.add(new PatternError(i, "the pattern contains a control character or line break"));
                return;
            }
        }
    }
}
