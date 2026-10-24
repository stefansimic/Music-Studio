package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.apic;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.audio;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.concat;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.frames;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.jpeg;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.langText;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.ownerData;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.raw;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.txxx;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v1Tag;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v2Tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Enc;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector.Id3File;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector.V2Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Byte-level contract of MP3 tag writers (ADR 0011): inputs are built with {@link Id3Bytes}, outputs inspected with
 * the library-independent {@link Id3Inspector}. Only real writers extend it (fakes copy bytes).
 */
public abstract class Id3WriterContract {

    @TempDir
    protected Path dir;

    /** @return the writer under test */
    protected abstract TagWriter subject();

    /** @return a reader for the source tags */
    protected abstract TagReader reader();

    private static final byte[] PICTURE = jpeg(500);

    /** A rich v2.4 tag: multi-values, full date, PRIV, TXXX ReplayGain, USLT, two COMM, APIC, unknown XABC. */
    private static byte[] richV24() {
        return v2Tag(4, frames(
                text("TIT2", Enc.UTF8, "Old Title"),
                text("TPE1", Enc.UTF8, "A", "B"),
                text("TDRC", Enc.UTF8, "2020-05-01"),
                text("TCON", Enc.UTF8, "Pop", "Rock"),
                ownerData("PRIV", "owner", new byte[] {1, 2, 3}),
                txxx("REPLAYGAIN_TRACK_GAIN", "-6.5 dB"),
                langText("USLT", Enc.UTF8, "eng", "", "lyrics"),
                langText("COMM", Enc.LATIN1, "eng", "iTunNORM", " 00000A2B"),
                langText("COMM", Enc.LATIN1, "eng", "", "real comment"),
                apic("image/jpeg", 3, "", PICTURE),
                raw("XABC", new byte[] {9, 8, 7})), 64);
    }

    private static byte[] richV23() {
        return v2Tag(3, frames(
                text("TIT2", Enc.LATIN1, "Old Title"),
                text("TPE1", Enc.LATIN1, "Artist"),
                text("TYER", Enc.LATIN1, "1999"),
                ownerData("PRIV", "owner", new byte[] {1, 2, 3}),
                txxx("REPLAYGAIN_TRACK_GAIN", "-6.5 dB"),
                txxx("REPLAYGAIN_TRACK_PEAK", "0.98"),
                langText("USLT", Enc.LATIN1, "eng", "", "lyrics"),
                langText("COMM", Enc.LATIN1, "eng", "iTunNORM", " 00000A2B"),
                apic("image/jpeg", 3, "", PICTURE),
                raw("XABC", new byte[] {9, 8, 7})), 32);
    }

    private Path file(String name, byte[]... parts) throws IOException {
        Path file = dir.resolve("src").resolve(name);
        Files.createDirectories(file.getParent());
        return Files.write(file, concat(parts));
    }

    private Path write(Path source, TagWriteOptions options, UnaryOperator<MediaMetadata> change) throws Exception {
        Path target = dir.resolve("out").resolve(source.getFileName());
        Files.createDirectories(target.getParent());
        subject().writeCopy(source, target, request(source, options, change), Cancellation.NONE, ByteProgress.NONE);
        return target;
    }

    private TagWriteRequest request(Path source, TagWriteOptions options, UnaryOperator<MediaMetadata> change)
            throws Exception {
        return TagWriterContract.request(reader().read(source), options, change);
    }

    private static TagWriteOptions options(Id3v2Choice v2, Id3v1Choice v1, V2Encoding enc, boolean remove) {
        return new TagWriteOptions(v2, v1, enc, V1TextMode.ASCII, remove);
    }

    private static UnaryOperator<MediaMetadata> title(String title) {
        return m -> m.with(Field.TITLE, new FieldValue.Text(title));
    }

    private static V2Tag v2(Path file) {
        return Id3Inspector.inspect(file).v2().orElseThrow();
    }

    // --- payload --------------------------------------------------------------------------------------------

    @Test
    void payloadIsByteIdenticalForEveryOptionCombination() throws Exception {
        byte[] trailer = "APETAGEX-like trailer before the ID3v1 block".getBytes(StandardCharsets.US_ASCII);
        byte[] payload = concat(audio(), trailer);
        Path source = file("rich.mp3", richV23(), payload, v1Tag("Old", "", "", "1999", "", 1, 17));
        int i = 0;
        for (Id3v2Choice v2 : Id3v2Choice.values()) {
            for (Id3v1Choice v1 : Id3v1Choice.values()) {
                for (boolean remove : new boolean[] {false, true}) {
                    if (!TagWriteOptions.problems(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, remove).isEmpty()) {
                        continue;
                    }
                    Path target = dir.resolve("payload-" + i++ + ".mp3");
                    subject().writeCopy(source, target, request(source, options(v2, v1, V2Encoding.AUTO, remove),
                            title("New")), Cancellation.NONE, ByteProgress.NONE);
                    assertThat(Id3Inspector.inspect(target).payload()).as("%s %s %s", v2, v1, remove)
                            .isEqualTo(payload);
                }
            }
        }
    }

    // --- preservation ---------------------------------------------------------------------------------------

    @Test
    void sameVersionPreservesEveryOtherFrame() throws Exception {
        Path source = file("same.mp3", richV23(), audio());
        V2Tag before = v2(source);
        V2Tag after = v2(write(source, options(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO, false),
                title("New Title")));
        assertThat(after.ids()).containsExactlyInAnyOrderElementsOf(before.ids());
        for (String id : List.of("PRIV", "XABC", "APIC")) {
            assertThat(after.frame(id).orElseThrow().body()).as(id).isEqualTo(before.frame(id).orElseThrow().body());
        }
        assertThat(after.frames("TXXX")).extracting(Id3Inspector::text)
                .containsExactlyInAnyOrderElementsOf(before.frames("TXXX").stream().map(Id3Inspector::text).toList());
        assertThat(Id3Inspector.text(after.frame("USLT").orElseThrow())).contains("lyrics");
        assertThat(after.frames("COMM")).hasSize(1);
        assertThat(Id3Inspector.text(after.frame("TIT2").orElseThrow())).isEqualTo("New Title");
        assertThat(Id3Inspector.text(after.frame("TYER").orElseThrow())).isEqualTo("1999");
    }

    @Test
    void v24ToV23ConvertsDatesAndMultiValues() throws Exception {
        Path source = file("v24.mp3", richV24(), audio());
        TagWriteRequest request = request(source, options(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO,
                false), m -> m);
        assertThat(subject().preflight(source, request)).isEmpty();
        Path target = dir.resolve("v23.mp3");
        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        V2Tag after = v2(target);
        assertThat(after.major()).isEqualTo(3);
        assertThat(Id3Inspector.text(after.frame("TYER").orElseThrow())).isEqualTo("2020");
        assertThat(Id3Inspector.text(after.frame("TDAT").orElseThrow())).isEqualTo("0105");
        assertThat(Id3Inspector.text(after.frame("TPE1").orElseThrow())).isEqualTo("A; B");
        assertThat(Id3Inspector.text(after.frame("TCON").orElseThrow())).isEqualTo("Pop; Rock");
        assertThat(after.ids()).contains("PRIV", "TXXX", "USLT", "APIC", "XABC").doesNotContain("TDRC");
        assertThat(after.frames("COMM")).hasSize(2);
    }

    @Test
    void v24ToV22DropsOnlyWhatPreflightReports() throws Exception {
        Path source = file("v24-to-22.mp3", richV24(), audio());
        TagWriteRequest request = request(source, options(Id3v2Choice.V2_2, Id3v1Choice.NONE, V2Encoding.AUTO,
                false), m -> m);
        List<String> reported = subject().preflight(source, request).stream()
                .peek(issue -> assertThat(issue.code()).isEqualTo(IssueCode.FRAME_NOT_REPRESENTABLE))
                .map(issue -> issue.message()).toList();
        assertThat(reported).anyMatch(m -> m.contains("PRIV")).anyMatch(m -> m.contains("XABC"));
        Path target = dir.resolve("v22.mp3");
        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        V2Tag after = v2(target);
        assertThat(after.major()).isEqualTo(2);
        assertThat(after.ids()).allMatch(id -> id.length() == 3)
                .contains("TT2", "TP1", "TYE", "TCO", "PIC", "ULT", "TXX", "COM").doesNotContain("XAB");
        assertThat(after.frames("COM")).hasSize(2);
        // every source frame is either in the copy or reported
        Set<String> lost = Set.of("PRIV", "XABC");
        assertThat(reported).hasSize(lost.size());
    }

    // --- values and encodings -------------------------------------------------------------------------------

    @Test
    void writtenValuesUseTheirPlainFormat() throws Exception {
        Path source = file("values.mp3", audio());
        Path target = write(source, TagWriteOptions.DEFAULT, m -> m
                .with(Field.GENRE, FieldValue.Genres.of("Rock"))
                .with(Field.TRACK, FieldValue.Position.of(3, 12))
                .with(Field.DISC, FieldValue.Position.of(1))
                .with(Field.COMMENT, new FieldValue.Text("note"))
                .with(Field.YEAR, new FieldValue.YearValue(2001)));
        V2Tag after = v2(target);
        assertThat(Id3Inspector.text(after.frame("TCON").orElseThrow())).isEqualTo("Rock");
        assertThat(Id3Inspector.text(after.frame("TRCK").orElseThrow())).isEqualTo("3/12");
        assertThat(Id3Inspector.text(after.frame("TPOS").orElseThrow())).isEqualTo("1");
        assertThat(Id3Inspector.text(after.frame("TYER").orElseThrow())).isEqualTo("2001");
        byte[] comm = after.frame("COMM").orElseThrow().body();
        assertThat(new String(comm, 1, 3, StandardCharsets.ISO_8859_1)).isEqualTo("eng");
        assertThat(comm[4]).as("empty description").isZero();
    }

    @Test
    void encodings() throws Exception {
        Path source = file("enc.mp3", audio());
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_3, V2Encoding.AUTO, "Plain")).isZero();
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_3, V2Encoding.AUTO, "Ђорђе")).isEqualTo(1);
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_4, V2Encoding.AUTO, "Ђорђе")).isEqualTo(3);
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_4, V2Encoding.UTF16, "Ђорђе")).isEqualTo(1);
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_3, V2Encoding.UTF16, "Plain")).isEqualTo(1);
        assertThat(encodingOfTitle(source, Id3v2Choice.V2_3, V2Encoding.LATIN1, "Café")).isZero();
        Path latin = dir.resolve("enc-latin.mp3");
        subject().writeCopy(source, latin, request(source, options(Id3v2Choice.V2_3, Id3v1Choice.NONE,
                V2Encoding.LATIN1, false), title("Café")), Cancellation.NONE, ByteProgress.NONE);
        byte[] body = v2(latin).frame("TIT2").orElseThrow().body();
        assertThat(body).contains((byte) 0xE9);
        assertThat(Id3Inspector.text(v2(latin).frame("TIT2").orElseThrow())).isEqualTo("Café");
    }

    private int encodingOfTitle(Path source, Id3v2Choice v2, V2Encoding enc, String title) throws Exception {
        Path target = dir.resolve("enc-" + v2 + "-" + enc + "-" + Math.abs(title.hashCode()) + ".mp3");
        subject().writeCopy(source, target, request(source, options(v2, Id3v1Choice.NONE, enc, false), title(title)),
                Cancellation.NONE, ByteProgress.NONE);
        V2Tag tag = v2(target);
        assertThat(Id3Inspector.text(tag.frame(v2 == Id3v2Choice.V2_2 ? "TT2" : "TIT2").orElseThrow()))
                .isEqualTo(title);
        return Id3Inspector.encoding(tag.frame("TIT2").orElseThrow());
    }

    // --- ID3v1 ----------------------------------------------------------------------------------------------

    @Test
    void v1BlockIsByteExact() throws Exception {
        Path source = file("v1.mp3", audio());
        MediaMetadata proposed = MediaMetadata.empty()
                .with(Field.TITLE, new FieldValue.Text("Ђорђе Балашевић – Неки нови клинци"))
                .with(Field.ARTIST, new FieldValue.Text("Artist"))
                .with(Field.TRACK, FieldValue.Position.of(7, 12))
                .with(Field.YEAR, new FieldValue.YearValue(1986))
                .with(Field.GENRE, FieldValue.Genres.of("Rock"));
        Id3v1Values values = Id3v1Projection.project(proposed, Id3v1Choice.V1_1, V1TextMode.ASCII).values();
        TagWriteRequest request = new TagWriteRequest(MediaFormat.MP3, FieldChanges.NONE, new CoverChange.Keep(),
                options(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, false), Optional.of(values));
        Path target = dir.resolve("v1-out.mp3");
        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        byte[] block = Id3Inspector.inspect(target).v1Block().orElseThrow();
        assertThat(block).isEqualTo(v1Tag(values.title(), "Artist", "", "1986", "", 7, 17));
        assertThat(Id3Inspector.inspect(target).v2()).isEmpty();
    }

    @Test
    void v10HasA30ByteComment() throws Exception {
        Path source = file("v10.mp3", audio());
        Id3v1Values values = Id3v1Projection.project(MediaMetadata.empty().with(Field.COMMENT,
                new FieldValue.Text("thirty byte v1.0 comment field")), Id3v1Choice.V1_0, V1TextMode.ASCII).values();
        TagWriteRequest request = new TagWriteRequest(MediaFormat.MP3, FieldChanges.NONE, new CoverChange.Keep(),
                options(Id3v2Choice.NONE, Id3v1Choice.V1_0, V2Encoding.AUTO, false), Optional.of(values));
        Path target = dir.resolve("v10-out.mp3");
        subject().writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        Id3Inspector.V1Fields fields = Id3Inspector.v1Fields(Id3Inspector.inspect(target).v1Block().orElseThrow());
        assertThat(fields.comment()).isEqualTo("thirty byte v1.0 comment field");
        assertThat(fields.track()).isEqualTo(-1);
        assertThat(fields.genre()).isEqualTo(255);
    }

    // --- kept / removed slots -------------------------------------------------------------------------------

    @Test
    void keptTagsAreByteIdenticalAndRemovedTagsAreGone() throws Exception {
        byte[] v2 = richV23();
        byte[] v1 = v1Tag("Old", "Artist", "", "1999", "", 1, 17);
        Path source = file("keep.mp3", v2, audio(), v1);
        Path keptV2 = write(source, options(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, false), m -> m);
        assertThat(Id3Inspector.inspect(keptV2).v2().orElseThrow().raw()).isEqualTo(v2);
        Files.delete(keptV2);
        Path keptV1 = write(source, options(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO, false), m -> m);
        assertThat(Id3Inspector.inspect(keptV1).v1Block()).hasValueSatisfying(b -> assertThat(b).isEqualTo(v1));
        Files.delete(keptV1);
        Path stripped = write(source, options(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO, true), m -> m);
        Id3File file = Id3Inspector.inspect(stripped);
        assertThat(file.v2()).isEmpty();
        assertThat(file.v1Block()).isEmpty();
        assertThat(file.payload()).isEqualTo(audio());
    }

    // --- side effects and damaged input ---------------------------------------------------------------------

    @Test
    void noOtherFilesAreCreated() throws Exception {
        Path source = file("only.mp3", richV24(), audio());
        Path out = Files.createDirectories(dir.resolve("only-out"));
        subject().writeCopy(source, out.resolve("x.mp3"), request(source, TagWriteOptions.DEFAULT, title("T")),
                Cancellation.NONE, ByteProgress.NONE);
        try (Stream<Path> files = Files.list(out)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("x.mp3");
        }
        try (Stream<Path> files = Files.list(source.getParent())) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("only.mp3");
        }
    }

    @Test
    void damagedTagThatMustBeRewrittenIsCorrupt() throws Exception {
        byte[] good = concat(Id3Bytes.frameHeader(3, "TIT2", 6), new byte[] {0, 'G', 'o', 'o', 'd', '!'});
        byte[] bad = concat(Id3Bytes.frameHeader(3, "TPE1", 0x7FFF_FFF0), new byte[] {0, 'x'});
        byte[] body = concat(good, bad, new byte[16]);
        Path source = file("damaged.mp3", Id3Bytes.v2Header(3, 0, body.length), body, audio());
        TagReadResult read = reader().read(source);
        TagWriteRequest request = TagWriterContract.request(read, TagWriteOptions.DEFAULT, title("New"));
        assertThatThrownBy(() -> subject().writeCopy(source, dir.resolve("damaged-out.mp3"), request,
                Cancellation.NONE, ByteProgress.NONE)).isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.CORRUPT));
        assertThat(Arrays.equals(Files.readAllBytes(source), concat(Id3Bytes.v2Header(3, 0, body.length), body,
                audio()))).isTrue();
    }
}
