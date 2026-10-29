package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * The part of a pattern between two slashes: a folder name or the file name.
 *
 * @param tokens not empty, no nulls; no two placeholders or wildcards next to each other
 */
public record PatternSegment(List<Token> tokens) {

    public PatternSegment {
        Require.noNullElements(tokens, "tokens");
        Require.that(!tokens.isEmpty(), "a segment must not be empty");
        tokens = List.copyOf(tokens);
        for (int i = 1; i < tokens.size(); i++) {
            Require.that(tokens.get(i - 1) instanceof Token.Literal || tokens.get(i) instanceof Token.Literal,
                    "two placeholders need a literal between them");
        }
    }
}
