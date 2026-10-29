package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import java.util.List;

/**
 * Outcome of parsing a pattern: a pattern (with hints) or the errors. Parsing never throws for bad user input.
 *
 * @param <P> the pattern type
 */
public sealed interface PatternParseResult<P> {

    /**
     * A valid pattern.
     *
     * @param pattern the pattern; not null
     * @param hints   warnings that do not make it invalid (for example a file extension in the pattern); not null
     */
    record Valid<P>(P pattern, List<Issue> hints) implements PatternParseResult<P> {
        public Valid {
            Require.nonNull(pattern, "pattern");
            Require.noNullElements(hints, "hints");
            hints = List.copyOf(hints);
        }
    }

    /**
     * An invalid pattern.
     *
     * @param errors not empty, ordered by column
     */
    record Invalid<P>(List<PatternError> errors) implements PatternParseResult<P> {
        public Invalid {
            Require.noNullElements(errors, "errors");
            Require.that(!errors.isEmpty(), "an invalid result needs at least one error");
            errors = List.copyOf(errors);
        }
    }
}
