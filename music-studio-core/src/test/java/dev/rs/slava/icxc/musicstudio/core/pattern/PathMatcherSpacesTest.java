package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A separator that is only spaces must not make a name ambiguous because of how many spaces it has. */
class PathMatcherSpacesTest {

    private static InputPattern pattern(String source) {
        return ((PatternParseResult.Valid<InputPattern>) PatternParser.parseInput(source)).pattern();
    }

    @ParameterizedTest
    @ValueSource(strings = {"A B", "A  B", "A   B", "A\u00A0B", "A \u00A0B", "A\u3000B", "A\tB"})
    void spaceOnlySeparatorIsNeverAmbiguous(String name) {
        MatchResult result = PathMatcher.match(pattern("{artist} {title}"), Path.of("/m/" + name + ".mp3"));
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B");
    }

    @Test
    void realAmbiguityWithSpacesIsStillAmbiguous() {
        MatchResult result = PathMatcher.match(pattern("{artist} {title}"), Path.of("/m/A B C.mp3"));
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A").containsEntry(Field.TITLE, "B C");
    }

    @Test
    void separatorWithSpacesAroundADashIgnoresTheNumberOfSpaces() {
        MatchResult result = PathMatcher.match(pattern("{artist} - {title}"), Path.of("/m/A  -   B.mp3"));
        assertThat(result.kind()).isEqualTo(MatchKind.MATCHED);
    }
}
