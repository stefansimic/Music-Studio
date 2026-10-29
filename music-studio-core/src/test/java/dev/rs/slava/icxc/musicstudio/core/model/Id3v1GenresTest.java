package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class Id3v1GenresTest {

    private static List<String> names(String raw) {
        return Id3v1Genres.resolveReference(raw).orElseThrow().stream().map(Genre::name).toList();
    }

    @Test
    void tableShape() {
        assertThat(Id3v1Genres.count()).isEqualTo(192);
        assertThat(Id3v1Genres.name(0)).contains("Blues");
        assertThat(Id3v1Genres.name(12)).contains("Other");
        assertThat(Id3v1Genres.name(17)).contains("Rock");
        assertThat(Id3v1Genres.name(20)).contains("Alternative");
        assertThat(Id3v1Genres.name(79)).contains("Hard Rock");
        assertThat(Id3v1Genres.name(80)).contains("Folk");
        assertThat(Id3v1Genres.name(147)).contains("Synthpop");
        assertThat(Id3v1Genres.name(191)).contains("Psybient");
        assertThat(Id3v1Genres.name(192)).isEmpty();
        assertThat(Id3v1Genres.name(255)).isEmpty();
        assertThat(Id3v1Genres.name(-1)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"Rock,17", "rock,17", "ROCK,17", "Blues,0", "psybient,191"})
    void numberLookupIgnoresCase(String name, int expected) {
        assertThat(Id3v1Genres.number(name)).hasValue(expected);
    }

    @Test
    void unknownNameHasNoNumber() {
        assertThat(Id3v1Genres.number("Balkan")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"17", "(17)", "(17)Rock", " (17) ", "Rock"})
    void resolvesToRock(String raw) {
        assertThat(names(raw)).containsExactly("Rock");
    }

    @Test
    void resolvesMultipleReferences() {
        assertThat(names("(17)(20)")).containsExactly("Rock", "Alternative");
    }

    @Test
    void refinementIsAdded() {
        assertThat(names("(4)Eurodisco")).containsExactly("Disco", "Eurodisco");
    }

    @ParameterizedTest
    @ValueSource(strings = {"255", "(999)", "", "  ", "(255)", "192"})
    void unresolvable(String raw) {
        assertThat(Id3v1Genres.resolveReference(raw)).isEqualTo(Optional.empty());
    }

    @Test
    void plainTextIsKept() {
        assertThat(names("Balkan")).containsExactly("Balkan");
        assertThat(names("(999)Balkan")).containsExactly("Balkan");
        assertThat(names("((not a ref)")).containsExactly("(not a ref)");
    }
}
