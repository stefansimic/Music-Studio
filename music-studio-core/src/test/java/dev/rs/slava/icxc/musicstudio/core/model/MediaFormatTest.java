package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MediaFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"a.MP3", "a.mp3", ".mp3", "dir.x.Mp3", "Ђорђе – 🎵.mp3"})
    void recognisesMp3(String name) {
        assertThat(MediaFormat.ofFileName(name)).contains(MediaFormat.MP3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.mp3.txt", "a", "", "mp3", "a.", "a.flac"})
    void ignoresOthers(String name) {
        assertThat(MediaFormat.ofFileName(name)).isEmpty();
    }

    @Test
    void nullRejected() {
        assertThatThrownBy(() -> MediaFormat.ofFileName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mp3HasId3Families() {
        assertThat(MediaFormat.MP3.tagFamilies()).containsExactlyInAnyOrder(TagFamily.ID3V1, TagFamily.ID3V2);
        assertThat(MediaFormat.MP3.extensions()).containsExactly("mp3");
    }
}
