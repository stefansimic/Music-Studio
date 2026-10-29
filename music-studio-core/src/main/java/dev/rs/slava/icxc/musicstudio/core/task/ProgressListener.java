package dev.rs.slava.icxc.musicstudio.core.task;

/**
 * Receives progress events.
 *
 * <p><b>Thread-safety:</b> called on the use case's thread; must be fast and must not call back into the use case.
 */
public interface ProgressListener {

    /** A listener that ignores everything. */
    ProgressListener NONE = event -> {};

    /** @param event the event; not null */
    void onProgress(ProgressEvent event);
}
