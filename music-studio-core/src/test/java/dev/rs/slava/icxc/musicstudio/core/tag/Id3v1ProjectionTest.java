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
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class Id3v1ProjectionTest {

    private static Id3v1Projection.Result project(MediaMetadata metadata) {
        return Id3v1Projection.project(metadata, Id3v1Choice.V1_1, V1TextMode.ASCII);
    }

    private static MediaMetadata meta(Field field, FieldValue value) {
        return MediaMetadata.empty().with(field, value);
    }

    private static List<IssueCode> codes(Id3v1Projection.Result result) {
        return result.issues().stream().map(Issue::code).toList();
    }

    @Test
    void cyrillicIsTransliteratedWithWarning() {
        Id3v1Projection.Result result = project(meta(Field.ARTIST, new Text("Ђорђе Балашевић")));
        assertThat(result.values().artist()).isEqualTo("Djordje Balasevic");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.V1_TRANSLITERATED);
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).contains(Field.ARTIST);
        });
    }

    @Test
    void longTitleIsTruncatedAtWordBoundary() {
        Id3v1Projection.Result result = project(meta(Field.TITLE, new Text("A Rather Long Title That Has Many Words")));
        assertThat(result.values().title()).isEqualTo("A Rather Long Title That Has").hasSizeLessThanOrEqualTo(30);
        assertThat(codes(result)).containsExactly(IssueCode.V1_TRUNCATED);
        assertThat(project(meta(Field.TITLE, new Text("X".repeat(40)))).values().title()).isEqualTo("X".repeat(30));
    }

    @Test
    void emojiIsDroppedNeverQuestionMark() {
        Id3v1Projection.Result result = project(meta(Field.TITLE, new Text("Song 🎵")));
        assertThat(result.values().title()).isEqualTo("Song").doesNotContain("?");
        assertThat(codes(result)).contains(IssueCode.V1_CHAR_DROPPED);
    }

    @Test
    void latin1ModeKeepsAccents() {
        assertThat(Id3v1Projection.project(meta(Field.TITLE, new Text("Café")), Id3v1Choice.V1_1, V1TextMode.LATIN1)
                .values().title()).isEqualTo("Café");
        Id3v1Projection.Result ascii = project(meta(Field.TITLE, new Text("Café")));
        assertThat(ascii.values().title()).isEqualTo("Cafe");
        assertThat(codes(ascii)).containsExactly(IssueCode.V1_TRANSLITERATED);
    }

    @Test
    void commentLength() {
        String comment = "x".repeat(29);
        assertThat(Id3v1Projection.project(meta(Field.COMMENT, new Text(comment)), Id3v1Choice.V1_0, V1TextMode.ASCII)
                .values().comment()).hasSize(29);
        assertThat(project(meta(Field.COMMENT, new Text(comment))).values().comment()).hasSize(28);
        assertThat(project(meta(Field.COMMENT, new Text("line 1\nline 2"))).values().comment())
                .isEqualTo("line 1 line 2");
    }

    @Test
    void track() {
        Id3v1Projection.Result withTotal = project(meta(Field.TRACK, Position.of(7, 12)));
        assertThat(withTotal.values().track()).isEqualTo(7);
        assertThat(withTotal.issues()).singleElement().satisfies(i -> assertThat(i.severity())
                .isEqualTo(Severity.INFO));
        Id3v1Projection.Result big = project(meta(Field.TRACK, Position.of(300)));
        assertThat(big.values().track()).isZero();
        assertThat(big.issues()).singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo(Severity.WARNING));
        Id3v1Projection.Result v10 = Id3v1Projection.project(meta(Field.TRACK, Position.of(3)), Id3v1Choice.V1_0,
                V1TextMode.ASCII);
        assertThat(v10.values().track()).isZero();
        assertThat(v10.issues()).singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo(Severity.INFO));
        assertThat(project(MediaMetadata.empty()).values().track()).isZero();
    }

    @Test
    void genre() {
        Id3v1Projection.Result two = project(meta(Field.GENRE, Genres.of("Rock", "Pop")));
        assertThat(two.values().genre()).isEqualTo(17);
        assertThat(two.issues()).singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo(Severity.INFO));
        Id3v1Projection.Result balkan = project(meta(Field.GENRE, Genres.of("Balkan")));
        assertThat(balkan.values().genre()).isEqualTo(Id3v1Projection.OTHER);
        assertThat(balkan.issues()).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Severity.WARNING);
            assertThat(i.message()).contains("Balkan").contains("Other");
        });
        assertThat(project(MediaMetadata.empty()).values().genre()).isEqualTo(255);
    }

    @Test
    void albumArtistAndDiscAreInfo() {
        Id3v1Projection.Result result = project(MediaMetadata.empty().with(Field.ALBUM_ARTIST, new Text("VA"))
                .with(Field.DISC, Position.of(1, 2)));
        assertThat(result.issues()).hasSize(2).allMatch(i -> i.severity() == Severity.INFO
                && i.code() == IssueCode.V1_FIELD_NOT_REPRESENTABLE);
    }

    @Test
    void year() {
        assertThat(project(meta(Field.YEAR, new YearValue(999))).values().year()).isEqualTo(OptionalInt.of(999));
        assertThat(project(MediaMetadata.empty()).values().year()).isEmpty();
    }

    @Test
    void valuesInvariantsAndReadBack() {
        assertThatThrownBy(() -> new Id3v1Values(Id3v1Choice.V1_1, V1TextMode.ASCII, "Café", "", "",
                OptionalInt.empty(), "", 0, 255)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Id3v1Values(Id3v1Choice.V1_1, V1TextMode.ASCII, "x".repeat(31), "", "",
                OptionalInt.empty(), "", 0, 255)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Id3v1Values(Id3v1Choice.V1_0, V1TextMode.ASCII, "", "", "",
                OptionalInt.empty(), "", 3, 255)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Id3v1Values(Id3v1Choice.V1_1, V1TextMode.ASCII, "", "", "",
                OptionalInt.empty(), "", 0, 256)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Id3v1Values(Id3v1Choice.NONE, V1TextMode.ASCII, "", "", "",
                OptionalInt.empty(), "", 0, 255)).isInstanceOf(IllegalArgumentException.class);
        Id3v1Values values = new Id3v1Values(Id3v1Choice.V1_1, V1TextMode.ASCII, "Title", "", "Album",
                OptionalInt.of(2001), "", 0, 12);
        assertThat(values.readBack()).isEqualTo(MediaMetadata.empty().with(Field.TITLE, new Text("Title"))
                .with(Field.ALBUM, new Text("Album")).with(Field.YEAR, new YearValue(2001))
                .with(Field.GENRE, Genres.of("Other")));
        assertThat(new Id3v1Values(Id3v1Choice.V1_1, V1TextMode.ASCII, "", "", "", OptionalInt.empty(), "", 0, 255)
                .readBack().isEmpty()).isTrue();
    }
}
