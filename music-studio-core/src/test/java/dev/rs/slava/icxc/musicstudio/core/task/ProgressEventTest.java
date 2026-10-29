package dev.rs.slava.icxc.musicstudio.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProgressEventTest {

    @Test
    void validEvents() {
        assertThat(ProgressEvent.of("read", 3, 10).total()).isEqualTo(10);
        assertThat(ProgressEvent.of("list", 0, -1).total()).isEqualTo(-1);
        assertThat(ProgressEvent.of("read", 10, 10).done()).isEqualTo(10);
        assertThat(ProgressEvent.of("read", 1, 2, Path.of("/a.mp3").toAbsolutePath()).current()).isPresent();
    }

    @Test
    void invalidEvents() {
        assertThatThrownBy(() -> ProgressEvent.of("read", 11, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of("read", -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of("read", 0, -2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of(" ", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProgressEvent.of(null, 0, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProgressEvent.of("read", 0, 1, Path.of("rel")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noneListenersAreNoOps() {
        assertThatCode(() -> ProgressListener.NONE.onProgress(ProgressEvent.of("x", 0, 0))).doesNotThrowAnyException();
        assertThatCode(() -> ByteProgress.NONE.onBytes(1, 2)).doesNotThrowAnyException();
    }
}
