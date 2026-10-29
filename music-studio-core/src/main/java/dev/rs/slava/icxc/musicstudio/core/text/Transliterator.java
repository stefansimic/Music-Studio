package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.text.Normalizer;

/**
 * Project-owned transliteration for ID3v1 and FAT32-safe names: Serbian Cyrillic is converted to Gaj Latin and then
 * to ASCII (č/ć→c, š→s, ž→z, đ→dj, dž→dz, ä→ae, ö→oe, ü→ue, ß→ss, other accents stripped via NFD). Greek follows
 * ELOT 743 (α→a, β→v, γ→g, δ→d, θ→th, η→i, υ→y, χ→ch, ψ→ps, ω→o; {@code ου}→ou, {@code αυ/ευ}→av/ev before a voiced
 * sound and af/ef otherwise, {@code γγ}→ng, {@code γκ}→g at the start of a word and gk elsewhere, {@code μπ}→b and
 * {@code ντ}→d at the start of a word and mp/nt elsewhere; tonos and dialytika are removed, final sigma is s).
 * Characters that cannot be mapped are dropped, never replaced with {@code ?}.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Transliterator {

    private static final String[] CYRILLIC_UPPER = {
        "A", "B", "V", "G", "D", "Dj", "E", "Z", "Z", "I", "J", "K", "L", "Lj", "M", "N", "Nj", "O", "P", "R", "S",
        "T", "C", "U", "F", "H", "C", "C", "Dz", "S"
    };
    private static final String CYRILLIC_UPPER_LETTERS = "АБВГДЂЕЖЗИЈКЛЉМНЊОПРСТЋУФХЦЧЏШ";

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
            int start = i;
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp <= 0x7F || (keepLatin1 && cp <= 0xFF)) {
                out.appendCodePoint(cp);
                continue;
            }
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.GREEK && greekBase(cp) != 0) {
                i = start + greek(value, start, out);
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

    private static final String GREEK_VOICED_AFTER_AV_EV = "αεηιουωβγδζλμνρ";

    /** @return the lower-case base letter of a Greek letter (accents removed, final sigma as sigma), 0 if none */
    private static int greekBase(int cp) {
        if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.GREEK || !Character.isLetter(cp)) {
            return 0;
        }
        String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        int base = Character.toLowerCase(decomposed.codePointAt(0));
        return base == 'ς' ? 'σ' : base;
    }

    /** @return the index of the next letter after {@code from} (marks skipped), or -1 if there is none */
    private static int nextLetter(String value, int from) {
        int j = from;
        while (j < value.length()) {
            int cp = value.codePointAt(j);
            int type = Character.getType(cp);
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) {
                return Character.isLetter(cp) ? j : -1;
            }
            j += Character.charCount(cp);
        }
        return -1;
    }

    /** Transliterates the Greek letter (or digraph) at {@code at}; @return the number of chars consumed */
    private static int greek(String value, int at, StringBuilder out) {
        int cp = value.codePointAt(at);
        int base = greekBase(cp);
        int afterFirst = at + Character.charCount(cp);
        int nextIndex = nextLetter(value, afterFirst);
        int next = nextIndex < 0 ? 0 : greekBase(value.codePointAt(nextIndex));
        boolean wordStart = at == 0 || !Character.isLetter(value.codePointBefore(at));
        boolean upper = Character.isUpperCase(cp);
        boolean caps = upper && nextIndex >= 0 && Character.isUpperCase(value.codePointAt(nextIndex));
        String latin = null;
        int end = nextIndex < 0 ? afterFirst : nextIndex + Character.charCount(value.codePointAt(nextIndex));
        if (next == 'υ' && (base == 'α' || base == 'ε')) {
            int afterIndex = nextLetter(value, end);
            int following = afterIndex < 0 ? 0 : greekBase(value.codePointAt(afterIndex));
            boolean voiced = following != 0 && GREEK_VOICED_AFTER_AV_EV.indexOf(following) >= 0;
            latin = (base == 'α' ? "a" : "e") + (voiced ? "v" : "f");
        } else if (base == 'ο' && next == 'υ') {
            latin = "ou";
        } else if (base == 'γ' && next == 'γ') {
            latin = "ng";
        } else if (base == 'γ' && next == 'κ') {
            latin = wordStart ? "g" : "gk";
        } else if (base == 'γ' && next == 'ξ') {
            latin = "nx";
        } else if (base == 'γ' && next == 'χ') {
            latin = "nch";
        } else if (base == 'μ' && next == 'π') {
            latin = wordStart ? "b" : "mp";
        } else if (base == 'ν' && next == 'τ') {
            latin = wordStart ? "d" : "nt";
        }
        int consumed;
        if (latin != null) {
            consumed = end - at;
        } else {
            latin = greekLetter(base);
            consumed = afterFirst - at;
            if (latin == null) {
                return consumed;
            }
        }
        out.append(upper ? (caps ? latin.toUpperCase() : titleCase(latin)) : latin);
        return consumed;
    }

    private static String titleCase(String latin) {
        return Character.toUpperCase(latin.charAt(0)) + latin.substring(1);
    }

    private static String greekLetter(int base) {
        return switch (base) {
            case 'α' -> "a";
            case 'β' -> "v";
            case 'γ' -> "g";
            case 'δ' -> "d";
            case 'ε' -> "e";
            case 'ζ' -> "z";
            case 'η' -> "i";
            case 'θ' -> "th";
            case 'ι' -> "i";
            case 'κ' -> "k";
            case 'λ' -> "l";
            case 'μ' -> "m";
            case 'ν' -> "n";
            case 'ξ' -> "x";
            case 'ο' -> "o";
            case 'π' -> "p";
            case 'ρ' -> "r";
            case 'σ' -> "s";
            case 'τ' -> "t";
            case 'υ' -> "y";
            case 'φ' -> "f";
            case 'χ' -> "ch";
            case 'ψ' -> "ps";
            case 'ω' -> "o";
            default -> null;
        };
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
