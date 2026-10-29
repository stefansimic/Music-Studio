package dev.rs.slava.icxc.musicstudio.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Id3v1TextTest {

    @Test
    void shortAsciiIsUnchanged() {
        Id3v1Text.FittedText f = Id3v1Text.fit("Song", true, 30);
        assertThat(f.text()).isEqualTo("Song");
        assertThat(f.transliterated()).isFalse();
        assertThat(f.truncated()).isFalse();
        assertThat(f.dropped()).isFalse();
    }

    @Test
    void cyrillicIsTransliterated() {
        Id3v1Text.FittedText f = Id3v1Text.fit("Ђорђе Балашевић", true, 30);
        assertThat(f.text()).isEqualTo("Djordje Balasevic");
        assertThat(f.transliterated()).isTrue();
    }

    @Test
    void longTitleIsCutAtWordBoundary() {
        String title = "The quick brown fox jumps over"; // 30
        String longer = title + "x"; // 31, last word "overx" does not fit
        Id3v1Text.FittedText f = Id3v1Text.fit(longer, true, 30);
        assertThat(longer).hasSize(31);
        assertThat(f.truncated()).isTrue();
        assertThat(f.text()).isEqualTo("The quick brown fox jumps");
        assertThat(f.text().length()).isLessThanOrEqualTo(30);
    }

    @Test
    void cutExactlyBeforeSpace() {
        String value = "a".repeat(30) + " tail";
        Id3v1Text.FittedText f = Id3v1Text.fit(value, true, 30);
        assertThat(f.text()).isEqualTo("a".repeat(30));
        assertThat(f.truncated()).isTrue();
    }

    @Test
    void singleLongWordIsCutHard() {
        Id3v1Text.FittedText f = Id3v1Text.fit("x".repeat(40), true, 30);
        assertThat(f.text()).isEqualTo("x".repeat(30));
        assertThat(f.truncated()).isTrue();
    }

    @Test
    void latin1ModeKeepsAcute() {
        Id3v1Text.FittedText f = Id3v1Text.fit("Café", false, 30);
        assertThat(f.text()).isEqualTo("Café");
        assertThat(f.transliterated()).isFalse();
        assertThat(Id3v1Text.fit("Café", true, 30).text()).isEqualTo("Cafe");
    }

    @Test
    void emojiDropped() {
        Id3v1Text.FittedText f = Id3v1Text.fit("A🎵", true, 30);
        assertThat(f.text()).isEqualTo("A");
        assertThat(f.dropped()).isTrue();
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> Id3v1Text.fit(null, true, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Id3v1Text.fit("a", true, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncateKeepsAFittingTextUnchanged() {
        assertThat(Id3v1Text.truncate("exactly ten", 11)).isEqualTo("exactly ten");
        assertThat(Id3v1Text.truncate("", 5)).isEmpty();
    }

    @Test
    void truncateCutsAtTheLastSpaceInsideTheLimit() {
        assertThat(Id3v1Text.truncate("one two three", 10)).isEqualTo("one two");
    }

    @Test
    void truncateCutsExactlyBeforeASpaceAtTheLimit() {
        assertThat(Id3v1Text.truncate("one two three", 7)).isEqualTo("one two");
    }

    @Test
    void truncateCutsHardInsideASingleLongWordAndNeverLeavesTrailingSpaces() {
        assertThat(Id3v1Text.truncate("abcdefghijkl", 5)).isEqualTo("abcde");
        assertThat(Id3v1Text.truncate("ab    cdefg", 6)).isEqualTo("ab");
    }

    @Test
    void truncateChecksPreconditions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Id3v1Text.truncate(null, 5))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Id3v1Text.truncate("a", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
