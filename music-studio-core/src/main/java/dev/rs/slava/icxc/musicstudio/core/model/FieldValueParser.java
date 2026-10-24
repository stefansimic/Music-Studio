package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses user text (pattern captures, {@code --set} values, GUI cells) into typed field values. One parser for all
 * inputs, so frontends never duplicate the typed rules.
 *
 * <ul>
 *   <li>Input is normalised to Unicode NFC and stripped of whitespace (including no-break spaces).</li>
 *   <li>TEXT: not empty, no control characters and no bidi overrides/isolates (they could disguise text in a
 *       tag); line breaks only for multi-line fields (COMMENT).</li>
 *   <li>YEAR: 4 ASCII digits, optionally in {@code ()} or {@code []}, range 1000–2999.</li>
 *   <li>POSITION: 1–4 ASCII digits ≥ 1, optionally followed by {@code /} or {@code of} (any case) and a total of
 *       1–4 digits ≥ 1; spaces around the separator are allowed: {@code 3}, {@code 03}, {@code 3/12},
 *       {@code 3of12}, {@code 3 OF 12}.</li>
 *   <li>GENRES: split on {@code ;}, each part stripped, empty parts dropped, case-insensitive duplicates dropped
 *       (the first wins); at least one genre.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class FieldValueParser {

    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 2999;
    private static final int MAX_DIGITS = 4;

    private FieldValueParser() {
    }

    /**
     * Parses {@code raw} as a value of {@code field}.
     *
     * <p><b>Preconditions:</b> field, raw not null (NPE).
     * <p><b>Postconditions/Guarantees:</b> Valid: a value of {@code field.type()}, NFC, stripped (incl. no-break
     * spaces). Invalid: a reason that names the (stripped) input. Pure, never throws for any text.
     *
     * @param field the target field; not null
     * @param raw   the user text; not null
     * @return {@link ParsedValue.Valid} or {@link ParsedValue.Invalid}; never null
     * @throws NullPointerException if an argument is null
     */
    public static ParsedValue parse(Field field, String raw) {
        Require.nonNull(field, "field");
        Require.nonNull(raw, "raw");
        String value = strip(Normalizer.normalize(raw, Normalizer.Form.NFC));
        return switch (field.type()) {
            case TEXT -> text(field, value);
            case YEAR -> year(value);
            case POSITION -> position(field, value);
            case GENRES -> genres(value);
        };
    }

    /**
     * Strips whitespace including no-break spaces ({@link Character#isSpaceChar}) from both ends.
     *
     * @param value any text; not null
     * @return the stripped text; never null
     * @throws NullPointerException if {@code value} is null
     */
    public static String strip(String value) {
        Require.nonNull(value, "value");
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    /**
     * @param codePoint any code point
     * @return true for whitespace and space separators (incl. no-break space)
     */
    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static ParsedValue text(Field field, String value) {
        if (value.isEmpty()) {
            return new ParsedValue.Invalid("empty " + field.placeholder());
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean lineBreak = c == '\n' || c == '\r';
            if (Character.isISOControl(c) && !(lineBreak && field.multiLine()) || isBidiControl(c)) {
                return new ParsedValue.Invalid(
                        quote(value) + " contains a control character or line break, not allowed in "
                                + field.placeholder());
            }
        }
        return new ParsedValue.Valid(new FieldValue.Text(value));
    }

    private static boolean isBidiControl(char c) {
        return c >= '\u202A' && c <= '\u202E' || c >= '\u2066' && c <= '\u2069';
    }

    private static ParsedValue year(String value) {
        String digits = value;
        if (digits.length() >= 2 && (digits.startsWith("(") && digits.endsWith(")")
                || digits.startsWith("[") && digits.endsWith("]"))) {
            digits = strip(digits.substring(1, digits.length() - 1));
        }
        if (digits.length() != 4 || !asciiDigits(digits)) {
            return new ParsedValue.Invalid(quote(value) + " is not a valid year (4 digits expected)");
        }
        int year = Integer.parseInt(digits);
        if (year < MIN_YEAR || year > MAX_YEAR) {
            return new ParsedValue.Invalid(quote(value) + " is not a valid year (" + MIN_YEAR + "–" + MAX_YEAR + ")");
        }
        return new ParsedValue.Valid(new FieldValue.YearValue(year));
    }

    private static ParsedValue position(Field field, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        int numberEnd = 0;
        while (numberEnd < lower.length() && isAsciiDigit(lower.charAt(numberEnd))) {
            numberEnd++;
        }
        ParsedValue invalid = new ParsedValue.Invalid(quote(value) + " is not a valid " + field.placeholder()
                + " number (e.g. 3, 03, 3/12 or 3of12)");
        if (numberEnd == 0 || numberEnd > MAX_DIGITS) {
            return invalid;
        }
        int number = Integer.parseInt(lower.substring(0, numberEnd));
        String rest = strip(lower.substring(numberEnd));
        if (rest.isEmpty()) {
            return number < 1 ? invalid : new ParsedValue.Valid(FieldValue.Position.of(number));
        }
        String totalText;
        if (rest.startsWith("/")) {
            totalText = strip(rest.substring(1));
        } else if (rest.startsWith("of")) {
            totalText = strip(rest.substring(2));
        } else {
            return invalid;
        }
        if (totalText.isEmpty() || totalText.length() > MAX_DIGITS || !asciiDigits(totalText)) {
            return invalid;
        }
        int total = Integer.parseInt(totalText);
        if (number < 1 || total < 1) {
            return invalid;
        }
        return new ParsedValue.Valid(FieldValue.Position.of(number, total));
    }

    private static ParsedValue genres(String value) {
        List<Genre> genres = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : value.split(";", -1)) {
            String name = strip(part);
            if (name.isEmpty()) {
                continue;
            }
            for (int i = 0; i < name.length(); i++) {
                if (Character.isISOControl(name.charAt(i)) || isBidiControl(name.charAt(i))) {
                    return new ParsedValue.Invalid(quote(value) + " contains a control character or line break");
                }
            }
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                genres.add(new Genre(name));
            }
        }
        if (genres.isEmpty()) {
            return new ParsedValue.Invalid(quote(value) + " contains no genre");
        }
        return new ParsedValue.Valid(new FieldValue.Genres(genres));
    }

    private static boolean asciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isAsciiDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
