package dev.rs.slava.icxc.musicstudio.cli;

/** The {@code --format} values of commands that list things. */
public enum OutputFormat {
    /** Human-readable fixed-width table. */
    TABLE,
    /** One JSON document, streamed. */
    JSON,
    /** RFC 4180 CSV. */
    CSV
}
