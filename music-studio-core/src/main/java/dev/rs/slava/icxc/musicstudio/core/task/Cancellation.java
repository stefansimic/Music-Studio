package dev.rs.slava.icxc.musicstudio.core.task;

/**
 * Read side of cooperative cancellation. Long-running operations check it between units of work.
 *
 * <p><b>Thread-safety:</b> implementations must be safe to read from any thread.
 */
public interface Cancellation {

    /** A cancellation that never triggers. */
    Cancellation NONE = () -> false;

    /** @return true once cancellation was requested; never reverts to false */
    boolean isCancelled();

    /**
     * @throws OperationCancelledException if {@link #isCancelled()}
     */
    default void throwIfCancelled() {
        if (isCancelled()) {
            throw new OperationCancelledException();
        }
    }
}
