package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.text.Normalizer;

/**
 * Project-owned transliteration for ID3v1 and FAT32-safe names: Serbian Cyrillic is converted to Gaj Latin and then
 * to ASCII (č/ć→c, š→s, ž→z, đ→dj, dž→dz, ä→ae, ö→oe, ü→ue, ß→ss, other accents stripped via NFD). Characters that
 * cannot be mapped are dropped, never replaced with {@code ?}.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Transliterator {

    private static final String[] CYRILLIC_UPPER = {
        "A", "B", "V", "G", "D", "Dj", "E", "Z", "Z", "I", "J", "K", "L", "Lj", "M", "N", "Nj", "O", "P", "R", "S",
        "C", "U", "F", "H", "C", "C", "Dz", "S"
    };
    private static final String CYRILLIC_UPPER_LETTERS = "АБВГДЂЕЖЗИЈКЛЉМНЊОПРСЋУФХЦЧЏШ";

    private Transliterator() {}

    /**
     * Result of a transliteration.
     *
     * @param text         the converted text; never null
     * @param changed      true if {@code text} differs from the input
     * @param droppedChars true if characters without a mapping were removed
     */
    public record Transliteration(String text, boolean changed, boolean droppedChars) {
        public Transliteration {
            Require.nonNull(text, "text");
        }
    }

    /**
     * Converts to pure ASCII.
     *
     * @param value text to convert; not null
     * @return the transliteration; never null
     * @throws NullPointerException if {@code value} is null
     */
    public static Transliteration toAscii(String value) {
        Require.nonNull(value, "value");
        return convert(value, false);
    }

    /**
     * Converts like {@link #toAscii} but keeps characters that exist in ISO-8859-1 (é, ä, ö, ü, ß, …).
     *
     * @param value text to convert; not null
     * @return the transliteration; never null
     * @throws NullPointerException if {@code value} is null
     */
    public static Transliteration toLatin1(String value) {
        Require.nonNull(value, "value");
        return convert(value, true);
    }

    private static Transliteration convert(String value, boolean keepLatin1) {
        StringBuilder out = new StringBuilder(value.length());
        boolean dropped = false;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp <= 0x7F || (keepLatin1 && cp <= 0xFF)) {
                out.appendCodePoint(cp);
                continue;
            }
            String cyrillic = cyrillic(cp);
            if (cyrillic != null) {
                out.append(cyrillic);
                continue;
            }
            String special = special(cp);
            if (special != null) {
                out.append(special);
                continue;
            }
            if (!appendDecomposed(cp, keepLatin1, out)) {
                dropped = true;
            }
        }
        String text = out.toString();
        return new Transliteration(text, !text.equals(value), dropped);
    }

    private static String cyrillic(int cp) {
        int upper = CYRILLIC_UPPER_LETTERS.indexOf(Character.toUpperCase(cp));
        if (upper < 0 || cp > 0x045F) {
            return null;
        }
        String latin = CYRILLIC_UPPER[upper];
        return Character.isLowerCase(cp) ? latin.toLowerCase() : latin;
    }

    private static String special(int cp) {
        return switch (cp) {
            case 'ä' -> "ae";
            case 'ö' -> "oe";
            case 'ü' -> "ue";
            case 'Ä' -> "Ae";
            case 'Ö' -> "Oe";
            case 'Ü' -> "Ue";
            case 'ß' -> "ss";
            case 'đ' -> "dj";
            case 'Đ' -> "Dj";
            case 'ł' -> "l";
            case 'Ł' -> "L";
            case 'ø' -> "o";
            case 'Ø' -> "O";
            case 'æ' -> "ae";
            case 'Æ' -> "Ae";
            case 'œ' -> "oe";
            case 'Œ' -> "Oe";
            default -> null;
        };
    }

    /** Appends the base characters of the NFD form without combining marks; false if nothing could be kept. */
    private static boolean appendDecomposed(int cp, boolean keepLatin1, StringBuilder out) {
        String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        boolean kept = false;
        for (int j = 0; j < decomposed.length(); ) {
            int part = decomposed.codePointAt(j);
            j += Character.charCount(part);
            int type = Character.getType(part);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                kept = true; // a mark that is stripped is a normal accent removal, not a loss of a character
                continue;
            }
            if (part <= 0x7F || (keepLatin1 && part <= 0xFF)) {
                out.appendCodePoint(part);
                kept = true;
            }
        }
        return kept;
    }
}
