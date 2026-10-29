package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Fits a text into an ID3v1 field: transliteration first, then truncation (at a word boundary when possible).
 *
 * <p>This class sits below {@code model} in the layering, so the text mode is a plain boolean.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Id3v1Text {

    private Id3v1Text() {}

    /**
     * Result of {@link #fit}.
     *
     * @param text          the text to store; single-byte characters only, at most {@code maxBytes} long; never null
     * @param transliterated true if the transliteration changed the text
     * @param truncated     true if the text was shortened
     * @param dropped       true if characters without a mapping were removed
     */
    public record FittedText(String text, boolean transliterated, boolean truncated, boolean dropped) {
        public FittedText {
            Require.nonNull(text, "text");
        }
    }

    /**
     * @param value     text to fit; not null
     * @param asciiOnly true: ASCII only; false: ISO-8859-1 characters are kept
     * @param maxBytes  field size in bytes, greater than 0 (every resulting character is one byte)
     * @return the fitted text; never null. Truncation cuts at the last space inside the limit when there is one,
     *         otherwise hard at {@code maxBytes}; trailing spaces are removed.
     * @throws NullPointerException     if {@code value} is null
     * @throws IllegalArgumentException if {@code maxBytes <= 0}
     */
    public static FittedText fit(String value, boolean asciiOnly, int maxBytes) {
        Require.nonNull(value, "value");
        Require.that(maxBytes > 0, "maxBytes must be > 0");
        Transliterator.Transliteration converted =
                asciiOnly ? Transliterator.toAscii(value) : Transliterator.toLatin1(value);
        String text = converted.text().strip(); // a dropped character may leave a blank at either end
        boolean truncated = false;
        if (text.length() > maxBytes) {
            truncated = true;
            int cut = maxBytes;
            if (text.charAt(maxBytes) != ' ') {
                int space = text.lastIndexOf(' ', maxBytes - 1);
                if (space > 0) {
                    cut = space;
                }
            }
            text = text.substring(0, cut).stripTrailing();
        }
        assert text.length() <= maxBytes : "fitted text must not exceed the field size";
        return new FittedText(text, converted.changed(), truncated, converted.droppedChars());
    }
}
