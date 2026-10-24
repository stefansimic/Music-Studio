package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MediaFormatTest {

    @Test
    void detectsMp3CaseInsensitively() {
        assertThat(MediaFormat.ofFileName("a.MP3")).contains(MediaFormat.MP3);
        assertThat(MediaFormat.ofFileName("a.mp3")).contains(MediaFormat.MP3);
        assertThat(MediaFormat.ofFileName("a.Mp3")).contains(MediaFormat.MP3);
    }

    @Test
    void hiddenFileNamedLikeTheExtensionIsStillMp3() {
        assertThat(MediaFormat.ofFileName(".mp3")).contains(MediaFormat.MP3);
    }

    @Test
    void otherNamesAreUnknown() {
        assertThat(MediaFormat.ofFileName("a.mp3.txt")).isEmpty();
        assertThat(MediaFormat.ofFileName("a")).isEmpty();
        assertThat(MediaFormat.ofFileName("a.")).isEmpty();
        assertThat(MediaFormat.ofFileName("mp3")).isEmpty();
    }

    @Test
    void extensionOf() {
        assertThat(MediaFormat.extensionOf("Cover.JPG")).contains("jpg");
        assertThat(MediaFormat.extensionOf("README")).isEmpty();
        assertThat(MediaFormat.extensionOf("a.")).isEmpty();
    }

    @Test
    void tagFamilies() {
        assertThat(MediaFormat.MP3.tagFamilies()).containsExactlyInAnyOrder(TagFamily.ID3V2, TagFamily.ID3V1);
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> MediaFormat.ofFileName(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MediaFormat.extensionOf(null)).isInstanceOf(NullPointerException.class);
    }
}
