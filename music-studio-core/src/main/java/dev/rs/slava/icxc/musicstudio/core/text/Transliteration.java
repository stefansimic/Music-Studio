package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * The result of a {@link Transliterator} call.
 *
 * @param text         the transliterated text; not null, may be empty
 * @param changed      true if {@code text} differs from the input
 * @param droppedChars true if at least one character could not be mapped and was dropped (never replaced by "?")
 */
public record Transliteration(String text, boolean changed, boolean droppedChars) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code text} is null
     */
    public Transliteration {
        Require.nonNull(text, "text");
    }
}
