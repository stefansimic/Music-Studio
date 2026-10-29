package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** P01-P09: the pattern grammar (input and output), with Q5 (adjacent placeholders rejected) applied. */
class PatternParserTest {

    static InputPattern in(String source) {
        PatternParseResult<InputPattern> result = PatternParser.parseInput(source);
        assertThat(result).as(source).isInstanceOf(PatternParseResult.Valid.class);
        return ((PatternParseResult.Valid<InputPattern>) result).pattern();
    }

    static List<PatternError> inErrors(String source) {
        PatternParseResult<InputPattern> result = PatternParser.parseInput(source);
        assertThat(result).as(source).isInstanceOf(PatternParseResult.Invalid.class);
        return ((PatternParseResult.Invalid<InputPattern>) result).errors();
    }

    static List<PatternError> outErrors(String source) {
        PatternParseResult<OutputPattern> result = PatternParser.parseOutput(source);
        assertThat(result).as(source).isInstanceOf(PatternParseResult.Invalid.class);
        return ((PatternParseResult.Invalid<OutputPattern>) result).errors();
    }

    private static Token.Placeholder ph(Field field) {
        return new Token.Placeholder(field, OptionalInt.empty());
    }

    // ---- P01 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{album}/{artist} - {title} - {year}", "{album}/{artist}_{title}", "{*}",
            "{track} - {title}", "{year} - {album}/{track}. {title}", "{artist}/{album}/{track} - {title}",
            "{title}", "{*} - {artist} - {title}"})
    void validPatternsParseAndKeepTheSourceVerbatim(String source) {
        assertThat(in(source).source()).isEqualTo(source);
    }

    @Test
    void segmentCountFollowsTheSlashes() {
        assertThat(in("{album}/{artist} - {title} - {year}").segments()).hasSize(2);
        assertThat(in("{artist}/{album}/{track} - {title}").segments()).hasSize(3);
        assertThat(in("{*}").segments()).hasSize(1);
    }

    // ---- P02 ----------------------------------------------------------------------------------------------------

    @Test
    void tokensOfASimplePattern() {
        InputPattern pattern = in("{artist} - {title}");
        assertThat(pattern.segments()).hasSize(1);
        assertThat(pattern.segments().get(0).tokens()).containsExactly(ph(Field.ARTIST),
                new Token.Literal(" - "), ph(Field.TITLE));
    }

    @Test
    void slashSplitsSegmentsInOrder() {
        InputPattern pattern = in("{album}/{track}. {title}");
        assertThat(pattern.segments().get(0).tokens()).containsExactly(ph(Field.ALBUM));
        assertThat(pattern.segments().get(1).tokens()).containsExactly(ph(Field.TRACK), new Token.Literal(". "),
                ph(Field.TITLE));
    }

    @Test
    void doubledBracesAreLiteralBraces() {
        assertThat(in("{title} {{live}}").segments().get(0).tokens())
                .containsExactly(ph(Field.TITLE), new Token.Literal(" {live}"));
        assertThat(in("a{{b").segments().get(0).tokens()).containsExactly(new Token.Literal("a{b"));
    }

    @Test
    void wildcardIsATokenOfItsOwn() {
        assertThat(in("{*} - {title}").segments().get(0).tokens())
                .containsExactly(new Token.Wildcard(), new Token.Literal(" - "), ph(Field.TITLE));
    }

    @Test
    void everyFieldHasAPlaceholder() {
        for (Field field : Field.values()) {
            assertThat(in("{" + field.placeholder() + "}").segments().get(0).tokens()).containsExactly(ph(field));
        }
    }

    // ---- P03 ----------------------------------------------------------------------------------------------------

    @Test
    void unknownPlaceholderPointsAtTheOpeningBrace() {
        List<PatternError> errors = inErrors("x {foo}");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).column()).isEqualTo(2);
        assertThat(errors.get(0).message()).contains("foo");
    }

    @Test
    void unbalancedOpeningBrace() {
        List<PatternError> errors = inErrors("{artist} - {title");
        assertThat(errors).extracting(PatternError::column).containsExactly(11);
    }

    @Test
    void strayClosingBrace() {
        assertThat(inErrors("{artist} -} {title}")).extracting(PatternError::column).containsExactly(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{album}//{title}", "/{title}", "{title}/", "{album}/ /{title}x/"})
    void emptyFolderOrFileNamePartIsAnError(String source) {
        // "{album}/ /{title}x/" ends with an empty segment; " " is a non-empty literal and valid
        assertThat(inErrors(source)).isNotEmpty();
    }

    @Test
    void emptyPartErrorIsAtTheSegment() {
        assertThat(inErrors("{album}//{title}")).extracting(PatternError::column).containsExactly(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void emptyPatternIsRejected(String source) {
        assertThat(inErrors(source)).hasSize(1);
        assertThat(inErrors(source).get(0).column()).isZero();
    }

    @Test
    void adjacentUntypedPlaceholdersAreRejected() {
        List<PatternError> errors = inErrors("{artist}{title}");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).column()).isEqualTo(8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{year}{track}", "{track}{title}", "{track}{disc}", "{*}{title}", "{title}{*}"})
    void adjacentPlaceholdersAreRejectedEvenWhenTyped(String source) {
        assertThat(inErrors(source)).isNotEmpty();
    }

    @Test
    void placeholdersSeparatedBySingleSpaceAreAccepted() {
        assertThat(in("{track} {title}").segments().get(0).tokens()).hasSize(3);
    }

    @Test
    void severalErrorsAreAllReturnedOrderedByColumn() {
        List<PatternError> errors = inErrors("{bar} - {foo} - {title");
        assertThat(errors).extracting(PatternError::column).containsExactly(0, 8, 16);
        assertThat(errors).extracting(PatternError::column).isSorted();
    }

    @Test
    void errorsInDifferentSegmentsAreAllReported() {
        List<PatternError> errors = inErrors("{foo}/{bar}");
        assertThat(errors).extracting(PatternError::column).containsExactly(0, 6);
    }

    @Test
    void theSamePlaceholderTwiceIsAnError() {
        List<PatternError> errors = inErrors("{artist} - {artist}");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).column()).isEqualTo(11);
        assertThat(errors.get(0).message()).contains("artist");
    }

    @Test
    void columnsCountCodePointsNotChars() {
        // the emoji is two chars but one code point
        assertThat(inErrors("🎵 {foo}")).extracting(PatternError::column).containsExactly(2);
    }

    // ---- P04 ----------------------------------------------------------------------------------------------------

    @Test
    void wildcardIsNotAllowedInOutputPatterns() {
        assertThat(outErrors("{*} - {title}")).extracting(PatternError::column).containsExactly(0);
    }

    @Test
    void formatIsNotAllowedInInputPatterns() {
        assertThat(inErrors("{track:00} - {title}")).extracting(PatternError::column).containsExactly(0);
    }

    @Test
    void zeroPaddedTrackIsOkInOutputPatterns() {
        PatternParseResult<OutputPattern> result = PatternParser.parseOutput("{track:00} - {title}");
        OutputPattern pattern = ((PatternParseResult.Valid<OutputPattern>) result).pattern();
        assertThat(pattern.segments().get(0).tokens().get(0))
                .isEqualTo(new Token.Placeholder(Field.TRACK, OptionalInt.of(2)));
    }

    @Test
    void discAndWiderPaddingAreOk() {
        OutputPattern pattern = ((PatternParseResult.Valid<OutputPattern>) PatternParser
                .parseOutput("{disc:000}-{track:0000}")).pattern();
        assertThat(pattern.segments().get(0).tokens()).containsExactly(
                new Token.Placeholder(Field.DISC, OptionalInt.of(3)), new Token.Literal("-"),
                new Token.Placeholder(Field.TRACK, OptionalInt.of(4)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{title:00}", "{year:00}", "{track:0a}", "{track:0}", "{track:00000}", "{track:}",
            "{track:ab}", "{disc:1}"})
    void invalidFormatsAreRejectedInOutputPatterns(String source) {
        assertThat(outErrors(source)).isNotEmpty();
        assertThat(outErrors(source).get(0).column()).isZero();
    }

    @Test
    void outputPatternsShareTheRestOfTheGrammar() {
        assertThat(outErrors("{artist}{title}")).isNotEmpty();
        assertThat(outErrors("{foo}")).isNotEmpty();
        assertThat(outErrors("")).isNotEmpty();
        assertThat(PatternParser.parseOutput("{artist}/{album}/{title}")).isInstanceOf(PatternParseResult.Valid.class);
    }

    // ---- P05 ----------------------------------------------------------------------------------------------------

    @Test
    void upperCasePlaceholderIsUnknownWithASuggestion() {
        List<PatternError> errors = inErrors("{Title}");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).containsIgnoringCase("unknown").contains("title");
    }

    @Test
    void spacesInsideBracesAreRejected() {
        assertThat(inErrors("{ title }")).hasSize(1);
        assertThat(inErrors("{title }")).hasSize(1);
        assertThat(inErrors("{}")).hasSize(1);
    }

    // ---- P06 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{artist} - {title}.mp3", "{title}.MP3", "{title}.Mp3"})
    void extensionInThePatternGivesAHintButStillParses(String source) {
        PatternParseResult.Valid<InputPattern> valid = (PatternParseResult.Valid<InputPattern>) PatternParser
                .parseInput(source);
        assertThat(valid.hints()).hasSize(1);
        Issue hint = valid.hints().get(0);
        assertThat(hint.code()).isEqualTo(IssueCode.PATTERN_EXTENSION);
        assertThat(hint.severity()).isEqualTo(Severity.WARNING);
        assertThat(valid.pattern().segments().get(0).tokens()).last().isInstanceOf(Token.Literal.class);
    }

    @Test
    void normalPatternHasNoHints() {
        PatternParseResult.Valid<InputPattern> valid = (PatternParseResult.Valid<InputPattern>) PatternParser
                .parseInput("{artist} - {title}");
        assertThat(valid.hints()).isEmpty();
        assertThat(((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput("{album}.mp3/{title}")).hints())
                .as("only the file name part matters").isEmpty();
    }

    // ---- P07 ----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"{artist} – {title}", "{artist} ・ {title}", "{artist} Και {title}", "🎵 {title}",
            "{artist} — {title}", "{artist} ‐ {title}", "Ђорђе {title}", "{artist}・{title}"})
    void nonAsciiLiteralsParse(String source) {
        InputPattern pattern = in(source);
        assertThat(pattern.segments().get(0).tokens()).anyMatch(t -> t instanceof Token.Literal);
    }

    @Test
    void literalsAreKeptExactly() {
        assertThat(in("{artist} – {title}").segments().get(0).tokens().get(1)).isEqualTo(new Token.Literal(" – "));
        assertThat(in("🎵 {title}").segments().get(0).tokens().get(0)).isEqualTo(new Token.Literal("🎵 "));
    }

    @Test
    void literalsAreNormalisedToNfc() {
        String nfd = "č"; // c + combining caron
        Token.Literal literal = (Token.Literal) in("{artist}" + nfd + "{title}").segments().get(0).tokens().get(1);
        assertThat(literal.text()).isEqualTo("č");
        assertThat(in("x" + nfd).segments().get(0).tokens().get(0)).isEqualTo(new Token.Literal("xč"));
    }

    // ---- P08 ----------------------------------------------------------------------------------------------------

    @Test
    void randomStringsNeverThrow() {
        String[] atoms = {"{", "}", "/", ":", "*", "title", "artist", "track", "year", "00", " - ", "_", ".mp3", "{{",
                "}}", "́", "🎵", "Ђ", "\n", " ", "a", "{*}", "{title}", "{track:00}"};
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = random.nextInt(12);
            for (int k = 0; k < n; k++) {
                sb.append(atoms[random.nextInt(atoms.length)]);
            }
            String source = sb.toString();
            assertThatCode(() -> {
                PatternParseResult<InputPattern> in = PatternParser.parseInput(source);
                PatternParseResult<OutputPattern> out = PatternParser.parseOutput(source);
                assertThat(in).isInstanceOfAny(PatternParseResult.Valid.class, PatternParseResult.Invalid.class);
                assertThat(out).isInstanceOfAny(PatternParseResult.Valid.class, PatternParseResult.Invalid.class);
            }).as(source).doesNotThrowAnyException();
        }
    }

    @Test
    void aVeryLongPatternParsesQuickly() {
        String source = "{artist} - ".repeat(0) + "x".repeat(2_500) + "{title}" + "y".repeat(2_493);
        assertThat(source.length()).isEqualTo(5_000);
        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofMillis(500), () -> {
            assertThat(PatternParser.parseInput(source)).isInstanceOf(PatternParseResult.Valid.class);
        });
    }

    // ---- P09 ----------------------------------------------------------------------------------------------------

    @Test
    void resultListsAreUnmodifiable() {
        InputPattern pattern = in("{artist} - {title}");
        assertThatThrownBy(() -> pattern.segments().add(pattern.segments().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.segments().get(0).tokens().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> inErrors("{foo}").clear()).isInstanceOf(UnsupportedOperationException.class);
        PatternParseResult.Valid<InputPattern> valid = (PatternParseResult.Valid<InputPattern>) PatternParser
                .parseInput("{title}.mp3");
        assertThatThrownBy(() -> valid.hints().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> PatternParser.parseInput(null)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> PatternParser.parseOutput(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PatternSegment(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InputPattern("x", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Token.Literal(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Token.Placeholder(null, OptionalInt.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PatternParseResult.Invalid<>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordInvariants() {
        assertThatThrownBy(() -> new Token.Literal("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Token.Placeholder(Field.TITLE, OptionalInt.of(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Token.Placeholder(Field.TRACK, OptionalInt.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Token.Placeholder(Field.TRACK, OptionalInt.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternError(-1, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternError(0, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternSegment(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternSegment(List.of(ph(Field.TITLE), ph(Field.ARTIST))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatternParseResult.Invalid<>(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InputPattern(" ", List.of(new PatternSegment(List.of(ph(Field.TITLE))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InputPattern("x", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
