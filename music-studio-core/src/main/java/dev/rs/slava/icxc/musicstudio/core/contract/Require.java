package dev.rs.slava.icxc.musicstudio.core.contract;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Always-active precondition checks. Never disabled, also not in native images.
 *
 * <p>Every public method of every public class starts with its precondition checks through this class, in the order
 * of its {@code @param} list. Records do the checks in their compact constructor. The exceptions signal programming
 * or contract errors (a bug in the caller), never user mistakes; user-facing problems are reported as issues in
 * result objects.
 *
 * <ul>
 *   <li>{@link NullPointerException} for {@code null} values</li>
 *   <li>{@link IllegalArgumentException} for invalid values</li>
 *   <li>{@link IllegalStateException} for calls that are invalid in the object's current state</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class Require {

    private Require() {
    }

    /**
     * Checks that {@code value} is not null.
     *
     * @param value the value to check; may be anything
     * @param name  the parameter name used in the message; not null
     * @param <T>   the value's type
     * @return {@code value}, never null
     * @throws NullPointerException with the message "{name} must not be null" if {@code value} is null
     */
    public static <T> T nonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    /**
     * Checks an arbitrary condition on an argument.
     *
     * @param condition the condition that must hold
     * @param message   the exception message; not null
     * @throws IllegalArgumentException with {@code message} if {@code condition} is false
     */
    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Checks an arbitrary condition on an argument. The message is formatted with {@link String#format} only if the
     * condition is false, so a failing format never costs anything on the success path.
     *
     * @param condition the condition that must hold
     * @param template  a {@link String#format} template; not null
     * @param args      the template arguments
     * @throws IllegalArgumentException with the formatted message if {@code condition} is false
     */
    public static void that(boolean condition, String template, Object... args) {
        if (!condition) {
            throw new IllegalArgumentException(String.format(template, args));
        }
    }

    /**
     * Checks that a string is neither null nor blank.
     *
     * @param value the value to check
     * @param name  the parameter name used in the message; not null
     * @return {@code value}, never null or blank
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
     * Checks that a collection is not null and contains no null element.
     *
     * @param values the collection to check
     * @param name   the parameter name used in the message; not null
     * @param <C>    the collection type
     * @return {@code values}, never null
     * @throws NullPointerException if {@code values} or one of its elements is null
     */
    public static <C extends Collection<?>> C noNullElements(C values, String name) {
        nonNull(values, name);
        for (Object value : values) {
            if (value == null) {
                throw new NullPointerException(name + " must not contain null elements");
            }
        }
        return values;
    }

    /**
     * Checks that a map is not null and contains no null key or value.
     *
     * @param map  the map to check
     * @param name the parameter name used in the message; not null
     * @param <M>  the map type
     * @return {@code map}, never null
     * @throws NullPointerException if {@code map}, one of its keys or one of its values is null
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
     * Checks that {@code min <= value <= max}.
     *
     * @param value the value to check
     * @param min   the inclusive lower bound
     * @param max   the inclusive upper bound
     * @param name  the parameter name used in the message; not null
     * @return {@code value}
     * @throws IllegalArgumentException if {@code value} is out of range
     */
    public static int inRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in " + min + ".." + max + " but was " + value);
        }
        return value;
    }

    /**
     * Checks that {@code value >= 0}.
     *
     * @param value the value to check
     * @param name  the parameter name used in the message; not null
     * @return {@code value}
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public static long notNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative but was " + value);
        }
        return value;
    }

    /**
     * Checks that a path is not null and absolute.
     *
     * @param path the path to check
     * @param name the parameter name used in the message; not null
     * @return {@code path}, never null
     * @throws NullPointerException     if {@code path} is null
     * @throws IllegalArgumentException if {@code path} is not absolute
     */
    public static Path absolute(Path path, String name) {
        nonNull(path, name);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute but was " + path);
        }
        return path;
    }

    /**
     * Checks that the object is in a state that allows the call.
     *
     * @param condition the condition that must hold
     * @param message   the exception message; not null
     * @throws IllegalStateException with {@code message} if {@code condition} is false
     */
    public static void state(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
