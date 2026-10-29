package dev.rs.slava.icxc.musicstudio.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequireTest {

    @Test
    void nonNullReturnsValue() {
        assertThat(Require.nonNull("x", "p")).isEqualTo("x");
    }

    @Test
    void nonNullFailsWithName() {
        assertThatThrownBy(() -> Require.nonNull(null, "thing"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("thing");
    }

    @Test
    void thatPassesAndFails() {
        assertThatCode(() -> Require.that(true, "m")).doesNotThrowAnyException();
        assertThatThrownBy(() -> Require.that(false, "boom"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
    }

    @Test
    void thatFormatsLazily() {
        assertThatThrownBy(() -> Require.that(false, "x=%d", 5)).hasMessage("x=5");
        assertThatCode(() -> Require.that(true, "bad %q format", 1)).doesNotThrowAnyException();
    }

    @Test
    void nonBlank() {
        assertThat(Require.nonBlank("a", "s")).isEqualTo("a");
        assertThatThrownBy(() -> Require.nonBlank(null, "s")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("s");
        assertThatThrownBy(() -> Require.nonBlank("  ", "s")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("s");
    }

    @Test
    void noNullElements() {
        List<String> ok = List.of("a");
        assertThat(Require.noNullElements(ok, "l")).isSameAs(ok);
        assertThatThrownBy(() -> Require.noNullElements(null, "l")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Require.noNullElements(Arrays.asList("a", null), "list"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("list");
    }

    @Test
    void noNullEntries() {
        Map<String, String> ok = Map.of("a", "b");
        assertThat(Require.noNullEntries(ok, "m")).isSameAs(ok);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("a", null);
        assertThatThrownBy(() -> Require.noNullEntries(nullValue, "map"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("map");
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "a");
        assertThatThrownBy(() -> Require.noNullEntries(nullKey, "map")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Require.noNullEntries(null, "map")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void inRange() {
        assertThat(Require.inRange(5, 1, 5, "n")).isEqualTo(5);
        assertThat(Require.inRange(1, 1, 5, "n")).isEqualTo(1);
        assertThatThrownBy(() -> Require.inRange(6, 1, 5, "num")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("num");
        assertThatThrownBy(() -> Require.inRange(0, 1, 5, "n")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notNegative() {
        assertThat(Require.notNegative(0, "n")).isZero();
        assertThatThrownBy(() -> Require.notNegative(-1, "size")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void absolute() {
        Path abs = Path.of("/").toAbsolutePath();
        assertThat(Require.absolute(abs, "p")).isSameAs(abs);
        assertThatThrownBy(() -> Require.absolute(null, "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Require.absolute(Path.of("rel/x"), "file"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file");
    }

    @Test
    void state() {
        assertThatCode(() -> Require.state(true, "m")).doesNotThrowAnyException();
        assertThatThrownBy(() -> Require.state(false, "bad state")).isInstanceOf(IllegalStateException.class)
                .hasMessage("bad state");
    }
}
