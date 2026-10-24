package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Cases from the 002 review. */
class PatternParserReviewTest {

    private static List<PatternError> errors(String source) {
        PatternParseResult<InputPattern> result = PatternParser.parseInput(source);
        assertThat(result).as(source).isInstanceOf(PatternParseResult.Invalid.class);
        return ((PatternParseResult.Invalid<InputPattern>) result).errors();
    }

    @Test
    void extensionWithTrailingSpaceIsAnError() {
        assertThat(errors("{title}.mp3 ")).singleElement().satisfies(e -> {
            assertThat(e.column()).isEqualTo(7);
            assertThat(e.message()).contains("extension");
        });
    }

    @Test
    void extensionColumnIsAnIndexIntoTheSourceAlsoAfterEscapes() {
        assertThat(errors("{title}{{x}}.mp3")).singleElement().satisfies(e -> assertThat(e.column()).isEqualTo(12));
    }

    @Test
    void controlCharactersAreErrors() {
        assertThat(errors("{title}\u0000")).anySatisfy(e -> assertThat(e.message()).contains("control character"));
        assertThat(errors("{title}\n{artist}")).anySatisfy(e -> assertThat(e.column()).isEqualTo(7));
    }

    @Test
    void aPatternWithoutPlaceholderIsAnError() {
        assertThat(errors("{*}")).singleElement().satisfies(e -> assertThat(e.message()).contains("no placeholder"));
        assertThat(errors("{*} - {*}")).anySatisfy(e -> assertThat(e.message()).contains("no placeholder"));
        assertThat(errors("just text")).singleElement().satisfies(e -> assertThat(e.message())
                .contains("no placeholder"));
    }
}
