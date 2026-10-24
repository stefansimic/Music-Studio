package dev.rs.slava.icxc.musicstudio.core.text;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * The result of {@link Id3v1Text#fit}.
 *
 * @param text           the fitted text; not null, may be empty
 * @param transliterated true if characters were transliterated (the text differs from the input before truncation)
 * @param truncated      true if the text was shortened to fit
 * @param dropped        true if characters could not be represented and were dropped
 */
public record FittedText(String text, boolean transliterated, boolean truncated, boolean dropped) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException if {@code text} is null
     */
    public FittedText {
        Require.nonNull(text, "text");
    }
}
