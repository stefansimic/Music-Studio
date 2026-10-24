package dev.rs.slava.icxc.musicstudio.core.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequireTest {

    @Test
    void nonNull() {
        Object value = new Object();
        assertThat(Require.nonNull(value, "v")).isSameAs(value);
        assertThatThrownBy(() -> Require.nonNull(null, "thing"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("thing");
    }

    @Test
    void that() {
        Require.that(true, "never");
        assertThatThrownBy(() -> Require.that(false, "bad value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("bad value");
    }

    @Test
    void thatWithTemplateFormatsOnlyOnFailure() {
        // an invalid template is not evaluated when the condition holds
        Require.that(true, "%d %q", "not a number");
        assertThatThrownBy(() -> Require.that(false, "x=%d", 5))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("x=5");
    }

    @Test
    void nonBlank() {
        assertThat(Require.nonBlank(" a ", "s")).isEqualTo(" a ");
        assertThatThrownBy(() -> Require.nonBlank(null, "name"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> Require.nonBlank(" \t", "name"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    @Test
    void noNullElements() {
        List<String> values = List.of("a", "b");
        assertThat(Require.noNullElements(values, "values")).isSameAs(values);
        assertThatThrownBy(() -> Require.noNullElements(null, "values"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("values");
        assertThatThrownBy(() -> Require.noNullElements(Arrays.asList("a", null), "values"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("values");
    }

    @Test
    void noNullEntries() {
        Map<String, String> map = Map.of("k", "v");
        assertThat(Require.noNullEntries(map, "map")).isSameAs(map);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("k", null);
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "v");
        assertThatThrownBy(() -> Require.noNullEntries(null, "map"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("map");
        assertThatThrownBy(() -> Require.noNullEntries(nullValue, "map"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("map");
        assertThatThrownBy(() -> Require.noNullEntries(nullKey, "map"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("map");
    }

    @Test
    void inRange() {
        assertThat(Require.inRange(1, 1, 3, "n")).isEqualTo(1);
        assertThat(Require.inRange(3, 1, 3, "n")).isEqualTo(3);
        assertThatThrownBy(() -> Require.inRange(0, 1, 3, "count"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
        assertThatThrownBy(() -> Require.inRange(4, 1, 3, "count"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
    }

    @Test
    void notNegative() {
        assertThat(Require.notNegative(0, "n")).isZero();
        assertThatThrownBy(() -> Require.notNegative(-1, "size"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
    }

    @Test
    void absolute() {
        Path abs = Path.of("/tmp/x").toAbsolutePath();
        assertThat(Require.absolute(abs, "p")).isSameAs(abs);
        assertThatThrownBy(() -> Require.absolute(null, "root"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("root");
        assertThatThrownBy(() -> Require.absolute(Path.of("relative"), "root"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("root");
    }

    @Test
    void state() {
        Require.state(true, "never");
        assertThatThrownBy(() -> Require.state(false, "not open"))
                .isInstanceOf(IllegalStateException.class).hasMessage("not open");
    }
}
