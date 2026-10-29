package dev.rs.slava.icxc.musicstudio.core.pattern;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.util.OptionalInt;

/** One element of a pattern segment. */
public sealed interface Token {

    /**
     * Text that must appear as written (case-sensitive, NFC). Leading and trailing spaces match any amount of
     * whitespace including none; inner runs of spaces match one or more.
     *
     * @param text not empty
     */
    record Literal(String text) implements Token {
        public Literal {
            Require.nonNull(text, "text");
            Require.that(!text.isEmpty(), "a literal must not be empty");
        }
    }

    /**
     * A value of a field.
     *
     * @param field    not null
     * @param padWidth zero-padding width of an output pattern ({@code {track:00}} = 2); empty for input patterns and
     *                 for fields without padding; if present 2..4 and the field is TRACK or DISC
     */
    record Placeholder(Field field, OptionalInt padWidth) implements Token {
        public Placeholder {
            Require.nonNull(field, "field");
            Require.nonNull(padWidth, "padWidth");
            Require.that(padWidth.isEmpty() || (padWidth.getAsInt() >= 2 && padWidth.getAsInt() <= 4
                    && (field == Field.TRACK || field == Field.DISC)), "padding needs track or disc and a width of 2..4");
        }
    }

    /** {@code {*}}: matches anything (also nothing) and is ignored. Input patterns only. */
    record Wildcard() implements Token {}
}
