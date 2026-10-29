package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Behaviour that is specific to how jaudiotagger parses files; the shared contract lives in the base class. */
class JaudiotaggerReaderSpecificsTest {

    @TempDir
    Path tempDir;

    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    @Test
    void id3v10AndId3v11AreToldApartByTheTrackByte() throws Exception {
        assertThat(reader.read(Fixtures.copyTo(Fixture.V1_0, tempDir)).versions()).containsExactly(TagVersion.ID3V1_0);
        assertThat(reader.read(Fixtures.copyTo(Fixture.V1_1, tempDir)).versions()).containsExactly(TagVersion.ID3V1_1);
    }

    @Test
    void id3v24DateBecomesTheYear() throws Exception {
        TagReadResult result = reader.read(Fixtures.copyTo(Fixture.V24_UTF8, tempDir));
        assertThat(result.effective().display(Field.YEAR)).contains("2020");
    }

    @Test
    void id3v22FramesAreRead() throws Exception {
        TagReadResult result = reader.read(Fixtures.copyTo(Fixture.V22, tempDir));
        assertThat(result.versions()).containsExactly(TagVersion.ID3V2_2);
        assertThat(result.effective().display(Field.TRACK)).contains("4/9");
    }

    @Test
    void multipleV24GenresKeepTheirOrder() throws Exception {
        TagReadResult result = reader.read(Fixtures.copyTo(Fixture.V24_UTF8, tempDir));
        assertThat(result.effective().display(Field.GENRE)).contains("Rock; Pop");
    }

    @Test
    void coverIsDescribedByTypeAndMime() throws Exception {
        assertThat(reader.read(Fixtures.copyTo(Fixture.V23_COVER, tempDir)).cover()).hasValueSatisfying(cover -> {
            assertThat(cover.type()).isEqualTo(PictureType.FRONT_COVER);
            assertThat(cover.mimeType()).isEqualTo("image/jpeg");
        });
        assertThat(reader.read(Fixtures.copyTo(Fixture.V23_OTHER_PICTURE, tempDir)).cover())
                .hasValueSatisfying(cover -> assertThat(cover.type()).isEqualTo(PictureType.OTHER));
    }

    @Test
    void theFileIsNotHeldOpenAfterReading() throws Exception {
        Path fdDir = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(fdDir), "needs /proc to count open file descriptors");
        Path file = Fixtures.copyTo(Fixture.V23_COVER, tempDir);
        reader.read(file); // warm up class loading
        long before = countOpenDescriptors(fdDir);
        for (int i = 0; i < 200; i++) {
            reader.read(file);
        }
        assertThat(countOpenDescriptors(fdDir)).isLessThanOrEqualTo(before + 2);
        Files.delete(file); // also the proof on Windows, where an open file cannot be deleted
        assertThat(file).doesNotExist();
    }

    private static long countOpenDescriptors(Path fdDir) throws IOException {
        try (Stream<Path> entries = Files.list(fdDir)) {
            return entries.count();
        }
    }

    @Test
    void anUnreadableId3v2TagIsReportedAsDamagedWhileTheAudioStaysReadable() throws Exception {
        // an "ID3" header that announces a tag of 1 byte of garbage in front of valid audio
        byte[] audio = Fixtures.bytes(Fixture.NO_TAGS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {'I', 'D', '3', 9, 0, 0, 0, 0, 0, 1, 0x7F});
        out.write(audio);
        Path file = tempDir.resolve("damaged-tag.mp3");
        Files.write(file, out.toByteArray());

        TagReadResult result = reader.read(file);
        assertThat(result.layers()).isEmpty();
        assertThat(result.stream()).isPresent();
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.TAG_DAMAGED);
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test
    void libraryExceptionsNeverEscape() {
        Path garbage = tempDir.resolve("garbage.mp3");
        assertThatThrownBy(() -> {
            Files.write(garbage, new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00, 1, 2, 3});
            reader.read(garbage);
        }).isInstanceOf(MediaAccessException.class);
    }

    /** Hand-built ID3v2.3 tag with one TCON frame (Latin-1) in front of the fixture audio. */
    private Path v23WithGenre(String genreText) throws IOException {
        byte[] text = genreText.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int frameLen = 1 + text.length;
        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        tag.writeBytes("TCON".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        tag.writeBytes(new byte[] {0, 0, (byte) (frameLen >> 8), (byte) frameLen, 0, 0, 0});
        tag.writeBytes(text);
        byte[] body = tag.toByteArray();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, (byte) (body.length >> 7), (byte) (body.length & 0x7F)});
        file.writeBytes(body);
        file.writeBytes(Fixtures.bytes(Fixture.NO_TAGS));
        Path path = tempDir.resolve("genre-" + Math.abs(genreText.hashCode()) + ".mp3");
        Files.write(path, file.toByteArray());
        return path;
    }

    @Test
    void semicolonJoinedGenresOfV23BecomeSeparateGenres() throws Exception {
        TagReadResult result = reader.read(v23WithGenre("Rock; Pop"));
        assertThat(result.effective().get(Field.GENRE).orElseThrow()).isEqualTo(
                new dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres(java.util.List.of(
                        new dev.rs.slava.icxc.musicstudio.core.model.Genre("Rock"),
                        new dev.rs.slava.icxc.musicstudio.core.model.Genre("Pop"))));
    }

    @Test
    void semicolonWithoutSpacesAndDuplicatesAreHandled() throws Exception {
        TagReadResult result = reader.read(v23WithGenre("Rock;Pop; rock ;Jazz"));
        assertThat(result.effective().display(Field.GENRE)).contains("Rock; Pop; Jazz");
    }

    @Test
    void trailingSemicolonDoesNotCreateAnEmptyGenre() throws Exception {
        TagReadResult result = reader.read(v23WithGenre("Rock;"));
        assertThat(result.effective().display(Field.GENRE)).contains("Rock");
    }
}
