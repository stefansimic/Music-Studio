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

    @ParameterizedTest
    @CsvSource({
        "Μίκης Θεοδωράκης, Mikis Theodorakis",
        "Νίκος Βέρτης, Nikos Vertis",
        "Ελευθερία Αρβανιτάκη, Eleftheria Arvanitaki",
        "Αλέξης, Alexis",
        "ψυχή, psychi",
        "Ουρανός, Ouranos",
        "σας, sas",
        "Ναΐφ, Naif"
    })
    void greekFollowsElot743StyleAndDropsTonosAndFinalSigma(String input, String expected) {
        Transliterator.Transliteration t = Transliterator.toAscii(input);
        assertThat(t.text()).isEqualTo(expected);
        assertThat(t.changed()).isTrue();
        assertThat(t.droppedChars()).isFalse();
        assertThat(t.text()).doesNotContain("?");
    }

    @Test
    void completeSerbianCyrillicSpecialLettersIncludingJLjNjCDzDj() {
        assertThat(Transliterator.toAscii("ЉЊЏЂЋЈ").text()).isEqualTo("LjNjDzDjCJ");
        assertThat(Transliterator.toAscii("љњџђћј").text()).isEqualTo("ljnjdzdjcj");
    }

    @Test
    void wholeSerbianCyrillicAlphabetProducesNonEmptyAsciiWithoutDrops() {
        String alphabet = "АБВГДЂЕЖЗИЈКЛЉМНЊОПРСТЋУФХЦЧЏШабвгдђежзијклљмнњопрстћуфхцчџш";
        Transliterator.Transliteration t = Transliterator.toAscii(alphabet);
        assertThat(t.droppedChars()).isFalse();
        assertThat(t.text()).isNotEmpty().matches("[\\x20-\\x7E]+");
    }

    @Test
    void emojiAndCjkAreDroppedNeverQuestionMark() {
        Transliterator.Transliteration t = Transliterator.toAscii("Song 🎵 日本");
        assertThat(t.droppedChars()).isTrue();
        assertThat(t.text()).doesNotContain("?").contains("Song");
    }
}
