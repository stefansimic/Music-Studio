package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.frames;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.ownerData;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.raw;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v2Tag;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Enc;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Inspector.V2Tag;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v22Tag;
import org.jaudiotagger.tag.id3.ID3v23Tag;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the jaudiotagger 3.0.1 behaviour the writer relies on or works around (ADR 0011). If a library upgrade
 * changes it, these tests fail loudly instead of the writer silently producing different tags.
 */
class JaudiotaggerLearningTest {

    @BeforeAll
    static void silence() {
        JaudiotaggerLogging.silence();
    }

    private static ID3v24Tag v24(byte[] tag) throws Exception {
        return new ID3v24Tag(ByteBuffer.wrap(tag), "learning");
    }

    private static V2Tag serialise(AbstractID3v2Tag tag) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tag.write(out);
        return Id3Inspector.inspect(out.toByteArray()).v2().orElseThrow();
    }

    @Test
    void conversionToV23KeepsNulSeparatedMultiValues() throws Exception {
        V2Tag v23 = serialise(new ID3v23Tag(v24(v2Tag(4, frames(text("TPE1", Enc.UTF8, "A", "B")), 0))));
        // non-standard in v2.3, therefore V2Conversion joins them with "; "
        assertThat(Id3Inspector.text(v23.frame("TPE1").orElseThrow())).isEqualTo("A\0B");
    }

    @Test
    void conversionToV23SplitsTheDateIntoTyerAndTdat() throws Exception {
        V2Tag v23 = serialise(new ID3v23Tag(v24(v2Tag(4, frames(text("TDRC", Enc.UTF8, "2020-05-01")), 0))));
        assertThat(v23.ids()).containsExactly("TYER", "TDAT");
        assertThat(Id3Inspector.text(v23.frame("TDAT").orElseThrow())).isEqualTo("0105");
    }

    @Test
    void conversionToV22TruncatesUnknownFrameIdsAndDropsPriv() throws Exception {
        V2Tag v22 = serialise(new ID3v22Tag(v24(v2Tag(4, frames(text("TIT2", Enc.LATIN1, "x"),
                ownerData("PRIV", "o", new byte[] {1}), raw("XABC", new byte[] {9})), 0))));
        // the mangled "XAB" is why V2Conversion removes unknown frames across the v2.2 boundary
        assertThat(v22.ids()).containsExactly("TT2", "XAB");
    }

    @Test
    void readingMergesTyerAndTdatIntoOneAggregatedField() throws Exception {
        ID3v23Tag tag = new ID3v23Tag(ByteBuffer.wrap(v2Tag(3, frames(text("TYER", Enc.LATIN1, "2020"),
                text("TDAT", Enc.LATIN1, "0105")), 0)), "learning");
        // why the reader falls back to the generic YEAR key
        assertThat(tag.getFields("TYER")).isEmpty();
        assertThat(tag.getFirst(org.jaudiotagger.tag.FieldKey.YEAR)).startsWith("2020");
    }

    @Test
    void getFrameReturnsNullForAMissingId() throws Exception {
        assertThat(new ID3v23Tag().getFrame("COMM")).isNull();
    }

    @Test
    void serialisingWritesNoPaddingAndNoUnsynchronisation() throws Exception {
        V2Tag tag = serialise(v24(v2Tag(4, frames(text("TIT2", Enc.LATIN1, "x")), 500)));
        assertThat(tag.flags()).isZero();
        assertThat(tag.raw()).hasSize(10 + 10 + 2);
    }

    @Test
    void writingDoesNotChangeTheGlobalOptions() throws Exception {
        TagOptionSingleton options = TagOptionSingleton.getInstance();
        boolean unsync = options.isUnsyncTags();
        boolean pad = options.isPadNumbers();
        byte v23Encoding = options.getId3v23DefaultTextEncoding();
        serialise(new ID3v23Tag(v24(v2Tag(4, frames(text("TIT2", Enc.UTF8, "Ђорђе")), 0))));
        assertThat(options.isUnsyncTags()).isEqualTo(unsync).isFalse();
        assertThat(options.isPadNumbers()).isEqualTo(pad).isFalse();
        assertThat(options.getId3v23DefaultTextEncoding()).isEqualTo(v23Encoding);
    }
}
