package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Additional edge cases for the v1 versus v2 difference marker (story 001, LENIENT and STRICT). */
class ConflictSemanticsEdgeCasesTest {

    private static TagReadResult both(Field field, FieldValue v1, FieldValue v2) {
        return new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty().with(field, v2)),
                        new TagLayer(TagVersion.ID3V1_1, MediaMetadata.empty().with(field, v1))),
                Optional.empty(), Optional.empty(), List.of());
    }

    private static FieldValue t(String s) {
        return new FieldValue.Text(s);
    }

    @Test
    void aCaseOnlyDifferenceIsADifferenceInBothModes() {
        TagReadResult r = both(Field.TITLE, t("hello world"), t("Hello World"));
        assertThat(r.conflicts(V1Comparison.LENIENT)).containsExactly(Field.TITLE);
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.TITLE);
    }

    @Test
    void cyrillicV2WithTransliteratedV1() {
        TagReadResult r = both(Field.ARTIST, t("Djordje Balasevic"), t("Ђорђе Балашевић"));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.ARTIST);
    }

    @Test
    void emojiInV2DroppedInV1IsLenientOnly() {
        TagReadResult r = both(Field.TITLE, t("Song"), t("Song 🎵"));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.TITLE);
    }

    @Test
    void commonTransliterationsOfAccentedNamesAreLenientNoConflict() {
        assertThat(both(Field.ARTIST, t("Beyonce"), t("Beyonc\u00E9")).conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(both(Field.ARTIST, t("Beyonc\u00E9"), t("Beyonc\u00E9")).conflicts(V1Comparison.STRICT)).isEmpty();
        assertThat(both(Field.ARTIST, t("Mueller"), t("M\u00FCller")).conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(both(Field.ARTIST, t("Ljubav"), t("\u0409\u0443\u0431\u0430\u0432")).conflicts(V1Comparison.LENIENT))
                .isEmpty();
    }

    
    @Test
    void leadingEmojiDroppedInV1IsLenientNoConflict() {
        assertThat(both(Field.TITLE, t("Song"), t("\uD83C\uDFB5 Song")).conflicts(V1Comparison.LENIENT)).isEmpty();
    }

    @Test
    void hardCutAt30CharactersIsLenientOnly() {
        String v2 = "abcdefghijklmnopqrstuvwxyz0123456789";
        TagReadResult r = both(Field.ALBUM, t(v2.substring(0, 30)), t(v2));
        assertThat(r.conflicts(V1Comparison.LENIENT)).isEmpty();
        assertThat(r.conflicts(V1Comparison.STRICT)).containsExactly(Field.ALBUM);
    }

    @Test
    void aV1ValueThatIsLongerThanV2OrUnrelatedIsAConflict() {
        assertThat(both(Field.TITLE, t("Title and more"), t("Title")).conflicts(V1Comparison.LENIENT))
                .containsExactly(Field.TITLE);
        assertThat(both(Field.TITLE, t("Totally different"), t("Title")).conflicts(V1Comparison.LENIENT))
                .containsExactly(Field.TITLE);
    }

    @Test
    void trackNumberOnlyComparedAndYearCompared() {
        TagReadResult sameTrack = both(Field.TRACK, new FieldValue.Position(3, OptionalInt.empty()),
                new FieldValue.Position(3, OptionalInt.of(12)));
        assertThat(sameTrack.conflicts(V1Comparison.STRICT)).isEmpty();
        assertThat(sameTrack.conflicts(V1Comparison.LENIENT)).isEmpty();
        TagReadResult otherTrack = both(Field.TRACK, new FieldValue.Position(4, OptionalInt.empty()),
                new FieldValue.Position(3, OptionalInt.of(12)));
        assertThat(otherTrack.conflicts(V1Comparison.LENIENT)).containsExactly(Field.TRACK);
        TagReadResult year = both(Field.YEAR, new FieldValue.YearValue(2004), new FieldValue.YearValue(2005));
        assertThat(year.conflicts(V1Comparison.LENIENT)).containsExactly(Field.YEAR);
        assertThat(year.conflicts(V1Comparison.STRICT)).containsExactly(Field.YEAR);
    }

    @Test
    void genreDifferencesAreCaseInsensitiveAndMultiGenreUsesThePrimary() {
        FieldValue rock = new FieldValue.Genres(List.of(new Genre("Rock")));
        FieldValue rockPop = new FieldValue.Genres(List.of(new Genre("rock"), new Genre("Pop")));
        assertThat(both(Field.GENRE, rock, rockPop).conflicts(V1Comparison.LENIENT)).isEmpty();
        FieldValue pop = new FieldValue.Genres(List.of(new Genre("Pop")));
        assertThat(both(Field.GENRE, pop, rockPop).conflicts(V1Comparison.LENIENT)).containsExactly(Field.GENRE);
    }

    @Test
    void aV2OnlyOrV1OnlyFileNeverHasConflicts() {
        TagReadResult v2only = new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty().with(Field.TITLE, t("x")))),
                Optional.empty(), Optional.empty(), List.of());
        assertThat(v2only.conflicts(V1Comparison.STRICT)).isEmpty();
    }
}
