package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Project-owned transliteration for targets that cannot hold arbitrary Unicode (ID3v1 tags, FAT32-safe names).
 *
 * <p>Pipeline, applied per character:
 * <ol>
 *   <li>Serbian Cyrillic → Gaj Latin (Ђ→Đ, Љ→Lj, Њ→Nj, Џ→Dž, Ћ→Ć, Ч→Č, Ш→Š, Ж→Ž, …). Digraphs are upper-cased
 *       completely ("LJ") when the surrounding text is upper case, otherwise title-cased ("Lj").</li>
 *   <li>Special Latin letters: đ→dj, ß→ss, æ→ae, ø→o, œ→oe, ł→l, þ→th, ð→d; in ASCII mode also ä→ae, ö→oe,
 *       ü→ue.</li>
 *   <li>Anything else outside the target character set is decomposed (NFD) and its combining marks are removed
 *       (č→c, é→e). Characters that still do not fit (emoji, CJK, …) are dropped, never replaced by "?".</li>
 * </ol>
 * Tabs and line breaks become a space. Other control characters are dropped.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Transliterator {

    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('А', "A"), Map.entry('Б', "B"), Map.entry('В', "V"), Map.entry('Г', "G"),
            Map.entry('Д', "D"), Map.entry('Ђ', "Đ"), Map.entry('Е', "E"), Map.entry('Ж', "Ž"),
            Map.entry('З', "Z"), Map.entry('И', "I"), Map.entry('Ј', "J"), Map.entry('К', "K"),
            Map.entry('Л', "L"), Map.entry('Љ', "Lj"), Map.entry('М', "M"), Map.entry('Н', "N"),
            Map.entry('Њ', "Nj"), Map.entry('О', "O"), Map.entry('П', "P"), Map.entry('Р', "R"),
            Map.entry('С', "S"), Map.entry('Т', "T"), Map.entry('Ћ', "Ć"), Map.entry('У', "U"),
            Map.entry('Ф', "F"), Map.entry('Х', "H"), Map.entry('Ц', "C"), Map.entry('Ч', "Č"),
            Map.entry('Џ', "Dž"), Map.entry('Ш', "Š"),
            // common letters of other Cyrillic alphabets, so Russian/Macedonian names stay readable
            Map.entry('Й', "J"), Map.entry('Ы', "Y"), Map.entry('Э', "E"), Map.entry('Ю', "Ju"),
            Map.entry('Я', "Ja"), Map.entry('Щ', "Šč"), Map.entry('Ё', "Jo"), Map.entry('Ѓ', "Gj"),
            Map.entry('Ќ', "Kj"), Map.entry('Ѕ', "Dz"), Map.entry('Є', "Je"), Map.entry('І', "I"),
            Map.entry('Ї', "Ji"), Map.entry('Ъ', ""), Map.entry('Ь', ""));

    private static final Map<Character, String> SPECIAL_LATIN = Map.ofEntries(
            Map.entry('Đ', "Dj"), Map.entry('đ', "dj"), Map.entry('ß', "ss"), Map.entry('ẞ', "SS"),
            Map.entry('Æ', "AE"), Map.entry('æ', "ae"), Map.entry('Ø', "O"), Map.entry('ø', "o"),
            Map.entry('Œ', "OE"), Map.entry('œ', "oe"), Map.entry('Ł', "L"), Map.entry('ł', "l"),
            Map.entry('Þ', "Th"), Map.entry('þ', "th"), Map.entry('Ð', "D"), Map.entry('ð', "d"),
            Map.entry('ı', "i"));

    private static final Map<Character, String> GERMAN_UMLAUTS = Map.of(
            'Ä', "Ae", 'ä', "ae", 'Ö', "Oe", 'ö', "oe", 'Ü', "Ue", 'ü', "ue");

    private Transliterator() {
    }

    /**
     * Transliterates to printable ASCII (0x20–0x7E).
     *
     * @param text the text to transliterate; not null
     * @return the result; never null
     * @throws NullPointerException if {@code text} is null
     */
    public static Transliteration toAscii(String text) {
        Require.nonNull(text, "text");
        return transliterate(text, false);
    }

    /**
     * Transliterates to printable ISO-8859-1 (0x20–0x7E, 0xA0–0xFF). Latin-1 letters such as é, ä and ß are kept.
     *
     * @param text the text to transliterate; not null
     * @return the result; never null
     * @throws NullPointerException if {@code text} is null
     */
    public static Transliteration toLatin1(String text) {
        Require.nonNull(text, "text");
        return transliterate(text, true);
    }

    private static Transliteration transliterate(String input, boolean latin1) {
        String latin = cyrillicToLatin(input);
        StringBuilder out = new StringBuilder(latin.length());
        boolean dropped = false;
        int i = 0;
        while (i < latin.length()) {
            int cp = latin.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (allowed(cp, latin1)) {
                out.appendCodePoint(cp);
            } else if (cp == '\t' || cp == '\n' || cp == '\r') {
                out.append(' ');
            } else if (Character.isBmpCodePoint(cp) && special((char) cp, latin1) != null) {
                String replacement = special((char) cp, latin1);
                out.append(caseDigraph(replacement, latin, i, next));
            } else {
                dropped |= appendDecomposed(cp, latin1, out);
            }
            i = next;
        }
        String result = out.toString();
        return new Transliteration(result, !result.equals(input), dropped);
    }

    private static String special(char c, boolean latin1) {
        String replacement = SPECIAL_LATIN.get(c);
        if (replacement == null && !latin1) {
            replacement = GERMAN_UMLAUTS.get(c);
        }
        return replacement;
    }

    private static String cyrillicToLatin(String input) {
        StringBuilder out = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean lower = Character.isLowerCase(c);
            String latin = CYRILLIC.get(lower ? Character.toUpperCase(c) : c);
            if (latin == null) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(input.length() + 8).append(input, 0, i);
            }
            out.append(lower ? latin.toLowerCase(Locale.ROOT) : caseDigraph(latin, input, i, i + 1));
        }
        return out == null ? input : out.toString();
    }

    /**
     * Upper-cases a title-case replacement ("Lj") completely when the surrounding text is upper case: the next
     * letter is upper case, or there is no next letter and the previous letter is upper case.
     */
    private static String caseDigraph(String replacement, String text, int start, int end) {
        if (replacement.length() < 2 || !Character.isUpperCase(replacement.charAt(0))) {
            return replacement;
        }
        boolean nextIsLetter = end < text.length() && Character.isLetter(text.charAt(end));
        boolean upper = nextIsLetter
                ? Character.isUpperCase(text.charAt(end))
                : start > 0 && Character.isUpperCase(text.charAt(start - 1));
        return upper ? replacement.toUpperCase(Locale.ROOT) : replacement;
    }

    /** @return true if at least one character was dropped */
    private static boolean appendDecomposed(int cp, boolean latin1, StringBuilder out) {
        if (Character.getType(cp) == Character.CONTROL) {
            return true;
        }
        String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        boolean dropped = false;
        boolean kept = false;
        for (int j = 0; j < decomposed.length(); ) {
            int part = decomposed.codePointAt(j);
            j += Character.charCount(part);
            if (Character.getType(part) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (allowed(part, latin1)) {
                out.appendCodePoint(part);
                kept = true;
            } else {
                dropped = true;
            }
        }
        return dropped || !kept;
    }

    private static boolean allowed(int cp, boolean latin1) {
        return (cp >= 0x20 && cp <= 0x7E) || (latin1 && cp >= 0xA0 && cp <= 0xFF);
    }
}
