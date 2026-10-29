package dev.rs.slava.icxc.musicstudio.core.task;

/** Thrown by cooperative cancellation checks when the user cancelled the running operation. */
public final class OperationCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperationCancelledException() {
        super("operation cancelled");
    }
}
