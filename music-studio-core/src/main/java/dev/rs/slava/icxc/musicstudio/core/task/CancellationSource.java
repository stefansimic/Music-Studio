package dev.rs.slava.icxc.musicstudio.core.task;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Write side of cooperative cancellation. The frontend owns it and passes it to use cases as {@link Cancellation}.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; {@link #cancel()} may be called from any thread.
 */
public final class CancellationSource implements Cancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** Requests cancellation. Idempotent. */
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
