package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The ID3v1 genre table (numbers 0–79 from ID3v1, 80–191 Winamp extensions) and the resolution of numeric genre
 * references found in ID3v2 TCON frames. Genre names are domain data, so the table lives in core.
 *
 * <p><b>Thread-safety:</b> immutable, safe for concurrent use.
 */
public final class Id3v1Genres {

    /** The genre number that means "no genre" in an ID3v1 tag. */
    public static final int NONE = 255;

    private static final List<String> NAMES = List.of(
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
            "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
            "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop",
            "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
            "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative",
            "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial",
            "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40",
            "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave",
            "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical",
            "Rock & Roll", "Hard Rock",
            // Winamp extensions
            "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic",
            "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
            "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera",
            "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam",
            "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet",
            "Punk Rock", "Drum Solo", "A capella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House",
            "Hardcore", "Terror", "Indie", "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap",
            "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock", "Merengue",
            "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop", "Abstract", "Art Rock", "Baroque", "Bhangra",
            "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM", "Eclectic", "Electro", "Electroclash",
            "Emo", "Experimental", "Garage", "Global", "IDM", "Illbient", "Industro-Goth", "Jam Band", "Krautrock",
            "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance",
            "Shoegaze", "Space Rock", "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre",
            "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep", "Garage Rock", "Psybient");

    private static final Map<String, Integer> NUMBERS = numbersByLowerCaseName();

    private Id3v1Genres() {
    }

    private static Map<String, Integer> numbersByLowerCaseName() {
        Map<String, Integer> numbers = new HashMap<>();
        for (int i = 0; i < NAMES.size(); i++) {
            numbers.putIfAbsent(NAMES.get(i).toLowerCase(Locale.ROOT), i);
        }
        return Map.copyOf(numbers);
    }

    /** @return the number of entries in the table (192) */
    public static int size() {
        return NAMES.size();
    }

    /**
     * @param number any int
     * @return the genre name for a table number, or empty for numbers outside the table (incl. 255 = none)
     */
    public static Optional<String> name(int number) {
        return number >= 0 && number < NAMES.size() ? Optional.of(NAMES.get(number)) : Optional.empty();
    }

    /**
     * Finds the table number of a genre name, case-insensitively.
     *
     * @param name the genre name; not null
     * @return the number, or empty if the name is not in the table
     * @throws NullPointerException if {@code name} is null
     */
    public static OptionalInt number(String name) {
        Require.nonNull(name, "name");
        Integer number = NUMBERS.get(name.strip().toLowerCase(Locale.ROOT));
        return number == null ? OptionalInt.empty() : OptionalInt.of(number);
    }

    /**
     * Resolves a raw genre string from a tag to genre names.
     *
     * <ul>
     *   <li>{@code "17"} and {@code "(17)"} → Rock; {@code "(17)(20)"} → Rock, Alternative</li>
     *   <li>{@code "(17)Rock"} → Rock (the refinement text is appended unless it repeats a referenced name)</li>
     *   <li>{@code "(RX)"} → Remix, {@code "(CR)"} → Cover (ID3v2.3 special references)</li>
     *   <li>{@code "((text"} → "(text" (escaped parenthesis); plain text → that text</li>
     *   <li>numbers outside the table (255, 999) and unterminated references ("(", "(99") are ignored; line breaks
     *       and control characters become spaces</li>
     * </ul>
     *
     * @param raw the raw value; not null
     * @return the genres in order without case-insensitive duplicates, or empty if nothing usable remains
     * @throws NullPointerException if {@code raw} is null
     */
    public static Optional<List<Genre>> resolveReference(String raw) {
        Require.nonNull(raw, "raw");
        String value = cleanUp(raw);
        List<String> names = new ArrayList<>();
        if (value.isEmpty()) {
            return Optional.empty();
        } else if (isDigits(value)) {
            numbered(value).ifPresent(names::add);
        } else if (value.startsWith("((")) {
            names.add(value.substring(1));
        } else {
            int pos = 0;
            while (pos < value.length() && value.charAt(pos) == '(' && !value.startsWith("((", pos)) {
                int close = value.indexOf(')', pos);
                if (close < 0) {
                    break;
                }
                String inner = value.substring(pos + 1, close).strip();
                if (isDigits(inner)) {
                    numbered(inner).ifPresent(names::add);
                } else if (inner.equals("RX")) {
                    names.add("Remix");
                } else if (inner.equals("CR")) {
                    names.add("Cover");
                } else {
                    break;
                }
                pos = close + 1;
            }
            String rest = value.substring(pos).strip();
            if (rest.startsWith("((")) {
                rest = rest.substring(1);
            } else if (rest.startsWith("(") && rest.indexOf(')') < 0) {
                rest = ""; // an unterminated reference such as "(" or "(99" is garbage, not a genre
            }
            if (!rest.isEmpty()) {
                names.add(rest);
            }
        }
        List<Genre> genres = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String stripped = name.strip();
            if (!stripped.isEmpty() && seen.add(stripped.toLowerCase(Locale.ROOT))) {
                genres.add(new Genre(stripped));
            }
        }
        return genres.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(genres));
    }

    private static Optional<String> numbered(String digits) {
        if (digits.length() > 3) {
            return Optional.empty();
        }
        return name(Integer.parseInt(digits));
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String cleanUp(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(Character.isISOControl(c) || c == ' ' || c == ' ' ? ' ' : c);
        }
        return out.toString().strip();
    }
}
