package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * A problem in a pattern.
 *
 * @param column  0-based position (in code points) in the source text where the problem starts, for highlighting
 * @param message plain English, not blank
 */
public record PatternError(int column, String message) {

    public PatternError {
        Require.that(column >= 0, "column must be >= 0");
        Require.nonBlank(message, "message");
    }
}
