package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FieldTest {

    @Test
    void placeholdersAreUniqueAndLowerCase() {
        assertThat(Arrays.stream(Field.values()).map(Field::placeholder)).doesNotHaveDuplicates()
                .allMatch(p -> p.equals(p.toLowerCase(java.util.Locale.ROOT)));
    }

    @Test
    void byPlaceholder() {
        assertThat(Field.byPlaceholder("albumartist")).contains(Field.ALBUM_ARTIST);
        assertThat(Field.byPlaceholder("Title")).isEmpty();
        assertThatThrownBy(() -> Field.byPlaceholder(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void onlyCommentIsMultiLine() {
        assertThat(Arrays.stream(Field.values()).filter(Field::multiLine)).containsExactly(Field.COMMENT);
    }
}
