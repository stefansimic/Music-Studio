package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.time.Duration;

/**
 * Width and formatting helpers. Widths are terminal columns: emoji and East Asian wide characters count 2,
 * combining marks 0, everything else 1. Cutting works on code points, so surrogate pairs are never split.
 *
 * <p>Tags and file names come from untrusted downloads: everything printed to a terminal goes through
 * {@link #oneLine}, which neutralises control characters (ANSI escape sequences, bell) and bidi overrides.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Text {

    private static final String ELLIPSIS = "…";

    private Text() {
    }

    /**
     * @param value any text; not null
     * @return the number of terminal columns {@code value} occupies
     * @throws NullPointerException if {@code value} is null
     */
    public static int width(String value) {
        Require.nonNull(value, "value");
        return value.codePoints().map(Text::columns).sum();
    }

    private static int columns(int cp) {
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || cp == 0x200D) {
            return 0;
        }
        boolean wide = cp >= 0x1100 && cp <= 0x115F || cp >= 0x2E80 && cp <= 0xA4CF || cp >= 0xAC00 && cp <= 0xD7A3
                || cp >= 0xF900 && cp <= 0xFAFF || cp >= 0xFE30 && cp <= 0xFE4F || cp >= 0xFF00 && cp <= 0xFF60
                || cp >= 0xFFE0 && cp <= 0xFFE6 || cp >= 0x1F300 && cp <= 0x1F64F || cp >= 0x1F900 && cp <= 0x1F9FF
                || cp >= 0x1F680 && cp <= 0x1F6FF || cp >= 0x20000 && cp <= 0x3FFFD;
        return wide ? 2 : 1;
    }

    /**
     * Cuts {@code value} to at most {@code width} columns (ending with "…" when cut) and pads it with spaces to
     * exactly {@code width} columns.
     */
    public static String fit(String value, int width) {
        Require.nonNull(value, "value");
        String cut = cut(value, width);
        return cut + " ".repeat(Math.max(0, width - width(cut)));
    }

    /** Cuts {@code value} to at most {@code width} columns, ending with "…" when cut; never splits a code point. */
    public static String cut(String value, int width) {
        Require.nonNull(value, "value");
        if (width(value) <= width) {
            return value;
        }
        if (width <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int columns = columns(cp);
            if (used + columns > width - 1) {
                break;
            }
            out.appendCodePoint(cp);
            used += columns;
            i += Character.charCount(cp);
        }
        return out + ELLIPSIS;
    }

    /**
     * Makes text safe for one terminal line: runs of line breaks and tabs become one space; every other control
     * character (ESC, BEL, DEL, C1 controls) and every bidi override/isolate becomes U+FFFD, so a tag can neither
     * move the cursor, recolour the output nor reorder the text.
     *
     * @param value any text; not null
     * @return the safe text; never null
     * @throws NullPointerException if {@code value} is null
     */
    public static String oneLine(String value) {
        Require.nonNull(value, "value");
        String spaced = value.replaceAll("[\\r\\n\\t\\u2028\\u2029]+", " ");
        StringBuilder out = new StringBuilder(spaced.length());
        spaced.codePoints().forEach(cp -> out.appendCodePoint(unsafe(cp) ? 0xFFFD : cp));
        return out.toString();
    }

    private static boolean unsafe(int cp) {
        return Character.isISOControl(cp) || cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069
                || cp == 0x200E || cp == 0x200F || cp == 0x061C;
    }

    /** @return "m:ss", or "h:mm:ss" from one hour on */
    public static String duration(Duration duration) {
        Require.nonNull(duration, "duration");
        long seconds = Math.round(duration.toMillis() / 1000.0);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, rest) : "%d:%02d".formatted(minutes, rest);
    }
}
