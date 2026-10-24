package dev.rs.slava.icxc.musicstudio.core.task;

/**
 * Receives progress of a long-running use case.
 *
 * <p>Called on the use case's thread; must be fast and must not call back into the use case.
 */
public interface ProgressListener {

    /** Ignores all events. */
    ProgressListener NONE = event -> {
    };

    /**
     * @param event the progress event; never null
     */
    void onProgress(ProgressEvent event);
}
