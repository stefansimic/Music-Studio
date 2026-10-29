package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldTest {

    @Test
    void placeholdersAreUniqueAndResolvable() {
        for (Field field : Field.values()) {
            assertThat(Field.byPlaceholder(field.placeholder())).contains(field);
        }
        assertThat(Field.byPlaceholder("TITLE")).isEmpty();
        assertThat(Field.byPlaceholder("nope")).isEmpty();
    }

    @Test
    void onlyCommentIsMultiLine() {
        for (Field field : Field.values()) {
            assertThat(field.multiLine()).isEqualTo(field == Field.COMMENT);
        }
    }
}
