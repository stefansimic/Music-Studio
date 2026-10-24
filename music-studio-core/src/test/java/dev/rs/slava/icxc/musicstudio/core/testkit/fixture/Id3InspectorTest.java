package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.apic;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.audio;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.concat;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.frames;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.jpeg;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.text;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v1Tag;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.v2Tag;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Id3Bytes.Enc;
import org.junit.jupiter.api.Test;

/** The inspector must be right on its own: round trips of every Id3Bytes builder. */
class Id3InspectorTest {

    @Test
    void v23WithV1AndPayload() {
        byte[] audio = audio();
        byte[] v1 = v1Tag("Title", "Artist", "Album", "2001", "Comment", 7, 17);
        byte[] file = concat(v2Tag(3, frames(text("TIT2", Enc.UTF16_BOM_LE, "Ђорђе"),
                text("TPE1", Enc.LATIN1, "Artist"), apic("image/jpeg", 3, "", jpeg(300))), 64), audio, v1);
        Id3Inspector.Id3File parsed = Id3Inspector.inspect(file);
        assertThat(parsed.v2()).hasValueSatisfying(tag -> {
            assertThat(tag.major()).isEqualTo(3);
            assertThat(tag.ids()).containsExactly("TIT2", "TPE1", "APIC");
            assertThat(Id3Inspector.text(tag.frame("TIT2").orElseThrow())).isEqualTo("Ђорђе");
            assertThat(Id3Inspector.encoding(tag.frame("TIT2").orElseThrow())).isEqualTo(1);
            assertThat(Id3Inspector.text(tag.frame("TPE1").orElseThrow())).isEqualTo("Artist");
        });
        assertThat(parsed.payload()).isEqualTo(audio);
        assertThat(parsed.v1Block()).hasValueSatisfying(block -> {
            Id3Inspector.V1Fields fields = Id3Inspector.v1Fields(block);
            assertThat(fields).isEqualTo(new Id3Inspector.V1Fields("Title", "Artist", "Album", "2001", "Comment", 7,
                    17));
        });
    }

    @Test
    void v22AndV24AndMultiValues() {
        Id3Inspector.Id3File v22 = Id3Inspector.inspect(concat(v2Tag(2, frames(text("TT2", Enc.LATIN1, "x")), 0),
                audio()));
        assertThat(v22.v2().orElseThrow().ids()).containsExactly("TT2");
        Id3Inspector.Id3File v24 = Id3Inspector.inspect(concat(v2Tag(4, frames(text("TCON", Enc.UTF8, "Pop",
                "Rock")), 10), audio()));
        assertThat(Id3Inspector.text(v24.v2().orElseThrow().frame("TCON").orElseThrow())).isEqualTo("Pop\0Rock");
        assertThat(v24.v1Block()).isEmpty();
    }

    @Test
    void unsynchronisedV23() {
        byte[] picture = jpeg(400);
        Id3Inspector.Id3File parsed = Id3Inspector.inspect(concat(Id3Bytes.v23TagUnsynchronised(frames(
                apic("image/jpeg", 3, "", picture)), 16), audio()));
        byte[] body = parsed.v2().orElseThrow().frame("APIC").orElseThrow().body();
        assertThat(java.util.Arrays.copyOfRange(body, body.length - picture.length, body.length)).isEqualTo(picture);
        assertThat(parsed.payload()).isEqualTo(audio());
    }

    @Test
    void v10Block() {
        Id3Inspector.V1Fields fields = Id3Inspector.v1Fields(v1Tag("T", "", "", "", "thirty byte v1.0 comment field",
                null, 255));
        assertThat(fields.track()).isEqualTo(-1);
        assertThat(fields.comment()).isEqualTo("thirty byte v1.0 comment field");
    }
}
