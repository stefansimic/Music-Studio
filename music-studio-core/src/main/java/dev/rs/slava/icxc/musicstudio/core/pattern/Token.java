package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.util.OptionalInt;

/** One element of a pattern segment. */
public sealed interface Token {

    /**
     * Literal text, e.g. the separator {@code " - "}. Matched case-sensitively; its whitespace runs are tolerant
     * (see {@link PathMatcher}).
     *
     * @param text not empty, NFC-normalised by the parser
     */
    record Literal(String text) implements Token {

        /**
         * Validates the text.
         *
         * @throws NullPointerException     if {@code text} is null
         * @throws IllegalArgumentException if {@code text} is empty
         */
        public Literal {
            Require.nonNull(text, "text");
            Require.that(!text.isEmpty(), "literal text must not be empty");
        }
    }

    /**
     * A field placeholder such as {@code {artist}}.
     *
     * @param field    the field; not null
     * @param padWidth zero-padding width (output patterns, 004); empty in input patterns; ≥ 1 if present
     */
    record Placeholder(Field field, OptionalInt padWidth) implements Token {

        /**
         * Validates the components.
         *
         * @throws NullPointerException     if a component is null
         * @throws IllegalArgumentException if {@code padWidth} is present and &lt; 1
         */
        public Placeholder {
            Require.nonNull(field, "field");
            Require.nonNull(padWidth, "padWidth");
            padWidth.ifPresent(width -> Require.that(width >= 1, "padWidth must be >= 1"));
        }

        /**
         * @param field the field; not null
         * @return a placeholder without padding
         */
        public static Placeholder of(Field field) {
            return new Placeholder(field, OptionalInt.empty());
        }
    }

    /** {@code {*}}: matches any text (also empty) that is ignored. Input patterns only. */
    record Wildcard() implements Token {
    }
}
