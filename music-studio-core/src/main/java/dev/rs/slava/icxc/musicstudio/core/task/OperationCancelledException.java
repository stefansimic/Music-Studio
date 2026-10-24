package dev.rs.slava.icxc.musicstudio.core.task;

/** Thrown by ports and use cases when a {@link Cancellation} was triggered. */
public final class OperationCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with the message "operation cancelled". */
    public OperationCancelledException() {
        super("operation cancelled");
    }
}
