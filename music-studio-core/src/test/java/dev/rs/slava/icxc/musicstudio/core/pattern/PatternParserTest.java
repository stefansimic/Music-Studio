package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PatternParserTest {

    static InputPattern valid(String source) {
        PatternParseResult<InputPattern> result = PatternParser.parseInput(source);
        assertThat(result).as("parse %s", source).isInstanceOf(PatternParseResult.Valid.class);
        return ((PatternParseResult.Valid<InputPattern>) result).pattern();
    }

    private static List<PatternError> invalid(String source) {
        PatternParseResult<InputPattern> result = PatternParser.parseInput(source);
        assertThat(result).as("parse %s", source).isInstanceOf(PatternParseResult.Invalid.class);
        return ((PatternParseResult.Invalid<InputPattern>) result).errors();
    }

    private static Token lit(String text) {
        return new Token.Literal(text);
    }

    private static Token ph(Field field) {
        return Token.Placeholder.of(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{album}/{artist} - {title} - {year}", "{album}/{artist}_{title}", "{*} - {title}",
            "{year} - {album}/{track}. {title}", "{Artist} - {TITLE}", "{track}{title}", "{year}{track}",
            "{artist} {title}", "{artist} – {title}", "Ђорђе {artist} – {title}x"})
    void validPatterns(String source) {
        assertThat(valid(source).source()).isEqualTo(source);
    }

    @Test
    void structure() {
        InputPattern pattern = valid("{album}/{artist} - {title} - {year}");
        assertThat(pattern.segments()).containsExactly(
                new PatternSegment(List.of(ph(Field.ALBUM))),
                new PatternSegment(List.of(ph(Field.ARTIST), lit(" - "), ph(Field.TITLE), lit(" - "),
                        ph(Field.YEAR))));
        assertThat(pattern.placeholders()).containsExactly(Field.ALBUM, Field.ARTIST, Field.TITLE, Field.YEAR);
    }

    @Test
    void placeholderNamesAreCaseInsensitive() {
        assertThat(valid("{Artist} - {TITLE}").placeholders()).containsExactly(Field.ARTIST, Field.TITLE);
    }

    @Test
    void escapedBracesBecomeLiteralText() {
        assertThat(valid("{{live}} {title}").segments().getFirst().tokens())
                .containsExactly(lit("{live} "), ph(Field.TITLE));
    }

    @Test
    void wildcard() {
        assertThat(valid("{*} - {title}").segments().getFirst().tokens())
                .containsExactly(new Token.Wildcard(), lit(" - "), ph(Field.TITLE));
        assertThat(valid("{*} - {title}").placeholders()).containsExactly(Field.TITLE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{foo}|0|unknown placeholder",
            "{artist} - {title|11|not closed",
            "title}|5|without",
            "{}|0|empty placeholder",
            "{track:00}|0|output patterns",
            "{artist}{title}|8|separator",
            "{*}{title}|3|separator",
            "{artist}/{artist} - {title}|9|{*}",
            "a//b|2|empty folder level",
            "/a|0|empty folder level",
            "a/|2|empty folder level",
            "{artist} - {title}.mp3|18|extension",
            "{artist} - {title}.MP3|18|extension",
            "{genre}{album}|7|separator",
    })
    void errorsWithColumn(String source, int column, String message) {
        assertThat(invalid(source)).anySatisfy(error -> {
            assertThat(error.column()).isEqualTo(column);
            assertThat(error.message()).contains(message);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyPattern(String source) {
        assertThat(invalid(source)).singleElement().satisfies(e -> assertThat(e.message()).contains("empty"));
    }

    @Test
    void unknownPlaceholderListsTheValidNames() {
        assertThat(invalid("{foo}").getFirst().message()).contains("{artist}").contains("{*}");
    }

    @Test
    void allErrorsAreReportedSortedByColumn() {
        List<PatternError> errors = invalid("{foo} - {bar}/{x}");
        assertThat(errors).extracting(PatternError::column).containsExactly(0, 8, 14);
    }

    @Test
    void columnsAreCharIndicesAlsoAfterEmoji() {
        assertThat(invalid("🎵 {foo}").getFirst().column()).isEqualTo(3);
    }

    @Test
    void typedPlaceholdersMayBeAdjacent() {
        assertThat(valid("{track}{title}").segments().getFirst().tokens())
                .containsExactly(ph(Field.TRACK), ph(Field.TITLE));
    }

    @Test
    void extensionInsideTheNameIsFine() {
        valid("{title}.mp3 remix");
        valid("{artist}.{title}");
    }

    @Test
    void inputPatternInvariants() {
        PatternSegment segment = new PatternSegment(List.of(ph(Field.TITLE)));
        assertThatThrownBy(() -> new InputPattern("x", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InputPattern("x", List.of(new PatternSegment(
                List.of(new Token.Placeholder(Field.TRACK, OptionalInt.of(2)))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InputPattern("x", List.of(segment, segment)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternSegment(List.of(lit("a"), lit("b"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternSegment(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatternParser.parseInput(null)).isInstanceOf(NullPointerException.class);
    }
}
