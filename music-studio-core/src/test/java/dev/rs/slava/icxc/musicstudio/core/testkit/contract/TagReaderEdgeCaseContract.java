package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.apic;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.audio;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.concat;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.frames;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.langText;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v1Tag;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v2Tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Enc;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Frame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Edge cases of story 001 on real files built byte-by-byte with {@link Id3Bytes} (independent of any tagging
 * library). Every {@link TagReader} that reads real files extends this class in addition to {@link TagReaderContract}.
 * Fakes that answer from the fixture catalogue do not.
 *
 * <p>Expectations come from the ID3 specification and the {@link TagReader#read} Javadoc. Every read additionally
 * proves the file was not modified (SHA-256 and mtime).
 */
public abstract class TagReaderEdgeCaseContract {

    private static final FileTime OLD_MTIME = FileTime.from(Instant.parse("2001-02-03T04:05:06Z"));

    @TempDir
    protected Path dir;

    /** @return the reader under test */
    protected abstract TagReader subject();

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Writes bytes to a new file in the temp dir with an old mtime. */
    protected Path write(String name, byte[] content) throws IOException {
        Path file = dir.resolve(name).toAbsolutePath();
        Files.write(file, content);
        Files.setLastModifiedTime(file, OLD_MTIME);
        return file;
    }

    /** Reads and asserts that the file is byte-for-byte unchanged afterwards. */
    protected TagReadResult read(Path file) throws Exception {
        String before = Fixtures.sha256(file);
        FileTime mtime = Files.getLastModifiedTime(file);
        try {
            return subject().read(file);
        } finally {
            assertThat(Fixtures.sha256(file)).as("content of %s unchanged", file.getFileName()).isEqualTo(before);
            assertThat(Files.getLastModifiedTime(file)).as("mtime of %s unchanged", file.getFileName())
                    .isEqualTo(mtime);
        }
    }

    private TagReadResult readV2(int major, Frame... frames) throws Exception {
        return read(write("v2" + major + "-" + System.nanoTime() + ".mp3",
                concat(v2Tag(major, List.of(frames), 64), audio())));
    }

    private static MediaMetadata layer(TagReadResult result, TagFamily family) {
        return result.layer(family).orElseThrow(() -> new AssertionError("no " + family + " layer")).metadata();
    }

    private static MediaMetadata v2(TagReadResult result) {
        return layer(result, TagFamily.ID3V2);
    }

    private static Optional<FieldValue> t(String value) {
        return Optional.of(new Text(value));
    }

    // --- text encodings --------------------------------------------------------------------------------------

    @Test
    void v24Utf16BeWithoutBomIsDecoded() throws Exception {
        TagReadResult result = readV2(4,
                text("TIT2", Enc.UTF16BE, "Ђорђе Балашевић 🎵"),
                text("TPE1", Enc.UTF16BE, "Müller čćšžđ"),
                text("TALB", Enc.UTF16BE, "東京事変"));
        assertThat(result.versions()).containsExactly(TagVersion.ID3V2_4);
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Ђорђе Балашевић 🎵"));
        assertThat(v2(result).get(Field.ARTIST)).isEqualTo(t("Müller čćšžđ"));
        assertThat(v2(result).get(Field.ALBUM)).isEqualTo(t("東京事変"));
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void v24Utf16WithBomInBothByteOrdersIsDecoded() throws Exception {
        TagReadResult result = readV2(4,
                text("TIT2", Enc.UTF16_BOM_LE, "Љубав 😀"),
                text("TPE1", Enc.UTF16_BOM_BE, "Ärzte"));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Љубав 😀"));
        assertThat(v2(result).get(Field.ARTIST)).isEqualTo(t("Ärzte"));
    }

    @Test
    void v23Utf16BigEndianBomIsDecoded() throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.UTF16_BOM_BE, "Šta ćeš 🎸"));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Šta ćeš 🎸"));
    }

    /** The Javadoc promises "UTF-16 with/without BOM"; files without BOM in the wild are little-endian. */
    @Test
    void v23Utf16WithoutBomIsDecoded() throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.UTF16_NO_BOM_LE, "Čačak"));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Čačak"));
    }

    @Test
    void v24Utf8WithCjkAndEmojiIsDecoded() throws Exception {
        TagReadResult result = readV2(4,
                text("TIT2", Enc.UTF8, "夜に駆ける 🎶"),
                text("TPE1", Enc.UTF8, "YOASOBI"),
                text("TPE2", Enc.UTF8, "Ђорђе"),
                langText("COMM", Enc.UTF8, "eng", "", "Zeile 1\nZeile 2 – ü"));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("夜に駆ける 🎶"));
        assertThat(v2(result).get(Field.ALBUM_ARTIST)).isEqualTo(t("Ђорђе"));
        assertThat(v2(result).get(Field.COMMENT)).as("comment is multi-line").isEqualTo(t("Zeile 1\nZeile 2 – ü"));
    }

    @Test
    void nulTerminatedTextIsStripped() throws Exception {
        TagReadResult result = readV2(3,
                Id3Bytes.textTerminated("TIT2", Enc.LATIN1, "Terminated"),
                Id3Bytes.textTerminated("TPE1", Enc.UTF16_BOM_LE, "Ђорђе"));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Terminated"));
        assertThat(v2(result).get(Field.ARTIST)).isEqualTo(t("Ђорђе"));
    }

    @Test
    void v1Latin1CharactersAreDecoded() throws Exception {
        TagReadResult result = read(write("v1-latin1.mp3",
                concat(audio(), v1Tag("Café Müller", "Björk", "Straße", "1999", "", 2, 13))));
        MediaMetadata v1 = layer(result, TagFamily.ID3V1);
        assertThat(v1.get(Field.TITLE)).isEqualTo(t("Café Müller"));
        assertThat(v1.get(Field.ARTIST)).isEqualTo(t("Björk"));
        assertThat(v1.get(Field.ALBUM)).isEqualTo(t("Straße"));
    }

    // --- empty / whitespace values ---------------------------------------------------------------------------

    @Test
    void emptyAndWhitespaceValuesAreAbsentAndOthersStripped() throws Exception {
        TagReadResult result = readV2(3,
                text("TIT2", Enc.LATIN1, "   "),
                text("TPE1", Enc.LATIN1, "  Padded Artist \t"),
                text("TALB", Enc.LATIN1, ""),
                text("TPE2", Enc.UTF16_BOM_LE, "　 "),
                text("TCON", Enc.LATIN1, " "),
                text("TRCK", Enc.LATIN1, " "),
                text("TYER", Enc.LATIN1, ""),
                langText("COMM", Enc.LATIN1, "eng", "", "  "));
        MediaMetadata v2 = v2(result);
        assertThat(v2.get(Field.ARTIST)).isEqualTo(t("Padded Artist"));
        assertThat(v2.values().keySet()).containsExactly(Field.ARTIST);
        assertThat(result.issues()).as("empty values are not invalid values").isEmpty();
    }

    @Test
    void v1PaddedWithSpacesHasNoSpuriousValues() throws Exception {
        TagReadResult result = read(write("v1-spaces.mp3",
                concat(audio(), v1Tag("Spaced", "", "", "", "", null, 255, (byte) ' '))));
        MediaMetadata v1 = layer(result, TagFamily.ID3V1);
        assertThat(v1.values()).containsOnlyKeys(Field.TITLE);
        assertThat(v1.get(Field.TITLE)).isEqualTo(t("Spaced"));
    }

    @Test
    void v1TagWithOnlyEmptyFieldsIsStillReportedAsLayer() throws Exception {
        TagReadResult result = read(write("v1-empty.mp3", concat(audio(), v1Tag("", "", "", "", "", null, 255))));
        assertThat(result.versions()).containsExactly(TagVersion.ID3V1_0);
        assertThat(layer(result, TagFamily.ID3V1).isEmpty()).isTrue();
    }

    @Test
    void lineBreaksInSingleLineFieldsDoNotBreakReading() throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.LATIN1, "First\r\nSecond"));
        assertThat(v2(result).get(Field.TITLE)).hasValueSatisfying(value -> assertThat(value.display())
                .contains("First").contains("Second").doesNotContain("\n").doesNotContain("\r"));
    }

    // --- genres ----------------------------------------------------------------------------------------------

    static Stream<Arguments> v1GenreBytes() {
        return Stream.of(
                Arguments.of(0, Optional.of("Blues")),
                Arguments.of(13, Optional.of("Pop")),
                Arguments.of(17, Optional.of("Rock")),
                Arguments.of(20, Optional.of("Alternative")),
                Arguments.of(80, Optional.of("Folk")),
                Arguments.of(255, Optional.empty()),
                Arguments.of(254, Optional.empty()));
    }

    @ParameterizedTest(name = "ID3v1 genre byte {0} -> {1}")
    @MethodSource("v1GenreBytes")
    void v1GenreNumberIsShownAsText(int genreByte, Optional<String> expected) throws Exception {
        TagReadResult result = read(write("v1-genre-" + genreByte + ".mp3",
                concat(audio(), v1Tag("Genre " + genreByte, "", "", "", "", 1, genreByte))));
        assertThat(layer(result, TagFamily.ID3V1).get(Field.GENRE))
                .isEqualTo(expected.map(name -> Genres.of(name)));
    }

    static Stream<Arguments> v2GenreReferences() {
        return Stream.of(
                Arguments.of(3, new String[] {"(17)"}, List.of("Rock")),
                Arguments.of(3, new String[] {"17"}, List.of("Rock")),
                Arguments.of(3, new String[] {"(17)Rock"}, List.of("Rock")),
                Arguments.of(3, new String[] {"(17)(20)"}, List.of("Rock", "Alternative")),
                Arguments.of(3, new String[] {"(0)"}, List.of("Blues")),
                Arguments.of(3, new String[] {"Balkan"}, List.of("Balkan")),
                Arguments.of(3, new String[] {"Turbo-Folk"}, List.of("Turbo-Folk")),
                Arguments.of(2, new String[] {"(13)"}, List.of("Pop")),
                Arguments.of(2, new String[] {"(17)Rock"}, List.of("Rock")),
                Arguments.of(4, new String[] {"17"}, List.of("Rock")),
                Arguments.of(4, new String[] {"17", "Pop"}, List.of("Rock", "Pop")),
                Arguments.of(4, new String[] {"Rock", "Rock"}, List.of("Rock")),
                Arguments.of(4, new String[] {"Electronic", "Pop", "Rock"}, List.of("Electronic", "Pop", "Rock")));
    }

    @ParameterizedTest(name = "ID3v2.{0} genre {1} -> {2}")
    @MethodSource("v2GenreReferences")
    void v2GenreReferencesAreShownAsText(int major, String[] raw, List<String> expected) throws Exception {
        Frame tcon = text(major == 2 ? "TCO" : "TCON", Enc.LATIN1, raw);
        Frame title = text(major == 2 ? "TT2" : "TIT2", Enc.LATIN1, "Genre test");
        TagReadResult result = readV2(major, title, tcon);
        assertThat(v2(result).get(Field.GENRE)).contains(Genres.of(expected.toArray(String[]::new)));
    }

    @Test
    void unknownGenreNumberIsAbsentNotShownAsNumber() throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.LATIN1, "x"), text("TCON", Enc.LATIN1, "(254)"));
        assertThat(v2(result).get(Field.GENRE)).isEmpty();
    }

    // --- track / disc / year ---------------------------------------------------------------------------------

    static Stream<Arguments> positions() {
        return Stream.of(
                Arguments.of("3/12", Optional.of(Position.of(3, 12))),
                Arguments.of("03/12", Optional.of(Position.of(3, 12))),
                Arguments.of("03", Optional.of(Position.of(3))),
                Arguments.of("3", Optional.of(Position.of(3))),
                Arguments.of(" 5 / 9 ", Optional.of(Position.of(5, 9))),
                Arguments.of("3/", Optional.of(Position.of(3))),
                Arguments.of("A1", Optional.empty()),
                Arguments.of("0", Optional.empty()),
                Arguments.of("-1", Optional.empty()),
                Arguments.of("99999", Optional.empty()));
    }

    @ParameterizedTest(name = "TRCK/TPOS ''{0}'' -> {1}")
    @MethodSource("positions")
    void trackAndDiscTextIsParsed(String raw, Optional<Position> expected) throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.LATIN1, "Pos"), text("TRCK", Enc.LATIN1, raw),
                text("TPOS", Enc.LATIN1, raw));
        assertThat(v2(result).get(Field.TRACK)).as("track").isEqualTo(expected.map(p -> (FieldValue) p));
        assertThat(v2(result).get(Field.DISC)).as("disc").isEqualTo(expected.map(p -> (FieldValue) p));
        if (expected.isEmpty()) {
            assertThat(result.issues()).filteredOn(i -> i.code() == IssueCode.READ_VALUE_INVALID)
                    .extracting(i -> i.field().orElseThrow())
                    .containsExactlyInAnyOrder(Field.TRACK, Field.DISC);
            assertThat(result.issues()).allSatisfy(i -> {
                assertThat(i.severity()).isEqualTo(Severity.WARNING);
                assertThat(i.message()).contains(raw.strip());
            });
        } else {
            assertThat(result.issues()).isEmpty();
        }
    }

    @Test
    void v24PartialAndFullTimestampsYieldTheYear() throws Exception {
        assertThat(v2(readV2(4, text("TDRC", Enc.UTF8, "2020-05"))).get(Field.YEAR))
                .contains(new YearValue(2020));
        assertThat(v2(readV2(4, text("TDRC", Enc.UTF8, "1987-11-03T12:34:56"))).get(Field.YEAR))
                .contains(new YearValue(1987));
        assertThat(v2(readV2(3, text("TYER", Enc.LATIN1, "1973"))).get(Field.YEAR))
                .contains(new YearValue(1973));
    }

    /** Taggers that mix versions write TDRC into v2.3 and TYER into v2.4 tags; the year must not get lost. */
    @Test
    void yearFromTheOtherVersionsFrameIsRead() throws Exception {
        assertThat(v2(readV2(3, text("TIT2", Enc.LATIN1, "x"), text("TDRC", Enc.LATIN1, "2019"))).get(Field.YEAR))
                .contains(new YearValue(2019));
        assertThat(v2(readV2(3, text("TDRC", Enc.UTF16_BOM_LE, "2017-04-01"))).get(Field.YEAR))
                .contains(new YearValue(2017));
        assertThat(v2(readV2(4, text("TIT2", Enc.UTF8, "x"), text("TYER", Enc.UTF8, "2018"))).get(Field.YEAR))
                .contains(new YearValue(2018));
    }

    /** jaudiotagger merges TYER + TDAT into one aggregated field when reading; the year must still be found. */
    @Test
    void v23YearWithDateFrameIsRead() throws Exception {
        assertThat(v2(readV2(3, text("TYER", Enc.LATIN1, "2020"), text("TDAT", Enc.LATIN1, "0105"),
                text("TIME", Enc.LATIN1, "1200"))).get(Field.YEAR)).contains(new YearValue(2020));
    }

    @Test
    void nativeYearFrameWinsOverTheOtherVersionsFrame() throws Exception {
        assertThat(v2(readV2(3, text("TYER", Enc.LATIN1, "2001"), text("TDRC", Enc.LATIN1, "1999")))
                .get(Field.YEAR)).contains(new YearValue(2001));
        assertThat(v2(readV2(4, text("TYER", Enc.UTF8, "2001"), text("TDRC", Enc.UTF8, "1999")))
                .get(Field.YEAR)).contains(new YearValue(1999));
    }

    @Test
    void invalidYearIsOmittedWithWarning() throws Exception {
        TagReadResult result = readV2(3, text("TIT2", Enc.LATIN1, "x"), text("TYER", Enc.LATIN1, "abcd"));
        assertThat(v2(result).get(Field.YEAR)).isEmpty();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.READ_VALUE_INVALID);
            assertThat(issue.field()).contains(Field.YEAR);
            assertThat(issue.message()).contains("abcd");
        });
    }

    // --- other / unknown frames ------------------------------------------------------------------------------

    @Test
    void manyOtherFramesDoNotDisturbReading() throws Exception {
        byte[] priv = new byte[300];
        java.util.Arrays.fill(priv, (byte) 0xAB);
        TagReadResult result = readV2(3,
                Id3Bytes.ownerData("UFID", "http://musicbrainz.org", "1234-5678".getBytes(StandardCharsets.US_ASCII)),
                Id3Bytes.txxx("REPLAYGAIN_TRACK_GAIN", "-6.54 dB"),
                Id3Bytes.txxx("REPLAYGAIN_TRACK_PEAK", "0.987654"),
                Id3Bytes.txxx("MusicBrainz Album Id", "abc"),
                Id3Bytes.ownerData("PRIV", "WM/MediaClassPrimaryID", priv),
                Id3Bytes.ownerData("PRIV", "AverageLevel", new byte[] {1, 2, 3, 4}),
                text("TIT2", Enc.LATIN1, "Real Title"),
                langText("USLT", Enc.UTF16_BOM_LE, "eng", "", "La la la\nLyrics line 2"),
                text("TCOM", Enc.LATIN1, "Composer"),
                text("TSOP", Enc.LATIN1, "Artist, The"),
                text("TCMP", Enc.LATIN1, "1"),
                text("TENC", Enc.LATIN1, "LAME"),
                text("TPE1", Enc.UTF16_BOM_LE, "Ђорђе"),
                Id3Bytes.raw("WXXX", concat(new byte[] {0, 0}, "https://example.com".getBytes(StandardCharsets.US_ASCII))),
                Id3Bytes.raw("XABC", "experimental frame".getBytes(StandardCharsets.US_ASCII)),
                Id3Bytes.raw("GEOB", concat(new byte[] {0}, "application/octet-stream\0f.bin\0desc\0".getBytes(
                        StandardCharsets.ISO_8859_1), new byte[] {9, 9, 9})),
                text("TALB", Enc.LATIN1, "Real Album"),
                text("TRCK", Enc.LATIN1, "4/9"),
                text("TCON", Enc.LATIN1, "(17)"),
                langText("COMM", Enc.LATIN1, "eng", "", "Real comment"));
        MediaMetadata v2 = v2(result);
        assertThat(v2.get(Field.TITLE)).isEqualTo(t("Real Title"));
        assertThat(v2.get(Field.ARTIST)).isEqualTo(t("Ђорђе"));
        assertThat(v2.get(Field.ALBUM)).isEqualTo(t("Real Album"));
        assertThat(v2.get(Field.TRACK)).contains(Position.of(4, 9));
        assertThat(v2.get(Field.GENRE)).contains(Genres.of("Rock"));
        assertThat(v2.get(Field.COMMENT)).isEqualTo(t("Real comment"));
        assertThat(v2.get(Field.ALBUM_ARTIST)).as("TSOP/TCOM must not leak into album artist").isEmpty();
        assertThat(result.issues()).isEmpty();
        assertThat(result.stream()).isPresent();
    }

    /**
     * iTunes writes a COMM frame with description "iTunNORM" (loudness data) besides the user comment. The user's
     * comment is the one with an empty description, regardless of frame order.
     */
    @Test
    void userCommentIsTheCommFrameWithEmptyDescription() throws Exception {
        TagReadResult result = readV2(3,
                text("TIT2", Enc.LATIN1, "iTunes"),
                langText("COMM", Enc.LATIN1, "eng", "iTunNORM", " 00000A2B 00000A2B 00003A4F 00003A4F"),
                langText("COMM", Enc.LATIN1, "eng", "iTunSMPB", " 00000000 00000210 000007E0"),
                langText("COMM", Enc.LATIN1, "eng", "", "The real comment"));
        assertThat(v2(result).get(Field.COMMENT)).isEqualTo(t("The real comment"));
    }

    // --- huge values -----------------------------------------------------------------------------------------

    @Test
    void hugeTextValuesAreReadCompletely() throws Exception {
        String longTitle = "Ђ".repeat(5_000) + "🎵".repeat(1_000) + "end";
        String longComment = ("Line of a very long comment – " + "x".repeat(70) + "\n").repeat(2_000).strip();
        TagReadResult result = readV2(3,
                text("TIT2", Enc.UTF16_BOM_LE, longTitle),
                text("TPE1", Enc.LATIN1, "A".repeat(65_000)),
                langText("COMM", Enc.UTF16_BOM_LE, "eng", "", longComment));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t(longTitle));
        assertThat(v2(result).get(Field.ARTIST)).isEqualTo(t("A".repeat(65_000)));
        assertThat(v2(result).get(Field.COMMENT)).isEqualTo(t(longComment));
    }

    @Test
    void hugeNonJpegCoverIsDescribedWithoutLoadingProblems() throws Exception {
        byte[] png = Id3Bytes.png(3 * 1024 * 1024);
        Path file = write("huge-cover.mp3", concat(v2Tag(3, frames(
                text("TIT2", Enc.LATIN1, "Huge cover"),
                apic("image/png", 3, "", png)), 1024 * 1024), audio()));
        TagReadResult result = read(file);
        assertThat(result.cover()).contains(new CoverInfo(PictureType.FRONT_COVER, "image/png", png.length));
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Huge cover"));
        assertThat(result.stream()).isPresent();
    }

    @Test
    void frontCoverIsPreferredOverAnEarlierOtherPicture() throws Exception {
        byte[] back = Id3Bytes.jpeg(500);
        byte[] front = Id3Bytes.jpeg(777);
        TagReadResult result = readV2(3,
                apic("image/jpeg", 4, "back", back),
                apic("image/jpeg", 3, "front", front));
        assertThat(result.cover()).contains(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 777));
    }

    @Test
    void v24CoverIsDescribed() throws Exception {
        TagReadResult result = readV2(4, text("TIT2", Enc.UTF8, "v24 cover"),
                apic("image/jpeg", 3, "", Id3Bytes.jpeg(1234)));
        assertThat(result.cover()).contains(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 1234));
    }

    // --- ID3v2.2 ---------------------------------------------------------------------------------------------

    @Test
    void v22AllColumnsAndPictureAreRead() throws Exception {
        TagReadResult result = readV2(2,
                text("TT2", Enc.UTF16_BOM_LE, "Ђорђе v2.2"),
                text("TP1", Enc.LATIN1, "Artist"),
                text("TP2", Enc.LATIN1, "Album Artist"),
                text("TAL", Enc.LATIN1, "Album"),
                text("TRK", Enc.LATIN1, "2/8"),
                text("TPA", Enc.LATIN1, "1/2"),
                text("TYE", Enc.LATIN1, "1984"),
                text("TCO", Enc.LATIN1, "(17)"),
                langText("COM", Enc.LATIN1, "eng", "", "v2.2 comment"),
                Id3Bytes.pic("JPG", 3, Id3Bytes.jpeg(321)));
        assertThat(result.versions()).containsExactly(TagVersion.ID3V2_2);
        MediaMetadata v2 = v2(result);
        assertThat(v2.get(Field.TITLE)).isEqualTo(t("Ђорђе v2.2"));
        assertThat(v2.get(Field.ARTIST)).isEqualTo(t("Artist"));
        assertThat(v2.get(Field.ALBUM_ARTIST)).isEqualTo(t("Album Artist"));
        assertThat(v2.get(Field.ALBUM)).isEqualTo(t("Album"));
        assertThat(v2.get(Field.TRACK)).contains(Position.of(2, 8));
        assertThat(v2.get(Field.DISC)).contains(Position.of(1, 2));
        assertThat(v2.get(Field.YEAR)).contains(new YearValue(1984));
        assertThat(v2.get(Field.GENRE)).contains(Genres.of("Rock"));
        assertThat(v2.get(Field.COMMENT)).isEqualTo(t("v2.2 comment"));
        assertThat(result.cover()).hasValueSatisfying(cover -> {
            assertThat(cover.type()).isEqualTo(PictureType.FRONT_COVER);
            assertThat(cover.sizeBytes()).isEqualTo(321);
        });
        assertThat(result.issues()).isEmpty();
    }

    // --- tag structure ---------------------------------------------------------------------------------------

    @Test
    void v24AndV10TogetherAreBothReportedAndCompared() throws Exception {
        Path file = write("v24-v10.mp3", concat(
                v2Tag(4, frames(text("TIT2", Enc.UTF8, "Ђорђе"), text("TPE1", Enc.UTF8, "Same")), 64),
                audio(),
                v1Tag("Djordje", "Other", "", "", "thirty byte v1.0 comment field", null, 255)));
        TagReadResult result = read(file);
        assertThat(result.versions()).containsExactlyInAnyOrder(TagVersion.ID3V2_4, TagVersion.ID3V1_0);
        assertThat(result.effective().get(Field.TITLE)).as("v2 is primary").isEqualTo(t("Ђорђе"));
        assertThat(result.effective().get(Field.COMMENT)).as("v1 fills gaps")
                .isEqualTo(t("thirty byte v1.0 comment field"));
        assertThat(layer(result, TagFamily.ID3V1).get(Field.ARTIST)).isEqualTo(t("Other"));
    }

    @Test
    void unsynchronisedV23TagIsDecoded() throws Exception {
        byte[] jpeg = Id3Bytes.jpeg(400);
        Path file = write("unsync.mp3", concat(Id3Bytes.v23TagUnsynchronised(frames(
                text("TIT2", Enc.UTF16_BOM_LE, "Unsync ÿÿ"),
                apic("image/jpeg", 3, "", jpeg)), 32), audio()));
        TagReadResult result = read(file);
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Unsync ÿÿ"));
        assertThat(result.cover()).contains(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 400));
    }

    /** Tags are usable even though there is no audio: the Javadoc only allows CORRUPT if nothing can be read. */
    @Test
    void v2TagFollowedByBrokenAudioStillYieldsTheTags() throws Exception {
        byte[] garbage = ("no mpeg frames here, just text. ").repeat(200).getBytes(StandardCharsets.US_ASCII);
        Path file = write("tag-broken-audio.mp3", concat(
                v2Tag(3, frames(text("TIT2", Enc.LATIN1, "Tag OK"), text("TPE1", Enc.LATIN1, "Audio broken")), 64),
                garbage));
        TagReadResult result = read(file);
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Tag OK"));
        assertThat(v2(result).get(Field.ARTIST)).isEqualTo(t("Audio broken"));
        assertThat(result.stream()).as("no parsable audio header").isEmpty();
    }

    @Test
    void v2TagWithoutAnyAudioStillYieldsTheTags() throws Exception {
        Path file = write("tag-only.mp3", v2Tag(3, frames(text("TIT2", Enc.LATIN1, "Only a tag")), 0));
        TagReadResult result = read(file);
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Only a tag"));
        assertThat(result.stream()).isEmpty();
    }

    @Test
    void truncatedAudioAfterValidTagIsReadable() throws Exception {
        byte[] audio = audio();
        Path file = write("truncated-audio.mp3", concat(
                v2Tag(3, frames(text("TIT2", Enc.LATIN1, "Half")), 64),
                java.util.Arrays.copyOf(audio, audio.length / 2)));
        TagReadResult result = read(file);
        assertThat(v2(result).get(Field.TITLE)).isEqualTo(t("Half"));
        assertThat(result.stream()).isPresent();
    }

    /** Javadoc: "A damaged tag in an otherwise readable file: the readable layers are returned ... TAG_DAMAGED". */
    @Test
    void damagedV2TagInOtherwiseReadableFileKeepsTheV1Layer() throws Exception {
        byte[] goodFrame = concat(Id3Bytes.frameHeader(3, "TIT2", 6), new byte[] {0, 'G', 'o', 'o', 'd', '!'});
        byte[] badFrame = concat(Id3Bytes.frameHeader(3, "TPE1", 0x7FFF_FFF0), new byte[] {0, 'x'});
        byte[] body = concat(goodFrame, badFrame, new byte[16]);
        Path file = write("damaged-v2.mp3", concat(Id3Bytes.v2Header(3, 0, body.length), body, audio(),
                v1Tag("V1 Survives", "V1 Artist", "", "", "", 1, 17)));
        TagReadResult result;
        try {
            result = read(file);
        } catch (MediaAccessException e) {
            throw new AssertionError("a damaged v2 tag must not make the whole file unreadable: " + e.getMessage(), e);
        }
        assertThat(layer(result, TagFamily.ID3V1).get(Field.TITLE)).isEqualTo(t("V1 Survives"));
        assertThat(result.stream()).isPresent();
    }

    @Test
    void damagedV2TagIsReportedAsTagDamaged() throws Exception {
        byte[] goodFrame = concat(Id3Bytes.frameHeader(3, "TIT2", 6), new byte[] {0, 'G', 'o', 'o', 'd', '!'});
        byte[] badFrame = concat(Id3Bytes.frameHeader(3, "TPE1", 0x7FFF_FFF0), new byte[] {0, 'x'});
        byte[] body = concat(goodFrame, badFrame, new byte[16]);
        Path file = write("damaged-v2-report.mp3", concat(Id3Bytes.v2Header(3, 0, body.length), body, audio()));
        TagReadResult result = read(file);
        assertThat(result.issues()).as("issues (read result: %s)", result).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.TAG_DAMAGED);
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test
    void v2HeaderClaimingMoreBytesThanTheFileHasIsNotFatalForV1() throws Exception {
        byte[] body = concat(Id3Bytes.frameHeader(3, "TIT2", 6), new byte[] {0, 'S', 'h', 'o', 'r', 't'});
        Path file = write("v2-oversized.mp3", concat(Id3Bytes.v2Header(3, 0, 50_000_000), body, audio(),
                v1Tag("V1 Title", "", "", "", "", 1, 255)));
        TagReadResult result;
        try {
            result = read(file);
        } catch (MediaAccessException e) {
            assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT);
            throw new AssertionError("v1 tag is readable, so CORRUPT is not allowed: " + e.getMessage(), e);
        }
        assertThat(layer(result, TagFamily.ID3V1).get(Field.TITLE)).isEqualTo(t("V1 Title"));
    }

    @Test
    void randomBytesWithMp3ExtensionAreCorruptNotACrash() throws Exception {
        byte[] noise = new byte[64 * 1024];
        new java.util.Random(42).nextBytes(noise);
        Path file = write("noise.mp3", noise);
        try {
            TagReadResult result = read(file);
            // finding a fake frame sync in noise is legal; then there must at least be no tags
            assertThat(result.layers()).isEmpty();
        } catch (MediaAccessException e) {
            assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT);
            assertThat(e.getMessage()).isNotBlank();
        }
    }

    @Test
    void v1OnlyHeaderTagWithoutAudioIsCorruptOrTagOnly() throws Exception {
        Path file = write("only-v1.mp3", v1Tag("Only V1", "", "", "", "", 1, 17));
        try {
            TagReadResult result = read(file);
            assertThat(layer(result, TagFamily.ID3V1).get(Field.TITLE)).isEqualTo(t("Only V1"));
        } catch (MediaAccessException e) {
            assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT);
        }
    }

    // --- permissions / file system ---------------------------------------------------------------------------

    @Test
    void readOnlyFileIsReadable() throws Exception {
        Path file = write("readonly.mp3", concat(v2Tag(3, frames(text("TIT2", Enc.LATIN1, "RO")), 64), audio()));
        assumeTrue(file.toFile().setWritable(false), "cannot make file read-only");
        try {
            assertThat(v2(read(file)).get(Field.TITLE)).isEqualTo(t("RO"));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void unreadableFileIsAccessDeniedAndUnchanged() throws Exception {
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "POSIX only");
        assumeTrue(!"root".equals(System.getProperty("user.name")), "root can read anything");
        Path file = write("noperm.mp3", concat(v2Tag(3, frames(text("TIT2", Enc.LATIN1, "x")), 64), audio()));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        try {
            assertThatThrownBy(() -> subject().read(file)).isInstanceOfSatisfying(MediaAccessException.class,
                    e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ACCESS_DENIED));
            assertThatThrownBy(() -> subject().readCover(file)).isInstanceOfSatisfying(MediaAccessException.class,
                    e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.ACCESS_DENIED));
            assertThat(Files.getLastModifiedTime(file)).isEqualTo(OLD_MTIME);
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void fileNamesWithFat32IllegalAndOddCharactersAreReadable() throws Exception {
        byte[] content = concat(v2Tag(3, frames(text("TIT2", Enc.LATIN1, "odd name")), 64), audio());
        for (String name : List.of("a:b?c*.mp3", "quote\"pipe|lt<gt>.mp3", " leading space.mp3",
                "trailing dot..mp3", "東京 🎵 Ђ.Mp3", "é decomposed.mp3", "back\\slash.mp3", "new\nline.mp3")) {
            Path file;
            try {
                file = write(name, content);
            } catch (IOException | java.nio.file.InvalidPathException e) {
                continue; // file system does not allow this name
            }
            assertThat(v2(read(file)).get(Field.TITLE)).as(name).isEqualTo(t("odd name"));
        }
    }

    @Test
    void symlinkToAFileIsReadThroughTheLink() throws Exception {
        Path target = write("target.mp3", concat(v2Tag(3, frames(text("TIT2", Enc.LATIN1, "via link")), 64),
                audio()));
        Path link = dir.resolve("link.mp3").toAbsolutePath();
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not supported");
        }
        assertThat(v2(read(link)).get(Field.TITLE)).isEqualTo(t("via link"));
        assertThat(Files.isSymbolicLink(link)).as("link is still a link").isTrue();
    }

    @Test
    void danglingSymlinkIsNotFound() throws Exception {
        Path link = dir.resolve("dangling.mp3").toAbsolutePath();
        try {
            Files.createSymbolicLink(link, dir.resolve("nowhere.mp3"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links not supported");
        }
        assertThatThrownBy(() -> subject().read(link)).isInstanceOfSatisfying(MediaAccessException.class,
                e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
        assertThat(dir.resolve("nowhere.mp3")).as("nothing created at the link target").doesNotExist();
    }
}
