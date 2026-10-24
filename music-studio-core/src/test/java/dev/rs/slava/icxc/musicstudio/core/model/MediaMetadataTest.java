package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediaMetadataTest {

    @Test
    void isImmutable() {
        Map<Field, FieldValue> input = new HashMap<>();
        input.put(Field.TITLE, new Text("Song"));
        MediaMetadata metadata = new MediaMetadata(input);
        input.put(Field.ARTIST, new Text("Artist"));
        assertThat(metadata.values()).containsOnlyKeys(Field.TITLE);
        assertThatThrownBy(() -> metadata.values().put(Field.ALBUM, new Text("A")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidContent() {
        assertThatThrownBy(() -> new MediaMetadata(null)).isInstanceOf(NullPointerException.class);
        Map<Field, FieldValue> nullValue = new HashMap<>();
        nullValue.put(Field.TITLE, null);
        assertThatThrownBy(() -> new MediaMetadata(nullValue)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MediaMetadata(Map.of(Field.TITLE, new YearValue(2020))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaMetadata(Map.of(Field.TITLE, new Text("a\nb"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commentMayContainLineBreaks() {
        MediaMetadata metadata = new MediaMetadata(Map.of(Field.COMMENT, new Text("a\nb")));
        assertThat(metadata.display(Field.COMMENT)).contains("a\nb");
    }

    @Test
    void withWithoutAndAccessors() {
        MediaMetadata metadata = MediaMetadata.empty()
                .with(Field.TITLE, new Text("Song"))
                .with(Field.TRACK, Position.of(3, 12));
        assertThat(metadata.get(Field.TITLE)).contains(new Text("Song"));
        assertThat(metadata.display(Field.TRACK)).contains("3/12");
        assertThat(metadata.get(Field.ALBUM)).isEmpty();
        assertThat(metadata.isEmpty()).isFalse();
        MediaMetadata without = metadata.without(Field.TITLE);
        assertThat(without.get(Field.TITLE)).isEmpty();
        assertThat(metadata.get(Field.TITLE)).isPresent();
        assertThat(without.without(Field.TITLE)).isSameAs(without);
        assertThatThrownBy(() -> metadata.with(Field.YEAR, new Text("2020")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata.with(null, new Text("x"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> metadata.with(Field.TITLE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void differingFields() {
        MediaMetadata a = MediaMetadata.empty().with(Field.TITLE, new Text("A")).with(Field.ARTIST, new Text("X"));
        MediaMetadata b = MediaMetadata.empty().with(Field.TITLE, new Text("B")).with(Field.ARTIST, new Text("X"))
                .with(Field.YEAR, new YearValue(2000));
        assertThat(a.differingFields(b)).containsExactlyInAnyOrder(Field.TITLE, Field.YEAR);
        assertThat(a.differingFields(a)).isEmpty();
    }

    @Test
    void equalityIsByContent() {
        assertThat(MediaMetadata.empty().with(Field.TITLE, new Text("A")))
                .isEqualTo(new MediaMetadata(Map.of(Field.TITLE, new Text("A"))));
    }
}
