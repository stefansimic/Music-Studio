package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldValueParserTest {

    private static FieldValue valid(Field field, String raw) {
        ParsedValue parsed = FieldValueParser.parse(field, raw);
        assertThat(parsed).as("parse %s %s", field, raw).isInstanceOf(ParsedValue.Valid.class);
        return ((ParsedValue.Valid) parsed).value();
    }

    private static String invalid(Field field, String raw) {
        ParsedValue parsed = FieldValueParser.parse(field, raw);
        assertThat(parsed).as("parse %s %s", field, raw).isInstanceOf(ParsedValue.Invalid.class);
        return ((ParsedValue.Invalid) parsed).reason();
    }

    @Test
    void text() {
        assertThat(valid(Field.TITLE, "  Song ")).isEqualTo(new Text("Song"));
        assertThat(valid(Field.TITLE, " Song ")).isEqualTo(new Text("Song"));
        invalid(Field.TITLE, "");
        invalid(Field.TITLE, "   ");
        invalid(Field.TITLE, "a\nb");
        assertThat(valid(Field.COMMENT, "a\nb")).isEqualTo(new Text("a\nb"));
        invalid(Field.TITLE, "a\u0001");
        invalid(Field.COMMENT, "a\u0001");
    }

    @Test
    void textIsNormalisedToNfc() {
        String decomposed = Normalizer.normalize("Đorđe Šćepanović", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("Đorđe Šćepanović");
        assertThat(valid(Field.ARTIST, decomposed)).isEqualTo(new Text("Đorđe Šćepanović"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020", "(2020)", "[2020]", " 2020 ", "( 2020 )"})
    void validYears(String raw) {
        assertThat(valid(Field.YEAR, raw)).isEqualTo(new YearValue(2020));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20x0", "202", "20200", "0999", "3000", "(2020", "[2020)", "２０２０"})
    void invalidYears(String raw) {
        assertThat(invalid(Field.YEAR, raw)).contains(FieldValueParser.strip(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "03", "003", "0003"})
    void positionsWithoutTotal(String raw) {
        assertThat(valid(Field.TRACK, raw)).isEqualTo(Position.of(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3/12", "3 / 12", "3of12", "3 OF 12", "03/12", "3 of 12"})
    void positionsWithTotal(String raw) {
        assertThat(valid(Field.DISC, raw)).isEqualTo(Position.of(3, 12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0/12", "3/0", "A1", "12345", "٣", "3/", "3 12", "3of", "-1", "3/12/1"})
    void invalidPositions(String raw) {
        assertThat(invalid(Field.TRACK, raw)).contains("track");
    }

    @Test
    void numberGreaterThanTotalIsValid() {
        assertThat(valid(Field.TRACK, "13/12")).isEqualTo(Position.of(13, 12));
    }

    @Test
    void genres() {
        assertThat(valid(Field.GENRE, "Pop")).isEqualTo(Genres.of("Pop"));
        assertThat(valid(Field.GENRE, "Pop; Rock")).isEqualTo(Genres.of("Pop", "Rock"));
        assertThat(valid(Field.GENRE, "Pop;;pop; Rock ")).isEqualTo(Genres.of("Pop", "Rock"));
        invalid(Field.GENRE, ";");
        invalid(Field.GENRE, "");
        invalid(Field.GENRE, "Po\np");
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> FieldValueParser.parse(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FieldValueParser.parse(Field.TITLE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ParsedValue.Invalid(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stripHandlesNoBreakSpaces() {
        assertThat(FieldValueParser.strip("  a b  ")).isEqualTo("a b");
        assertThat(FieldValueParser.isSpace(' ')).isTrue();
        assertThat(FieldValueParser.isSpace('a')).isFalse();
    }
}
