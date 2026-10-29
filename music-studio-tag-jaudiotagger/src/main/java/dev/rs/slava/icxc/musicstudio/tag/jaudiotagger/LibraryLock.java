package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import java.util.concurrent.locks.ReentrantLock;

/**
 * jaudiotagger keeps mutable process-wide state (observed: concurrent reads of <em>different</em> files return wrong
 * durations and audio start offsets), so every call into the library goes through this one lock. The lock is static
 * because the state is shared by all reader instances; the writer (story 003) must use the same lock.
 */
final class LibraryLock {

    private static final ReentrantLock LOCK = new ReentrantLock();

    private LibraryLock() {}

    /** An action that calls the library. */
    @FunctionalInterface
    interface Action<T, E extends Exception> {
        T run() throws E;
    }

    /**
     * Runs {@code action} while holding the library lock.
     *
     * @param action the library calls; not null
     * @param <T>    result type
     * @param <E>    checked exception type of the action
     * @return the action's result
     * @throws E whatever the action throws
     */
    static <T, E extends Exception> T run(Action<T, E> action) throws E {
        LOCK.lock();
        try {
            return action.run();
        } finally {
            LOCK.unlock();
        }
    }
}
