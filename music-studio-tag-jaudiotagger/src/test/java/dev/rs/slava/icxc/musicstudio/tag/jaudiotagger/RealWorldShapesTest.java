package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-world MP3 shapes the committed fixtures do not cover. The tags are built byte by byte here (independent of
 * jaudiotagger's writer) in front of the audio of {@code no-tags.mp3}, which starts with a Xing/Info header.
 */
class RealWorldShapesTest {

    @TempDir
    Path tempDir;

    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    // ---- tiny ID3 writer ------------------------------------------------------------------------------------------

    private static byte[] syncsafe(int n) {
        return new byte[] {(byte) (n >> 21 & 0x7F), (byte) (n >> 14 & 0x7F), (byte) (n >> 7 & 0x7F), (byte) (n & 0x7F)};
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] text(int encoding, Charset charset, String value) {
        return join(new byte[] {(byte) encoding}, value.getBytes(charset));
    }

    private static byte[] utf8(String value) {
        return text(3, StandardCharsets.UTF_8, value);
    }

    private static byte[] latin1(String value) {
        return text(0, StandardCharsets.ISO_8859_1, value);
    }

    private static byte[] frame(int major, String id, byte[] body) {
        byte[] size = major == 4 ? syncsafe(body.length)
                : new byte[] {(byte) (body.length >> 24), (byte) (body.length >> 16), (byte) (body.length >> 8),
                        (byte) body.length};
        return join(id.getBytes(StandardCharsets.ISO_8859_1), size, new byte[] {0, 0}, body);
    }

    private static byte[] tag(int major, int flags, byte[] frames, int padding, boolean footer) {
        byte[] header = join("ID3".getBytes(StandardCharsets.ISO_8859_1), new byte[] {(byte) major, 0, (byte) flags},
                syncsafe(frames.length + padding));
        byte[] foot = footer ? join("3DI".getBytes(StandardCharsets.ISO_8859_1), new byte[] {(byte) major, 0, (byte) flags},
                syncsafe(frames.length + padding)) : new byte[0];
        return join(header, frames, new byte[padding], foot);
    }

    /** Inserts 0x00 after every 0xFF that is followed by 0xE0+ or 0x00 (ID3v2 unsynchronisation of the whole tag). */
    private static byte[] unsynchronise(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            out.write(data[i]);
            if (data[i] == (byte) 0xFF && (i + 1 == data.length || (data[i + 1] & 0xE0) == 0xE0 || data[i + 1] == 0)) {
                out.write(0);
            }
        }
        return out.toByteArray();
    }

    private Path mp3(String name, byte[]... prefix) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, join(join(prefix), Fixtures.bytes(Fixture.NO_TAGS)));
        return file;
    }

    private TagReadResult read(String name, byte[] tag) throws Exception {
        return reader.read(mp3(name, tag));
    }

    private static byte[] v1Tag(String title, String artist, int track, int genre) {
        byte[] b = new byte[128];
        b[0] = 'T';
        b[1] = 'A';
        b[2] = 'G';
        System.arraycopy(title.getBytes(StandardCharsets.ISO_8859_1), 0, b, 3, title.length());
        System.arraycopy(artist.getBytes(StandardCharsets.ISO_8859_1), 0, b, 33, artist.length());
        if (track > 0) {
            b[125] = 0;
            b[126] = (byte) track;
        }
        b[127] = (byte) genre;
        return b;
    }

    // ---- tests ------------------------------------------------------------------------------------------------------

    @Test
    void multiValueV24ArtistIsReadWithoutNulCharacters() throws Exception {
        byte[] f = frame(4, "TPE1", utf8("Ђорђе\u0000Marko\u0000Đ"));
        TagReadResult result = read("multi.mp3", tag(4, 0, f, 0, false));
        String artist = result.effective().display(Field.ARTIST).orElseThrow();
        assertThat(artist).contains("Ђорђе").contains("Marko").contains("Đ").doesNotContain("\u0000");
    }

    @Test
    void unknownAndExoticFramesNextToNormalOnesDoNotDisturbReading() throws Exception {
        byte[] frames = join(
                frame(3, "TIT2", latin1("Title")),
                frame(3, "TXXX", join(new byte[] {0}, "custom\0value".getBytes(StandardCharsets.ISO_8859_1))),
                frame(3, "PRIV", join("owner@x\0".getBytes(StandardCharsets.ISO_8859_1), new byte[] {1, 2, 3})),
                frame(3, "USLT", join(new byte[] {0}, "eng".getBytes(), "\0lyrics\nline2".getBytes())),
                frame(3, "TCOM", latin1("Composer")),
                frame(3, "ZZZZ", new byte[] {1, 2, 3}),
                frame(3, "TPE1", latin1("Artist")));
        TagReadResult result = read("exotic.mp3", tag(3, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TITLE)).contains("Title");
        assertThat(result.effective().display(Field.ARTIST)).contains("Artist");
        assertThat(result.versions()).containsExactly(TagVersion.ID3V2_3);
    }

    @Test
    void tyerAndTdatGiveTheYear() throws Exception {
        byte[] frames = join(frame(3, "TYER", latin1("1999")), frame(3, "TDAT", latin1("0312")));
        assertThat(read("tyer.mp3", tag(3, 0, frames, 0, false)).effective().display(Field.YEAR)).contains("1999");
    }

    @Test
    void trackAndDiscStringsInAllSortsOfShapes() throws Exception {
        assertThat(trackOf(" 3 / 12 ")).contains("3/12");
        assertThat(trackOf("3/0")).contains("3");
        assertThat(trackOf("12/3")).contains("12/3");
        assertThat(trackOf("0/12")).isEmpty();
        assertThat(trackOf("07")).contains("7");
        assertThat(trackOf("3/")).isIn(java.util.Optional.of("3"), java.util.Optional.empty());
    }

    private java.util.Optional<String> trackOf(String raw) throws Exception {
        byte[] frames = frame(3, "TRCK", latin1(raw));
        return read("trck-" + Math.abs(raw.hashCode()) + ".mp3", tag(3, 0, frames, 0, false)).effective()
                .display(Field.TRACK);
    }

    @Test
    void absurdTrackIsAWarningNotAFailure() throws Exception {
        byte[] frames = frame(3, "TRCK", latin1("99999999999999999999"));
        TagReadResult result = read("huge-track.mp3", tag(3, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TRACK)).isEmpty();
        assertThat(result.issues()).anyMatch(i -> i.code() == IssueCode.READ_VALUE_INVALID);
    }

    @Test
    void theCommentWithoutDescriptionWinsOverOneWithADescription() throws Exception {
        byte[] withDescription = frame(3, "COMM", join(new byte[] {0}, "eng".getBytes(), "iTunNORM\0 0000 1111".getBytes()));
        byte[] plain = frame(3, "COMM", join(new byte[] {0}, "eng".getBytes(), "\0the real comment".getBytes()));
        TagReadResult result = read("comm.mp3", tag(3, 0, join(withDescription, plain), 0, false));
        assertThat(result.effective().display(Field.COMMENT)).contains("the real comment");
    }

    @Test
    void utf16WithoutBomAndUtf16BeInV24() throws Exception {
        byte[] frames = join(frame(4, "TIT2", text(2, StandardCharsets.UTF_16BE, "Ђорђе Балашевић")),
                frame(4, "TPE1", text(1, StandardCharsets.UTF_16LE, "čćšžđ")));
        // encoding 1 requires a BOM, so write a real one for TPE1
        byte[] bomArtist = join(new byte[] {1, (byte) 0xFF, (byte) 0xFE}, "čćšžđ".getBytes(StandardCharsets.UTF_16LE));
        frames = join(frame(4, "TIT2", text(2, StandardCharsets.UTF_16BE, "Ђорђе Балашевић")),
                frame(4, "TPE1", bomArtist));
        TagReadResult result = read("utf16.mp3", tag(4, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TITLE)).contains("Ђорђе Балашевић");
        assertThat(result.effective().display(Field.ARTIST)).contains("čćšžđ");
    }

    @Test
    void emojiInUtf8AndUtf16() throws Exception {
        byte[] frames = join(frame(4, "TIT2", utf8("Song 🎵 中文")),
                frame(4, "TALB", join(new byte[] {1, (byte) 0xFF, (byte) 0xFE},
                        "Album 🎶".getBytes(StandardCharsets.UTF_16LE))));
        TagReadResult result = read("emoji.mp3", tag(4, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TITLE)).contains("Song 🎵 中文");
        assertThat(result.effective().display(Field.ALBUM)).contains("Album 🎶");
    }

    @Test
    void v23TagWithUnsynchronisationIsDecoded() throws Exception {
        // title containing 0xFF 0xE0 -> unsynchronised as FF 00 E0; whole-tag flag 0x80
        byte[] title = join(new byte[] {0}, new byte[] {'A', (byte) 0xFF, (byte) 0xE0, 'B'});
        byte[] frames = unsynchronise(join(frame(3, "TIT2", title), frame(3, "TPE1", latin1("Artist"))));
        TagReadResult result = read("unsync.mp3", tag(3, 0x80, frames, 0, false));
        assertThat(result.effective().display(Field.ARTIST)).contains("Artist");
        assertThat(result.effective().display(Field.TITLE)).contains("AÿàB");
    }

    @Test
    void v24FooterAndPaddingAreSkippedAndAudioStillFound() throws Exception {
        byte[] frames = frame(4, "TIT2", utf8("Footer"));
        TagReadResult withFooter = read("footer.mp3", tag(4, 0x10, frames, 0, true));
        assertThat(withFooter.effective().display(Field.TITLE)).contains("Footer");
        assertThat(withFooter.stream()).isPresent();
        TagReadResult padded = read("padded.mp3", tag(4, 0, frames, 4096, false));
        assertThat(padded.effective().display(Field.TITLE)).contains("Footer");
        assertThat(padded.stream()).isPresent();
        assertThat(padded.stream().orElseThrow().bitrateKbps()).isEqualTo(128);
    }

    @Test
    void v1OnlyFileWithXingHeaderGetsStreamInfoAndV1Values() throws Exception {
        Path file = tempDir.resolve("v1-xing.mp3");
        Files.write(file, join(Fixtures.bytes(Fixture.NO_TAGS), v1Tag("Old", "Band", 5, 17)));
        TagReadResult result = reader.read(file);
        assertThat(result.versions()).containsExactly(TagVersion.ID3V1_1);
        assertThat(result.effective().display(Field.GENRE)).contains("Rock");
        assertThat(result.effective().display(Field.TRACK)).contains("5");
        assertThat(result.stream().orElseThrow().bitrateKbps()).isEqualTo(128);
    }

    @Test
    void v1TagWithUnknownGenreByteIsNotAFailure() throws Exception {
        Path file = tempDir.resolve("v1-g255.mp3");
        Files.write(file, join(Fixtures.bytes(Fixture.NO_TAGS), v1Tag("Old", "Band", 0, 255)));
        TagReadResult result = reader.read(file);
        assertThat(result.effective().display(Field.TITLE)).contains("Old");
        assertThat(result.effective().display(Field.GENRE)).isEmpty();
    }

    @Test
    void veryLongValuesAreReadInFull() throws Exception {
        String longTitle = "x".repeat(20_000);
        TagReadResult result = read("long.mp3", tag(4, 0, frame(4, "TIT2", utf8(longTitle)), 0, false));
        assertThat(result.effective().display(Field.TITLE)).contains(longTitle);
    }

    @Test
    void embeddedNewlinesInSingleLineFieldsBecomeSpaces() throws Exception {
        byte[] frames = join(frame(4, "TIT2", utf8("Line1\nLine2\r\nLine3")), frame(4, "TPE1", utf8("A\u0000B")));
        TagReadResult result = read("newline.mp3", tag(4, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TITLE)).contains("Line1 Line2 Line3");
    }

    @Test
    void whitespaceOnlyValuesAreEmptyFields() throws Exception {
        byte[] frames = join(frame(3, "TIT2", latin1("   ")), frame(3, "TPE1", latin1("\0\0")),
                frame(3, "TALB", latin1("Album")));
        TagReadResult result = read("blank.mp3", tag(3, 0, frames, 0, false));
        assertThat(result.effective().display(Field.TITLE)).isEmpty();
        assertThat(result.effective().display(Field.ARTIST)).isEmpty();
        assertThat(result.effective().display(Field.ALBUM)).contains("Album");
    }

    @Test
    void genreReferencesInAllShapes() throws Exception {
        assertThat(genreOf("(17)")).contains("Rock");
        assertThat(genreOf("(17)Rock")).contains("Rock");
        assertThat(genreOf("17")).contains("Rock");
        assertThat(genreOf("(17)(20)")).contains("Rock; Alternative");
        assertThat(genreOf("(RX)")).isNotNull();
        assertThat(genreOf("(999)")).isEmpty();
        assertThat(genreOf("Narodna muzika")).contains("Narodna muzika");
    }

    private java.util.Optional<String> genreOf(String raw) throws Exception {
        return read("g-" + Math.abs(raw.hashCode()) + ".mp3", tag(3, 0, frame(3, "TCON", latin1(raw)), 0, false))
                .effective().display(Field.GENRE);
    }

    @Test
    void uppercaseAndMixedCaseExtensionsAndABareDotMp3AreReadable() throws Exception {
        List<String> names = new ArrayList<>(List.of("UP.MP3", "Mixed.Mp3", ".mp3"));
        for (String name : names) {
            Path file = tempDir.resolve(name);
            Files.write(file, join(tag(3, 0, frame(3, "TIT2", latin1("T")), 0, false), Fixtures.bytes(Fixture.NO_TAGS)));
            assertThat(reader.read(file).effective().display(Field.TITLE)).as(name).contains("T");
        }
    }

    @Test
    void unicodeFileNamesWithEmojiAndCjkAreReadable() throws Exception {
        for (String name : List.of("Ђорђе 🎵.mp3", "中文 – 歌.mp3", "čćšžđ Äöü.mp3")) {
            Path file = tempDir.resolve(name);
            Files.write(file, join(tag(3, 0, frame(3, "TIT2", latin1("T")), 0, false), Fixtures.bytes(Fixture.NO_TAGS)));
            assertThat(reader.read(file).effective().display(Field.TITLE)).as(name).contains("T");
        }
    }

    @Test
    void readingKeepsContentAndModificationTime() throws Exception {
        Path file = mp3("mtime.mp3", tag(3, 0, frame(3, "TIT2", latin1("T")), 0, false));
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000_000L));
        String before = Fixtures.sha256(file);
        reader.read(file);
        assertThat(Fixtures.sha256(file)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(file).toMillis()).isEqualTo(1_000_000_000_000L);
    }

    @Test
    void truncatedTagDoesNotCrashAndIsReported() throws Exception {
        byte[] full = tag(3, 0, frame(3, "TIT2", latin1("A long enough title to truncate")), 0, false);
        Path file = tempDir.resolve("trunc.mp3");
        Files.write(file, java.util.Arrays.copyOf(full, full.length - 10));
        try {
            TagReadResult result = reader.read(file);
            assertThat(result).isNotNull();
        } catch (dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        }
    }
}
