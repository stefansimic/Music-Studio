package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matches the names on a path against an {@link InputPattern}. The last segment is matched against the file name
 * without its extension, the segment before it against the parent folder, and so on; segments are independent.
 *
 * <p>Semantics (ADR 0012, architecture 10.2): everything is NFC and works on code points, so no surrogate pair is
 * ever split. Literals are case-sensitive; leading and trailing spaces of a literal match any amount of whitespace
 * (also none), inner runs of spaces and literals that are only spaces match one or more. Placeholders capture non-empty,
 * stripped text; typed ones must parse ({@link FieldValueParser}), which prunes splits. {@code {*}} may capture nothing.
 * The match is a dynamic program over (token, position) that counts complete splits up to 2; the chosen split is the
 * one with the shortest earlier captures (non-greedy, left to right). Two or more splits mean AMBIGUOUS.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class PathMatcher {

    private static final String DASHES = "‐‑‒–—―−﹘﹣－";

    private PathMatcher() {}

    /**
     * @param pattern the pattern; not null
     * @param file    the path of a file (absolute or relative); not null. Folders above the path are only looked at
     *                if the pattern has that many segments.
     * @return the match; never null; never throws for odd names
     * @throws NullPointerException if an argument is null
     */
    public static MatchResult match(InputPattern pattern, Path file) {
        Require.nonNull(pattern, "pattern");
        Require.nonNull(file, "file");
        List<PatternSegment> segments = pattern.segments();
        List<String> names = new ArrayList<>();
        Path current = file;
        for (int i = 0; i < segments.size(); i++) {
            if (current == null || current.getFileName() == null) {
                return MatchResult.noMatch("The path has " + names.size() + " name(s) but the pattern has "
                        + segments.size() + " part(s) separated by '/'");
            }
            String name = current.getFileName().toString();
            if (i == 0) {
                name = withoutExtension(name);
            }
            names.add(Normalizer.normalize(name, Normalizer.Form.NFC));
            current = current.getParent();
        }
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        Map<Field, String> raw = new EnumMap<>(Field.class);
        int alternatives = 0;
        for (int s = 0; s < segments.size(); s++) {
            String name = names.get(segments.size() - 1 - s); // segment s from the left matches names from the end
            Segment result = matchSegment(segments.get(s), name);
            if (result.failure() != null) {
                String where = segments.size() == 1 ? "" : "In part " + (s + 1) + " of " + segments.size() + " (\""
                        + name + "\"): ";
                return MatchResult.noMatch(where + result.failure());
            }
            values.putAll(result.values());
            raw.putAll(result.raw());
            alternatives += result.count() > 1 ? 1 : 0;
        }
        MatchKind kind = alternatives > 0 ? MatchKind.AMBIGUOUS : MatchKind.MATCHED;
        return new MatchResult(kind, values, raw, alternatives > 0 ? 1 : 0, Optional.empty());
    }

    private static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && MediaFormat.ofFileName(name).isPresent()) {
            return name.substring(0, dot);
        }
        return name;
    }

    // ---- one segment ------------------------------------------------------------------------------------------------

    private record Segment(int count, String failure, Map<Field, FieldValue> values, Map<Field, String> raw) {}

    /** A literal split into its matching elements. */
    private record Lit(String text, int[] core, boolean leadSpace, boolean trailSpace, boolean onlySpace) {}

    private static Lit lit(String text) {
        int[] cps = text.codePoints().toArray();
        int start = 0;
        int end = cps.length;
        while (start < end && FieldValueParser.isSpace(cps[start])) {
            start++;
        }
        while (end > start && FieldValueParser.isSpace(cps[end - 1])) {
            end--;
        }
        boolean only = start == end;
        return new Lit(text, java.util.Arrays.copyOfRange(cps, start, end), start > 0 && !only, end < cps.length && !only,
                only);
    }

    private static Segment matchSegment(PatternSegment segment, String name) {
        int[] s = name.codePoints().toArray();
        List<Token> tokens = segment.tokens();
        Lit[] lits = new Lit[tokens.size()];
        for (int i = 0; i < lits.length; i++) {
            if (tokens.get(i) instanceof Token.Literal literal) {
                lits[i] = lit(literal.text());
            }
        }
        Search search = Search.of(tokens, lits, s);
        int start = 0;
        if (!(tokens.get(0) instanceof Token.Literal)) {
            start = skipSpaces(s, 0);
        }
        int count = search.count(0, start);
        if (count == 0) {
            return new Segment(0, search.failureMessage(name), Map.of(), Map.of());
        }
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        Map<Field, String> raw = new EnumMap<>(Field.class);
        // reconstruct the chosen (shortest-first) split
        int pos = start;
        for (int t = 0; t < tokens.size(); t++) {
            Token token = tokens.get(t);
            if (token instanceof Token.Literal) {
                pos = search.literalEnd(t, pos);
            } else {
                int end = search.chosenEnd(t, pos);
                String captured = FieldValueParser.strip(new String(s, pos, end - pos));
                if (token instanceof Token.Placeholder placeholder) {
                    FieldValue value = FieldValueParser.parse(placeholder.field(), captured).value().orElseThrow();
                    values.put(placeholder.field(), value);
                    raw.put(placeholder.field(), captured);
                }
                pos = end;
            }
        }
        return new Segment(count, null, values, raw);
    }

    private static int skipSpaces(int[] s, int from) {
        int p = from;
        while (p < s.length && FieldValueParser.isSpace(s[p])) {
            p++;
        }
        return p;
    }

    /** The memoised search over (token, position) for one segment. */
    private record Search(List<Token> tokens, Lit[] lits, int[] s, int[][] memo, int[][] choice, int[] failPos,
                          String[] failText) {

        static Search of(List<Token> tokens, Lit[] lits, int[] s) {
            int[][] memo = new int[tokens.size() + 1][s.length + 1];
            for (int[] row : memo) {
                java.util.Arrays.fill(row, -1);
            }
            return new Search(tokens, lits, s, memo, new int[tokens.size() + 1][s.length + 1], new int[] {-1},
                    new String[1]);
        }

        /** @return the end position after matching literal token {@code t} at {@code pos}, or -1 */
        int literalEnd(int t, int pos) {
            Lit lit = lits[t];
            int p = pos;
            if (lit.onlySpace) {
                int q = skipSpaces(s, p);
                return q > p ? q : -1;
            }
            if (lit.leadSpace) {
                p = skipSpaces(s, p);
            }
            int i = 0;
            int[] core = lit.core;
            while (i < core.length) {
                if (FieldValueParser.isSpace(core[i])) {
                    int j = i;
                    while (j < core.length && FieldValueParser.isSpace(core[j])) {
                        j++;
                    }
                    int q = skipSpaces(s, p);
                    if (q == p) {
                        return -1;
                    }
                    p = q;
                    i = j;
                } else {
                    if (p >= s.length || s[p] != core[i]) {
                        return -1;
                    }
                    p++;
                    i++;
                }
            }
            if (lit.trailSpace) {
                p = skipSpaces(s, p);
            }
            return p;
        }

        /** @return the number of ways (0, 1, 2 = two or more) to match tokens t.. against s[pos..] */
        int count(int t, int pos) {
            if (t == tokens.size()) {
                return pos == s.length ? 1 : 0;
            }
            if (memo[t][pos] >= 0) {
                return memo[t][pos];
            }
            int total = 0;
            int chosen = -1;
            Token token = tokens.get(t);
            if (token instanceof Token.Literal) {
                int end = literalEnd(t, pos);
                if (end >= 0) {
                    total = count(t + 1, end);
                    chosen = end;
                } else {
                    fail(pos, "separator \"" + lits[t].text + "\" not found after \"" + shown(pos) + "\"");
                }
            } else {
                boolean last = t + 1 == tokens.size();
                boolean wildcard = token instanceof Token.Wildcard;
                if (last) {
                    int end = s.length;
                    if (capturable(token, pos, end)) {
                        total = 1;
                        chosen = end;
                    }
                } else {
                    for (int end = pos + (wildcard ? 0 : 1); end <= s.length && total < 2; end++) {
                        if (!boundaryOk(t + 1, pos, end)) {
                            continue;
                        }
                        int litEnd = literalEnd(t + 1, end);
                        if (litEnd < 0) {
                            continue;
                        }
                        int rest = count(t + 2, litEnd);
                        if (rest > 0 && capturable(token, pos, end)) {
                            if (chosen < 0) {
                                chosen = end;
                            }
                            total = Math.min(2, total + rest);
                        }
                    }
                    if (total == 0 && failPos[0] <= pos) {
                        fail(pos, "separator \"" + lits[t + 1].text + "\" not found after \"" + shown(pos) + "\"");
                    }
                }
            }
            memo[t][pos] = total;
            choice[t][pos] = chosen;
            return total;
        }

        /** The capture may only end where it does not end in spaces that the next literal would skip anyway. */
        private boolean boundaryOk(int literalToken, int from, int end) {
            if ((lits[literalToken].leadSpace || lits[literalToken].onlySpace) && end > from
                    && FieldValueParser.isSpace(s[end - 1])) {
                return false;
            }
            return true;
        }

        private boolean capturable(Token token, int from, int end) {
            // the whole name is NFC already (see match), so the slice is not normalised again
            String captured = FieldValueParser.strip(new String(s, from, end - from));
            if (token instanceof Token.Wildcard) {
                return true;
            }
            Field field = ((Token.Placeholder) token).field();
            if (captured.isEmpty()) {
                fail(from, "nothing between the separators for {" + field.placeholder() + "}");
                return false;
            }
            FieldValueParser.Parsed parsed = FieldValueParser.parse(field, captured);
            if (parsed.value().isEmpty()) {
                fail(from + 1, parsed.error().orElse("invalid value"));
                return false;
            }
            return true;
        }

        int chosenEnd(int t, int pos) {
            count(t, pos);
            return choice[t][pos];
        }

        private String shown(int pos) {
            String text = new String(s, pos, s.length - pos);
            return text.length() > 40 ? text.substring(0, 40) + "…" : text;
        }

        private void fail(int pos, String message) {
            if (pos > failPos[0]) {
                failPos[0] = pos;
                failText[0] = message;
            }
        }

        String failureMessage(String name) {
            String message = failText[0] == null ? "The name does not fit the pattern" : failText[0];
            for (Lit lit : lits) {
                if (lit != null && hasDashMismatch(lit.text, name)) {
                    return message + ". Note: the name has a different dash than the pattern; '-' and the "
                            + "look-alikes '–' '—' are different characters";
                }
            }
            return message;
        }

        private boolean hasDashMismatch(String literal, String name) {
            boolean literalHyphen = literal.indexOf('-') >= 0;
            boolean literalOther = literal.chars().anyMatch(c -> DASHES.indexOf(c) >= 0);
            boolean nameHyphen = name.indexOf('-') >= 0;
            boolean nameOther = name.chars().anyMatch(c -> DASHES.indexOf(c) >= 0);
            return (literalHyphen && nameOther && !nameHyphen) || (literalOther && nameHyphen && !nameOther);
        }
    }
}
