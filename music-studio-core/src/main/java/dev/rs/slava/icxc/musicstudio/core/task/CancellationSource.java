package dev.rs.slava.icxc.musicstudio.core.task;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Cancellation} the frontend owns and triggers (Cancel button, Ctrl-C).
 *
 * <p><b>Thread-safety:</b> safe for concurrent use; {@link #cancel()} is visible to all threads immediately.
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
