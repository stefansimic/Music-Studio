package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaMetadataTest {

    private static FieldValue text(String s) {
        return new FieldValue.Text(s);
    }

    @Test
    void emptyHasNoValues() {
        assertThat(MediaMetadata.empty().isEmpty()).isTrue();
        assertThat(MediaMetadata.empty().get(Field.TITLE)).isEmpty();
    }

    @Test
    void wrongValueTypeRejected() {
        assertThatThrownBy(() -> new MediaMetadata(Map.of(Field.YEAR, text("2020"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaMetadata.empty().with(Field.TITLE, new FieldValue.YearValue(2020)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineBreakRules() {
        assertThatThrownBy(() -> MediaMetadata.empty().with(Field.TITLE, text("a\nb")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MediaMetadata.empty().with(Field.COMMENT, text("a\nb")).display(Field.COMMENT))
                .contains("a\nb");
    }

    @Test
    void nullsRejected() {
        assertThatThrownBy(() -> new MediaMetadata(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MediaMetadata.empty().with(null, text("a"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MediaMetadata.empty().with(Field.TITLE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isImmutable() {
        Map<Field, FieldValue> input = new EnumMap<>(Field.class);
        input.put(Field.TITLE, text("A"));
        MediaMetadata metadata = new MediaMetadata(input);
        input.put(Field.ARTIST, text("B"));
        input.remove(Field.TITLE);
        assertThat(metadata.values()).containsOnlyKeys(Field.TITLE);
        assertThatThrownBy(() -> metadata.values().put(Field.ARTIST, text("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withAndWithoutReturnCopies() {
        MediaMetadata a = MediaMetadata.empty().with(Field.TITLE, text("A"));
        MediaMetadata b = a.with(Field.ARTIST, text("B"));
        assertThat(a.get(Field.ARTIST)).isEmpty();
        assertThat(b.without(Field.ARTIST)).isEqualTo(a);
        assertThat(b.display(Field.ARTIST)).contains("B");
    }

    @Test
    void differingFields() {
        MediaMetadata a = MediaMetadata.empty().with(Field.TITLE, text("A")).with(Field.ALBUM, text("X"));
        MediaMetadata b = MediaMetadata.empty().with(Field.TITLE, text("B")).with(Field.ARTIST, text("Y"));
        assertThat(a.differingFields(b)).containsExactlyInAnyOrder(Field.TITLE, Field.ALBUM, Field.ARTIST);
        assertThat(a.differingFields(a)).isEmpty();
    }
}
