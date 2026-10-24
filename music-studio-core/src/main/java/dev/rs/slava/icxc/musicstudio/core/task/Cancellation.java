package dev.rs.slava.icxc.musicstudio.core.task;

/**
 * Cooperative cancellation, checked by long-running operations between units of work (files, directories).
 *
 * <p><b>Thread-safety:</b> implementations must be safe to read from any thread.
 */
public interface Cancellation {

    /** Never cancelled. */
    Cancellation NONE = () -> false;

    /** @return true once cancellation was requested; never goes back to false */
    boolean isCancelled();

    /**
     * Throws if cancellation was requested.
     *
     * @throws OperationCancelledException if {@link #isCancelled()} is true
     */
    default void throwIfCancelled() {
        if (isCancelled()) {
            throw new OperationCancelledException();
        }
    }
}
