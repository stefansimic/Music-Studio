package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagWriteOptionsTest {

    @Test
    void defaults() {
        TagWriteOptions options = TagWriteOptions.DEFAULT;
        assertThat(options.id3v2()).isEqualTo(Id3v2Choice.V2_3);
        assertThat(options.id3v1()).isEqualTo(Id3v1Choice.V1_1);
        assertThat(options.v2Encoding()).isEqualTo(V2Encoding.AUTO);
        assertThat(options.v1TextMode()).isEqualTo(V1TextMode.ASCII);
        assertThat(options.removeOtherVersions()).isFalse();
        assertThat(options.selectedVersions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
        assertThat(new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII, false)
                .selectedVersions()).containsExactly(TagVersion.ID3V1_1);
    }

    /** The full cross product against a hand-written predicate. */
    @Test
    void crossProduct() {
        int valid = 0;
        for (Id3v2Choice v2 : Id3v2Choice.values()) {
            for (Id3v1Choice v1 : Id3v1Choice.values()) {
                for (V2Encoding enc : V2Encoding.values()) {
                    for (V1TextMode mode : V1TextMode.values()) {
                        for (boolean remove : new boolean[] {false, true}) {
                            boolean expected = !(enc == V2Encoding.UTF8 && v2 != Id3v2Choice.V2_4)
                                    && !(enc != V2Encoding.AUTO && v2 == Id3v2Choice.NONE)
                                    && !(v2 == Id3v2Choice.NONE && v1 == Id3v1Choice.NONE && !remove);
                            List<String> problems = TagWriteOptions.problems(v2, v1, enc, mode, remove);
                            assertThat(problems.isEmpty()).as("%s %s %s %s %s", v2, v1, enc, mode, remove)
                                    .isEqualTo(expected);
                            if (expected) {
                                new TagWriteOptions(v2, v1, enc, mode, remove);
                                valid++;
                            } else {
                                assertThat(problems).allMatch(p -> !p.isBlank());
                                assertThatThrownBy(() -> new TagWriteOptions(v2, v1, enc, mode, remove))
                                        .isInstanceOf(IllegalArgumentException.class);
                            }
                        }
                    }
                }
            }
        }
        assertThat(valid).isPositive();
    }

    @Test
    void problemMessages() {
        assertThat(TagWriteOptions.problems(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.UTF8, V1TextMode.ASCII,
                false)).singleElement().asString().contains("UTF-8").contains("ID3v2.4");
        assertThat(TagWriteOptions.problems(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.UTF16, V1TextMode.ASCII,
                false)).singleElement().asString().contains("needs an ID3v2");
        assertThat(TagWriteOptions.problems(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII,
                false)).singleElement().asString().contains("remove other versions");
        assertThat(TagWriteOptions.problems(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII,
                true)).isEmpty();
    }

    @Test
    void unsupportedVersionsAndEncodings() {
        FormatCapabilities caps = new FormatCapabilities(MediaFormat.MP3,
                EnumSet.of(TagVersion.ID3V2_3, TagVersion.ID3V2_4, TagVersion.ID3V1_1),
                EnumSet.of(V2Encoding.AUTO, V2Encoding.UTF16), EnumSet.allOf(Field.class), false);
        assertThat(caps.unsupported(TagWriteOptions.DEFAULT)).isEmpty();
        assertThat(caps.unsupported(new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, false))).singleElement().satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo(IssueCode.UNSUPPORTED_WRITE_VERSION);
                    assertThat(issue.message()).contains("ID3v2.2");
                });
        assertThat(caps.unsupported(new TagWriteOptions(Id3v2Choice.V2_4, Id3v1Choice.V1_0, V2Encoding.UTF8,
                V1TextMode.ASCII, false))).hasSize(2);
        assertThat(caps.unsupported(new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false))).isEmpty();
    }

    @Test
    void capabilitiesInvariants() {
        assertThatThrownBy(() -> new FormatCapabilities(MediaFormat.MP3, Set.of(), Set.of(V2Encoding.AUTO),
                Set.of(Field.TITLE), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormatCapabilities(MediaFormat.MP3, Set.of(TagVersion.MP4_ILST),
                Set.of(V2Encoding.AUTO), Set.of(Field.TITLE), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormatCapabilities(MediaFormat.MP3, Set.of(TagVersion.ID3V2_3),
                Set.of(V2Encoding.UTF16), Set.of(Field.TITLE), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormatCapabilities(MediaFormat.MP3, Set.of(TagVersion.ID3V2_3),
                Set.of(V2Encoding.AUTO), Set.of(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Id3v2Choice.NONE.version()).isEmpty();
        assertThat(Id3v1Choice.V1_0.version()).contains(TagVersion.ID3V1_0);
        assertThat(V1TextMode.ASCII.asciiOnly()).isTrue();
    }
}
