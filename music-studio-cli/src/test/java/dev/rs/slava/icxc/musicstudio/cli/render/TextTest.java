package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void fitPadsAndCuts() {
        assertThat(Text.fit("abc", 5)).isEqualTo("abc  ");
        assertThat(Text.fit("abcdef", 5)).isEqualTo("abcd…");
        assertThat(Text.fit("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void emojiAreNotSplitAndCountTwoColumns() {
        assertThat(Text.width("🎵")).isEqualTo(2);
        assertThat(Text.width("Ђорђе")).isEqualTo(5);
        String cut = Text.cut("ab🎵🎵🎵", 5);
        assertThat(cut).isEqualTo("ab🎵…");
        assertThat(Text.width(Text.fit("🎵x", 6))).isEqualTo(6);
        // a cut never leaves a lone surrogate
        assertThat(Text.cut("a🎵b", 3).chars().noneMatch(c -> Character.isSurrogate((char) c)
                && !Text.cut("a🎵b", 3).codePoints().anyMatch(cp -> cp > 0xFFFF))).isTrue();
    }

    @Test
    void oneLine() {
        assertThat(Text.oneLine("a\r\nb\tc")).isEqualTo("a b c");
    }

    @Test
    void oneLineNeutralisesTerminalControlSequences() {
        String evil = "\u001B[2J\u001B[31mEVIL\u0007\u202Etxt.exe\u0085";
        String safe = Text.oneLine(evil);
        assertThat(safe).doesNotContain("\u001B", "\u0007", "\u202E", "\u0085")
                .isEqualTo("\uFFFD[2J\uFFFD[31mEVIL\uFFFD\uFFFDtxt.exe\uFFFD");
        assertThat(Text.oneLine("Ђорђе 🎵 č")).isEqualTo("Ђорђе 🎵 č");
    }

    @Test
    void duration() {
        assertThat(Text.duration(Duration.ofMillis(1045))).isEqualTo("0:01");
        assertThat(Text.duration(Duration.ofSeconds(225))).isEqualTo("3:45");
        assertThat(Text.duration(Duration.ofSeconds(3725))).isEqualTo("1:02:05");
    }
}
