package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Numbers the tracks of each folder 1..n (exportable files only).
 *
 * @param order    the order that decides the numbers; not null
 * @param setTotal also set the total (= number of exportable files in the folder)
 */
public record AutoNumbering(NumberingOrder order, boolean setTotal) {

    /** The sort order for auto-numbering. */
    public enum NumberingOrder {
        /** By path in natural order ({@code PathOrder.NATURAL}: "2 - x" before "10 - y"). */
        FILE_NAME,
        /** By the existing track number; files without one come last; ties by path. */
        EXISTING_TRACK
    }

    /**
     * Validates the order.
     *
     * @throws NullPointerException if {@code order} is null
     */
    public AutoNumbering {
        Require.nonNull(order, "order");
    }
}
