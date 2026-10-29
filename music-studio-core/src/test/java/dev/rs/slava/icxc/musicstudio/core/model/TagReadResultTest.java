package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TagReadResultTest {

    private static FieldValue text(String s) {
        return new FieldValue.Text(s);
    }

    private static FieldValue genres(String... names) {
        return new FieldValue.Genres(java.util.Arrays.stream(names).map(Genre::new).toList());
    }

    private static TagLayer v1(MediaMetadata m) {
        return new TagLayer(TagVersion.ID3V1_1, m);
    }

    private static TagLayer v2(MediaMetadata m) {
        return new TagLayer(TagVersion.ID3V2_3, m);
    }

    private static TagReadResult result(TagLayer... layers) {
        return new TagReadResult(MediaFormat.MP3, List.of(layers), Optional.empty(), Optional.empty(), List.of());
    }

    private static MediaMetadata meta(Field f, FieldValue v) {
        return MediaMetadata.empty().with(f, v);
    }

    @Test
    void duplicateFamilyRejected() {
        assertThatThrownBy(() -> result(v2(MediaMetadata.empty()), new TagLayer(TagVersion.ID3V2_4, MediaMetadata.empty())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }

    @Test
    void wrongOrderRejected() {
        assertThatThrownBy(() -> result(v1(MediaMetadata.empty()), v2(MediaMetadata.empty())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sorted");
    }

    @Test
    void familyNotValidForFormatRejected() {
        assertThatThrownBy(() -> result(new TagLayer(TagVersion.MP4_ILST, MediaMetadata.empty())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void untaggedFile() {
        TagReadResult r = result();
        assertThat(r.versions()).isEmpty();
        assertThat(r.effective().isEmpty()).isTrue();
        assertThat(r.conflicts(V1Comparison.STRICT)).isEmpty();
    }

    @Test
    void effectiveUsesV2AndFallsBackToV1() {
        TagReadResult r = result(
                v2(meta(Field.TITLE, text("V2 title"))),
                v1(meta(Field.TITLE, text("V1 title")).with(Field.ARTIST, text("V1 artist"))));
        assertThat(r.effective().display(Field.TITLE)).contains("V2 title");
        assertThat(r.effective().display(Field.ARTIST)).contains("V1 artist");
        assertThat(r.versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        assertThat(r.layer(TagFamily.ID3V1)).isPresent();
        assertThat(r.layer(TagFamily.MP4)).isEmpty();
    }

    @Test
    void equalValuesAreNoConflict() {
        TagReadResult r = result(v2(meta(Field.ARTIST, text("Same"))), v1(meta(Field.ARTIST, text("Same"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).isEmpty();
    }

    @Test
    void transliterationIsLenientOnly() {
        TagReadResult r = result(v2(meta(Field.ARTIST, text("Ђорђе"))), v1(meta(Field.ARTIST, text("Djordje"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.ARTIST);
    }

    @Test
    void truncationIsLenientOnly() {
        String longTitle = "The quick brown fox jumps overly lazy dog";
        String cut = "The quick brown fox jumps over"; // hard cut at 30
        String wordCut = "The quick brown fox jumps";
        for (String v1 : List.of(cut, wordCut)) {
            TagReadResult r = result(v2(meta(Field.TITLE, text(longTitle))), v1(meta(Field.TITLE, text(v1))));
            assertThat(r.conflicts(V1Comparison.LENIENT)).as(v1).isEmpty();
            assertThat(r.conflicts(V1Comparison.STRICT)).as(v1).containsExactly(Field.TITLE);
        }
    }

    @Test
    void differentArtistIsAlwaysConflict() {
        TagReadResult r = result(v2(meta(Field.ARTIST, text("One"))), v1(meta(Field.ARTIST, text("Other"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).containsExactly(Field.ARTIST);
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.ARTIST);
    }

    @Test
    void v2GenreWithoutV1NumberIsLenientOnly() {
        TagReadResult r = result(v2(meta(Field.GENRE, genres("Balkan"))), v1(meta(Field.GENRE, genres("Other"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.GENRE);
    }

    @Test
    void differentKnownGenreIsConflict() {
        TagReadResult r = result(v2(meta(Field.GENRE, genres("Pop"))), v1(meta(Field.GENRE, genres("Rock"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).containsExactly(Field.GENRE);
    }

    @Test
    void extraV2GenresAreLenientOnly() {
        TagReadResult r = result(v2(meta(Field.GENRE, genres("Rock", "Pop"))), v1(meta(Field.GENRE, genres("Rock"))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.GENRE);
    }

    @Test
    void trackTotalIsIgnoredButNumberCompared() {
        FieldValue v2Track = new FieldValue.Position(3, OptionalInt.of(12));
        assertThat(result(v2(meta(Field.TRACK, v2Track)),
                v1(meta(Field.TRACK, new FieldValue.Position(3, OptionalInt.empty())))).conflicts(V1Comparison.STRICT))
                .isEmpty();
        assertThat(result(v2(meta(Field.TRACK, v2Track)),
                v1(meta(Field.TRACK, new FieldValue.Position(4, OptionalInt.empty())))).conflicts(V1Comparison.LENIENT))
                .containsExactly(Field.TRACK);
    }

    @Test
    void fieldMissingInOneLayerIsNoConflict() {
        TagReadResult r = result(v2(meta(Field.ARTIST, text("A"))), v1(meta(Field.TITLE, text("T"))));
        assertThat(r.conflicts(V1Comparison.STRICT)).isEmpty();
    }

    @Test
    void v1CommentLimitIs28Bytes() {
        String comment = "a".repeat(28) + " tail";
        TagReadResult r = result(v2(meta(Field.COMMENT, text(comment))), v1(meta(Field.COMMENT, text("a".repeat(28)))));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
    }

    @Test
    void nullModeRejected() {
        assertThatThrownBy(() -> result().conflicts(null)).isInstanceOf(NullPointerException.class);
    }
}
