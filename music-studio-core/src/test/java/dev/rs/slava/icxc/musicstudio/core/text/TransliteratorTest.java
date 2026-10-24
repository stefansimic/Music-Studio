package dev.rs.slava.icxc.musicstudio.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransliteratorTest {

    @ParameterizedTest
    @CsvSource({
            "Ђорђе Балашевић, Djordje Balasevic",
            "Љубав, Ljubav",
            "ЉУБАВ, LJUBAV",
            "Њ, Nj",
            "Џез, Dzez",
            "Džez, Dzez",
            "Müller, Mueller",
            "Straße, Strasse",
            "čćšžđ ČĆŠŽĐ, ccszdj CCSZDJ",
            "Beyoncé, Beyonce",
            "Đorđe, Djordje",
            "ĐORĐE, DJORDJE",
    })
    void toAscii(String input, String expected) {
        Transliteration t = Transliterator.toAscii(input);
        assertThat(t.text()).isEqualTo(expected);
        assertThat(t.changed()).isTrue();
        assertThat(t.droppedChars()).isFalse();
    }

    @Test
    void pureAsciiIsUnchanged() {
        Transliteration t = Transliterator.toAscii("Hello World 123 !?");
        assertThat(t.text()).isEqualTo("Hello World 123 !?");
        assertThat(t.changed()).isFalse();
        assertThat(t.droppedChars()).isFalse();
    }

    @Test
    void emojiIsDroppedAndReported() {
        Transliteration t = Transliterator.toAscii("Song 🎵 X");
        assertThat(t.text()).isEqualTo("Song  X");
        assertThat(t.text()).doesNotContain("?");
        assertThat(t.changed()).isTrue();
        assertThat(t.droppedChars()).isTrue();
    }

    @Test
    void cjkIsDropped() {
        Transliteration t = Transliterator.toAscii("日本");
        assertThat(t.text()).isEmpty();
        assertThat(t.droppedChars()).isTrue();
    }

    @Test
    void lineBreaksBecomeSpaces() {
        assertThat(Transliterator.toAscii("a\nb\tc").text()).isEqualTo("a b c");
    }

    @Test
    void latin1KeepsLatin1Letters() {
        Transliteration t = Transliterator.toLatin1("Beyoncé Müller Straße");
        assertThat(t.text()).isEqualTo("Beyoncé Müller Straße");
        assertThat(t.changed()).isFalse();
    }

    @Test
    void latin1StillTransliteratesSerbian() {
        assertThat(Transliterator.toLatin1("Ђорђе čćšžđ").text()).isEqualTo("Djordje ccszdj");
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> Transliterator.toAscii(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Transliterator.toLatin1(null)).isInstanceOf(NullPointerException.class);
    }
}
