package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValueParser;
import dev.rs.slava.icxc.musicstudio.core.model.ParsedValue;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matches an {@link InputPattern} against a file path (architecture §10.2, ADR 0010).
 *
 * <ul>
 *   <li>The last segment matches the file name without its extension (the text before the last dot), segment k
 *       from the end the k-th path element from the end – also above the scan root. Segments are independent.</li>
 *   <li>Names and literals are compared in Unicode NFC, code point by code point.</li>
 *   <li>A literal is [leading whitespace] core [trailing whitespace]. Cores match case-sensitively. Every
 *       whitespace run of a literal consumes the whole whitespace run of the name (possessive), so one split of the
 *       name gives exactly one solution. Strict tier: every run needs ≥ 1 whitespace character, except at a segment
 *       edge. Lenient tier (only if the strict tier finds nothing): the leading/trailing runs may be empty.</li>
 *   <li>A placeholder captures non-empty text (stripped) that {@link FieldValueParser} accepts – typed
 *       placeholders thereby prune splits. {@code {*}} captures anything, also nothing.</li>
 *   <li>Dynamic programming counts the solutions (capped at 2). One → MATCHED; two or more → AMBIGUOUS with the
 *       non-greedy (shortest captures first) split chosen and the greedy split as alternative; none → NO_MATCH
 *       with the furthest progress as reason.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> stateless and pure (no I/O), safe for concurrent use.
 */
public final class PathMatcher {

    private PathMatcher() {
    }

    /** A literal split into whitespace runs and non-whitespace chunks. */
    private record CompiledLiteral(boolean leadingSpace, List<int[]> chunks, boolean trailingSpace) {

        boolean spaceOnly() {
            return chunks.isEmpty();
        }
    }

    /** The name of one path element prepared for matching. */
    private record Name(int[] cps, int[] spaceBefore, int[] badBefore, int[] badMultiBefore, int[] contentBefore) {
    }

    private record SegmentResult(MatchKind kind, Map<Field, FieldValue> values, Map<Field, String> raw,
                                 Map<Field, String> alternative, String reason) {
    }

    /**
     * Matches {@code pattern} against {@code file}.
     *
     * <p><b>Preconditions:</b> pattern, file not null (NPE); file has at least one name element (IAE).
     * <p><b>Postconditions/Guarantees:</b> see {@link MatchResult}: MATCHED iff exactly one set of placeholder values
     * fits; AMBIGUOUS iff several sets fit (the non-greedy one is chosen; {@code alternative} shows other values);
     * NO_MATCH with a reason otherwise. Values are NFC and parsed by {@link FieldValueParser}. Pure, no I/O.
     *
     * @param pattern the input pattern; not null
     * @param file    the file path; not null, at least one name element
     * @return the match result; never null
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if {@code file} has no name element (e.g. "/")
     */
    public static MatchResult match(InputPattern pattern, Path file) {
        Require.nonNull(pattern, "pattern");
        Require.nonNull(file, "file");
        Require.that(file.getNameCount() >= 1, "file must have a name: %s", file);
        int levels = pattern.segments().size();
        int available = file.getNameCount();
        if (available < levels) {
            return MatchResult.noMatch("the pattern has " + levels + " levels, the path has only " + available);
        }
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        Map<Field, String> raw = new EnumMap<>(Field.class);
        Map<Field, String> alternative = new EnumMap<>(Field.class);
        boolean ambiguous = false;
        for (int s = 0; s < levels; s++) {
            boolean last = s == levels - 1;
            String element = file.getName(available - levels + s).toString();
            String text = last ? baseName(element) : element;
            SegmentResult result = matchSegment(pattern.segments().get(s).tokens(), text);
            if (result.kind() == MatchKind.NO_MATCH) {
                String where = levels == 1 ? "" : (last ? "file name \"" : "folder \"") + text + "\": ";
                return MatchResult.noMatch(where + result.reason());
            }
            ambiguous |= result.kind() == MatchKind.AMBIGUOUS;
            values.putAll(result.values());
            raw.putAll(result.raw());
            alternative.putAll(result.alternative());
        }
        return new MatchResult(ambiguous ? MatchKind.AMBIGUOUS : MatchKind.MATCHED, values, raw, alternative,
                Optional.empty());
    }

    /** The file name without extension: the text before the last dot. */
    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    // --- one segment ----------------------------------------------------------------------------------------

    private static SegmentResult matchSegment(List<Token> tokens, String text) {
        Name name = prepare(Normalizer.normalize(text, Normalizer.Form.NFC));
        List<Object> compiled = compile(tokens);
        for (boolean strict : new boolean[] {true, false}) {
            int[][] ways = ways(compiled, name, strict);
            if (ways[0][0] > 0) {
                Map<Field, String> chosenRaw = new EnumMap<>(Field.class);
                Map<Field, FieldValue> chosen = reconstruct(compiled, name, strict, ways, false, chosenRaw);
                if (ways[0][0] == 1) {
                    return new SegmentResult(MatchKind.MATCHED, chosen, chosenRaw, Map.of(), "");
                }
                Map<Field, String> greedyRaw = new EnumMap<>(Field.class);
                reconstruct(compiled, name, strict, ways, true, greedyRaw);
                Map<Field, String> alternative = new EnumMap<>(Field.class);
                greedyRaw.forEach((field, value) -> {
                    if (!value.equals(chosenRaw.get(field))) {
                        alternative.put(field, value);
                    }
                });
                if (alternative.isEmpty()) {
                    // the extreme splits agree; a split in between may still give other values
                    alternative.putAll(otherValues(compiled, name, strict, ways, chosenRaw));
                }
                if (alternative.isEmpty()) {
                    // all splits differ only in ignored {*} text: the values are unambiguous
                    return new SegmentResult(MatchKind.MATCHED, chosen, chosenRaw, Map.of(), "");
                }
                return new SegmentResult(MatchKind.AMBIGUOUS, chosen, chosenRaw, alternative, "");
            }
        }
        return new SegmentResult(MatchKind.NO_MATCH, Map.of(), Map.of(), Map.of(), diagnose(compiled, name));
    }

    private static List<Object> compile(List<Token> tokens) {
        List<Object> compiled = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            compiled.add(token instanceof Token.Literal literal ? compileLiteral(literal.text()) : token);
        }
        return compiled;
    }

    private static CompiledLiteral compileLiteral(String text) {
        int[] cps = Normalizer.normalize(text, Normalizer.Form.NFC).codePoints().toArray();
        List<int[]> chunks = new ArrayList<>();
        int i = 0;
        boolean leading = cps.length > 0 && FieldValueParser.isSpace(cps[0]);
        while (i < cps.length) {
            while (i < cps.length && FieldValueParser.isSpace(cps[i])) {
                i++;
            }
            int start = i;
            while (i < cps.length && !FieldValueParser.isSpace(cps[i])) {
                i++;
            }
            if (i > start) {
                chunks.add(Arrays.copyOfRange(cps, start, i));
            }
        }
        boolean trailing = cps.length > 0 && FieldValueParser.isSpace(cps[cps.length - 1]) && !chunks.isEmpty();
        return new CompiledLiteral(leading, List.copyOf(chunks), trailing);
    }

    private static Name prepare(String text) {
        int[] cps = text.codePoints().toArray();
        int n = cps.length;
        int[] space = new int[n + 1];
        int[] bad = new int[n + 1];
        int[] badMulti = new int[n + 1];
        int[] content = new int[n + 1];
        for (int i = 0; i < n; i++) {
            int cp = cps[i];
            boolean isSpace = FieldValueParser.isSpace(cp);
            boolean control = Character.isISOControl(cp);
            space[i + 1] = space[i] + (isSpace ? 1 : 0);
            bad[i + 1] = bad[i] + (control ? 1 : 0);
            badMulti[i + 1] = badMulti[i] + (control && cp != '\n' && cp != '\r' ? 1 : 0);
            content[i + 1] = content[i] + (!isSpace && cp != ';' ? 1 : 0);
        }
        return new Name(cps, space, bad, badMulti, content);
    }

    // --- dynamic programming --------------------------------------------------------------------------------

    /** ways[i][p] = number of matches (capped at 2) of tokens i.. against cps[p..n). */
    private static int[][] ways(List<Object> tokens, Name name, boolean strict) {
        int m = tokens.size();
        int n = name.cps().length;
        int[][] ways = new int[m + 1][n + 1];
        ways[m][n] = 1;
        for (int i = m - 1; i >= 0; i--) {
            Object token = tokens.get(i);
            for (int p = n; p >= 0; p--) {
                if (token instanceof CompiledLiteral literal) {
                    int end = literalEnd(literal, name, p, strict, i == 0, i == m - 1);
                    ways[i][p] = end < 0 ? 0 : ways[i + 1][end];
                } else {
                    int count = 0;
                    int minEnd = token instanceof Token.Wildcard ? p : p + 1;
                    for (int e = minEnd; e <= n && count < 2; e++) {
                        if (ways[i + 1][e] > 0 && captureValid(token, name, p, e)) {
                            count += ways[i + 1][e];
                        }
                    }
                    ways[i][p] = Math.min(count, 2);
                }
            }
        }
        return ways;
    }

    /** End position after matching a literal at p, or -1. */
    private static int literalEnd(CompiledLiteral literal, Name name, int p, boolean strict, boolean leadingEdge,
                                  boolean trailingEdge) {
        int[] cps = name.cps();
        int n = cps.length;
        if (literal.spaceOnly()) {
            if (p > 0 && FieldValueParser.isSpace(cps[p - 1])) {
                return -1;
            }
            int q = skipSpace(cps, p);
            return q == p && !leadingEdge && !trailingEdge ? -1 : q;
        }
        int q = p;
        if (literal.leadingSpace()) {
            if (p > 0 && FieldValueParser.isSpace(cps[p - 1])) {
                return -1; // possessive: the run must be consumed from its start
            }
            q = skipSpace(cps, p);
            if (q == p && strict && !leadingEdge) {
                return -1;
            }
        }
        for (int k = 0; k < literal.chunks().size(); k++) {
            int[] chunk = literal.chunks().get(k);
            if (q + chunk.length > n) {
                return -1;
            }
            for (int j = 0; j < chunk.length; j++) {
                if (cps[q + j] != chunk[j]) {
                    return -1;
                }
            }
            q += chunk.length;
            if (k < literal.chunks().size() - 1) {
                int r = skipSpace(cps, q);
                if (r == q) {
                    return -1;
                }
                q = r;
            }
        }
        if (literal.trailingSpace()) {
            int r = skipSpace(cps, q);
            if (r == q && strict && !trailingEdge) {
                return -1;
            }
            q = r;
        }
        return q;
    }

    private static int skipSpace(int[] cps, int from) {
        int q = from;
        while (q < cps.length && FieldValueParser.isSpace(cps[q])) {
            q++;
        }
        return q;
    }

    /** Whether cps[p..e) is a valid capture for the token; cheap checks first. */
    private static boolean captureValid(Object token, Name name, int p, int e) {
        if (token instanceof Token.Wildcard) {
            return true;
        }
        Field field = ((Token.Placeholder) token).field();
        int length = e - p;
        int nonSpace = length - (name.spaceBefore()[e] - name.spaceBefore()[p]);
        if (nonSpace == 0) {
            return false;
        }
        return switch (field.type()) {
            case TEXT -> (field.multiLine() ? name.badMultiBefore() : name.badBefore())[e]
                    - (field.multiLine() ? name.badMultiBefore() : name.badBefore())[p] == 0;
            case GENRES -> name.badBefore()[e] - name.badBefore()[p] == 0
                    && name.contentBefore()[e] - name.contentBefore()[p] > 0;
            case YEAR, POSITION -> nonSpace <= 16 && parse(field, name, p, e).isPresent();
        };
    }

    private static Optional<FieldValue> parse(Field field, Name name, int p, int e) {
        ParsedValue parsed = FieldValueParser.parse(field, text(name, p, e));
        return parsed instanceof ParsedValue.Valid valid ? Optional.of(valid.value()) : Optional.empty();
    }

    private static String text(Name name, int p, int e) {
        return new String(name.cps(), p, e - p);
    }

    /** Rebuilds the chosen (shortest captures first) or greedy (longest first) split. */
    private static Map<Field, FieldValue> reconstruct(List<Object> tokens, Name name, boolean strict, int[][] ways,
                                                      boolean greedy, Map<Field, String> raw) {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        int m = tokens.size();
        int n = name.cps().length;
        int p = 0;
        for (int i = 0; i < m; i++) {
            Object token = tokens.get(i);
            if (token instanceof CompiledLiteral literal) {
                p = literalEnd(literal, name, p, strict, i == 0, i == m - 1);
                continue;
            }
            int minEnd = token instanceof Token.Wildcard ? p : p + 1;
            int end = -1;
            if (greedy) {
                for (int e = n; e >= minEnd && end < 0; e--) {
                    end = ways[i + 1][e] > 0 && captureValid(token, name, p, e) ? e : -1;
                }
            } else {
                for (int e = minEnd; e <= n && end < 0; e++) {
                    end = ways[i + 1][e] > 0 && captureValid(token, name, p, e) ? e : -1;
                }
            }
            assert end >= 0 : "a counted solution must be reconstructible";
            if (token instanceof Token.Placeholder placeholder) {
                String captured = text(name, p, end);
                ParsedValue parsed = FieldValueParser.parse(placeholder.field(), captured);
                assert parsed instanceof ParsedValue.Valid : "a valid capture must parse";
                values.put(placeholder.field(), ((ParsedValue.Valid) parsed).value());
                raw.put(placeholder.field(), FieldValueParser.strip(captured));
            }
            p = end;
        }
        return values;
    }

    /**
     * Placeholder values that some complete solution gives but the chosen one does not. A capture (p, e) of token
     * i is part of a solution iff tokens before i reach p and tokens after i complete from e.
     */
    private static Map<Field, String> otherValues(List<Object> tokens, Name name, boolean strict, int[][] ways,
                                                  Map<Field, String> chosenRaw) {
        boolean[][] reach = reach(tokens, name, strict);
        Map<Field, String> other = new EnumMap<>(Field.class);
        int n = name.cps().length;
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Token.Placeholder placeholder)) {
                continue;
            }
            Field field = placeholder.field();
            for (int p = 0; p <= n && !other.containsKey(field); p++) {
                if (!reach[i][p]) {
                    continue;
                }
                for (int e = p + 1; e <= n; e++) {
                    if (ways[i + 1][e] > 0 && captureValid(placeholder, name, p, e)) {
                        String value = FieldValueParser.strip(text(name, p, e));
                        if (!value.equals(chosenRaw.get(field))) {
                            other.put(field, value);
                            break;
                        }
                    }
                }
            }
        }
        return other;
    }

    /** reach[i][p] = tokens before i can match cps[0..p). */
    private static boolean[][] reach(List<Object> tokens, Name name, boolean strict) {
        int m = tokens.size();
        int n = name.cps().length;
        boolean[][] reach = new boolean[m + 1][n + 1];
        reach[0][0] = true;
        for (int i = 0; i < m; i++) {
            Object token = tokens.get(i);
            for (int p = 0; p <= n; p++) {
                if (!reach[i][p]) {
                    continue;
                }
                if (token instanceof CompiledLiteral literal) {
                    int end = literalEnd(literal, name, p, strict, i == 0, i == m - 1);
                    if (end >= 0) {
                        reach[i + 1][end] = true;
                    }
                } else {
                    for (int e = token instanceof Token.Wildcard ? p : p + 1; e <= n; e++) {
                        if (captureValid(token, name, p, e)) {
                            reach[i + 1][e] = true;
                        }
                    }
                }
            }
        }
        return reach;
    }

    // --- diagnostics ----------------------------------------------------------------------------------------

    /** Furthest progress of the lenient tier: which token failed where. */
    private static String diagnose(List<Object> tokens, Name name) {
        int m = tokens.size();
        int n = name.cps().length;
        if (n == 0) {
            return "the name is empty";
        }
        boolean controlsAllowed = tokens.stream().anyMatch(t -> t instanceof Token.Wildcard
                || t instanceof Token.Placeholder p && p.field().multiLine());
        if (name.badBefore()[n] > 0 && !controlsAllowed) {
            return "the name contains a control character or line break";
        }
        boolean[][] reach = reach(tokens, name, false);
        int best = -1;
        int bestP = -1;
        for (int i = m; i >= 0 && best < 0; i--) {
            for (int p = n; p >= 0; p--) {
                if (reach[i][p]) {
                    best = i;
                    bestP = p;
                    break;
                }
            }
        }
        String before = FieldValueParser.strip(text(name, 0, bestP));
        String rest = text(name, bestP, n);
        if (best == m) {
            return "unexpected text \"" + rest + "\" at the end";
        }
        Object token = tokens.get(best);
        if (token instanceof CompiledLiteral literal) {
            String separator = literalText(literal);
            return before.isEmpty()
                    ? "the name does not start with \"" + separator + "\""
                    : "separator \"" + separator + "\" not found after \"" + before + "\"";
        }
        Field field = ((Token.Placeholder) token).field();
        if (FieldValueParser.strip(rest).isEmpty()) {
            return "the name ends before {" + field.placeholder() + "}";
        }
        if (best == m - 1) {
            ParsedValue parsed = FieldValueParser.parse(field, rest);
            if (parsed instanceof ParsedValue.Invalid invalid) {
                return invalid.reason();
            }
        }
        return "no valid {" + field.placeholder() + "} in \"" + FieldValueParser.strip(rest) + "\"";
    }

    private static String literalText(CompiledLiteral literal) {
        StringBuilder text = new StringBuilder(literal.leadingSpace() ? " " : "");
        for (int k = 0; k < literal.chunks().size(); k++) {
            if (k > 0) {
                text.append(' ');
            }
            text.append(new String(literal.chunks().get(k), 0, literal.chunks().get(k).length));
        }
        return text.append(literal.trailingSpace() || literal.spaceOnly() ? " " : "").toString();
    }
}
