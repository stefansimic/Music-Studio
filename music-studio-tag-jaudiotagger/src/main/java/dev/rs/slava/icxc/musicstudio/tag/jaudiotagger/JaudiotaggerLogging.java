package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Silences jaudiotagger's java.util.logging output. The library logs every damaged frame and every non-MP3 file;
 * that would clutter CLI output (stdout/stderr are part of the CLI's interface). Unreadable files reach callers as
 * {@code MediaAccessException}s and damaged frames as TAG_DAMAGED issues. Some details are only in the library's
 * log and are lost while it is off (e.g. which duplicate frame was ignored); a later debug switch can set the
 * level to WARNING.
 */
final class JaudiotaggerLogging {

    /** Strong reference: java.util.logging only keeps loggers weakly, so a level set on a dropped logger is lost. */
    private static final Logger LIBRARY_LOGGER = Logger.getLogger("org.jaudiotagger");

    private JaudiotaggerLogging() {
    }

    /** Turns the library's logging off. Idempotent, thread-safe. */
    static void silence() {
        LIBRARY_LOGGER.setLevel(Level.OFF);
    }
}
