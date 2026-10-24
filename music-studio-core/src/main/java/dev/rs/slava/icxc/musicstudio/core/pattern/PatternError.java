package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * A syntax error in a pattern.
 *
 * @param column  0-based UTF-16 char index into the pattern source (a JavaFX caret position); ≥ 0
 * @param message English, user-facing; not blank
 */
public record PatternError(int column, String message) {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if {@code column} is negative or {@code message} is blank
     * @throws NullPointerException     if {@code message} is null
     */
    public PatternError {
        Require.notNegative(column, "column");
        Require.nonBlank(message, "message");
    }
}
