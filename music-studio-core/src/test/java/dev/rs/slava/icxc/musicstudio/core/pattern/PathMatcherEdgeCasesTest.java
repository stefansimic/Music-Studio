package dev.rs.slava.icxc.musicstudio.core.pattern;

import static dev.rs.slava.icxc.musicstudio.core.pattern.PathMatcherTest.match;
import static dev.rs.slava.icxc.musicstudio.core.pattern.PathMatcherTest.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** E01-E19, E24, E25 on the matcher level (names from the story and from real libraries). */
class PathMatcherEdgeCasesTest {

    private static FieldValue.Position pos(int n) {
        return new FieldValue.Position(n, OptionalInt.empty());
    }

    // ---- E01 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{title} ({year})|Song (2020).mp3", "{title} {year}|Song (2020).mp3",
            "{title} [{year}]|Song [2020].mp3", "{title} {year}|Song [2020].mp3", "{title} {year}|Song 2020.mp3"})
    void yearInBrackets(String spec) {
        String[] parts = spec.split("\\|");
        MatchResult result = match(parts[0], parts[1]);
        assertThat(result.kind()).as(spec).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).as(spec).containsEntry(Field.TITLE, text("Song"))
                .containsEntry(Field.YEAR, new FieldValue.YearValue(2020));
    }

    // ---- E02 / E03 / E04 ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"01 - Song.mp3", "1 - Song.mp3", "001 - Song.mp3"})
    void leadingTrackNumber(String name) {
        MatchResult result = match("{track} - {title}", name);
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.TRACK, pos(1)).containsEntry(Field.TITLE, text("Song"));
    }

    @Test
    void leadingTrackWithDotUnderscoreAndSpace() {
        for (String name : List.of("01. Song.mp3", "01.Song.mp3")) {
            MatchResult result = match("{track}. {title}", name);
            assertThat(result.kind()).as(name).isEqualTo(MatchKind.MATCHED);
            assertThat(result.values()).as(name).containsEntry(Field.TRACK, pos(1))
                    .containsEntry(Field.TITLE, text("Song"));
        }
        MatchResult underscore = match("{track}_{title}", "01_Song.mp3");
        assertThat(underscore.values()).containsEntry(Field.TRACK, pos(1)).containsEntry(Field.TITLE, text("Song"));
        MatchResult space = match("{track} {title}", "01 Song.mp3");
        assertThat(space.values()).containsEntry(Field.TRACK, pos(1)).containsEntry(Field.TITLE, text("Song"));
    }

    @Test
    void trackOfTotalInTheName() {
        MatchResult a = match("{track} {title}", "03of12 Song.mp3");
        assertThat(a.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(a.values()).containsEntry(Field.TRACK, new FieldValue.Position(3, OptionalInt.of(12)))
                .containsEntry(Field.TITLE, text("Song"));
        MatchResult b = match("{track} - {title}", "3 of 12 - Song.mp3");
        assertThat(b.values()).containsEntry(Field.TRACK, new FieldValue.Position(3, OptionalInt.of(12)));
    }

    @Test
    void trackZeroIsAccepted() {
        assertThat(match("{track} - {title}", "00 - Intro.mp3").values()).containsEntry(Field.TRACK, pos(0));
    }

    // ---- E05 ----------------------------------------------------------------------------------------------------

    @Test
    void emptyCaptureInvalidatesTheSplit() {
        assertThat(match("{artist} - {title}", "- Song.mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(match("{artist} - {title}", "Artist - .mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(match("{artist} - {title}", "Artist -.mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
        MatchResult doubled = match("{artist} - {title}", "Artist -  - Song.mp3");
        assertThat(doubled.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(doubled.raw()).containsEntry(Field.ARTIST, "Artist");
        assertThat(doubled.raw().get(Field.TITLE)).isNotBlank();
    }

    // ---- E06 ----------------------------------------------------------------------------------------------------

    @Test
    void yearInFolderName() {
        MatchResult ok = match("{year} - {album}/{track} - {title}", "2020 - Album/01 - Song.mp3");
        assertThat(ok.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(ok.values()).containsEntry(Field.YEAR, new FieldValue.YearValue(2020))
                .containsEntry(Field.ALBUM, text("Album")).containsEntry(Field.TRACK, pos(1))
                .containsEntry(Field.TITLE, text("Song"));
        MatchResult bad = match("{year} - {album}/{track} - {title}", "2020 Album/01 - Song.mp3");
        assertThat(bad.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(bad.reason().orElseThrow()).contains("part 1 of 2");
    }

    // ---- E07 / E08 ----------------------------------------------------------------------------------------------

    @Test
    void separatorInsideTheArtistName() {
        MatchResult result = match("{artist} - {title}", "AC - DC - Thunderstruck.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "AC").containsEntry(Field.TITLE, "DC - Thunderstruck");

        MatchResult withYear = match("{artist} - {title} - {year}", "AC - DC - Thunderstruck - 1990.mp3");
        assertThat(withYear.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(withYear.raw()).containsEntry(Field.ARTIST, "AC").containsEntry(Field.TITLE, "DC - Thunderstruck")
                .containsEntry(Field.YEAR, "1990");

        assertThat(match("{artist} - {title}", "Artist - Song.mp3").kind()).isEqualTo(MatchKind.MATCHED);
    }

    @Test
    void underscoreAmbiguityIsNotResolved() {
        MatchResult result = match("{artist}_{title}", "Artist_Name_Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.TITLE, "Name_Song");
        assertThat(match("{artist}_{title}", "Artist_Song.mp3").kind()).isEqualTo(MatchKind.MATCHED);
    }

    // ---- E09 Greek ----------------------------------------------------------------------------------------------

    @Test
    void greekNamesFolderAndFile() {
        MatchResult result = match("{albumartist}/{artist} - {title}",
                "Ελευθερία Αρβανιτάκη/Μίκης Θεοδωράκης - Ένα το χελιδόνι.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ALBUM_ARTIST, "Ελευθερία Αρβανιτάκη")
                .containsEntry(Field.ARTIST, "Μίκης Θεοδωράκης").containsEntry(Field.TITLE, "Ένα το χελιδόνι");
    }

    @Test
    void greekFinalSigmaTonosDialytikaAndCapitalSigma() {
        // final sigma, tonos, dialytika + tonos (U+0390), capital sigma
        String title = "Σοφίας ΐ ΣΑΣ ς";
        MatchResult result = match("{artist} - {title}", "Μελίνα Μερκούρη - " + title + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.TITLE, title);
        assertThat(result.values()).containsEntry(Field.TITLE, text(title));
    }

    @Test
    void greekLiteralSeparator() {
        MatchResult result = match("{artist} και {title}", "Σωκράτης και Ὀδυσσεύς.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Σωκράτης").containsEntry(Field.TITLE, "Ὀδυσσεύς");
    }

    // ---- E10 Serbian Cyrillic -----------------------------------------------------------------------------------

    @Test
    void serbianCyrillicWithEnDash() {
        MatchResult result = match("{album}/{artist} – {title}", "Теразије/Ђорђе Балашевић – Живот је море.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ALBUM, "Теразије").containsEntry(Field.ARTIST, "Ђорђе Балашевић")
                .containsEntry(Field.TITLE, "Живот је море");
    }

    @Test
    void serbianCyrillicHyphenAndSpecialLetters() {
        MatchResult result = match("{artist} - {title}", "Ђорђе Балашевић - Живот је море.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        MatchResult letters = match("{artist} - {title}", "ЉЊЏЂЋЈ - љњџђћј.mp3");
        assertThat(letters.raw()).containsEntry(Field.ARTIST, "ЉЊЏЂЋЈ").containsEntry(Field.TITLE, "љњџђћј");
    }

    @Test
    void mixedCyrillicAndLatinInOneName() {
        MatchResult result = match("{artist} - {title}", "Ђорђе Đorđe - Čćšžđ ЧЋШЖ.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Ђорђе Đorđe").containsEntry(Field.TITLE, "Čćšžđ ЧЋШЖ");
    }

    // ---- E11 Latin diacritics -----------------------------------------------------------------------------------

    @Test
    void latinDiacriticsKeepExactCharacters() {
        MatchResult result = match("{artist} - {title}", "Đorđe Balašević - Ćevapi, Šljivovica.mp3");
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Đorđe Balašević")
                .containsEntry(Field.TITLE, "Ćevapi, Šljivovica");
        MatchResult other = match("{artist} - {title}", "Müller - Café.mp3");
        assertThat(other.raw()).containsEntry(Field.ARTIST, "Müller").containsEntry(Field.TITLE, "Café");
    }

    // ---- E12 NFC / NFD ------------------------------------------------------------------------------------------

    @Test
    void nfdFileNameMatchesNfcPatternLiteral() {
        String nfd = Normalizer.normalize("A \u010D B", Normalizer.Form.NFD);
        assertThat(nfd).contains("c\u030C");
        MatchResult result = match("{artist} \u010D {title}", nfd + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B");
    }

    @Test
    void nfcFileNameMatchesNfdPatternLiteral() {
        MatchResult result = match("{artist} c\u030C {title}", "A \u010D B.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
    }

    @Test
    void extractedValuesAreNfc() {
        String name = Normalizer.normalize("\u010Devapi - \u0110or\u0111e", Normalizer.Form.NFD);
        MatchResult result = match("{artist} - {title}", name + ".mp3");
        assertThat(result.raw().get(Field.ARTIST)).isEqualTo("\u010Devapi").isEqualTo(
                Normalizer.normalize(result.raw().get(Field.ARTIST), Normalizer.Form.NFC));
        assertThat(result.values().get(Field.ARTIST)).isEqualTo(text("\u010Devapi"));
        assertThat(result.values().get(Field.TITLE)).isEqualTo(text("\u0110or\u0111e"));
    }

    @Test
    void cyrillicIsUnaffectedByNormalisation() {
        MatchResult result = match("{artist} - {title}", "Ђорђе - Живот.mp3");
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Ђорђе");
    }

    // ---- E13 emoji ----------------------------------------------------------------------------------------------

    @Test
    void emojiInLiteralsAndCaptures() {
        MatchResult result = match("🎵 {artist} - {title} 🎶", "🎵 Artist - Song 🎶.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "Artist").containsEntry(Field.TITLE, "Song");
        MatchResult inCapture = match("{artist} - {title}", "🎵 Artist - Song 🎶.mp3");
        assertThat(inCapture.raw()).containsEntry(Field.ARTIST, "🎵 Artist").containsEntry(Field.TITLE, "Song 🎶");
    }

    @Test
    void zwjSequenceFlagAndSkinToneStayWhole() {
        String family = "👨\u200D👩\u200D👧";
        String flag = "🇷🇸";
        String thumbs = "👍🏽";
        MatchResult result = match("{artist} - {title}", family + " - " + flag + thumbs + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, family).containsEntry(Field.TITLE, flag + thumbs);
        assertThat(result.values()).containsEntry(Field.ARTIST, text(family));
    }

    @Test
    void emojiAsSeparatorDoesNotSplitSurrogatePairs() {
        MatchResult result = match("{artist}🎵{title}", "A🎵B.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B");
        // a literal made of the low surrogate half of another emoji must not match inside a pair
        assertThat(match("{artist}\uDFB5{title}", "A🎵B.mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
    }

    // ---- E14 exotic whitespace ----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"A\u00A0-\u00A0B", "A\u3000-\u3000B", "A\t-\tB", "A\u2003-\u2003B"})
    void exoticWhitespaceAroundTheSeparator(String name) {
        MatchResult result = match("{artist} - {title}", name + ".mp3");
        assertThat(result.kind()).as(name).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B");
    }

    @Test
    void newlineIsAnOrdinaryCharacterOfTheCapture() {
        MatchResult result = match("{artist} - {title}", "A\nB - C.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw().get(Field.ARTIST)).contains("A").contains("B");
        assertThat(result.raw().get(Field.TITLE)).isEqualTo("C");
    }

    // ---- E15 dash look-alikes -----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"A \u2013 B", "A \u2014 B", "A \u2010 B", "A \u2212 B"})
    void dashLookAlikesAreNotFoldedButHinted(String name) {
        MatchResult result = match("{artist} - {title}", name + ".mp3");
        assertThat(result.kind()).as(name).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason().orElseThrow()).as(name).containsIgnoringCase("dash");
    }

    @Test
    void theHintIsOnlyGivenWhenALookAlikeIsPresent() {
        assertThat(match("{artist} - {title}", "A and B.mp3").reason().orElseThrow()).doesNotContainIgnoringCase("dash");
    }

    @Test
    void enDashPatternDoesNotMatchHyphenName() {
        assertThat(match("{artist} \u2013 {title}", "A - B.mp3").kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(match("{artist} \u2013 {title}", "A \u2013 B.mp3").kind()).isEqualTo(MatchKind.MATCHED);
    }

    // ---- E16 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Song - \u0662\u0660\u0662\u0660", "Song - \uFF12\uFF10\uFF12\uFF10"})
    void digitsOfOtherScriptsAreNotYears(String name) {
        MatchResult result = match("{title} - {year}", name + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason().orElseThrow()).contains("is not a 4-digit year");
    }

    // ---- E17 / E18 / E19 ----------------------------------------------------------------------------------------

    @Test
    void dotsInNamesOnlyTheExtensionIsStrippedOnce() {
        MatchResult result = match("{artist} - {title}", "A.B.C - Song.Part 2.mp3");
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A.B.C").containsEntry(Field.TITLE, "Song.Part 2");
    }

    @Test
    void veryLongNames() {
        String artist = "Ђ".repeat(130);
        String title = "x".repeat(140);
        MatchResult result = match("{artist} - {title}", artist + " - " + title + ".mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, artist).containsEntry(Field.TITLE, title);
        String huge = "a".repeat(5_000);
        assertThat(match("{title}", huge + ".mp3").raw().get(Field.TITLE)).hasSize(5_000);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsStylePath() {
        MatchResult result = PathMatcher.match(PatternParserTest.in("{album}/{artist} - {title}"),
                Path.of("C:\\Music\\Album\\A - B.mp3"));
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ALBUM, "Album");
    }

    @Test
    void absolutePathOfTheCurrentPlatformWorks() {
        Path path = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("Album").resolve("A - B.mp3");
        MatchResult result = PathMatcher.match(PatternParserTest.in("{album}/{artist} - {title}"), path);
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
    }

    // ---- E24 / E25 ----------------------------------------------------------------------------------------------

    @Test
    void genrePlaceholder() {
        assertThat(match("{genre} - {title}", "Pop - Song.mp3").values().get(Field.GENRE))
                .isEqualTo(new FieldValue.Genres(List.of(new Genre("Pop"))));
        assertThat(match("{genre} - {title}", "Pop; Rock - Song.mp3").values().get(Field.GENRE))
                .isEqualTo(new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock"))));
        assertThat(match("{genre} - {title}", "Totally Unknown Style - Song.mp3").values().get(Field.GENRE))
                .isEqualTo(new FieldValue.Genres(List.of(new Genre("Totally Unknown Style"))));
    }

    @Test
    void discAndTrackAcrossFolderAndFile() {
        MatchResult result = match("CD{disc}/{track} - {title}", "CD1/03 - Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsEntry(Field.DISC, pos(1)).containsEntry(Field.TRACK, pos(3))
                .containsEntry(Field.TITLE, text("Song"));
    }

    @Test
    void allNineFieldsOverThreeLevels() {
        MatchResult result = match("{albumartist}/{album} ({year})/{track}-{disc}-{genre}-{artist}-{comment}-{title}",
                "AA/Alb (1999)/03-1-Pop-Art-Remark-Song.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.values()).containsOnlyKeys(Field.values());
        assertThat(result.values()).containsEntry(Field.YEAR, new FieldValue.YearValue(1999))
                .containsEntry(Field.TRACK, pos(3)).containsEntry(Field.DISC, pos(1))
                .containsEntry(Field.GENRE, new FieldValue.Genres(List.of(new Genre("Pop"))));
    }
}
