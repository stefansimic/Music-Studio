package dev.rs.slava.icxc.musicstudio.core.tag;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.genres;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.year;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection.Projection;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T06, T15, T16, T17, E07, E08, E20-E27 (projection level). */
class Id3v1ProjectionTest {

    private static final Charset W1253 = Charset.forName("windows-1253");
    private static final Charset W1251 = Charset.forName("windows-1251");

    private static TagWriteOptions opts(Id3v1Choice v1, V1TextMode mode, V1NonLatinPolicy policy) {
        return new TagWriteOptions(Id3v2Choice.V2_3, v1, V2Encoding.AUTO, mode, policy, false);
    }

    private static Projection project(MediaMetadata m, V1TextMode mode, V1NonLatinPolicy policy) {
        return Id3v1Projection.project(m, opts(Id3v1Choice.V1_1, mode, policy));
    }

    private static List<IssueCode> codes(Projection p) {
        return p.issues().stream().map(Issue::code).toList();
    }

    private static Issue issue(Projection p, IssueCode code) {
        return p.issues().stream().filter(i -> i.code() == code).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " in " + p.issues()));
    }

    @Test
    void v1NoneProducesNothing() {
        Projection p = Id3v1Projection.project(meta(Field.TITLE, "x"),
                opts(Id3v1Choice.NONE, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1));
        assertThat(p.values()).isEmpty();
        assertThat(p.issues()).isEmpty();
        assertThat(p.skipped()).isFalse();
    }

    // ---- T06 / E26

    @Test
    void asciiModeTransliteratesUmlautsAndReportsIt() {
        Projection p = project(meta(Field.TITLE, "Müller"), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().title()).isEqualTo("Mueller");
        assertThat(p.values().orElseThrow().encoding()).isEqualTo(V1Encoding.ASCII);
        assertThat(codes(p)).contains(IssueCode.V1_TRANSLITERATED).doesNotContain(IssueCode.V1_SKIPPED_NON_LATIN);
    }

    @Test
    void latin1ModeKeepsUmlautsAsOneByte() {
        Projection p = project(meta(Field.TITLE, "Müller", Field.ALBUM, "Café"), V1TextMode.LATIN1,
                V1NonLatinPolicy.SKIP_V1);
        Id3v1Values v = p.values().orElseThrow();
        assertThat(v.title()).isEqualTo("Müller");
        assertThat(v.album()).isEqualTo("Café");
        assertThat(v.encoding()).isEqualTo(V1Encoding.LATIN1);
        assertThat(v.title().getBytes(v.encoding().charset())).contains((byte) 0xFC);
        assertThat(codes(p)).doesNotContain(IssueCode.V1_TRANSLITERATED, IssueCode.V1_SKIPPED_NON_LATIN);
    }

    @Test
    void latinOnlyTextIsUnaffectedByThePolicy() {
        for (V1NonLatinPolicy policy : V1NonLatinPolicy.values()) {
            Projection p = project(meta(Field.TITLE, "Café", Field.ARTIST, "Müller"), V1TextMode.LATIN1, policy);
            assertThat(p.skipped()).isFalse();
            assertThat(p.values().orElseThrow().title()).isEqualTo("Café");
            assertThat(codes(p)).isEmpty();
        }
    }

    // ---- T15 / E07

    @Test
    void longTitleIsCutAtAWordBoundaryAndReported() {
        String title = "The quick brown fox jumps over the lazy dog again";
        assertThat(title.length()).isGreaterThan(40);
        Projection p = project(meta(Field.TITLE, title), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        String cut = p.values().orElseThrow().title();
        assertThat(cut.length()).isLessThanOrEqualTo(30);
        assertThat(title).startsWith(cut);
        assertThat(title.charAt(cut.length())).isEqualTo(' ');
        Issue truncated = issue(p, IssueCode.V1_TRUNCATED);
        assertThat(truncated.severity()).isEqualTo(Severity.WARNING);
        assertThat(truncated.field()).contains(Field.TITLE);
    }

    @Test
    void titleOfExactly30CharactersIsNotTruncated() {
        String title = "123456789012345678901234567890";
        Projection p = project(meta(Field.TITLE, title), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().title()).isEqualTo(title);
        assertThat(codes(p)).doesNotContain(IssueCode.V1_TRUNCATED);
    }

    @Test
    void title31CharactersWithoutSpaceIsCutHard() {
        String title = "1234567890123456789012345678901";
        Projection p = project(meta(Field.TITLE, title), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().title()).hasSize(30);
        assertThat(codes(p)).contains(IssueCode.V1_TRUNCATED);
    }

    // ---- E08

    @Test
    void v11CommentIsCutTo28AndTrackIsKept() {
        String comment = "123456789012345678901234567890";
        MediaMetadata m = meta(Field.COMMENT, comment, Field.TRACK, position(7, 12));
        Id3v1Values v = Id3v1Projection.project(m, opts(Id3v1Choice.V1_1, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1))
                .values().orElseThrow();
        assertThat(v.comment()).hasSize(28).isEqualTo(comment.substring(0, 28));
        assertThat(v.track()).isEqualTo(7);
        assertThat(v.version()).isEqualTo(Id3v1Choice.V1_1);
    }

    @Test
    void v10KeepsThirtyCommentCharactersAndHasNoTrack() {
        String comment = "123456789012345678901234567890";
        MediaMetadata m = meta(Field.COMMENT, comment, Field.TRACK, position(7, 12));
        Projection p = Id3v1Projection.project(m, opts(Id3v1Choice.V1_0, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1));
        assertThat(p.values().orElseThrow().comment()).isEqualTo(comment);
        assertThat(p.values().orElseThrow().track()).isZero();
        assertThat(issue(p, IssueCode.V1_FIELD_NOT_REPRESENTABLE).field()).contains(Field.TRACK);
    }

    @Test
    void trackAbove255IsWarnedAndNotWritten() {
        Projection p = project(meta(Field.TRACK, position(300, 0)), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().track()).isZero();
        assertThat(issue(p, IssueCode.V1_FIELD_NOT_REPRESENTABLE).field()).contains(Field.TRACK);
    }

    // ---- T16 / T17

    @Test
    void cyrillicWithTransliteratePolicyBecomesGajAscii() {
        Projection p = project(meta(Field.ARTIST, "Ђорђе Балашевић"), V1TextMode.ASCII,
                V1NonLatinPolicy.TRANSLITERATE);
        assertThat(p.values().orElseThrow().artist()).isEqualTo("Djordje Balasevic");
        assertThat(issue(p, IssueCode.V1_TRANSLITERATED).field()).contains(Field.ARTIST);
    }

    @Test
    void albumArtistAndDiscCannotBeStoredInV1() {
        MediaMetadata m = meta(Field.TITLE, "T", Field.ALBUM_ARTIST, "AA", Field.DISC, position(1, 2));
        Projection p = project(m, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.issues()).filteredOn(i -> i.code() == IssueCode.V1_FIELD_NOT_REPRESENTABLE)
                .extracting(i -> i.field().orElseThrow()).containsExactlyInAnyOrder(Field.ALBUM_ARTIST, Field.DISC);
    }

    @Test
    void severalGenresWriteOnlyTheFirstWithAnInfo() {
        Projection p = project(meta(Field.GENRE, genres("Rock", "Pop")), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().genre()).isEqualTo(17);
        Issue info = issue(p, IssueCode.V1_FIELD_NOT_REPRESENTABLE);
        assertThat(info.severity()).isEqualTo(Severity.INFO);
        assertThat(info.field()).contains(Field.GENRE);
    }

    @Test
    void unknownGenreBecomesOtherWithAWarning() {
        Projection p = project(meta(Field.GENRE, genres("Balkan Brass")), V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1);
        assertThat(p.values().orElseThrow().genre()).isEqualTo(12);
        assertThat(issue(p, IssueCode.V1_FIELD_NOT_REPRESENTABLE).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void noGenreWritesTheNoGenreMarkerAndYearIsKept() {
        Id3v1Values v = project(meta(Field.YEAR, year(1999)), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1)
                .values().orElseThrow();
        assertThat(v.genre()).isEqualTo(Id3v1Values.NO_GENRE);
        assertThat(v.year()).hasValue(1999);
    }

    @Test
    void emptyMetadataGivesEmptyFields() {
        Id3v1Values v = project(MediaMetadata.empty(), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1).values()
                .orElseThrow();
        assertThat(v.title()).isEmpty();
        assertThat(v.year()).isEmpty();
    }

    @Test
    void multilineCommentIsFlattenedToOneLine() {
        Id3v1Values v = project(meta(Field.COMMENT, "line one\nline two"), V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1).values().orElseThrow();
        assertThat(v.comment()).isEqualTo("line one line two");
    }

    // ---- E20 / E21 default policy

    @Test
    void greekWithDefaultPolicySkipsV1AndNeverTransliterates() {
        MediaMetadata m = meta(Field.ARTIST, "Μίκης Θεοδωράκης", Field.TITLE, "Ελευθερία Αρβανιτάκη");
        Projection p = project(m, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.skipped()).isTrue();
        assertThat(p.values()).isEmpty();
        assertThat(codes(p)).containsExactly(IssueCode.V1_SKIPPED_NON_LATIN);
        assertThat(p.issues().get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(p.issues().get(0).message()).doesNotContain("?");
    }

    @Test
    void serbianCyrillicWithDefaultPolicySkipsV1() {
        MediaMetadata m = meta(Field.ARTIST, "Ђорђе Балашевић", Field.ALBUM, "ЉЊЏЂЋЈ љњџђћј");
        Projection p = project(m, V1TextMode.LATIN1, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.skipped()).isTrue();
        assertThat(codes(p)).containsExactly(IssueCode.V1_SKIPPED_NON_LATIN);
    }

    @Test
    void latinDiacriticsAloneDoNotTriggerTheNonLatinPolicy() {
        Projection p = project(meta(Field.TITLE, "čćšžđ"), V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.skipped()).isFalse();
        assertThat(p.values().orElseThrow().title()).isEqualTo("ccszdj");
    }

    // ---- E22 / E23 / E24

    @Test
    void greekCodepageWritesWindows1253BytesThatDecodeToTheOriginal() {
        String artist = "Μίκης Θεοδωράκης";
        String title = "Ελευθερία Αρβανιτάκη";
        Projection p = project(meta(Field.ARTIST, artist, Field.TITLE, title, Field.ALBUM, "Νίκος Βέρτης"),
                V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE);
        Id3v1Values v = p.values().orElseThrow();
        assertThat(v.encoding()).isEqualTo(V1Encoding.WINDOWS_1253);
        assertThat(new String(v.artist().getBytes(W1253), W1253)).isEqualTo(artist);
        assertThat(v.artist()).isEqualTo(artist);
        assertThat(v.title()).isEqualTo(title);
        assertThat(v.artist().getBytes(W1253)).doesNotContain((byte) '?');
        Issue legacy = issue(p, IssueCode.V1_LEGACY_CODEPAGE);
        assertThat(legacy.severity()).isEqualTo(Severity.INFO);
        assertThat(legacy.message()).contains("1253");
        assertThat(codes(p)).doesNotContain(IssueCode.V1_TRANSLITERATED, IssueCode.V1_SKIPPED_NON_LATIN);
    }

    @Test
    void cyrillicCodepageWritesWindows1251BytesThatDecodeToTheOriginal() {
        String artist = "Ђорђе Балашевић";
        Projection p = project(meta(Field.ARTIST, artist), V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE);
        Id3v1Values v = p.values().orElseThrow();
        assertThat(v.encoding()).isEqualTo(V1Encoding.WINDOWS_1251);
        assertThat(new String(v.artist().getBytes(W1251), W1251)).isEqualTo(artist);
        assertThat(issue(p, IssueCode.V1_LEGACY_CODEPAGE).message()).contains("1251");
    }

    @Test
    void cyrillicCodepageFoldsMissingLatinLettersAndDropsTheRest() {
        Projection folded = project(meta(Field.TITLE, "Ђорђе čćšžđ"), V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE);
        assertThat(folded.values().orElseThrow().title()).isEqualTo("Ђорђе ccszdj");
        assertThat(issue(folded, IssueCode.V1_TRANSLITERATED).field()).contains(Field.TITLE);
        Projection dropped = project(meta(Field.TITLE, "Ђорђе 🎵"), V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE);
        assertThat(dropped.values().orElseThrow().title()).isEqualTo("Ђорђе");
        assertThat(issue(dropped, IssueCode.V1_CHAR_DROPPED).field()).contains(Field.TITLE);
        assertThat(dropped.values().orElseThrow().title()).doesNotContain("?");
    }

    @Test
    void mixedGreekAndCyrillicSkipsV1EvenWithCodepagePolicy() {
        MediaMetadata m = meta(Field.TITLE, "Ελευθερία", Field.ARTIST, "Ђорђе Балашевић");
        Projection p = project(m, V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE);
        assertThat(p.skipped()).isTrue();
        assertThat(p.values()).isEmpty();
        assertThat(issue(p, IssueCode.V1_SKIPPED_NON_LATIN).message()).containsIgnoringCase("mix");
    }

    // ---- E25

    @Test
    void transliteratePolicyWritesLatinForGreekAndCyrillicAndNeverEmpty() {
        MediaMetadata m = meta(Field.ARTIST, "Μίκης Θεοδωράκης", Field.ALBUM, "Νίκος Βέρτης",
                Field.TITLE, "Ελευθερία Αρβανιτάκη", Field.COMMENT, "Ђорђе Балашевић");
        Projection p = project(m, V1TextMode.ASCII, V1NonLatinPolicy.TRANSLITERATE);
        Id3v1Values v = p.values().orElseThrow();
        assertThat(v.artist()).isEqualTo("Mikis Theodorakis");
        assertThat(v.album()).isEqualTo("Nikos Vertis");
        assertThat(v.title()).isEqualTo("Eleftheria Arvanitaki");
        assertThat(v.comment()).isEqualTo("Djordje Balasevic");
        assertThat(v.encoding()).isEqualTo(V1Encoding.ASCII);
        assertThat(p.issues()).filteredOn(i -> i.code() == IssueCode.V1_TRANSLITERATED).hasSize(4);
        assertThat(p.skipped()).isFalse();
    }

    // ---- E27

    @Test
    void emojiAndCjkAreNeverWrittenAsQuestionMarks() {
        MediaMetadata m = meta(Field.TITLE, "Song 🎵 日本");
        Projection skip = project(m, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1);
        assertThat(skip.skipped()).isTrue();
        for (V1NonLatinPolicy policy : new V1NonLatinPolicy[] {V1NonLatinPolicy.TRANSLITERATE,
                V1NonLatinPolicy.CODEPAGE}) {
            Projection p = project(m, V1TextMode.ASCII, policy);
            assertThat(p.values().orElseThrow().title()).isEqualTo("Song").doesNotContain("?");
            assertThat(issue(p, IssueCode.V1_CHAR_DROPPED).field()).contains(Field.TITLE);
        }
    }

    @Test
    void emojiOnlyInLatinTextIsDroppedAndReportedWithoutSkipping() {
        Projection p = project(meta(Field.TITLE, "Song 🎵"), V1TextMode.LATIN1, V1NonLatinPolicy.SKIP_V1);
        assertThat(p.skipped()).isFalse();
        assertThat(p.values().orElseThrow().title()).isEqualTo("Song");
        assertThat(codes(p)).contains(IssueCode.V1_CHAR_DROPPED);
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> Id3v1Projection.project(null, TagWriteOptions.DEFAULT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Id3v1Projection.project(MediaMetadata.empty(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
