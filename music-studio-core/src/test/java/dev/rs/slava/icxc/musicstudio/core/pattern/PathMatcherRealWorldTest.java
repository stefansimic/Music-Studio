package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Story 002 edge cases with names as they occur in real downloads (tester additions to {@link PathMatcherTest}).
 * Expected values follow the story, architecture §10.2 and ADR 0010 – not the implementation.
 */
class PathMatcherRealWorldTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private static MatchResult match(String pattern, String relative) {
        return PathMatcher.match(PatternParserTest.valid(pattern), ROOT.resolve(relative));
    }

    private static Map<Field, String> raw(String spec) {
        Map<Field, String> map = new EnumMap<>(Field.class);
        if (spec == null || spec.isBlank()) {
            return map;
        }
        for (String pair : spec.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(Field.byPlaceholder(kv[0]).orElseThrow(), kv[1]);
        }
        return map;
    }

    @ParameterizedTest(name = "{0} | {1} -> {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            // --- leading track numbers 01 - / 01. / 01_ in full patterns
            "{track} - {artist} - {title}|01 - Artist - Title (feat. Other).mp3|MATCHED|track=01&artist=Artist&title=Title (feat. Other)|",
            "{track}. {artist} - {title}|01. Artist - Title.mp3|MATCHED|track=01&artist=Artist&title=Title|",
            "{track}. {artist} - {title}|1.Artist - Title.mp3|MATCHED|track=1&artist=Artist&title=Title|",
            "{track}_{artist} - {title}|07_Artist - Title.mp3|MATCHED|track=07&artist=Artist&title=Title|",
            "{track}_{title}|01_My_Song.mp3|MATCHED|track=01&title=My_Song|",
            "{track} {title}|01 Song.mp3|MATCHED|track=01&title=Song|",
            "{track} - {title}|03of12 - Song.mp3|MATCHED|track=03of12&title=Song|",
            "{track} - {title}|3 of 12 - Song.mp3|MATCHED|track=3 of 12&title=Song|",
            "{*} - {artist} - {title}|007 - Artist - Title.mp3|MATCHED|artist=Artist&title=Title|",
            "{disc}-{track} {title}|1-01 Song.mp3|MATCHED|disc=1&track=01&title=Song|",
            "CD{disc}/{track} - {title}|CD2/03 - Song.mp3|MATCHED|disc=2&track=03&title=Song|",
            // --- missing / extra spaces around the separator
            "{track} - {title}|01-Song.mp3|MATCHED|track=01&title=Song|",
            "{track} - {title}|01 -Song.mp3|MATCHED|track=01&title=Song|",
            "{track} - {title}|01- Song.mp3|MATCHED|track=01&title=Song|",
            "{artist} - {title}|Artist  -  Song.mp3|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|'Artist\t-\tSong.mp3'|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|'  Artist - Song  .mp3'|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|Artist  Name - Song.mp3|MATCHED|artist=Artist  Name&title=Song|",
            // --- hyphens inside values: strict tier wins
            "{artist} - {title}|Jay-Z - Hip-Hop.mp3|MATCHED|artist=Jay-Z&title=Hip-Hop|",
            "{artist} - {title}|AC-DC - T.N.T.mp3|MATCHED|artist=AC-DC&title=T.N.T|",
            "{artist} - {title}|Mr. Big - To Be With You.mp3|MATCHED|artist=Mr. Big&title=To Be With You|",
            "{artist} - {title}|Artist - Title (Official Video) [HD].mp3|MATCHED|artist=Artist&title=Title (Official Video) [HD]|",
            // --- double separators
            "{artist} - {title}|Artist -- Song.mp3|AMBIGUOUS|artist=Artist&title=- Song|artist=Artist -&title=Song",
            "{artist} - {title}|Jay-Z -Song.mp3|AMBIGUOUS|artist=Jay&title=Z -Song|artist=Jay-Z&title=Song",
            "{artist}_{title}|Artist_Song_Name.mp3|AMBIGUOUS|artist=Artist&title=Song_Name|artist=Artist_Song&title=Name",
            // --- year in brackets
            "{title} {year}|Song [2020].mp3|MATCHED|title=Song&year=[2020]|",
            "{title} {year}|Song (Live) (2020).mp3|MATCHED|title=Song (Live)&year=(2020)|",
            "{artist} - {title} ({year})|Artist - Song (Remastered 2011) (1985).mp3|MATCHED|artist=Artist&title=Song (Remastered 2011)&year=1985|",
            "{title} [{year}]|Song [2020].mp3|MATCHED|title=Song&year=2020|",
            "{artist} - {title} - {year}|Artist - Song - (2020).mp3|MATCHED|artist=Artist&title=Song&year=(2020)|",
            "{artist} - {title}|Prince - 1999.mp3|MATCHED|artist=Prince&title=1999|",
            // --- folder names with year
            "{year} - {album}/{track} {title}|2020 - Best - Of/01 Song.mp3|MATCHED|year=2020&album=Best - Of&track=01&title=Song|",
            "{album} ({year})/{track} - {title}|Album Name (2020)/01 - Song.mp3|MATCHED|album=Album Name&year=2020&track=01&title=Song|",
            "{album} [{year}]/{title}|Album [2020]/Song.mp3|MATCHED|album=Album&year=2020&title=Song|",
            "{artist}/{year} - {album}/{track} - {title}|Artist/2020 - Album/01 - Song.mp3|MATCHED|artist=Artist&year=2020&album=Album&track=01&title=Song|",
            "{album}/{title}|2020 - Album/Song.mp3|MATCHED|album=2020 - Album&title=Song|",
            "{year}/{album}/{title}|2020/Album/Song.mp3|MATCHED|year=2020&album=Album&title=Song|",
            "{album}/{title}|Album.Vol.1/Song.mp3|MATCHED|album=Album.Vol.1&title=Song|",
            // --- non-ASCII
            "{artist} - {title}|Die Ärzte - Schrei nach Liebe.mp3|MATCHED|artist=Die Ärzte&title=Schrei nach Liebe|",
            "{artist} - {title}|Barış Manço - Dağlar Dağlar.mp3|MATCHED|artist=Barış Manço&title=Dağlar Dağlar|",
            "{artist} - {title}|坂本龍一 - 戦場のメリークリスマス.mp3|MATCHED|artist=坂本龍一&title=戦場のメリークリスマス|",
            "{artist} - {title}|宇多田ヒカル　-　First Love.mp3|MATCHED|artist=宇多田ヒカル&title=First Love|",
            "{album}/{artist} - {title}|Југословенски Рок/Бијело Дугме - Ђурђевдан.mp3|MATCHED|album=Југословенски Рок&artist=Бијело Дугме&title=Ђурђевдан|",
            "{artist} – {title}|Artist – Song.mp3|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|A - B .mp3|MATCHED|artist=A&title=B|",
            // --- emoji (surrogate pairs, ZWJ sequences, flags)
            "{artist} - {title}|🔥 Artist - Song 🎶.mp3|MATCHED|artist=🔥 Artist&title=Song 🎶|",
            "{artist} - {title}|👨‍👩‍👧 - Family.mp3|MATCHED|artist=👨‍👩‍👧&title=Family|",
            "{artist} - {title}|🇷🇸 Artist - Song.mp3|MATCHED|artist=🇷🇸 Artist&title=Song|",
            "{artist} 🎵 {title}|A 🎵 B.mp3|MATCHED|artist=A&title=B|",
            "{track}{title}|01🎵.mp3|MATCHED|track=01&title=🎵|",
            // --- characters that are illegal on FAT32 are extracted as-is (cleanup is story 005)
            "{artist} - {title}|'Who? - What: *Now* <1> | 2.mp3'|MATCHED|'artist=Who?&title=What: *Now* <1> | 2'|",
            "{track} {title}|3OF12 Song.mp3|MATCHED|track=3OF12&title=Song|",
            "{artist} - {title}|A - B - C - D.mp3|AMBIGUOUS|artist=A&title=B - C - D|artist=A - B - C&title=D",
            "'{title} '|Song.mp3|MATCHED|title=Song|",
            "{track}-{title}|'01 -Song.mp3'|MATCHED|track=01&title=Song|",
            // --- extension handling
            "{artist} - {title}|Artist - Song.MP3|MATCHED|artist=Artist&title=Song|",
            "{artist} - {title}|Artist - Song feat. X.mp3|MATCHED|artist=Artist&title=Song feat. X|",
    })
    void realWorldNames(String pattern, String path, MatchKind kind, String expected, String alternative) {
        MatchResult result = match(pattern, path);
        assertThat(result.kind()).as("kind; result %s", result).isEqualTo(kind);
        assertThat(result.raw()).as("raw").isEqualTo(raw(expected));
        assertThat(result.alternative()).as("alternative").isEqualTo(raw(alternative));
        assertThat(result.values().keySet()).isEqualTo(result.raw().keySet());
        assertThat(result.reason()).isEmpty();
    }

    @ParameterizedTest(name = "{0} | {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            // separator missing entirely
            "{artist} - {title}|ArtistSong.mp3|\" - \"",
            // a different dash is a different literal (case-/char-sensitive)
            "{artist} - {title}|Artist – Song.mp3|\" - \"",
            // year required, but this folder has none
            "{year} - {album}/{title}|Album/Song.mp3|folder \"Album\"",
            // year literal brackets differ
            "{title} ({year})|Song [2020].mp3|(",
            // invalid year in brackets
            "{title} {year}|Song (20200).mp3|20200",
            // nothing but the separator
            "{artist} - {title}|' - .mp3'|",
            // whitespace-only name
            "{title}|'   .mp3'|",
            // a whitespace-only literal needs >= 1 space even in the lenient tier (ADR 0010)
            "{artist} {title}|ArtistSong.mp3|",
            "{track} {title}|01Song.mp3|",
            // a year must be exactly 4 digits
            "{title} {year}|Song 2020s.mp3|",
            // track that is not a number
            "{track} - {title}|A1 - Song.mp3|",
            "{track} - {title}|٣ - Song.mp3|",
    })
    void noMatch(String pattern, String path, String reasonPart) {
        MatchResult result = match(pattern, path);
        assertThat(result.kind()).as("result %s", result).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.values()).isEmpty();
        assertThat(result.raw()).isEmpty();
        assertThat(result.alternative()).isEmpty();
        assertThat(result.reason()).hasValueSatisfying(reason -> {
            assertThat(reason).isNotBlank();
            if (reasonPart != null) {
                assertThat(reason).contains(reasonPart);
            }
        });
    }

    @Test
    void typedValuesOfRealWorldNames() {
        MatchResult result = match("{album} ({year})/{track}. {artist} - {title}",
                "Best Of (2019)/03of12. Ђорђе - Pesma.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.YEAR, new YearValue(2019))
                .containsEntry(Field.TRACK, Position.of(3, 12))
                .containsEntry(Field.ALBUM, new Text("Best Of"))
                .containsEntry(Field.ARTIST, new Text("Ђорђе"));
        assertThat(match("{track} - {title}", "3 of 12 - Song.mp3").values())
                .containsEntry(Field.TRACK, Position.of(3, 12));
        assertThat(match("{title} {year}", "Song [2020].mp3").values())
                .containsEntry(Field.YEAR, new YearValue(2020));
    }

    @Test
    void trailingNbspIsStrippedFromTheValue() {
        MatchResult result = match("{artist} - {title}", "A - B .mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.TITLE, new Text("B"));
    }

    @Test
    void nfdFolderAndFileNamesGiveNfcValues() {
        String folder = Normalizer.normalize("Čovek bez sluha", Normalizer.Form.NFD);
        String file = Normalizer.normalize("Đorđe Balašević - Ćaletova pesma", Normalizer.Form.NFD);
        MatchResult result = match("{album}/{artist} - {title}", folder + "/" + file + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.ALBUM, new Text("Čovek bez sluha"))
                .containsEntry(Field.ARTIST, new Text("Đorđe Balašević"))
                .containsEntry(Field.TITLE, new Text("Ćaletova pesma"));
        result.raw().values().forEach(v -> assertThat(Normalizer.isNormalized(v, Normalizer.Form.NFC)).isTrue());
    }

    @Test
    void nfdLiteralInThePatternMatchesNfcName() {
        String nfdPattern = Normalizer.normalize("{title} (Uživo)", Normalizer.Form.NFD);
        assertThat(PathMatcher.match(PatternParserTest.valid(nfdPattern), ROOT.resolve("Pesma (Uživo).mp3")).raw())
                .containsEntry(Field.TITLE, "Pesma");
        String nfdName = Normalizer.normalize("Pesma (Uživo).mp3", Normalizer.Form.NFD);
        assertThat(PathMatcher.match(PatternParserTest.valid("{title} (Uživo)"), ROOT.resolve(nfdName)).raw())
                .containsEntry(Field.TITLE, "Pesma");
    }

    @Test
    void emojiNeverSplitsASurrogatePair() {
        // a typed placeholder next to an emoji: the capture boundary must fall between code points
        MatchResult result = match("{title}{track}", "🎵7.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.TITLE, "🎵").containsEntry(Field.TRACK, "7");
    }

    @Test
    void separatorMissingInAFewFilesOfABatch() {
        // the same pattern over a batch; each name gets its own tier
        String pattern = "{artist} - {title}";
        assertThat(match(pattern, "Artist - One.mp3").kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(match(pattern, "Artist-Two.mp3").raw()).isEqualTo(raw("artist=Artist&title=Two"));
        assertThat(match(pattern, "Artist -Three.mp3").raw()).isEqualTo(raw("artist=Artist&title=Three"));
        assertThat(match(pattern, "Artist Four.mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
    }

    @Test
    void longRealisticNameMatches() {
        String title = "Very Long Title ".repeat(12).strip();
        MatchResult result = match("{track} - {artist} - {title}", "01 - Artist - " + title + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.TITLE, title);
    }
}
