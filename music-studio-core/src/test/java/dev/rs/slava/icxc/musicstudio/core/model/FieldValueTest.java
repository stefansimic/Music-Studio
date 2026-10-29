package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FieldValueTest {

    @Test
    void textRules() {
        assertThat(new FieldValue.Text("Song").display()).isEqualTo("Song");
        assertThatThrownBy(() -> new FieldValue.Text(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldValue.Text(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Text("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Text(" x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Text("a\u0000b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void yearRules() {
        assertThat(new FieldValue.YearValue(2020).display()).isEqualTo("2020");
        assertThatThrownBy(() -> new FieldValue.YearValue(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.YearValue(10000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionRules() {
        assertThat(new FieldValue.Position(3, OptionalInt.of(12)).display()).isEqualTo("3/12");
        assertThat(new FieldValue.Position(3, OptionalInt.empty()).display()).isEqualTo("3");
        assertThat(new FieldValue.Position(13, OptionalInt.of(12)).display()).isEqualTo("13/12");
        assertThat(new FieldValue.Position(0, OptionalInt.empty()).display()).isEqualTo("0"); // intro track
        assertThatThrownBy(() -> new FieldValue.Position(-1, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Position(10_000, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Position(1, OptionalInt.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Position(1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void genresRules() {
        FieldValue.Genres genres = new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("Rock")));
        assertThat(genres.primary()).isEqualTo(new Genre("Pop"));
        assertThat(genres.display()).isEqualTo("Pop; Rock");
        assertThatThrownBy(() -> new FieldValue.Genres(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldValue.Genres(List.of(new Genre("Pop"), new Genre("POP"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void genreRules() {
        assertThatThrownBy(() -> new Genre(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Genre("a\nb")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Genre(" Rock")).isInstanceOf(IllegalArgumentException.class);
    }
}
