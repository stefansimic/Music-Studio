package dev.rs.slava.icxc.musicstudio.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CancellationSourceTest {

    @Test
    void initiallyNotCancelled() {
        CancellationSource source = new CancellationSource();
        assertThat(source.isCancelled()).isFalse();
        assertThatCode(source::throwIfCancelled).doesNotThrowAnyException();
    }

    @Test
    void cancelIsIdempotentAndThrows() {
        CancellationSource source = new CancellationSource();
        source.cancel();
        source.cancel();
        assertThat(source.isCancelled()).isTrue();
        assertThatThrownBy(source::throwIfCancelled).isInstanceOf(OperationCancelledException.class);
    }

    @Test
    void cancelIsVisibleFromAnotherThread() throws Exception {
        CancellationSource source = new CancellationSource();
        CountDownLatch seen = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean();
        Thread watcher = new Thread(() -> {
            while (!source.isCancelled()) {
                Thread.onSpinWait();
            }
            result.set(true);
            seen.countDown();
        });
        watcher.start();
        source.cancel();
        assertThat(seen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result).isTrue();
    }

    @Test
    void noneNeverCancels() {
        assertThat(Cancellation.NONE.isCancelled()).isFalse();
        assertThatCode(Cancellation.NONE::throwIfCancelled).doesNotThrowAnyException();
    }
}
