package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;

/**
 * Number the tracks by sort order when the name does not contain them. Files are numbered per folder from 1; only
 * files that are exportable take a number.
 *
 * @param order    not null
 * @param setTotal also set the folder's file count as the track total
 */
public record AutoNumbering(NumberingOrder order, boolean setTotal) {

    /** The order inside a folder. */
    public enum NumberingOrder {
        /** By file name in natural order (2 before 10). */
        FILE_NAME,
        /** By the track number the files have now; files without one come last, by file name. */
        EXISTING_TRACK
    }

    public AutoNumbering {
        Require.nonNull(order, "order");
    }
}
