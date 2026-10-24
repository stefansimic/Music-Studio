package dev.rs.slava.icxc.musicstudio.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Id3v1TextTest {

    @Test
    void shortAsciiIsUnchanged() {
        FittedText f = Id3v1Text.fit("Song", true, 30);
        assertThat(f).isEqualTo(new FittedText("Song", false, false, false));
    }

    @Test
    void longTitleIsCutAtWordBoundary() {
        String title = "This Title Has Thirty One Chars"; // 31 characters
        assertThat(title).hasSize(31);
        FittedText f = Id3v1Text.fit(title, true, 30);
        assertThat(f.text()).isEqualTo("This Title Has Thirty One").hasSizeLessThanOrEqualTo(30);
        assertThat(f.truncated()).isTrue();
        assertThat(f.transliterated()).isFalse();
    }

    @Test
    void singleLongWordIsCutHard() {
        String word = "A".repeat(40);
        FittedText f = Id3v1Text.fit(word, true, 30);
        assertThat(f.text()).isEqualTo("A".repeat(30));
        assertThat(f.truncated()).isTrue();
    }

    @Test
    void boundaryTooEarlyIsCutHard() {
        FittedText f = Id3v1Text.fit("Ab Cdefghijklmnopqrstuvwxyzabcdefgh", true, 30);
        assertThat(f.text()).hasSize(30).startsWith("Ab Cdefghij");
    }

    @Test
    void transliteratesBeforeFitting() {
        FittedText f = Id3v1Text.fit("Ђорђе Балашевић", true, 30);
        assertThat(f).isEqualTo(new FittedText("Djordje Balasevic", true, false, false));
    }

    @Test
    void latin1ModeKeepsAccents() {
        FittedText f = Id3v1Text.fit("Café", false, 30);
        assertThat(f).isEqualTo(new FittedText("Café", false, false, false));
        assertThat(Id3v1Text.fit("Café", true, 30).text()).isEqualTo("Cafe");
    }

    @Test
    void droppedCharactersAreReportedAndTrailingSpaceStripped() {
        FittedText f = Id3v1Text.fit("Song 🎵", true, 30);
        assertThat(f.text()).isEqualTo("Song");
        assertThat(f.dropped()).isTrue();
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> Id3v1Text.fit(null, true, 30)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Id3v1Text.fit("a", true, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
