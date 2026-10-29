package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** E08 invariants of {@link Id3v1Values}. */
class Id3v1ValuesTest {

    private static Id3v1Values of(Id3v1Choice version, V1Encoding enc, String title, String comment, int track,
                                  int genre, OptionalInt year) {
        return new Id3v1Values(version, enc, title, "", "", year, comment, track, genre);
    }

    @Test
    void validValuesAreAccepted() {
        Id3v1Values v = of(Id3v1Choice.V1_1, V1Encoding.ASCII, "a".repeat(30), "c".repeat(28), 255, 255,
                OptionalInt.of(9999));
        assertThat(v.title()).hasSize(30);
        assertThat(of(Id3v1Choice.V1_0, V1Encoding.ASCII, "t", "c".repeat(30), 0, 0, OptionalInt.empty()).comment())
                .hasSize(30);
    }

    @Test
    void v10CannotCarryATrack() {
        assertThatThrownBy(() -> of(Id3v1Choice.V1_0, V1Encoding.ASCII, "t", "", 1, 255, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void v11CommentIsLimitedTo28() {
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "t", "c".repeat(29), 1, 255,
                OptionalInt.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void titleIsLimitedTo30AndMustBeEncodable() {
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "a".repeat(31), "", 1, 255,
                OptionalInt.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "Müller", "", 1, 255, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.LATIN1, "Ελ", "", 1, 255, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangesAreEnforced() {
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "t", "", 256, 255, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "t", "", 1, 256, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "t", "", 1, 255, OptionalInt.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, V1Encoding.ASCII, "t", "", 1, 255, OptionalInt.of(10000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noneIsNotAVersionAndNullsAreRejected() {
        assertThatThrownBy(() -> of(Id3v1Choice.NONE, V1Encoding.ASCII, "t", "", 0, 255, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of(null, V1Encoding.ASCII, "t", "", 0, 255, OptionalInt.empty()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> of(Id3v1Choice.V1_1, null, "t", "", 0, 255, OptionalInt.empty()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void codepageCharactersFitByByteCount() {
        Id3v1Values greek = of(Id3v1Choice.V1_1, V1Encoding.WINDOWS_1253, "Ελευθερία".repeat(3), "", 1, 255,
                OptionalInt.empty());
        assertThat(greek.title().getBytes(V1Encoding.WINDOWS_1253.charset())).hasSize(27);
    }
}
