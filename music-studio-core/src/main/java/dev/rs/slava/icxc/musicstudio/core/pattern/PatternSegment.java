package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * One path level of a pattern (the text between two {@code /}).
 *
 * @param tokens non-empty, no nulls, no two adjacent literals (the parser merges them); copied
 */
public record PatternSegment(List<Token> tokens) {

    /**
     * Validates and copies the tokens.
     *
     * @throws NullPointerException     if {@code tokens} or an element is null
     * @throws IllegalArgumentException if empty or two literals are adjacent
     */
    public PatternSegment {
        Require.noNullElements(tokens, "tokens");
        Require.that(!tokens.isEmpty(), "a segment needs at least one token");
        for (int i = 1; i < tokens.size(); i++) {
            Require.that(!(tokens.get(i - 1) instanceof Token.Literal && tokens.get(i) instanceof Token.Literal),
                    "adjacent literals must be merged");
        }
        tokens = List.copyOf(tokens);
    }
}
