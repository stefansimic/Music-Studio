package dev.rs.slava.icxc.musicstudio.core.model;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/** A typed metadata value. The type must match {@link Field#type()}. */
public sealed interface FieldValue {

    /** @return the type of this value; never null */
    FieldType type();

    /** @return canonical display text such as "Song", "2020", "3/12", "3" or "Pop; Rock"; never null */
    String display();

    /**
     * Text value. Lossy for multi-valued frames: several values are merged into one display string and exact
     * duplicates are dropped, so it must not be written back to a tag as-is.
     *
     * @param value stripped, not empty, without U+0000. Line breaks are only allowed for multi-line fields; that is
     *              checked by {@link MediaMetadata}.
     */
    record Text(String value) implements FieldValue {
        public Text {
            Require.nonBlank(value, "value");
            Require.that(value.equals(value.strip()), "text must be stripped");
            Require.that(value.indexOf('\u0000') < 0, "text must not contain U+0000");
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
     * Year. Finer dates (TDRC 2020-05-01) are preserved by adapters while the year stays unchanged.
     *
     * @param year 1..9999
     */
    record YearValue(int year) implements FieldValue {
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
     * Track or disc position.
     *
     * @param number 1..9999
     * @param total  1..9999 if present. {@code number > total} is allowed because real files contain it.
     */
    record Position(int number, OptionalInt total) implements FieldValue {
        public Position {
            Require.inRange(number, 1, 9999, "number");
            Require.nonNull(total, "total");
            if (total.isPresent()) {
                Require.inRange(total.getAsInt(), 1, 9999, "total");
            }
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
     * Genres in priority order.
     *
     * @param values non-empty, no duplicates (case-insensitive); the first is the primary genre
     */
    record Genres(List<Genre> values) implements FieldValue {
        public Genres {
            Require.noNullElements(values, "values");
            values = List.copyOf(values);
            Require.that(!values.isEmpty(), "genres must not be empty");
            List<String> seen = new ArrayList<>();
            for (Genre genre : values) {
                String key = genre.name().toLowerCase(Locale.ROOT);
                Require.that(!seen.contains(key), "duplicate genre: %s", genre.name());
                seen.add(key);
            }
        }

        /** @return the first (primary) genre; never null */
        public Genre primary() {
            return values.get(0);
        }

        @Override
        public FieldType type() {
            return FieldType.GENRES;
        }

        @Override
        public String display() {
            return String.join("; ", values.stream().map(Genre::name).toList());
        }
    }
}
