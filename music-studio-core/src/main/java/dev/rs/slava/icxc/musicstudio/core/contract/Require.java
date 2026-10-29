package dev.rs.slava.icxc.musicstudio.core.contract;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Always-active precondition checks. Never disabled, also not in native images.
 *
 * <p>Exceptions signal programming or contract errors, never user mistakes:
 * {@link NullPointerException} for nulls, {@link IllegalArgumentException} for invalid values and
 * {@link IllegalStateException} for invalid object state.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Require {

    private Require() {}

    /**
     * @param value value to check
     * @param name  parameter name used in the message
     * @param <T>   value type
     * @return {@code value}, never null
     * @throws NullPointerException "{name} must not be null"
     */
    public static <T> T nonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    /**
     * @param condition condition that must hold
     * @param message   message of the exception
     * @throws IllegalArgumentException with {@code message} if {@code condition} is false
     */
    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Same as {@link #that(boolean, String)}, the message is formatted with {@link String#format} only on failure.
     *
     * @param condition condition that must hold
     * @param template  {@link String#format} template
     * @param args      template arguments
     * @throws IllegalArgumentException if {@code condition} is false
     */
    public static void that(boolean condition, String template, Object... args) {
        if (!condition) {
            throw new IllegalArgumentException(String.format(template, args));
        }
    }

    /**
     * @param value value to check
     * @param name  parameter name used in the message
     * @return {@code value}, neither null nor blank
     * @throws NullPointerException     if {@code value} is null
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String nonBlank(String value, String name) {
        nonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * @param values collection to check
     * @param name   parameter name used in the message
     * @param <C>    collection type
     * @return {@code values}, no null elements
     * @throws NullPointerException if {@code values} or any element is null
     */
    public static <C extends Collection<?>> C noNullElements(C values, String name) {
        nonNull(values, name);
        for (Object element : values) {
            if (element == null) {
                throw new NullPointerException(name + " must not contain null elements");
            }
        }
        return values;
    }

    /**
     * @param map  map to check
     * @param name parameter name used in the message
     * @param <M>  map type
     * @return {@code map}, no null keys or values
     * @throws NullPointerException if {@code map} or any key or value is null
     */
    public static <M extends Map<?, ?>> M noNullEntries(M map, String name) {
        nonNull(map, name);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new NullPointerException(name + " must not contain null keys or values");
            }
        }
        return map;
    }

    /**
     * @param value value to check
     * @param min   inclusive lower bound
     * @param max   inclusive upper bound
     * @param name  parameter name used in the message
     * @return {@code value}
     * @throws IllegalArgumentException unless {@code min <= value <= max}
     */
    public static int inRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in " + min + ".." + max + " but was " + value);
        }
        return value;
    }

    /**
     * @param value value to check
     * @param name  parameter name used in the message
     * @return {@code value}, {@code >= 0}
     * @throws IllegalArgumentException if {@code value < 0}
     */
    public static long notNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative but was " + value);
        }
        return value;
    }

    /**
     * @param path path to check
     * @param name parameter name used in the message
     * @return {@code path}, absolute
     * @throws NullPointerException     if {@code path} is null
     * @throws IllegalArgumentException if {@code path} is relative
     */
    public static Path absolute(Path path, String name) {
        nonNull(path, name);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute but was " + path);
        }
        return path;
    }

    /**
     * @param condition condition that must hold
     * @param message   message of the exception
     * @throws IllegalStateException if {@code condition} is false (call invalid in the object's current state)
     */
    public static void state(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
