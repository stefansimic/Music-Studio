package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/** The result of {@link FieldValueParser#parse}: a typed value or the reason why the input is not one. */
public sealed interface ParsedValue {

    /**
     * The input is a valid value.
     *
     * @param value the typed value; not null
     */
    record Valid(FieldValue value) implements ParsedValue {

        /**
         * Validates the value.
         *
         * @throws NullPointerException if {@code value} is null
         */
        public Valid {
            Require.nonNull(value, "value");
        }
    }

    /**
     * The input is not a valid value.
     *
     * @param reason English, names the input, e.g. {@code "20x0" is not a valid year}; not blank
     */
    record Invalid(String reason) implements ParsedValue {

        /**
         * Validates the reason.
         *
         * @throws NullPointerException     if {@code reason} is null
         * @throws IllegalArgumentException if {@code reason} is blank
         */
        public Invalid {
            Require.nonBlank(reason, "reason");
        }
    }
}
