package dev.rs.slava.icxc.musicstudio.core.task;

/** Byte-level progress of one file operation. */
public interface ByteProgress {

    /** A listener that ignores everything. */
    ByteProgress NONE = (done, total) -> {};

    /**
     * @param done  bytes processed, {@code >= 0}
     * @param total bytes in total, or -1 if unknown
     */
    void onBytes(long done, long total);
}
