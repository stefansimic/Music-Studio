package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.genres;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.year;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T02-T06, T10-T12, E08-E10, E19, E26, E28, E29 against real files. */
class JaudiotaggerWriteVersionsTest extends WriterTestSupport {

    private static final MediaMetadata BASIC = meta(Field.TITLE, "Title", Field.ARTIST, "Artist",
            Field.ALBUM, "Album", Field.YEAR, year(2001), Field.TRACK, position(5, 12), Field.GENRE, genres("Rock"));

    private static TagWriteOptions o(Id3v2Choice v2, Id3v1Choice v1, V2Encoding enc, V1TextMode mode, boolean remove) {
        return new TagWriteOptions(v2, v1, enc, mode, V1NonLatinPolicy.SKIP_V1, remove);
    }

    private static TagWriteOptions o(Id3v2Choice v2, Id3v1Choice v1) {
        return o(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, v2 == Id3v2Choice.NONE && v1 == Id3v1Choice.NONE);
    }

    static Stream<Arguments> combos() {
        return Stream.of(Id3v2Choice.values()).flatMap(v2 -> Stream.of(Id3v1Choice.values())
                .map(v1 -> Arguments.of(v2, v1)));
    }

    @ParameterizedTest
    @MethodSource("combos")
    void exactlyTheChosenVersionsArePresent(Id3v2Choice v2, Id3v1Choice v1) {
        TagWriteOptions options = o(v2, v1);
        Written w = write(Fixture.NO_TAGS, BASIC, options);
        assertThat(w.read().versions()).isEqualTo(options.selectedVersions());
        if (v2 != Id3v2Choice.NONE) {
            assertThat(w.rawV2().major()).isEqualTo(v2 == Id3v2Choice.V2_2 ? 2 : v2 == Id3v2Choice.V2_3 ? 3 : 4);
        } else {
            assertThat(Id3Raw.v2(w.bytes())).isEmpty();
        }
        assertThat(Id3Raw.v1(w.bytes()).isPresent()).isEqualTo(v1 != Id3v1Choice.NONE);
    }

    // ---- encodings

    @Test
    void utf16IsWrittenWithBom() {
        Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, "Ђорђе Балашевић"),
                o(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.UTF16, V1TextMode.ASCII, false));
        Id3Raw.Frame f = w.rawV2().frame("TIT2").orElseThrow();
        assertThat(f.encodingByte()).isEqualTo(1);
        int b1 = f.body()[1] & 0xFF;
        int b2 = f.body()[2] & 0xFF;
        assertThat((b1 == 0xFF && b2 == 0xFE) || (b1 == 0xFE && b2 == 0xFF)).as("BOM").isTrue();
        assertThat(RoundTripSupportV2.title(w)).isEqualTo("Ђорђе Балашевић");
    }

    @Test
    void utf8IsWrittenInV24() {
        Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, "Ђорђе Балашевић"),
                o(Id3v2Choice.V2_4, Id3v1Choice.NONE, V2Encoding.UTF8, V1TextMode.ASCII, false));
        assertThat(w.rawV2().frame("TIT2").orElseThrow().encodingByte()).isEqualTo(3);
        assertThat(RoundTripSupportV2.title(w)).isEqualTo("Ђорђе Балашевић");
    }

    @Test
    void autoUsesLatin1WhenItFitsAndTheRightUnicodeEncodingOtherwise() {
        for (String ascii : List.of("Plain", "Café")) {
            for (Id3v2Choice v : List.of(Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4)) {
                Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, ascii), o(v, Id3v1Choice.NONE));
                assertThat(w.rawV2().frames().get(0).encodingByte()).as("%s %s", ascii, v).isZero();
            }
        }
        for (String script : List.of("Ђорђе Балашевић", "Μίκης Θεοδωράκης")) {
            Written v23 = write(Fixture.NO_TAGS, meta(Field.TITLE, script), o(Id3v2Choice.V2_3, Id3v1Choice.NONE));
            assertThat(v23.rawV2().frames().get(0).encodingByte()).isEqualTo(1);
            Written v22 = write(Fixture.NO_TAGS, meta(Field.TITLE, script), o(Id3v2Choice.V2_2, Id3v1Choice.NONE));
            assertThat(v22.rawV2().frames().get(0).encodingByte()).isEqualTo(1);
            Written v24 = write(Fixture.NO_TAGS, meta(Field.TITLE, script), o(Id3v2Choice.V2_4, Id3v1Choice.NONE));
            assertThat(v24.rawV2().frames().get(0).encodingByte()).isEqualTo(3);
            assertThat(RoundTripSupportV2.title(v23)).isEqualTo(script);
            assertThat(RoundTripSupportV2.title(v24)).isEqualTo(script);
        }
    }

    @Test
    void forcedLatin1IsWrittenAsLatin1() {
        Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, "Café"),
                o(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.LATIN1, V1TextMode.ASCII, false));
        assertThat(w.rawV2().frame("TIT2").orElseThrow().encodingByte()).isZero();
    }

    // ---- v1 raw bytes (T06, E26)

    @Test
    void v1AsciiModeTransliteratesUmlautsLatin1ModeWritesOneByte() {
        MediaMetadata m = meta(Field.TITLE, "Müller", Field.TRACK, position(1, 0));
        Written ascii = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_3, Id3v1Choice.V1_1));
        assertThat(Id3Raw.v1Title(ascii.rawV1(), StandardCharsets.ISO_8859_1)).isEqualTo("Mueller");
        Written latin1 = write(Fixture.NO_TAGS, m,
                o(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.LATIN1, false));
        assertThat(latin1.rawV1()[3 + 1]).isEqualTo((byte) 0xFC);
        assertThat(Id3Raw.v1Title(latin1.rawV1(), StandardCharsets.ISO_8859_1)).isEqualTo("Müller");
        assertThat(RoundTripSupportV2.title(latin1)).isEqualTo("Müller");
    }

    @Test
    void v1FieldLimitsAndTrackByte() {
        String comment = "123456789012345678901234567890";
        MediaMetadata m = meta(Field.TITLE, "The quick brown fox jumps over the lazy dog again", Field.COMMENT, comment,
                Field.TRACK, position(7, 0));
        Written v11 = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_3, Id3v1Choice.V1_1));
        byte[] b = v11.rawV1();
        assertThat(Id3Raw.v1Title(b, StandardCharsets.ISO_8859_1).length()).isLessThanOrEqualTo(30);
        assertThat(Id3Raw.v1Text(b, 97, 28, StandardCharsets.ISO_8859_1)).isEqualTo(comment.substring(0, 28));
        assertThat(b[125]).isZero();
        assertThat(b[126]).isEqualTo((byte) 7);
        assertThat(RoundTripSupportV2.title(v11)).isEqualTo("The quick brown fox jumps over the lazy dog again");
        assertThat(v11.read().versions()).contains(TagVersion.ID3V1_1);

        Written v10 = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_3, Id3v1Choice.V1_0));
        assertThat(Id3Raw.v1Text(v10.rawV1(), 97, 30, StandardCharsets.ISO_8859_1)).isEqualTo(comment);
        assertThat(v10.read().versions()).contains(TagVersion.ID3V1_0).doesNotContain(TagVersion.ID3V1_1);
    }

    // ---- genres (E10)

    @Test
    void twoGenresAreJoinedForV22V23AndNulSeparatedForV24AndReadBackAsTwo() {
        MediaMetadata m = meta(Field.TITLE, "T", Field.GENRE, genres("Rock", "Pop"));
        for (Id3v2Choice v : List.of(Id3v2Choice.V2_2, Id3v2Choice.V2_3)) {
            Written w = write(Fixture.NO_TAGS, m, o(v, Id3v1Choice.NONE));
            String id = v == Id3v2Choice.V2_2 ? "TCO" : "TCON";
            assertThat(frameTextOf(w, id)).isEqualTo("Rock; Pop");
            assertThat(w.read().effective().get(Field.GENRE).orElseThrow())
                    .isEqualTo(genres("Rock", "Pop"));
        }
        Written v24 = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_4, Id3v1Choice.NONE));
        Id3Raw.Frame tcon = v24.rawV2().frame("TCON").orElseThrow();
        String raw = new String(tcon.body(), 1, tcon.body().length - 1, charsetOfEncodingByte(tcon.encodingByte()));
        assertThat(raw.replaceAll("\0$", "")).isEqualTo("Rock\0Pop");
        assertThat(v24.read().effective().get(Field.GENRE).orElseThrow()).isEqualTo(genres("Rock", "Pop"));
    }

    private static String frameTextOf(Written w, String id) {
        return frameText(w.rawV2().frame(id).orElseThrow());
    }

    // ---- remove / keep other versions (T10-T12, E19, E29)

    @Test
    void v24ToV23WithRemoveLeavesOnlyV23() {
        Written w = write(Fixture.V24_UTF8, currentV2(Fixture.V24_UTF8),
                o(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII, true));
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V2_3);
        assertThat(Id3Raw.v1(w.bytes())).isEmpty();
        assertThat(w.preflight()).extracting(i -> i.code()).doesNotContain(IssueCode.OTHER_VERSION_REMOVED,
                IssueCode.OTHER_VERSION_KEPT);
    }

    @Test
    void v24ToV23WithoutRemoveConvertsInPlaceWithoutASecondTagOrWarning() {
        Written w = write(Fixture.V24_UTF8, currentV2(Fixture.V24_UTF8),
                o(Id3v2Choice.V2_3, Id3v1Choice.NONE));
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V2_3);
        assertThat(w.rawV2().major()).isEqualTo(3);
        assertThat(w.preflight()).extracting(i -> i.code()).doesNotContain(IssueCode.OTHER_VERSION_REMOVED,
                IssueCode.OTHER_VERSION_KEPT);
        assertThat(w.read().effective().display(Field.TITLE)).contains("Title UTF-8 ñ");
    }

    @Test
    void existingV1StaysByteIdenticalWhenItsFamilyIsNoneAndRemoveIsOff() {
        Written w = write(Fixture.V11_V23_SAME, currentV2(Fixture.V11_V23_SAME), o(Id3v2Choice.V2_3, Id3v1Choice.NONE));
        byte[] sourceV1 = Id3Raw.v1(dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.bytes(w.source()))
                .orElseThrow();
        assertThat(w.rawV1()).isEqualTo(sourceV1);
        assertThat(w.read().versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        assertThat(w.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_KEPT);
    }

    @Test
    void existingV1IsRemovedWhenItsFamilyIsNoneAndRemoveIsOn() {
        Written w = write(Fixture.V11_V23_SAME, currentV2(Fixture.V11_V23_SAME),
                o(Id3v2Choice.V2_4, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII, true));
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V2_4);
        assertThat(Id3Raw.v1(w.bytes())).isEmpty();
        assertThat(w.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_REMOVED);
    }

    @Test
    void existingV2IsRemovedWhenOnlyV1IsChosenWithRemove() {
        Written w = write(Fixture.V24_UTF8, currentV2(Fixture.V24_UTF8),
                o(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII, true));
        assertThat(Id3Raw.v2(w.bytes())).isEmpty();
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V1_1);
        assertThat(w.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_REMOVED);
    }

    @Test
    void stripAllTagsRemovesBothFamiliesAndKeepsTheAudio() {
        Written w = write(Fixture.V11_V23_SAME, MediaMetadata.empty(), o(Id3v2Choice.NONE, Id3v1Choice.NONE));
        assertThat(w.read().layers()).isEmpty();
        assertThat(Id3Raw.v2(w.bytes())).isEmpty();
        assertThat(Id3Raw.v1(w.bytes())).isEmpty();
        assertThat(w.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_REMOVED);
        assertThat(w.read().stream()).isPresent();
    }

    @Test
    void skippedV1UnderTheDefaultPolicyFollowsTheFamilyNoneRule() {
        MediaMetadata greek = meta(Field.TITLE, "Ελευθερία Αρβανιτάκη", Field.ARTIST, "Μίκης Θεοδωράκης");
        TagWriteOptions keep = o(Id3v2Choice.V2_3, Id3v1Choice.V1_1);
        Written kept = write(Fixture.V11_V23_SAME, greek, keep);
        assertThat(kept.request().options().id3v1()).isEqualTo(Id3v1Choice.NONE);
        assertThat(kept.rawV1()).isEqualTo(Id3Raw.v1(dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport
                .bytes(kept.source())).orElseThrow());
        assertThat(kept.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_KEPT);

        Written removed = write(Fixture.V11_V23_SAME, greek,
                o(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII, true));
        assertThat(Id3Raw.v1(removed.bytes())).isEmpty();
        assertThat(removed.preflight()).anyMatch(i -> i.code() == IssueCode.OTHER_VERSION_REMOVED);
    }

    // ---- v2.4 -> v2.2 (E09)

    @Test
    void v24ToV22UsesV22FrameIdsMapsYearAndKeepsBothGenres() {
        Written w = write(Fixture.V24_UTF8, currentV2(Fixture.V24_UTF8), o(Id3v2Choice.V2_2, Id3v1Choice.NONE));
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V2_2);
        assertThat(w.rawV2().ids()).contains("TT2", "TP1", "TAL", "TYE", "TRK", "TCO")
                .noneMatch(id -> id.length() == 4);
        assertThat(w.read().effective().get(Field.YEAR)).contains(year(2020));
        assertThat(frameText(w.rawV2().frame("TCO").orElseThrow()).replace("\0", "<NUL>")).isEqualTo("Rock; Pop");
        assertThat(w.read().effective().get(Field.GENRE).orElseThrow()).isEqualTo(
                new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres(List.of(new Genre("Rock"), new Genre("Pop"))));
        assertThat(w.preflight()).extracting(i -> i.code()).doesNotContain(IssueCode.FRAME_NOT_REPRESENTABLE);
    }

    @Test
    void v24ToV23ConvertsAnUnchangedMultiGenreFrameToTheSemicolonForm() {
        Written w = write(Fixture.V24_UTF8, currentV2(Fixture.V24_UTF8), o(Id3v2Choice.V2_3, Id3v1Choice.NONE));
        assertThat(frameText(w.rawV2().frame("TCON").orElseThrow()).replace("\0", "<NUL>")).isEqualTo("Rock; Pop");
    }

    /** Small adapter around the reader result so that tests read like the spec. */
    private static final class RoundTripSupportV2 {
        static String title(Written w) {
            return dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.v2(w.read())
                    .display(Field.TITLE).orElse("");
        }
    }
}
