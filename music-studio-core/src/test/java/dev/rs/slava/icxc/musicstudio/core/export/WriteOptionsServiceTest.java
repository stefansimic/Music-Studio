package dev.rs.slava.icxc.musicstudio.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagWriter;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WriteOptionsServiceTest {

    private final FakeTagWriter writer = new FakeTagWriter(new FakeTagReader(),
            EnumSet.of(TagVersion.ID3V1_1, TagVersion.ID3V2_3));
    private final WriteOptionsService service = new WriteOptionsService(writer);

    @Test
    void passesCapabilitiesThrough() {
        assertThat(service.writableFormats()).containsExactly(MediaFormat.MP3);
        assertThat(service.capabilities(MediaFormat.MP3)).isEqualTo(writer.capabilities(MediaFormat.MP3));
    }

    @Test
    void validateReportsUnsupportedVersions() {
        TagWriteOptions v24 = new TagWriteOptions(Id3v2Choice.V2_4, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false);
        assertThat(service.validate(v24, Set.of(MediaFormat.MP3))).extracting(Issue::code)
                .containsExactly(IssueCode.UNSUPPORTED_WRITE_VERSION);
        assertThat(service.validate(TagWriteOptions.DEFAULT, Set.of(MediaFormat.MP3))).isEmpty();
    }

    @Test
    void emptyFormatsAreRejected() {
        assertThatThrownBy(() -> service.validate(TagWriteOptions.DEFAULT, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WriteOptionsService(null)).isInstanceOf(NullPointerException.class);
    }
}
