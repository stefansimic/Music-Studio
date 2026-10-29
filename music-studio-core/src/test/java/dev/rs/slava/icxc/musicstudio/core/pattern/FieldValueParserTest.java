package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** V01-V05 and E16: typed value parsing (also used by --set). */
class FieldValueParserTest {

    private static FieldValue ok(Field field, String text) {
        FieldValueParser.Parsed parsed = FieldValueParser.parse(field, text);
        assertThat(parsed.error()).as("%s <- '%s'", field, text).isEmpty();
        return parsed.value().orElseThrow();
    }

    private static String rejected(Field field, String text) {
        FieldValueParser.Parsed parsed = FieldValueParser.parse(field, text);
        assertThat(parsed.value()).as("%s <- '%s'", field, text).isEmpty();
        return parsed.error().orElseThrow();
    }

    private static FieldValue.Position pos(int number) {
        return new FieldValue.Position(number, OptionalInt.empty());
    }

    private static FieldValue.Position pos(int number, int total) {
        return new FieldValue.Position(number, OptionalInt.of(total));
    }

    // ---- V01 / E16 ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"2020", "(2020)", "[2020]", " 2020 ", "( 2020 )", " 2020 "})
    void yearAcceptsPlainAndBracketed(String text) {
        assertThat(ok(Field.YEAR, text)).isEqualTo(new FieldValue.YearValue(2020));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000", "1999", "2999"})
    void yearBoundaries(String text) {
        assertThat(ok(Field.YEAR, text).display()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20x0", "99", "0999", "3000", "２０２０", "٢٠٢٠",
            "(2020", "2020)", "[2020)", "20201", "-2020", "", "   ", "abcd", "2020 2021"})
    void yearRejectsEverythingElseWithAMessage(String text) {
        assertThat(rejected(Field.YEAR, text)).isNotBlank();
    }

    @Test
    void yearRejectionMessageNamesTheValue() {
        assertThat(rejected(Field.YEAR, "20x0")).isEqualTo("\"20x0\" is not a 4-digit year");
    }

    @Test
    void digitsOfOtherScriptsAreFineAsTitleText() {
        assertThat(ok(Field.TITLE, "٢٠٢٠")).isEqualTo(new FieldValue.Text("٢٠٢٠"));
        assertThat(ok(Field.TITLE, "２０２０")).isEqualTo(new FieldValue.Text("２０２０"));
    }

    // ---- V02 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Field.class, names = {"TRACK", "DISC"})
    void positionForms(Field field) {
        assertThat(ok(field, "3")).isEqualTo(pos(3));
        assertThat(ok(field, "03")).isEqualTo(pos(3));
        assertThat(ok(field, "003")).isEqualTo(pos(3));
        assertThat(ok(field, "3of12")).isEqualTo(pos(3, 12));
        assertThat(ok(field, "3 of 12")).isEqualTo(pos(3, 12));
        assertThat(ok(field, "3/12")).isEqualTo(pos(3, 12));
        assertThat(ok(field, "03/12")).isEqualTo(pos(3, 12));
        assertThat(ok(field, " 3 / 12 ")).isEqualTo(pos(3, 12));
        assertThat(ok(field, "9999")).isEqualTo(pos(9999));
    }

    @ParameterizedTest
    @EnumSource(value = Field.class, names = {"TRACK", "DISC"})
    void positionZeroIsValid(Field field) {
        assertThat(ok(field, "0")).isEqualTo(pos(0));
        assertThat(ok(field, "00")).isEqualTo(pos(0));
        assertThat(ok(field, "0/12")).isEqualTo(pos(0, 12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "3of", "of12", "3/", "-3", "abc", "3 12", "3/0", "3/12345", "3of0", "٣",
            "3/x", "3-12", "/12", "", "  ", "1.5"})
    void positionRejects(String text) {
        assertThat(rejected(Field.TRACK, text)).isNotBlank();
        assertThat(rejected(Field.DISC, text)).isNotBlank();
    }

    @Test
    void positionRejectionMessageNamesTheKind() {
        assertThat(rejected(Field.TRACK, "Artist")).isEqualTo("\"Artist\" is not a track number");
        assertThat(rejected(Field.DISC, "Artist")).contains("disc");
    }

    // ---- V03 ----------------------------------------------------------------------------------------------------

    @Test
    void textIsStrippedButInnerWhitespaceIsKept() {
        assertThat(ok(Field.TITLE, "  Hello   World \t")).isEqualTo(new FieldValue.Text("Hello   World"));
        assertThat(ok(Field.ARTIST, " 　Ђорђе　")).isEqualTo(new FieldValue.Text("Ђорђе"));
    }

    @Test
    void textIsNormalisedToNfc() {
        assertThat(ok(Field.ARTIST, "č")).isEqualTo(new FieldValue.Text("č"));
        assertThat(ok(Field.ARTIST, "Ђорђе")).isEqualTo(new FieldValue.Text("Ђорђе"));
        assertThat(ok(Field.TITLE, "Μίκης")).isEqualTo(new FieldValue.Text("Μίκης"));
    }

    @ParameterizedTest
    @EnumSource(value = Field.class, names = {"TITLE", "ARTIST", "ALBUM_ARTIST", "ALBUM", "COMMENT"})
    void emptyTextIsRejected(Field field) {
        assertThat(rejected(field, "")).isNotBlank();
        assertThat(rejected(field, " \t 　")).isNotBlank();
    }

    @Test
    void emojiAndCjkSurviveUnchanged() {
        assertThat(ok(Field.TITLE, "🎵 歌 👨‍👩‍👧")).isEqualTo(new FieldValue.Text("🎵 歌 👨‍👩‍👧"));
    }

    @Test
    void lineBreaksNeverProduceAnInvalidSingleLineValue() {
        FieldValue.Text title = (FieldValue.Text) ok(Field.TITLE, "A\nB");
        assertThat(title.value()).doesNotContain("\n").startsWith("A").endsWith("B");
        assertThat(ok(Field.COMMENT, "a\nb")).isEqualTo(new FieldValue.Text("a\nb"));
    }

    @Test
    void veryLongTextIsAccepted() {
        String longText = "ä".repeat(5_000);
        assertThat(ok(Field.COMMENT, longText).display()).hasSize(5_000);
    }

    // ---- V04 ----------------------------------------------------------------------------------------------------

    @Test
    void singleGenre() {
        assertThat(ok(Field.GENRE, "Pop")).isEqualTo(new FieldValue.Genres(List.of(new Genre("Pop"))));
    }

    @Test
    void multipleGenresAreSplitAtSemicolons() {
        assertThat(ok(Field.GENRE, "Pop; Rock")).isEqualTo(
                new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock"))));
        assertThat(ok(Field.GENRE, " Pop ;Rock;; ")).isEqualTo(
                new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock"))));
    }

    @Test
    void duplicateGenresAreMergedAndUnknownGenresAreKept() {
        assertThat(ok(Field.GENRE, "Pop; pop")).isEqualTo(new FieldValue.Genres(List.of(new Genre("Pop"))));
        assertThat(ok(Field.GENRE, "Балкански народни")).isEqualTo(
                new FieldValue.Genres(List.of(new Genre("Балкански народни"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", ";", " ; ; "})
    void emptyGenreIsRejected(String text) {
        assertThat(rejected(Field.GENRE, text)).isNotBlank();
    }

    // ---- V05 ----------------------------------------------------------------------------------------------------

    @Test
    void parseAcceptsWhatDisplayProduces() {
        for (Field field : Field.values()) {
            for (FieldValue value : samples(field)) {
                assertThat(ok(field, value.display())).as("%s %s", field, value).isEqualTo(value);
            }
        }
    }

    private static List<FieldValue> samples(Field field) {
        return switch (field.type()) {
            case TEXT -> List.of(new FieldValue.Text("Ђорђе Балашевић"), new FieldValue.Text("Μίκης 🎵"),
                    new FieldValue.Text("Müller (Live) - 2020"), new FieldValue.Text("a, \"b\""));
            case YEAR -> List.of(new FieldValue.YearValue(1999), new FieldValue.YearValue(2020));
            case POSITION -> List.of(pos(7), pos(0), pos(3, 12), pos(9999, 9999));
            case GENRES -> List.of(new FieldValue.Genres(List.of(new Genre("Pop"))),
                    new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock"), new Genre("Фолк"))));
        };
    }

    // ---- misc contract ------------------------------------------------------------------------------------------

    @Test
    void helpersAndPreconditions() {
        assertThat(FieldValueParser.strip("  a b 　")).isEqualTo("a b");
        assertThat(FieldValueParser.isSpace(' ')).isTrue();
        assertThat(FieldValueParser.isSpace('\t')).isTrue();
        assertThat(FieldValueParser.isSpace(0xA0)).isTrue();
        assertThat(FieldValueParser.isSpace(0x3000)).isTrue();
        assertThat(FieldValueParser.isSpace('a')).isFalse();
        assertThatThrownBy(() -> FieldValueParser.parse(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FieldValueParser.parse(Field.TITLE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldValueParser.Parsed(java.util.Optional.empty(), java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
