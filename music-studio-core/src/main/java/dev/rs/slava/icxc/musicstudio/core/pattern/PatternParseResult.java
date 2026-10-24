package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.List;

/**
 * The result of parsing a pattern: the pattern or all syntax errors.
 *
 * @param <P> the pattern type
 */
public sealed interface PatternParseResult<P> {

    /**
     * Parsing succeeded.
     *
     * @param pattern the parsed pattern; not null
     * @param <P>     the pattern type
     */
    record Valid<P>(P pattern) implements PatternParseResult<P> {

        /**
         * Validates the pattern.
         *
         * @throws NullPointerException if {@code pattern} is null
         */
        public Valid {
            Require.nonNull(pattern, "pattern");
        }
    }

    /**
     * Parsing failed.
     *
     * @param errors every error found, sorted by column; non-empty, no nulls; copied
     * @param <P>    the pattern type
     */
    record Invalid<P>(List<PatternError> errors) implements PatternParseResult<P> {

        /**
         * Validates and copies the errors.
         *
         * @throws NullPointerException     if {@code errors} or an element is null
         * @throws IllegalArgumentException if {@code errors} is empty
         */
        public Invalid {
            Require.noNullElements(errors, "errors");
            Require.that(!errors.isEmpty(), "an invalid result needs at least one error");
            errors = List.copyOf(errors);
        }
    }
}
