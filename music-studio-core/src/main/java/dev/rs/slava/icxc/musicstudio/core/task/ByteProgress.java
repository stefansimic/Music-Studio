package dev.rs.slava.icxc.musicstudio.core.task;

/** Receives byte-level progress of a single file operation (large copies). Must be fast. */
public interface ByteProgress {

    /** Ignores all events. */
    ByteProgress NONE = (done, total) -> {
    };

    /**
     * @param done  bytes done; ≥ 0
     * @param total total bytes, ≥ done, or -1 if unknown
     */
    void onBytes(long done, long total);
}
