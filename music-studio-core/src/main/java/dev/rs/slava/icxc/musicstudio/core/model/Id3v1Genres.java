package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The ID3v1 genre table (Winamp extension, 0..191) and the resolution of ID3v2 genre references.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Id3v1Genres {

    private static final List<String> NAMES = List.of(
            "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
            "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
            "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop",
            "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
            "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative",
            "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial",
            "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
            "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native US", "Cabaret", "New Wave", "Psychadelic",
            "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
            "Musical", "Rock & Roll", "Hard Rock",
            // Winamp extensions
            "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic",
            "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock",
            "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson",
            "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire",
            "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul",
            "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House", "Dance Hall", "Goa",
            "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop", "Negerpunk", "Polsk Punk",
            "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian",
            "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "JPop", "Synthpop",
            "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub",
            "EBM", "Eclectic", "Electro", "Electroclash", "Emo", "Experimental", "Garage", "Global", "IDM",
            "Illbient", "Industro-Goth", "Jam Band", "Krautrock", "Leftfield", "Lounge", "Math Rock",
            "New Romantic", "Nu-Breakz", "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock",
            "Trop Rock", "World Music", "Neoclassical", "Audiobook", "Audio Theatre", "Neue Deutsche Welle",
            "Podcast", "Indie Rock", "G-Funk", "Dubstep", "Garage Rock", "Psybient");

    private Id3v1Genres() {}

    /** @return number of table entries (192) */
    public static int count() {
        return NAMES.size();
    }

    /**
     * @param number genre byte, any int
     * @return the name, empty if {@code number} is outside 0..191 (255 means "none")
     */
    public static Optional<String> name(int number) {
        return number >= 0 && number < NAMES.size() ? Optional.of(NAMES.get(number)) : Optional.empty();
    }

    /**
     * @param name genre name; not null
     * @return the table index, matching case-insensitively; empty if the name is not in the table
     * @throws NullPointerException if {@code name} is null
     */
    public static OptionalInt number(String name) {
        Require.nonNull(name, "name");
        for (int i = 0; i < NAMES.size(); i++) {
            if (NAMES.get(i).equalsIgnoreCase(name.strip())) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Resolves a raw ID3v2 genre text to genre names: {@code "17"}, {@code "(17)"}, {@code "(17)Rock"},
     * {@code "(17)(20)"} and plain text. Numbers outside the table (incl. 255) are ignored. Text after the numbers is
     * a refinement and is added unless it repeats a genre already listed. Duplicates are removed.
     *
     * @param raw raw text as stored in the frame; not null
     * @return the genres in order, empty if nothing could be resolved
     * @throws NullPointerException if {@code raw} is null
     */
    public static Optional<List<Genre>> resolveReference(String raw) {
        Require.nonNull(raw, "raw");
        String rest = raw.strip();
        List<String> names = new ArrayList<>();
        if (rest.startsWith("((")) {
            rest = rest.substring(1); // "((" escapes a literal "("
        } else {
            while (rest.startsWith("(")) {
                int close = rest.indexOf(')');
                if (close < 0 || !isNumber(rest.substring(1, close))) {
                    break;
                }
                name(Integer.parseInt(rest.substring(1, close))).ifPresent(names::add);
                rest = rest.substring(close + 1);
            }
            if (names.isEmpty() && isNumber(rest)) {
                name(Integer.parseInt(rest)).ifPresent(names::add);
                rest = "";
            }
        }
        rest = rest.strip();
        if (!rest.isEmpty()) {
            names.add(rest);
        }
        List<Genre> genres = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String genreName : names) {
            if (seen.add(genreName.toLowerCase(Locale.ROOT))) {
                genres.add(new Genre(genreName));
            }
        }
        return genres.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(genres));
    }

    private static boolean isNumber(String text) {
        if (text.isEmpty() || text.length() > 3) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
