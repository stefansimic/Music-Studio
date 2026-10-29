package dev.rs.slava.icxc.musicstudio.core.tag;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.genres;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.year;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T21 (AC7 field-level change set), X06. */
class FieldChangesTest {

    private static final Set<Field> ALL = EnumSet.allOf(Field.class);

    @Test
    void onlyTheChangedFieldAppearsInSet() {
        MediaMetadata current = meta(Field.TITLE, "Old", Field.ARTIST, "Same", Field.YEAR, year(2000));
        MediaMetadata desired = meta(Field.TITLE, "New", Field.ARTIST, "Same", Field.YEAR, year(2000));
        FieldChanges changes = FieldChanges.between(current, desired, ALL);
        assertThat(changes.set()).containsOnlyKeys(Field.TITLE);
        assertThat(changes.set().get(Field.TITLE)).isEqualTo(text("New"));
        assertThat(changes.clear()).isEmpty();
    }

    @Test
    void fieldsAbsentInDesiredButPresentInCurrentAreCleared() {
        FieldChanges changes = FieldChanges.between(meta(Field.TITLE, "A", Field.ALBUM, "B"),
                meta(Field.TITLE, "A"), ALL);
        assertThat(changes.clear()).containsExactly(Field.ALBUM);
        assertThat(changes.set()).isEmpty();
    }

    @Test
    void fieldsAbsentInBothAreNeitherSetNorCleared() {
        FieldChanges changes = FieldChanges.between(MediaMetadata.empty(), MediaMetadata.empty(), ALL);
        assertThat(changes.isEmpty()).isTrue();
    }

    @Test
    void notWritableFieldsAreIgnored() {
        FieldChanges changes = FieldChanges.between(MediaMetadata.empty(), meta(Field.TITLE, "T", Field.ALBUM, "A"),
                Set.of(Field.TITLE));
        assertThat(changes.set()).containsOnlyKeys(Field.TITLE);
    }

    @Test
    void setAndClearMustBeDisjoint() {
        assertThatThrownBy(() -> new FieldChanges(Map.of(Field.TITLE, text("x")), Set.of(Field.TITLE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueTypeMustMatchTheField() {
        assertThatThrownBy(() -> new FieldChanges(Map.of(Field.TITLE, year(2000)), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describeListsOldAndNewInFieldOrder() {
        MediaMetadata current = meta(Field.TITLE, "Old", Field.ALBUM, "Gone");
        MediaMetadata desired = meta(Field.TITLE, "New", Field.TRACK, position(3, 10));
        FieldChanges changes = FieldChanges.between(current, desired, ALL);
        assertThat(changes.describe(current)).extracting(FieldChange::field)
                .containsExactly(Field.TITLE, Field.ALBUM, Field.TRACK);
        FieldChange title = changes.describe(current).get(0);
        assertThat(title.oldValue()).contains(text("Old"));
        assertThat(title.newValue()).contains(text("New"));
        assertThat(changes.describe(current).get(1).newValue()).isEmpty();
        assertThat(changes.describe(current).get(2).oldValue()).isEmpty();
    }

    @Test
    void applyToYieldsTheDesiredMetadataAndKeepsUntouchedFields() {
        MediaMetadata current = meta(Field.TITLE, "Old", Field.ALBUM, "Gone", Field.ARTIST, "Keep",
                Field.GENRE, genres("Rock", "Pop"));
        MediaMetadata desired = meta(Field.TITLE, "New", Field.ARTIST, "Keep", Field.GENRE, genres("Rock", "Pop"));
        FieldChanges changes = FieldChanges.between(current, desired, ALL);
        assertThat(changes.applyTo(current)).isEqualTo(desired);
    }

    @Test
    void betweenIsDeterministicAndIdempotentAfterApplying() {
        MediaMetadata current = meta(Field.TITLE, "A");
        MediaMetadata desired = meta(Field.TITLE, "B", Field.YEAR, year(1999));
        assertThat(FieldChanges.between(current, desired, ALL)).isEqualTo(FieldChanges.between(current, desired, ALL));
        MediaMetadata after = FieldChanges.between(current, desired, ALL).applyTo(current);
        assertThat(FieldChanges.between(after, desired, ALL).isEmpty()).isTrue();
    }

    @Test
    void changeRecordNeedsAnOldOrNewValue() {
        assertThatThrownBy(() -> new FieldChange(Field.TITLE, java.util.Optional.empty(), java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> FieldChanges.between(null, MediaMetadata.empty(), ALL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FieldChanges.between(MediaMetadata.empty(), null, ALL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> FieldChanges.none().applyTo(null)).isInstanceOf(NullPointerException.class);
    }
}
