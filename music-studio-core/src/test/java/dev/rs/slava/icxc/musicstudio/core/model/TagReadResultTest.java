package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TagReadResultTest {

    private static TagLayer v2(MediaMetadata metadata) {
        return new TagLayer(TagVersion.ID3V2_3, metadata);
    }

    private static TagLayer v1(MediaMetadata metadata) {
        return new TagLayer(TagVersion.ID3V1_1, metadata);
    }

    private static TagReadResult result(TagLayer... layers) {
        return new TagReadResult(MediaFormat.MP3, List.of(layers), Optional.empty(), Optional.empty(), List.of());
    }

    private static MediaMetadata text(Field field, String value) {
        return MediaMetadata.empty().with(field, new Text(value));
    }

    @Test
    void validatesLayers() {
        TagLayer v2 = v2(MediaMetadata.empty());
        TagLayer v1 = v1(MediaMetadata.empty());
        assertThatThrownBy(() -> result(v2, new TagLayer(TagVersion.ID3V2_4, MediaMetadata.empty())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> result(v1, v2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sorted");
        assertThatThrownBy(() -> result(new TagLayer(TagVersion.MP4_ILST, MediaMetadata.empty())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TagReadResult(null, List.of(), Optional.empty(), Optional.empty(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThat(result(v2, v1).versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
    }

    @Test
    void effectivePrefersV2AndFallsBackToV1() {
        TagReadResult result = result(
                v2(text(Field.TITLE, "Long Title")),
                v1(text(Field.TITLE, "Long").with(Field.YEAR, new YearValue(1999))));
        assertThat(result.effective().display(Field.TITLE)).contains("Long Title");
        assertThat(result.effective().display(Field.YEAR)).contains("1999");
        assertThat(TagReadResult.untagged(MediaFormat.MP3).effective().isEmpty()).isTrue();
        assertThat(result.layer(TagFamily.ID3V1)).isPresent();
        assertThat(result.layer(TagFamily.MP4)).isEmpty();
    }

    @Test
    void equalValuesAreNoConflict() {
        TagReadResult result = result(v2(text(Field.ARTIST, "Artist")), v1(text(Field.ARTIST, "Artist")));
        assertThat(result.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(result.conflicts(V1Comparison.STRICT)).isEmpty();
    }

    @Test
    void transliterationIsOnlyAConflictInStrictMode() {
        TagReadResult result = result(v2(text(Field.ARTIST, "Ђорђе Балашевић")),
                v1(text(Field.ARTIST, "Djordje Balasevic")));
        assertThat(result.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(result.conflicts(V1Comparison.STRICT)).containsExactly(Field.ARTIST);
    }

    @Test
    void truncationIsOnlyAConflictInStrictMode() {
        String title = "This Is A Rather Long Song Title That Does Not Fit";
        TagReadResult wordCut = result(v2(text(Field.TITLE, title)), v1(text(Field.TITLE, "This Is A Rather Long Song")));
        TagReadResult hardCut = result(v2(text(Field.TITLE, title)),
                v1(text(Field.TITLE, title.substring(0, 30).strip())));
        assertThat(wordCut.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(hardCut.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(wordCut.conflicts(V1Comparison.STRICT)).containsExactly(Field.TITLE);
    }

    @Test
    void differentArtistIsAConflict() {
        TagReadResult result = result(v2(text(Field.ARTIST, "Artist A")), v1(text(Field.ARTIST, "Artist B")));
        assertThat(result.conflicts(V1Comparison.LENIENT)).containsExactly(Field.ARTIST);
    }

    @Test
    void genreWithoutV1NumberIsNoConflictInLenientMode() {
        TagReadResult result = result(
                v2(MediaMetadata.empty().with(Field.GENRE, Genres.of("Balkan"))),
                v1(MediaMetadata.empty().with(Field.GENRE, Genres.of("Other"))));
        assertThat(result.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(result.conflicts(V1Comparison.STRICT)).containsExactly(Field.GENRE);
    }

    @Test
    void differentNumberedGenreIsAConflict() {
        TagReadResult result = result(
                v2(MediaMetadata.empty().with(Field.GENRE, Genres.of("Pop", "Rock"))),
                v1(MediaMetadata.empty().with(Field.GENRE, Genres.of("Rock"))));
        assertThat(result.conflicts(V1Comparison.LENIENT)).containsExactly(Field.GENRE);
        TagReadResult primary = result(
                v2(MediaMetadata.empty().with(Field.GENRE, Genres.of("Rock", "Pop"))),
                v1(MediaMetadata.empty().with(Field.GENRE, Genres.of("Rock"))));
        assertThat(primary.conflicts(V1Comparison.LENIENT)).isEmpty();
    }

    @Test
    void trackTotalIsIgnoredInLenientMode() {
        TagReadResult result = result(
                v2(MediaMetadata.empty().with(Field.TRACK, Position.of(3, 12))),
                v1(MediaMetadata.empty().with(Field.TRACK, Position.of(3))));
        assertThat(result.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(result.conflicts(V1Comparison.STRICT)).containsExactly(Field.TRACK);
    }

    @Test
    void fieldsMissingInOneLayerAreNoConflict() {
        TagReadResult result = result(v2(text(Field.TITLE, "T")), v1(text(Field.ARTIST, "A")));
        assertThat(result.conflicts(V1Comparison.STRICT)).isEmpty();
        assertThat(result(v2(text(Field.TITLE, "T"))).conflicts(V1Comparison.STRICT)).isEmpty();
    }

    @Test
    void v11CommentIsComparedWith28Bytes() {
        String comment = "A comment that is longer than 28 bytes";
        TagReadResult result = result(v2(text(Field.COMMENT, comment)),
                v1(text(Field.COMMENT, comment.substring(0, 28).strip())));
        assertThat(result.conflicts(V1Comparison.LENIENT)).isEmpty();
    }
}
