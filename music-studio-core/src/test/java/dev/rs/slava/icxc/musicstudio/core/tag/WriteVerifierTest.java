package dev.rs.slava.icxc.musicstudio.core.tag;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.layer;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.read;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.allFields;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Verification after the write (AC6): the re-read file must equal the plan. */
class WriteVerifierTest {

    private static final TagWriteOptions V23_ONLY = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE,
            V2Encoding.AUTO, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false);

    private static java.util.List<Issue> verify(dev.rs.slava.icxc.musicstudio.core.model.TagReadResult actual,
            TagWriteOptions options, MediaMetadata expected, Optional<Id3v1Values> v1) {
        return WriteVerifier.verify(actual, options, expected, allFields(), v1);
    }

    @Test
    void matchingTagsGiveNoIssues() {
        MediaMetadata expected = meta(Field.TITLE, "T");
        assertThat(verify(read(layer(TagVersion.ID3V2_3, expected)), V23_ONLY, expected, Optional.empty())).isEmpty();
    }

    @Test
    void wrongValueNamesTheField() {
        var issues = verify(read(layer(TagVersion.ID3V2_3, meta(Field.TITLE, "Wrong"))), V23_ONLY,
                meta(Field.TITLE, "T"), Optional.empty());
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.VERIFY_MISMATCH);
            assertThat(i.field()).contains(Field.TITLE);
        });
    }

    @Test
    void missingOrWrongVersionIsAMismatch() {
        MediaMetadata m = meta(Field.TITLE, "T");
        assertThat(verify(read(layer(TagVersion.ID3V2_4, m)), V23_ONLY, m, Optional.empty())).hasSize(1);
        assertThat(verify(read(), V23_ONLY, m, Optional.empty())).hasSize(1);
    }

    @Test
    void unexpectedExtraValueIsAMismatch() {
        var issues = verify(read(layer(TagVersion.ID3V2_3, meta(Field.TITLE, "T", Field.ALBUM, "Extra"))),
                V23_ONLY, meta(Field.TITLE, "T"), Optional.empty());
        assertThat(issues).singleElement().satisfies(i -> assertThat(i.field()).contains(Field.ALBUM));
    }

    @Test
    void v1IsComparedByteWise() {
        var v1 = new Id3v1Values(Id3v1Choice.V1_1, V1Encoding.LATIN1, "Müller", "", "", OptionalInt.of(2001), "", 5,
                255);
        TagWriteOptions both = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.LATIN1, V1NonLatinPolicy.SKIP_V1, true);
        var good = read(layer(TagVersion.ID3V1_1, meta(Field.TITLE, "Müller", Field.YEAR,
                dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.year(2001), Field.TRACK,
                dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position(5, 0))));
        assertThat(verify(good, both, MediaMetadata.empty(), Optional.of(v1))).isEmpty();
        var bad = read(layer(TagVersion.ID3V1_1, meta(Field.TITLE, "Mueller", Field.YEAR,
                dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.year(2001), Field.TRACK,
                dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position(5, 0))));
        assertThat(verify(bad, both, MediaMetadata.empty(), Optional.of(v1))).isNotEmpty();
    }

    @Test
    void codepageV1ValueIsComparedAsRawBytes() {
        var v1 = new Id3v1Values(Id3v1Choice.V1_1, V1Encoding.WINDOWS_1253, "Ελευθερία", "", "", OptionalInt.empty(),
                "", 1, 255);
        TagWriteOptions o = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.CODEPAGE, true);
        String asRead = new String("Ελευθερία".getBytes(V1Encoding.WINDOWS_1253.charset()),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        var read = read(layer(TagVersion.ID3V1_1, meta(Field.TITLE, asRead, Field.TRACK,
                dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position(1, 0))));
        assertThat(verify(read, o, MediaMetadata.empty(), Optional.of(v1))).isEmpty();
    }

    @Test
    void leftoverTagsOfAFamilySetToNoneWithRemoveAreMismatches() {
        TagWriteOptions strip = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, true);
        assertThat(verify(read(layer(TagVersion.ID3V2_3, meta(Field.TITLE, "T"))), strip, MediaMetadata.empty(),
                Optional.empty())).isNotEmpty();
        assertThat(verify(read(), strip, MediaMetadata.empty(), Optional.empty())).isEmpty();
    }
}
