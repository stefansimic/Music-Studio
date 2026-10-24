package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Id3v1GenresTest {

    @Test
    void table() {
        assertThat(Id3v1Genres.size()).isEqualTo(192);
        assertThat(Id3v1Genres.name(0)).contains("Blues");
        assertThat(Id3v1Genres.name(12)).contains("Other");
        assertThat(Id3v1Genres.name(17)).contains("Rock");
        assertThat(Id3v1Genres.name(79)).contains("Hard Rock");
        assertThat(Id3v1Genres.name(191)).contains("Psybient");
        assertThat(Id3v1Genres.name(192)).isEmpty();
        assertThat(Id3v1Genres.name(255)).isEmpty();
        assertThat(Id3v1Genres.name(-1)).isEmpty();
    }

    @Test
    void numberIsCaseInsensitive() {
        assertThat(Id3v1Genres.number("rock")).hasValue(17);
        assertThat(Id3v1Genres.number("HIP-HOP")).hasValue(7);
        assertThat(Id3v1Genres.number("Balkan")).isEmpty();
        assertThatThrownBy(() -> Id3v1Genres.number(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"17", "(17)", "(17)Rock", " 17 ", "Rock", "(17)rock"})
    void resolvesToRock(String raw) {
        assertThat(Id3v1Genres.resolveReference(raw)).contains(List.of(new Genre("Rock")));
    }

    @Test
    void resolvesSeveralReferences() {
        assertThat(Id3v1Genres.resolveReference("(17)(20)"))
                .contains(List.of(new Genre("Rock"), new Genre("Alternative")));
        assertThat(Id3v1Genres.resolveReference("(17)Indie Rock"))
                .contains(List.of(new Genre("Rock"), new Genre("Indie Rock")));
        assertThat(Id3v1Genres.resolveReference("(RX)(CR)"))
                .contains(List.of(new Genre("Remix"), new Genre("Cover")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"255", "(999)", "", "  ", "(255)", "1234", "(999999999999)(", "(", "(99"})
    void unknownNumbersAreAbsent(String raw) {
        assertThat(Id3v1Genres.resolveReference(raw)).isEmpty();
    }

    @Test
    void plainTextAndEscapes() {
        assertThat(Id3v1Genres.resolveReference("Balkan")).contains(List.of(new Genre("Balkan")));
        assertThat(Id3v1Genres.resolveReference("((Live)")).contains(List.of(new Genre("(Live)")));
        assertThat(Id3v1Genres.resolveReference("(Live) Set")).contains(List.of(new Genre("(Live) Set")));
        assertThat(Id3v1Genres.resolveReference("Pop\nRock")).contains(List.of(new Genre("Pop Rock")));
        assertThatThrownBy(() -> Id3v1Genres.resolveReference(null)).isInstanceOf(NullPointerException.class);
    }
}
