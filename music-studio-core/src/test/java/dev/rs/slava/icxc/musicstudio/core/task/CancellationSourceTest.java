package dev.rs.slava.icxc.musicstudio.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CancellationSourceTest {

    @Test
    void initiallyNotCancelled() {
        CancellationSource source = new CancellationSource();
        assertThat(source.isCancelled()).isFalse();
        source.throwIfCancelled();
    }

    @Test
    void cancelIsVisibleFromAnotherThread() throws InterruptedException {
        CancellationSource source = new CancellationSource();
        Thread thread = Thread.ofPlatform().start(source::cancel);
        thread.join();
        assertThat(source.isCancelled()).isTrue();
        assertThatThrownBy(source::throwIfCancelled).isInstanceOf(OperationCancelledException.class);
        source.cancel();
        assertThat(source.isCancelled()).isTrue();
    }

    @Test
    void noneIsNeverCancelled() {
        assertThat(Cancellation.NONE.isCancelled()).isFalse();
        Cancellation.NONE.throwIfCancelled();
        ProgressListener.NONE.onProgress(ProgressEvent.of("x", 0, 0));
        ByteProgress.NONE.onBytes(1, 2);
    }
}
