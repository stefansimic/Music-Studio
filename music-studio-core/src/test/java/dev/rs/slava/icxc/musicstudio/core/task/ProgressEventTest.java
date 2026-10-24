package dev.rs.slava.icxc.musicstudio.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressEventTest {

    @Test
    void validEvents() {
        assertThat(ProgressEvent.of("read", 3, 10).total()).isEqualTo(10);
        assertThat(ProgressEvent.of("list", 7, -1).total()).isEqualTo(-1);
        Path file = Path.of("/a.mp3").toAbsolutePath();
        assertThat(ProgressEvent.of("read", 0, 1, file).current()).contains(file);
    }

    @Test
    void invalidEvents() {
        assertThatThrownBy(() -> ProgressEvent.of("read", 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of("read", -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of(" ", 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of("read", 0, 0, Path.of("rel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProgressEvent("w", 0, 0, Optional.empty(), 5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProgressEvent("w", 0, 0, null, 0, -1)).isInstanceOf(NullPointerException.class);
    }
}
