package dev.rs.slava.icxc.musicstudio.core.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import org.junit.jupiter.api.Test;

/** AMBIGUOUS means "different values": also when the shortest and the longest split happen to agree. */
class PathMatcherAmbiguityTest {

    @Test
    void aSplitBetweenTheExtremesWithOtherValuesIsAmbiguous() {
        MatchResult result = PathMatcherTest.match("{*} - {artist} - {*}", "x - A - y - A - z.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        assertThat(result.raw()).containsEntry(Field.ARTIST, "A");
        assertThat(result.alternative()).containsKey(Field.ARTIST).doesNotContainEntry(Field.ARTIST, "A");
    }

    @Test
    void splitsThatDifferOnlyInIgnoredTextAreMatched() {
        MatchResult result = PathMatcherTest.match("{*} - {title}", "a - b - Song.mp3");
        // {*} may take "a" or "a - b", the title then differs: ambiguous
        assertThat(result.kind()).isEqualTo(MatchKind.AMBIGUOUS);
        MatchResult same = PathMatcherTest.match("{title} - {*}", "Song - x.mp3");
        assertThat(same.kind()).isEqualTo(MatchKind.MATCHED);
    }

    @Test
    void controlCharacterInsideAWildcardIsNotTheReason() {
        MatchResult result = PathMatcherTest.match("{*} - {year}", "a\u0001b - 20x0.mp3");
        assertThat(result.kind()).isEqualTo(MatchKind.NO_MATCH);
        assertThat(result.reason()).hasValueSatisfying(r -> assertThat(r).contains("20x0")
                .doesNotContain("control character"));
    }
}
