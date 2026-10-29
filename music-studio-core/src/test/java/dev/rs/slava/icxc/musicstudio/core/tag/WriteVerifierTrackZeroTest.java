package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Track 0 is stored in the file but read back as "not set": after a correct write that is not a mismatch. */
class WriteVerifierTrackZeroTest {

    private static final TagWriteOptions V23_ONLY = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE,
            V2Encoding.AUTO, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false);

    @Test
    void trackZeroReadBackAsAbsentIsNotAMismatch() {
        MediaMetadata expected = MediaMetadata.empty().with(Field.TRACK, new FieldValue.Position(0, OptionalInt.empty()));
        TagReadResult actual = new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty())), Optional.empty(), Optional.empty(),
                List.of());
        assertThat(WriteVerifier.verify(actual, V23_ONLY, expected, Set.of(Field.TRACK), Optional.empty())).isEmpty();
    }

    @Test
    void aRealDifferenceInTheTrackIsStillAMismatch() {
        MediaMetadata expected = MediaMetadata.empty().with(Field.TRACK, new FieldValue.Position(3, OptionalInt.empty()));
        TagReadResult actual = new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty())), Optional.empty(), Optional.empty(),
                List.of());
        assertThat(WriteVerifier.verify(actual, V23_ONLY, expected, Set.of(Field.TRACK), Optional.empty())).hasSize(1);
    }
}
