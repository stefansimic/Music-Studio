package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Fits a text value into a fixed-size ID3v1 field (one byte per character in ASCII and Latin-1).
 *
 * <p>The value is transliterated ({@link Transliterator#toAscii} or {@link Transliterator#toLatin1}), stripped,
 * and, if still too long, truncated at the last word boundary that keeps at least half of the field; otherwise it
 * is cut hard at {@code maxBytes}.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Id3v1Text {

    private Id3v1Text() {
    }

    /**
     * Fits {@code value} into {@code maxBytes} bytes.
     *
     * <p><b>Preconditions:</b> value not null (NPE); maxBytes ≥ 1 (IAE).
     * <p><b>Postconditions:</b> {@code result.text().length() <= maxBytes}; every character is printable ASCII
     * (asciiOnly) or printable Latin-1; no leading/trailing whitespace.
     *
     * @param value     the text to fit; not null
     * @param asciiOnly true for printable ASCII, false for printable Latin-1
     * @param maxBytes  the field size in bytes; ≥ 1
     * @return the fitted text; never null
     * @throws NullPointerException     if {@code value} is null
     * @throws IllegalArgumentException if {@code maxBytes < 1}
     */
    public static FittedText fit(String value, boolean asciiOnly, int maxBytes) {
        Require.nonNull(value, "value");
        Require.that(maxBytes >= 1, "maxBytes must be >= 1 but was %d", maxBytes);
        Transliteration t = asciiOnly ? Transliterator.toAscii(value) : Transliterator.toLatin1(value);
        String text = t.text().strip();
        boolean truncated = false;
        if (text.length() > maxBytes) {
            truncated = true;
            int boundary = text.lastIndexOf(' ', maxBytes);
            text = boundary >= maxBytes / 2 && boundary > 0
                    ? text.substring(0, boundary).strip()
                    : text.substring(0, maxBytes).strip();
        }
        assert text.length() <= maxBytes : "fitted text must fit";
        return new FittedText(text, t.changed(), truncated, t.droppedChars());
    }
}
