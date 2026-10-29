package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** E06, E20-E25, E27, E30: Greek and Serbian Cyrillic stay in their script in v2; v1 follows the policy. */
class JaudiotaggerScriptsTest extends WriterTestSupport {

    private static final Charset W1253 = Charset.forName("windows-1253");
    private static final Charset W1251 = Charset.forName("windows-1251");
    private static final String ARTIST_EL = "Μίκης Θεοδωράκης";
    private static final String TITLE_EL = "Ελευθερία Αρβανιτάκη";
    private static final String ARTIST_SR = "Ђорђе Балашевић";

    private static TagWriteOptions o(Id3v2Choice v2, V2Encoding enc, V1NonLatinPolicy policy) {
        return new TagWriteOptions(v2, Id3v1Choice.V1_1, enc, V1TextMode.ASCII, policy, false);
    }

    private static MediaMetadata greek() {
        return meta(Field.TITLE, TITLE_EL, Field.ARTIST, ARTIST_EL, Field.ALBUM, "Νίκος Βέρτης",
                Field.TRACK, position(3, 10));
    }

    // ---- E20 / E21

    @Test
    void greekWithDefaultPolicyHasV2WithTheExactTextAndNoV1() {
        Written w = write(Fixture.NO_TAGS, greek(), TagWriteOptions.DEFAULT);
        assertThat(w.read().versions()).containsExactly(TagVersion.ID3V2_3);
        assertThat(RoundTripSupport.v2(w.read()).display(Field.ARTIST)).contains(ARTIST_EL);
        assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains(TITLE_EL);
        assertThat(Id3Raw.v1(w.bytes())).isEmpty();
        assertThat(w.rawV2().frame("TPE1").orElseThrow().encodingByte()).isEqualTo(1);
        assertThat(new String(w.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Mikis");
    }

    @ParameterizedTest
    @EnumSource(value = Id3v2Choice.class, names = {"V2_3", "V2_4"})
    void serbianCyrillicAndLatinDiacriticsSurviveInUtf16AndUtf8(Id3v2Choice v2) {
        MediaMetadata m = meta(Field.TITLE, "ЉЊЏЂЋЈ љњџђћј", Field.ARTIST, ARTIST_SR, Field.ALBUM, "čćšžđ ČĆŠŽĐ");
        Written w = write(Fixture.NO_TAGS, m, o(v2, V2Encoding.AUTO, V1NonLatinPolicy.SKIP_V1));
        assertThat(RoundTripSupport.v2(w.read())).isEqualTo(m);
        assertThat(Id3Raw.v1(w.bytes())).isEmpty();
        assertThat(w.rawV2().frame("TPE1").orElseThrow().encodingByte()).isEqualTo(v2 == Id3v2Choice.V2_3 ? 1 : 3);
    }

    // ---- E22 / E23

    @Test
    void greekCodepageWritesWindows1253BytesAndKeepsV2() {
        Written w = write(Fixture.NO_TAGS, greek(), o(Id3v2Choice.V2_3, V2Encoding.AUTO, V1NonLatinPolicy.CODEPAGE));
        byte[] v1 = w.rawV1();
        assertThat(Id3Raw.v1Artist(v1, W1253)).isEqualTo(ARTIST_EL);
        assertThat(Id3Raw.v1Title(v1, W1253)).isEqualTo(TITLE_EL);
        assertThat(Id3Raw.v1Album(v1, W1253)).isEqualTo("Νίκος Βέρτης");
        assertThat(v1).doesNotContain((byte) '?');
        assertThat(RoundTripSupport.v2(w.read()).display(Field.ARTIST)).contains(ARTIST_EL);
        assertThat(Id3v1Projection.project(greek(), w.request().options()).issues()).isNotNull();
    }

    @Test
    void greekCodepageProjectionNamesTheCodepage() {
        TagWriteOptions options = o(Id3v2Choice.V2_3, V2Encoding.AUTO, V1NonLatinPolicy.CODEPAGE);
        assertThat(Id3v1Projection.project(greek(), options).issues()).anyMatch(
                i -> i.code() == IssueCode.V1_LEGACY_CODEPAGE && i.message().contains("1253"));
    }

    @Test
    void cyrillicCodepageWritesWindows1251BytesFoldsMissingLettersAndNeverQuestionMarks() {
        MediaMetadata m = meta(Field.TITLE, "Ђорђе čćšžđ", Field.ARTIST, ARTIST_SR, Field.TRACK, position(1, 0));
        Written w = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_3, V2Encoding.AUTO, V1NonLatinPolicy.CODEPAGE));
        byte[] v1 = w.rawV1();
        assertThat(Id3Raw.v1Artist(v1, W1251)).isEqualTo(ARTIST_SR);
        assertThat(Id3Raw.v1Title(v1, W1251)).isEqualTo("Ђорђе ccszdj");
        assertThat(v1).doesNotContain((byte) '?');
        assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains("Ђорђе čćšžđ");
    }

    // ---- E06 / E25

    @Test
    void cyrillicFixtureWithTransliteratePolicyGivesGajAsciiInV1AndKeepsCyrillicInV2() {
        MediaMetadata current = currentV2(Fixture.V23_UTF16_CYRILLIC);
        Written w = write(Fixture.V23_UTF16_CYRILLIC, current.with(Field.TRACK, position(2, 0)),
                o(Id3v2Choice.V2_3, V2Encoding.AUTO, V1NonLatinPolicy.TRANSLITERATE));
        byte[] v1 = w.rawV1();
        assertThat(Id3Raw.v1Artist(v1, StandardCharsets.ISO_8859_1)).isEqualTo("Djordje Balasevic");
        assertThat(Id3Raw.v1Title(v1, StandardCharsets.ISO_8859_1)).isEqualTo("ccszdj");
        assertThat(v1).doesNotContain((byte) '?');
        assertThat(RoundTripSupport.v2(w.read()).display(Field.ARTIST)).contains(ARTIST_SR);
        assertThat(RoundTripSupport.v2(w.read()).display(Field.ALBUM)).contains("Музика 🎵");
        assertThat(Id3v1Projection.project(current, w.request().options()).issues()).extracting(i -> i.code())
                .doesNotContain(IssueCode.V1_SKIPPED_NON_LATIN);
    }

    @Test
    void greekTransliterateGivesLatinV1ForAllFields() {
        Written w = write(Fixture.NO_TAGS, greek(),
                o(Id3v2Choice.V2_3, V2Encoding.AUTO, V1NonLatinPolicy.TRANSLITERATE));
        byte[] v1 = w.rawV1();
        assertThat(Id3Raw.v1Artist(v1, StandardCharsets.ISO_8859_1)).isEqualTo("Mikis Theodorakis");
        assertThat(Id3Raw.v1Title(v1, StandardCharsets.ISO_8859_1)).isEqualTo("Eleftheria Arvanitaki");
        assertThat(Id3Raw.v1Album(v1, StandardCharsets.ISO_8859_1)).isEqualTo("Nikos Vertis");
        assertThat(RoundTripSupport.v2(w.read()).display(Field.ARTIST)).contains(ARTIST_EL);
    }

    // ---- E27

    @Test
    void emojiAndCjkNeverEndUpAsQuestionMarksInV1() {
        MediaMetadata m = meta(Field.TITLE, "Song 🎵 日本", Field.TRACK, position(1, 0));
        Written skip = write(Fixture.NO_TAGS, m, TagWriteOptions.DEFAULT);
        assertThat(Id3Raw.v1(skip.bytes())).isEmpty();
        assertThat(RoundTripSupport.v2(skip.read()).display(Field.TITLE)).contains("Song 🎵 日本");
        Written translit = write(Fixture.NO_TAGS, m, o(Id3v2Choice.V2_3, V2Encoding.AUTO,
                V1NonLatinPolicy.TRANSLITERATE));
        assertThat(Id3Raw.v1Title(translit.rawV1(), StandardCharsets.ISO_8859_1)).isEqualTo("Song");
        assertThat(translit.rawV1()).doesNotContain((byte) '?');
    }

    // ---- E28

    @ParameterizedTest
    @EnumSource(value = Id3v2Choice.class, names = {"V2_2", "V2_3", "V2_4"})
    void autoKeepsGreekAndCyrillicInUnicodeEncodings(Id3v2Choice v2) {
        MediaMetadata m = meta(Field.TITLE, TITLE_EL, Field.ARTIST, ARTIST_SR);
        Written w = write(Fixture.NO_TAGS, m, new TagWriteOptions(v2, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false));
        int expected = v2 == Id3v2Choice.V2_4 ? 3 : 1;
        assertThat(w.rawV2().frames()).allSatisfy(f -> assertThat(f.encodingByte()).isEqualTo(expected));
        assertThat(RoundTripSupport.v2(w.read())).isEqualTo(m);
    }

    // ---- E30

    @ParameterizedTest
    @EnumSource(value = V1NonLatinPolicy.class)
    void greekFixtureWrittenInEveryPolicyKeepsTheV2ValuesIdentical(V1NonLatinPolicy policy) {
        MediaMetadata current = currentV2(Fixture.V23_UTF16_GREEK);
        for (TagWriteOptions options : List.of(o(Id3v2Choice.V2_3, V2Encoding.AUTO, policy),
                o(Id3v2Choice.V2_3, V2Encoding.UTF16, policy), o(Id3v2Choice.V2_4, V2Encoding.UTF8, policy),
                o(Id3v2Choice.V2_2, V2Encoding.AUTO, policy))) {
            Written w = write(Fixture.V23_UTF16_GREEK, current, options);
            assertThat(RoundTripSupport.v2(w.read())).as("%s", options).isEqualTo(current);
            assertThat(w.read().layer(TagFamily.ID3V1).isPresent()).as("v1 under %s", policy)
                    .isEqualTo(policy != V1NonLatinPolicy.SKIP_V1);
        }
    }

    @ParameterizedTest
    @EnumSource(value = V1NonLatinPolicy.class)
    void greekAndCyrillicFixtureKeepsV2AndSkipsV1ForMixedScripts(V1NonLatinPolicy policy) {
        MediaMetadata current = currentV2(Fixture.V24_UTF8_GREEK_CYRILLIC);
        for (Id3v2Choice v2 : List.of(Id3v2Choice.V2_3, Id3v2Choice.V2_4)) {
            Written w = write(Fixture.V24_UTF8_GREEK_CYRILLIC, current, o(v2, V2Encoding.AUTO, policy));
            assertThat(RoundTripSupport.v2(w.read())).isEqualTo(current);
            boolean v1Expected = policy == V1NonLatinPolicy.TRANSLITERATE;
            assertThat(w.read().layer(TagFamily.ID3V1).isPresent()).as("%s %s", policy, v2).isEqualTo(v1Expected);
        }
    }
}
