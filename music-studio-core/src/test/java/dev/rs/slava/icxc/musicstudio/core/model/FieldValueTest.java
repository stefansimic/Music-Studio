package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FieldValueTest {

    @Test
    void text() {
        assertThat(new Text("Song").display()).isEqualTo("Song");
        assertThat(new Text("Line 1\nLine 2").value()).contains("\n");
        assertThatThrownBy(() -> new Text(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Text(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text(" padded")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Text("a\0b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void year() {
        assertThat(new YearValue(2020).display()).isEqualTo("2020");
        assertThat(new YearValue(1).year()).isEqualTo(1);
        assertThat(new YearValue(9999).year()).isEqualTo(9999);
        assertThatThrownBy(() -> new YearValue(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YearValue(10000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void position() {
        assertThat(Position.of(3, 12).display()).isEqualTo("3/12");
        assertThat(Position.of(3).display()).isEqualTo("3");
        assertThat(Position.of(13, 12).number()).isEqualTo(13); // number > total is allowed
        assertThatThrownBy(() -> Position.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Position.of(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Position(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Position.of(10000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Position(1, OptionalInt.empty())).isEqualTo(Position.of(1));
    }

    @Test
    void genres() {
        Genres genres = Genres.of("Pop", "Rock");
        assertThat(genres.display()).isEqualTo("Pop; Rock");
        assertThat(genres.primary()).isEqualTo(new Genre("Pop"));
        assertThatThrownBy(() -> new Genres(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Genres.of("Rock", "rock")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Genres(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Genres.of("Pop", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void genresListIsCopied() {
        List<Genre> list = new ArrayList<>(List.of(new Genre("Pop")));
        Genres genres = new Genres(list);
        list.add(new Genre("Rock"));
        assertThat(genres.values()).hasSize(1);
        assertThatThrownBy(() -> genres.values().add(new Genre("X")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void genre() {
        assertThatThrownBy(() -> new Genre(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Genre(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Genre("Rock ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Genre("Ro\nck")).isInstanceOf(IllegalArgumentException.class);
    }
}
