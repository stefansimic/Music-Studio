package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Turns text (a captured part of a file name, or the value of {@code --set field=value}) into a typed
 * {@link FieldValue}. Text is normalised to NFC and stripped (also of no-break and ideographic spaces). Digits are ASCII
 * only: {@code ٢٠٢٠} or full-width digits are text, not numbers.
 *
 * <ul>
 *   <li>YEAR: {@code 2020}, {@code (2020)} or {@code [2020]}; 1000..2999.</li>
 *   <li>TRACK, DISC: 1 to 4 digits, optionally followed by {@code of N} or {@code /N} (spaces allowed); 0..9999.</li>
 *   <li>GENRE: one or more names separated by {@code ;}; duplicates are dropped.</li>
 *   <li>Other fields: any non-empty text; line breaks in single-line fields become spaces.</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class FieldValueParser {

    private FieldValueParser() {}

    /**
     * Result of {@link #parse}.
     *
     * @param value the typed value, empty if the text was rejected; not null
     * @param error why it was rejected, empty if it was accepted; not null. Exactly one of value and error is present.
     */
    public record Parsed(Optional<FieldValue> value, Optional<String> error) {
        public Parsed {
            Require.nonNull(value, "value");
            Require.nonNull(error, "error");
            Require.that(value.isPresent() != error.isPresent(), "exactly one of value and error must be present");
        }

        static Parsed ok(FieldValue value) {
            return new Parsed(Optional.of(value), Optional.empty());
        }

        static Parsed rejected(String message) {
            return new Parsed(Optional.empty(), Optional.of(message));
        }
    }

    /**
     * @param field the field the value is for; not null
     * @param text  the text; not null
     * @return the typed value, or the reason for rejection (naming the offending text); never null
     * @throws NullPointerException if an argument is null
     */
    public static Parsed parse(Field field, String text) {
        Require.nonNull(field, "field");
        Require.nonNull(text, "text");
        String value = strip(Normalizer.normalize(text, Normalizer.Form.NFC));
        if (value.isEmpty()) {
            return Parsed.rejected("The value for " + field.placeholder() + " is empty");
        }
        return switch (field.type()) {
            case YEAR -> year(value);
            case POSITION -> position(field, value);
            case GENRES -> genres(value);
            case TEXT -> text(field, value);
        };
    }

    /** Strips Unicode whitespace including no-break space and the ideographic space from both ends. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** @return true for whitespace, no-break spaces and the ideographic space */
    public static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || cp == 0xA0 || cp == 0x2007 || cp == 0x202F || cp == 0x3000;
    }

    private static Parsed year(String value) {
        String digits = value;
        if (value.length() >= 2 && ((value.startsWith("(") && value.endsWith(")"))
                || (value.startsWith("[") && value.endsWith("]")))) {
            digits = value.substring(1, value.length() - 1).strip();
        }
        if (digits.length() == 4 && asciiDigits(digits)) {
            int year = Integer.parseInt(digits);
            if (year >= 1000 && year <= 2999) {
                return Parsed.ok(new FieldValue.YearValue(year));
            }
        }
        return Parsed.rejected("\"" + value + "\" is not a 4-digit year");
    }

    private static Parsed position(Field field, String value) {
        String kind = field == Field.DISC ? "a disc number" : "a track number";
        int i = 0;
        while (i < value.length() && value.charAt(i) >= '0' && value.charAt(i) <= '9') {
            i++;
        }
        if (i == 0 || i > 4) {
            return Parsed.rejected("\"" + value + "\" is not " + kind);
        }
        int number = Integer.parseInt(value.substring(0, i));
        String rest = value.substring(i).stripLeading();
        OptionalInt total = OptionalInt.empty();
        if (!rest.isEmpty()) {
            String digits;
            if (rest.startsWith("/")) {
                digits = rest.substring(1).strip();
            } else if (rest.toLowerCase(Locale.ROOT).startsWith("of")) {
                digits = rest.substring(2).strip();
            } else {
                return Parsed.rejected("\"" + value + "\" is not " + kind);
            }
            if (digits.isEmpty() || digits.length() > 4 || !asciiDigits(digits)) {
                return Parsed.rejected("\"" + value + "\" is not " + kind);
            }
            int parsedTotal = Integer.parseInt(digits);
            if (parsedTotal < 1) {
                return Parsed.rejected("\"" + value + "\" is not " + kind + " (the total must be at least 1)");
            }
            total = OptionalInt.of(parsedTotal);
        }
        return Parsed.ok(new FieldValue.Position(number, total));
    }

    private static Parsed genres(String value) {
        Set<String> seen = new LinkedHashSet<>();
        List<Genre> genres = new ArrayList<>();
        for (String part : value.split(";")) {
            String name = strip(part);
            if (!name.isEmpty() && seen.add(name.toLowerCase(Locale.ROOT))) {
                genres.add(new Genre(name));
            }
        }
        if (genres.isEmpty()) {
            return Parsed.rejected("The genre is empty");
        }
        return Parsed.ok(new FieldValue.Genres(genres));
    }

    private static Parsed text(Field field, String value) {
        String text = value.replace("\u0000", "");
        if (!field.multiLine()) {
            text = singleLine(text);
        }
        text = strip(text);
        if (text.isEmpty()) {
            return Parsed.rejected("The value for " + field.placeholder() + " is empty");
        }
        return Parsed.ok(new FieldValue.Text(text));
    }

    /** Every line break (with the spaces around it) becomes one space. */
    private static String singleLine(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingBreak = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean lineBreak = c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029' || c == '\u0085';
            if (lineBreak) {
                while (out.length() > 0 && isSpace(out.charAt(out.length() - 1))) {
                    out.setLength(out.length() - 1);
                }
                pendingBreak = true;
            } else if (pendingBreak && isSpace(c)) {
                continue;
            } else {
                if (pendingBreak && out.length() > 0) {
                    out.append(' ');
                }
                pendingBreak = false;
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean asciiDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return !text.isEmpty();
    }
}
