package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * jaudiotagger logs through java.util.logging and is chatty: it warns about odd but harmless files on every read.
 * Our own {@code Issue}s are the user-facing channel, so the library's logger is limited to SEVERE.
 */
final class JaudiotaggerLogging {

    private static final String LIBRARY_LOGGER = "org.jaudiotagger";

    private JaudiotaggerLogging() {}

    /**
     * Limits the library's log output to SEVERE.
     *
     * @return the configured logger; the caller keeps it referenced, because java.util.logging holds loggers weakly
     *         and would forget the level after a garbage collection
     */
    static Logger quiet() {
        Logger logger = Logger.getLogger(LIBRARY_LOGGER);
        logger.setLevel(Level.SEVERE);
        return logger;
    }
}
