package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** FieldChanges, TagWriteRequest, WriteExpectation, SlotChange and WriteVerifier. */
class WriteRulesTest {

    private static final Set<Field> ALL = EnumSet.allOf(Field.class);

    private static MediaMetadata meta(Object... fieldValues) {
        MediaMetadata metadata = MediaMetadata.empty();
        for (int i = 0; i < fieldValues.length; i += 2) {
            metadata = metadata.with((Field) fieldValues[i], (FieldValue) fieldValues[i + 1]);
        }
        return metadata;
    }

    private static TagWriteOptions options(Id3v2Choice v2, Id3v1Choice v1, boolean remove) {
        return new TagWriteOptions(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, remove);
    }

    private static TagWriteRequest request(TagWriteOptions options, FieldChanges changes, MediaMetadata proposed) {
        Optional<Id3v1Values> v1 = options.id3v1() == Id3v1Choice.NONE ? Optional.empty()
                : Optional.of(Id3v1Projection.project(proposed, options.id3v1(), options.v1TextMode()).values());
        return new TagWriteRequest(MediaFormat.MP3, changes, new CoverChange.Keep(), options, v1);
    }

    // --- FieldChanges -------------------------------------------------------------------------------------------

    @Test
    void fieldChangesBetween() {
        MediaMetadata current = meta(Field.TITLE, new Text("Old"), Field.ARTIST, new Text("Same"),
                Field.ALBUM, new Text("Gone"));
        MediaMetadata desired = meta(Field.TITLE, new Text("New"), Field.ARTIST, new Text("Same"),
                Field.YEAR, new YearValue(2000));
        FieldChanges changes = FieldChanges.between(current, desired, ALL);
        assertThat(changes.set()).containsOnlyKeys(Field.TITLE, Field.YEAR);
        assertThat(changes.clear()).containsExactly(Field.ALBUM);
        assertThat(changes.applyTo(current)).isEqualTo(desired);
        assertThat(FieldChanges.between(current, desired, Set.of(Field.TITLE)).set()).containsOnlyKeys(Field.TITLE);
        assertThat(FieldChanges.NONE.isEmpty()).isTrue();
        assertThatThrownBy(() -> new FieldChanges(Map.of(Field.TITLE, new Text("x")), Set.of(Field.TITLE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldChanges(Map.of(Field.YEAR, new Text("x")), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldChangesRoundTripProperty() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            MediaMetadata a = random(random);
            MediaMetadata b = random(random);
            assertThat(FieldChanges.between(a, b, ALL).applyTo(a)).isEqualTo(b);
        }
    }

    private static MediaMetadata random(Random random) {
        MediaMetadata metadata = MediaMetadata.empty();
        if (random.nextBoolean()) {
            metadata = metadata.with(Field.TITLE, new Text("T" + random.nextInt(3)));
        }
        if (random.nextBoolean()) {
            metadata = metadata.with(Field.TRACK, Position.of(1 + random.nextInt(3)));
        }
        if (random.nextBoolean()) {
            metadata = metadata.with(Field.GENRE, Genres.of("G" + random.nextInt(3)));
        }
        return metadata;
    }

    // --- TagWriteRequest -----------------------------------------------------------------------------------------

    @Test
    void requestInvariants() {
        Id3v1Values v1 = Id3v1Projection.project(MediaMetadata.empty(), Id3v1Choice.V1_1, V1TextMode.ASCII).values();
        TagWriteOptions noV1 = options(Id3v2Choice.V2_3, Id3v1Choice.NONE, false);
        assertThatThrownBy(() -> new TagWriteRequest(MediaFormat.MP3, FieldChanges.NONE, new CoverChange.Keep(),
                noV1, Optional.of(v1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TagWriteRequest(MediaFormat.MP3, FieldChanges.NONE, new CoverChange.Keep(),
                TagWriteOptions.DEFAULT, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        Id3v1Values v10 = Id3v1Projection.project(MediaMetadata.empty(), Id3v1Choice.V1_0, V1TextMode.ASCII).values();
        assertThatThrownBy(() -> new TagWriteRequest(MediaFormat.MP3, FieldChanges.NONE, new CoverChange.Keep(),
                TagWriteOptions.DEFAULT, Optional.of(v10))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TagWriteRequest(MediaFormat.MP3,
                new FieldChanges(Map.of(Field.TITLE, new Text("x")), Set.of()), new CoverChange.Keep(),
                options(Id3v2Choice.NONE, Id3v1Choice.V1_1, false), Optional.of(v1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- WriteExpectation ----------------------------------------------------------------------------------------

    @Test
    void v24OnlyConvertedToV23AndV22() {
        List<TagLayer> current = List.of(new TagLayer(TagVersion.ID3V2_4, meta(Field.TITLE, new Text("Old"),
                Field.ARTIST, new Text("A"))));
        MediaMetadata proposed = meta(Field.TITLE, new Text("New"), Field.ARTIST, new Text("A"));
        FieldChanges changes = FieldChanges.between(current.getFirst().metadata(), proposed, ALL);
        for (Id3v2Choice choice : new Id3v2Choice[] {Id3v2Choice.V2_3, Id3v2Choice.V2_2}) {
            List<TagLayer> after = WriteExpectation.layersAfter(current,
                    request(options(choice, Id3v1Choice.NONE, false), changes, proposed));
            assertThat(after).containsExactly(new TagLayer(choice.version().orElseThrow(), proposed));
        }
    }

    @Test
    void keptAndRemovedOtherVersions() {
        TagLayer v2 = new TagLayer(TagVersion.ID3V2_3, meta(Field.TITLE, new Text("Old")));
        TagLayer v1 = new TagLayer(TagVersion.ID3V1_1, meta(Field.TITLE, new Text("Old"),
                Field.TRACK, Position.of(1)));
        MediaMetadata proposed = meta(Field.TITLE, new Text("New"));
        TagWriteOptions keep = options(Id3v2Choice.NONE, Id3v1Choice.V1_1, false);
        List<TagLayer> kept = WriteExpectation.layersAfter(List.of(v2, v1), request(keep, FieldChanges.NONE, proposed));
        assertThat(kept).hasSize(2).first().isEqualTo(v2);
        assertThat(kept.get(1).metadata().display(Field.TITLE)).contains("New");
        assertThat(WriteExpectation.slotIssues(List.of(v2, v1), keep, proposed)).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.OTHER_VERSION_KEPT);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.message()).contains("ID3v2.3").contains("title");
        });
        TagWriteOptions remove = options(Id3v2Choice.NONE, Id3v1Choice.V1_1, true);
        List<TagLayer> removed = WriteExpectation.layersAfter(List.of(v2, v1),
                request(remove, FieldChanges.NONE, proposed));
        assertThat(removed).hasSize(1).first().satisfies(l -> assertThat(l.version().family())
                .isEqualTo(TagFamily.ID3V1));
        assertThat(WriteExpectation.slotIssues(List.of(v2, v1), remove, proposed)).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo(IssueCode.OTHER_VERSION_REMOVED));
    }

    @Test
    void untaggedWithDefaultsAndStripAll() {
        MediaMetadata proposed = meta(Field.TITLE, new Text("Song"), Field.TRACK, Position.of(3));
        List<TagLayer> after = WriteExpectation.layersAfter(List.of(),
                request(TagWriteOptions.DEFAULT, FieldChanges.between(MediaMetadata.empty(), proposed, ALL), proposed));
        assertThat(after).extracting(TagLayer::version).containsExactly(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        TagWriteOptions strip = options(Id3v2Choice.NONE, Id3v1Choice.NONE, true);
        assertThat(WriteExpectation.layersAfter(List.of(new TagLayer(TagVersion.ID3V2_3, proposed)),
                request(strip, FieldChanges.NONE, proposed))).isEmpty();
    }

    @Test
    void v11WithoutTrackReadsBackAsV10() {
        List<TagLayer> after = WriteExpectation.layersAfter(List.of(),
                request(options(Id3v2Choice.NONE, Id3v1Choice.V1_1, false), FieldChanges.NONE,
                        meta(Field.TITLE, new Text("x"))));
        assertThat(after).extracting(TagLayer::version).containsExactly(TagVersion.ID3V1_0);
    }

    // --- SlotChange ------------------------------------------------------------------------------------------------

    @Test
    void slotChanges() {
        TagLayer before = new TagLayer(TagVersion.ID3V2_4, meta(Field.TITLE, new Text("Old")));
        TagLayer after = new TagLayer(TagVersion.ID3V2_3, meta(Field.TITLE, new Text("New")));
        TagLayer v1 = new TagLayer(TagVersion.ID3V1_1, meta(Field.TITLE, new Text("x")));
        List<SlotChange> changes = SlotChange.between(List.of(before), List.of(after, v1));
        assertThat(changes).hasSize(2);
        assertThat(changes.getFirst().before()).contains(TagVersion.ID3V2_4);
        assertThat(changes.getFirst().after()).contains(TagVersion.ID3V2_3);
        assertThat(changes.getFirst().values()).singleElement().satisfies(v -> {
            assertThat(v.before()).contains(new Text("Old"));
            assertThat(v.after()).contains(new Text("New"));
        });
        assertThat(changes.get(1).before()).isEmpty();
        assertThat(SlotChange.between(List.of(v1), List.of(v1))).singleElement()
                .satisfies(c -> assertThat(c.unchanged()).isTrue());
        assertThatThrownBy(() -> new ValueChange(Field.TITLE, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- WriteVerifier ---------------------------------------------------------------------------------------------

    private static TagReadResult actual(StreamInfo stream, TagLayer... layers) {
        return new TagReadResult(MediaFormat.MP3, List.of(layers), Optional.ofNullable(stream), Optional.empty(),
                List.of());
    }

    @Test
    void verifier() {
        StreamInfo stream = new StreamInfo(Duration.ofSeconds(10), 128, false);
        TagLayer v2 = new TagLayer(TagVersion.ID3V2_3, meta(Field.TITLE, new Text("X"), Field.GENRE,
                Genres.of("Pop", "Rock")));
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream, v2))).isEmpty();
        // genres as one joined string compare equal
        TagLayer joined = new TagLayer(TagVersion.ID3V2_3, meta(Field.TITLE, new Text("X"), Field.GENRE,
                Genres.of("Pop; Rock")));
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream, joined))).isEmpty();
        TagLayer wrong = new TagLayer(TagVersion.ID3V2_3, meta(Field.TITLE, new Text("Y"), Field.GENRE,
                Genres.of("Pop", "Rock")));
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream, wrong))).singleElement()
                .satisfies(i -> {
                    assertThat(i.code()).isEqualTo(IssueCode.VERIFY_MISMATCH);
                    assertThat(i.message()).isEqualTo("ID3v2.3 title: expected \"X\", found \"Y\"");
                });
        TagLayer v24 = new TagLayer(TagVersion.ID3V2_4, v2.metadata());
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream, v24))).hasSize(1);
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream))).hasSize(1);
        TagLayer extra = new TagLayer(TagVersion.ID3V1_1, MediaMetadata.empty());
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(stream, v2, extra))).hasSize(1);
        StreamInfo shorter = new StreamInfo(Duration.ofSeconds(9), 128, false);
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(shorter, v2))).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("duration"));
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), actual(null, v2))).hasSize(1);
        TagReadResult damaged = new TagReadResult(MediaFormat.MP3, List.of(v2), Optional.of(stream),
                Optional.empty(), List.of(Issue.error(IssueCode.TAG_DAMAGED, "broken")));
        assertThat(WriteVerifier.verify(List.of(v2), Optional.of(stream), damaged)).hasSize(1);
    }
}
