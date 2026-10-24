package dev.rs.slava.icxc.musicstudio.core.extract;

/** How pattern and fixed values combine with existing tag values. */
public enum MergeMode {
    /** Pattern and fixed values replace existing values (default). */
    OVERWRITE,
    /** Pattern and fixed values are only used where the field has no existing value. */
    FILL_EMPTY
}
