package dev.rs.slava.icxc.musicstudio.cli.render;

import java.time.Duration;

/** Small text helpers for fixed-width output. Widths count code points, so an emoji is never split. */
final class Text {

    private static final String ELLIPSIS = "…";

    private Text() {}

    /** Replaces control characters and line separators by a space, so one value never breaks a row. */
    static String oneLine(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            boolean control = Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029;
            out.appendCodePoint(control ? ' ' : cp);
        });
        return out.toString();
    }

    /** Cuts to {@code width} code points (ending with an ellipsis) or pads with spaces to exactly that width. */
    static String fit(String value, int width) {
        String text = oneLine(value);
        int length = text.codePointCount(0, text.length());
        if (length > width) {
            return text.substring(0, text.offsetByCodePoints(0, Math.max(0, width - 1))) + (width > 0 ? ELLIPSIS : "");
        }
        return text + " ".repeat(width - length);
    }

    /** @return minutes:seconds, for example "3:07"; seconds are rounded down */
    static String minutesSeconds(Duration duration) {
        long seconds = duration.getSeconds();
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
