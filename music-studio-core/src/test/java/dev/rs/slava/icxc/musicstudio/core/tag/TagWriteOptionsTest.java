package dev.rs.slava.icxc.musicstudio.core.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** T01 (AC1 choices and defaults), T07 (AC3 invalid combinations), X06 (preconditions). */
class TagWriteOptionsTest {

    @Test
    void defaultsAreV23V11AutoAsciiSkipV1AndKeepOtherVersions() {
        TagWriteOptions d = TagWriteOptions.DEFAULT;
        assertThat(d.id3v2()).isEqualTo(Id3v2Choice.V2_3);
        assertThat(d.id3v1()).isEqualTo(Id3v1Choice.V1_1);
        assertThat(d.v2Encoding()).isEqualTo(V2Encoding.AUTO);
        assertThat(d.v1TextMode()).isEqualTo(V1TextMode.ASCII);
        assertThat(d.v1NonLatinPolicy()).isEqualTo(V1NonLatinPolicy.SKIP_V1);
        assertThat(d.removeOtherVersions()).isFalse();
        assertThat(d.selectedVersions()).containsExactlyInAnyOrder(TagVersion.ID3V2_3, TagVersion.ID3V1_1);
    }

    static Stream<Arguments> validCombinations() {
        List<Arguments> list = new java.util.ArrayList<>();
        for (Id3v2Choice v2 : Id3v2Choice.values()) {
            for (Id3v1Choice v1 : Id3v1Choice.values()) {
                for (V2Encoding enc : V2Encoding.values()) {
                    for (V1TextMode mode : V1TextMode.values()) {
                        for (boolean remove : new boolean[] {false, true}) {
                            if (TagWriteOptions.problems(v2, v1, enc, mode, remove).isEmpty()) {
                                list.add(Arguments.of(v2, v1, enc, mode, remove));
                            }
                        }
                    }
                }
            }
        }
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("validCombinations")
    void everyCombinationWithoutProblemsConstructs(Id3v2Choice v2, Id3v1Choice v1, V2Encoding enc, V1TextMode mode,
                                                   boolean remove) {
        TagWriteOptions o = new TagWriteOptions(v2, v1, enc, mode, V1NonLatinPolicy.SKIP_V1, remove);
        assertThat(o.selectedVersions()).hasSize((v2 == Id3v2Choice.NONE ? 0 : 1) + (v1 == Id3v1Choice.NONE ? 0 : 1));
    }

    @Test
    void theValidSpaceIsNotEmptyAndContainsEveryPlainChoice() {
        assertThat(validCombinations().count()).isGreaterThan(40);
        for (Id3v2Choice v2 : new Id3v2Choice[] {Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4}) {
            for (Id3v1Choice v1 : Id3v1Choice.values()) {
                assertThat(TagWriteOptions.problems(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII, false)).isEmpty();
            }
        }
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.UTF8, false, "UTF8", "V2_3"),
                Arguments.of(Id3v2Choice.V2_2, Id3v1Choice.V1_1, V2Encoding.UTF8, false, "UTF8", "V2_2"),
                Arguments.of(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.UTF16, false, "UTF16", "NONE"),
                Arguments.of(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.LATIN1, true, "LATIN1", "NONE"),
                Arguments.of(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.UTF8, false, "UTF8", "NONE"),
                Arguments.of(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO, false, "id3v2", "id3v1"));
    }

    @ParameterizedTest
    @MethodSource("invalid")
    void invalidCombinationsAreRejectedWithAMessageNamingBothOptions(Id3v2Choice v2, Id3v1Choice v1,
            V2Encoding enc, boolean remove, String first, String second) {
        List<String> problems = TagWriteOptions.problems(v2, v1, enc, V1TextMode.ASCII, remove);
        assertThat(problems).isNotEmpty();
        assertThat(String.join(" ", problems)).contains(first).contains(second);
        assertThatThrownBy(() -> new TagWriteOptions(v2, v1, enc, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, remove))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(first).hasMessageContaining(second)
                .hasMessage(String.join("; ", problems));
    }

    @Test
    void stripAllTagsIsValidWhenExplicit() {
        TagWriteOptions o = new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, true);
        assertThat(o.selectedVersions()).isEmpty();
        assertThat(o.removeOtherVersions()).isTrue();
    }

    @Test
    void utf8IsOnlyValidWithV24() {
        assertThat(TagWriteOptions.problems(Id3v2Choice.V2_4, Id3v1Choice.NONE, V2Encoding.UTF8, V1TextMode.ASCII,
                false)).isEmpty();
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new TagWriteOptions(null, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TagWriteOptions(Id3v2Choice.V2_3, null, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1, null, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO, null,
                V1NonLatinPolicy.SKIP_V1, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, null, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TagWriteOptions.problems(null, Id3v1Choice.V1_1, V2Encoding.AUTO,
                V1TextMode.ASCII, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void selectedVersionsMapChoicesToTagVersions() {
        assertThat(new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.V1_0, V2Encoding.AUTO, V1TextMode.LATIN1,
                V1NonLatinPolicy.CODEPAGE, true).selectedVersions())
                .isEqualTo(Set.of(TagVersion.ID3V2_2, TagVersion.ID3V1_0));
        assertThat(Id3v2Choice.NONE.version()).isEmpty();
        assertThat(Id3v1Choice.V1_1.version()).contains(TagVersion.ID3V1_1);
    }
}
