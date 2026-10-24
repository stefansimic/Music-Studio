package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import java.util.concurrent.locks.ReentrantLock;

/**
 * The one lock around every jaudiotagger call. The library is not thread-safe: its MP3 header parser keeps static
 * state (concurrent reads return wrong durations – found by the reader contract) and its options are global.
 */
final class LibraryLock {

    /** Held while jaudiotagger parses, converts or serialises; never while our own code copies audio. */
    static final ReentrantLock LOCK = new ReentrantLock();

    private LibraryLock() {
    }
}
