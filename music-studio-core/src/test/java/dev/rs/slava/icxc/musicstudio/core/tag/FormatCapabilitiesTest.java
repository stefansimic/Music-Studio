package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagWriter;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T23 (pure part). */
class FormatCapabilitiesTest {

    @Test
    void fullCapabilitiesAcceptEveryValidOption() {
        var caps = FakeTagWriter.fullCapabilities();
        assertThat(caps.unsupported(TagWriteOptions.DEFAULT)).isEmpty();
        for (Id3v2Choice v2 : Id3v2Choice.values()) {
            for (Id3v1Choice v1 : Id3v1Choice.values()) {
                if (TagWriteOptions.problems(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, true).isEmpty()) {
                    assertThat(caps.unsupported(new TagWriteOptions(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII,
                            V1NonLatinPolicy.SKIP_V1, true))).isEmpty();
                }
            }
        }
    }

    @Test
    void missingVersionIsReportedAsUnsupportedWriteVersion() {
        var caps = FakeTagWriter.without(TagVersion.ID3V2_2);
        var issues = caps.unsupported(new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false));
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.UNSUPPORTED_WRITE_VERSION);
            assertThat(i.severity()).isEqualTo(Severity.ERROR);
            assertThat(i.message()).contains("ID3v2.2");
        });
    }

    @Test
    void everyMissingVersionIsReported() {
        var caps = FakeTagWriter.without(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        assertThat(caps.unsupported(TagWriteOptions.DEFAULT)).hasSize(2);
    }

    @Test
    void unsupportedEncodingUsesTheSameCode() {
        var caps = new FormatCapabilities(MediaFormat.MP3, EnumSet.allOf(TagVersion.class),
                EnumSet.of(V2Encoding.LATIN1, V2Encoding.UTF16), EnumSet.allOf(Field.class), false);
        var issues = caps.unsupported(new TagWriteOptions(Id3v2Choice.V2_4, Id3v1Choice.NONE, V2Encoding.UTF8,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false));
        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo(IssueCode.UNSUPPORTED_WRITE_VERSION);
            assertThat(i.message()).contains("UTF8");
        });
    }

    @Test
    void autoEncodingIsAlwaysAccepted() {
        var caps = new FormatCapabilities(MediaFormat.MP3, EnumSet.allOf(TagVersion.class),
                EnumSet.of(V2Encoding.LATIN1), EnumSet.allOf(Field.class), false);
        assertThat(caps.unsupported(TagWriteOptions.DEFAULT)).isEmpty();
    }

    @Test
    void emptySetsAreRejectedAndSetsAreCopied() {
        assertThatThrownBy(() -> new FormatCapabilities(MediaFormat.MP3, Set.of(), EnumSet.allOf(V2Encoding.class),
                EnumSet.allOf(Field.class), false)).isInstanceOf(IllegalArgumentException.class);
        EnumSet<TagVersion> versions = EnumSet.of(TagVersion.ID3V2_3);
        var caps = new FormatCapabilities(MediaFormat.MP3, versions, EnumSet.allOf(V2Encoding.class),
                EnumSet.allOf(Field.class), false);
        versions.add(TagVersion.ID3V2_4);
        assertThat(caps.writableVersions()).containsExactly(TagVersion.ID3V2_3);
        assertThatThrownBy(() -> caps.unsupported(null)).isInstanceOf(NullPointerException.class);
    }
}
