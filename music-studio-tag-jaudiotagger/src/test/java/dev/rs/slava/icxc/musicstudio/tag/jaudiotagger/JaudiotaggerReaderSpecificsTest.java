package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behaviour specific to the jaudiotagger adapter, beyond the shared contract. */
class JaudiotaggerReaderSpecificsTest {

    @TempDir
    Path dir;

    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    @Test
    void libraryLoggingIsSilenced() {
        assertThat(Logger.getLogger("org.jaudiotagger").getLevel()).isEqualTo(Level.OFF);
    }

    @Test
    void readCoverReturnsTheEmbeddedJpeg() throws Exception {
        Optional<CoverArt> cover = reader.readCover(Fixtures.copyTo(Fixture.V23_COVER, dir));
        assertThat(cover).hasValueSatisfying(art -> {
            assertThat(art.type()).isEqualTo(PictureType.FRONT_COVER);
            assertThat(art.mimeType()).isEqualTo("image/jpeg");
            assertThat(art.sizeBytes()).isEqualTo(FixtureCatalog.COVER_JPEG_BYTES);
            assertThat(art.data()).startsWith((byte) 0xFF, (byte) 0xD8); // JPEG SOI marker
        });
        assertThat(reader.readCover(Fixtures.copyTo(Fixture.V23_OTHER_PICTURE, dir)))
                .hasValueSatisfying(art -> assertThat(art.type()).isEqualTo(PictureType.OTHER));
        assertThat(reader.readCover(Fixtures.copyTo(Fixture.V23_LATIN1, dir))).isEmpty();
        assertThat(reader.readCover(Fixtures.copyTo(Fixture.NO_TAGS, dir))).isEmpty();
    }

    @Test
    void libraryExceptionsNeverEscape() throws Exception {
        Path truncated = dir.resolve("truncated.mp3");
        byte[] full = Fixtures.bytes(Fixture.V23_COVER);
        Files.write(truncated, Arrays.copyOf(full, 60)); // tag header claims more bytes than exist
        try {
            reader.read(truncated);
        } catch (MediaAccessException expected) {
            assertThat(expected.getCause()).isNotNull();
        }
        Path directory = Files.createDirectory(dir.resolve("folder.mp3"));
        assertThatThrownBy(() -> reader.read(directory)).isInstanceOf(MediaAccessException.class);
    }

    @Test
    void readCoverOfCorruptFileIsCorrupt() {
        Path corrupt = Fixtures.copyTo(Fixture.CORRUPT, dir);
        assertThatThrownBy(() -> reader.readCover(corrupt)).isInstanceOfSatisfying(MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT));
    }
}
