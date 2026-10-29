package dev.rs.slava.icxc.musicstudio.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class TransliteratorTest {

    @ParameterizedTest
    @CsvSource({
        "Ђорђе Балашевић, Djordje Balasevic",
        "Љубав, Ljubav",
        "Њ, Nj",
        "Džez, Dzez",
        "Џ, Dz",
        "čćšžđ, ccszdj",
        "ČĆŠŽĐ, CCSZDj",
        "Müller, Mueller",
        "Straße, Strasse",
        "Ärger Öl Über, Aerger Oel Ueber",
        "Café, Cafe",
        "Plain ASCII, Plain ASCII"
    })
    void toAscii(String input, String expected) {
        assertThat(Transliterator.toAscii(input).text()).isEqualTo(expected);
    }

    @Test
    void asciiIsUnchanged() {
        Transliterator.Transliteration t = Transliterator.toAscii("Hello World 123");
        assertThat(t.changed()).isFalse();
        assertThat(t.droppedChars()).isFalse();
    }

    @Test
    void changedWithoutDropping() {
        Transliterator.Transliteration t = Transliterator.toAscii("Ђорђе");
        assertThat(t.changed()).isTrue();
        assertThat(t.droppedChars()).isFalse();
    }

    @Test
    void emojiIsDroppedAndReported() {
        Transliterator.Transliteration t = Transliterator.toAscii("Song 🎵");
        assertThat(t.text()).isEqualTo("Song ");
        assertThat(t.changed()).isTrue();
        assertThat(t.droppedChars()).isTrue();
    }

    @Test
    void toLatin1KeepsLatin1Characters() {
        assertThat(Transliterator.toLatin1("é Müller Straße").text()).isEqualTo("é Müller Straße");
        assertThat(Transliterator.toLatin1("čćšžđ").text()).isEqualTo("ccszdj");
        assertThat(Transliterator.toLatin1("Ђорђе").text()).isEqualTo("Djordje");
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> Transliterator.toAscii(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Transliterator.toLatin1(null)).isInstanceOf(NullPointerException.class);
    }
}
