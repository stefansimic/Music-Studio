package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/** A typed metadata value. Which variant a field holds is decided by {@link Field#type()}. */
public sealed interface FieldValue {

    /** @return the type of this value; never null */
    FieldType type();

    /** @return canonical display text: "Song", "2020", "3/12" or "3", "Pop; Rock"; never null or blank */
    String display();

    /**
     * A text value.
     *
     * @param value stripped, not empty, no U+0000. Line breaks are allowed here; {@link MediaMetadata} rejects them
     *              for single-line fields.
     */
    record Text(String value) implements FieldValue {

        /**
         * Validates the value.
         *
         * @throws NullPointerException     if {@code value} is null
         * @throws IllegalArgumentException if {@code value} is blank, not stripped or contains U+0000
         */
        public Text {
            Require.nonBlank(value, "value");
            Require.that(value.equals(value.strip()), "text must be stripped: '%s'", value);
            Require.that(value.indexOf('\0') < 0, "text must not contain U+0000");
        }

        @Override
        public FieldType type() {
            return FieldType.TEXT;
        }

        @Override
        public String display() {
            return value;
        }
    }

    /**
     * A year. Finer dates (TDRC 2020-05-01) are preserved by adapters as long as the year is unchanged.
     *
     * @param year 1..9999
     */
    record YearValue(int year) implements FieldValue {

        /**
         * Validates the year.
         *
         * @throws IllegalArgumentException if {@code year} is not in 1..9999
         */
        public YearValue {
            Require.inRange(year, 1, 9999, "year");
        }

        @Override
        public FieldType type() {
            return FieldType.YEAR;
        }

        @Override
        public String display() {
            return Integer.toString(year);
        }
    }

    /**
     * A position such as track 3 of 12. {@code number > total} is allowed (real files contain it); core reports it
     * as a warning where relevant.
     *
     * @param number 1..9999
     * @param total  1..9999 if present; not null
     */
    record Position(int number, OptionalInt total) implements FieldValue {

        /**
         * Validates the components.
         *
         * @throws NullPointerException     if {@code total} is null
         * @throws IllegalArgumentException if a number is out of range
         */
        public Position {
            Require.inRange(number, 1, 9999, "number");
            Require.nonNull(total, "total");
            if (total.isPresent()) {
                Require.inRange(total.getAsInt(), 1, 9999, "total");
            }
        }

        /**
         * @param number 1..9999
         * @return a position without total
         * @throws IllegalArgumentException if {@code number} is out of range
         */
        public static Position of(int number) {
            return new Position(number, OptionalInt.empty());
        }

        /**
         * @param number 1..9999
         * @param total  1..9999
         * @return a position with total
         * @throws IllegalArgumentException if a number is out of range
         */
        public static Position of(int number, int total) {
            return new Position(number, OptionalInt.of(total));
        }

        @Override
        public FieldType type() {
            return FieldType.POSITION;
        }

        @Override
        public String display() {
            return total.isPresent() ? number + "/" + total.getAsInt() : Integer.toString(number);
        }
    }

    /**
     * One or more genres; order is priority (first = primary genre).
     *
     * @param values non-empty, no null elements, no duplicates (case-insensitive); copied defensively
     */
    record Genres(List<Genre> values) implements FieldValue {

        /**
         * Validates and copies the list.
         *
         * @throws NullPointerException     if {@code values} or an element is null
         * @throws IllegalArgumentException if the list is empty or contains duplicates (case-insensitive)
         */
        public Genres {
            Require.noNullElements(values, "values");
            Require.that(!values.isEmpty(), "genres must not be empty");
            values = List.copyOf(values);
            Set<String> seen = new HashSet<>();
            for (Genre genre : values) {
                Require.that(seen.add(genre.name().toLowerCase(Locale.ROOT)), "duplicate genre: %s", genre.name());
            }
        }

        /**
         * @param names genre names; each must satisfy {@link Genre}
         * @return the genres in the given order
         * @throws NullPointerException     if {@code names} or an element is null
         * @throws IllegalArgumentException if a name is invalid, the list is empty or contains duplicates
         */
        public static Genres of(String... names) {
            Require.noNullElements(Arrays.asList(Require.nonNull(names, "names")), "names");
            return new Genres(Arrays.stream(names).map(Genre::new).toList());
        }

        /** @return the primary (first) genre; never null */
        public Genre primary() {
            return values.getFirst();
        }

        @Override
        public FieldType type() {
            return FieldType.GENRES;
        }

        @Override
        public String display() {
            return values.stream().map(Genre::name).collect(Collectors.joining("; "));
        }
    }
}
