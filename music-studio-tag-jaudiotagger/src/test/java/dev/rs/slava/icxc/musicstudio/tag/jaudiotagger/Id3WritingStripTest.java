package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The manual ID3v2 removal must never turn a file into something shorter than the audio it keeps. */
class Id3WritingStripTest {

    @TempDir
    Path dir;

    private static byte[] id3Header(int size) {
        return new byte[] {'I', 'D', '3', 3, 0, 0, (byte) ((size >> 21) & 0x7F), (byte) ((size >> 14) & 0x7F),
                (byte) ((size >> 7) & 0x7F), (byte) (size & 0x7F)};
    }

    @Test
    void removesTheTagAndKeepsTheAudioBytes() throws IOException {
        byte[] audio = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] tag = Arrays.copyOf(id3Header(20), 30);
        byte[] bytes = new byte[tag.length + audio.length];
        System.arraycopy(tag, 0, bytes, 0, tag.length);
        System.arraycopy(audio, 0, bytes, tag.length, audio.length);
        Path file = Files.write(dir.resolve("a.mp3"), bytes);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            Id3Writing.stripV2(raf);
        }
        assertThat(Files.readAllBytes(file)).isEqualTo(audio);
    }

    @Test
    void aHeaderThatDeclaresMoreThanTheFileHasIsAnErrorAndLeavesTheFileAlone() throws IOException {
        byte[] bytes = Arrays.copyOf(id3Header(100_000), 64);
        Path file = Files.write(dir.resolve("b.mp3"), bytes);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            assertThatThrownBy(() -> Id3Writing.stripV2(raf)).isInstanceOf(IOException.class);
        }
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
    }
}
