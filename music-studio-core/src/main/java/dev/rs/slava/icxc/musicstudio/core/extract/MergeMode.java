package dev.rs.slava.icxc.musicstudio.core.extract;

/** What happens to a field that already has a value when a pattern or fixed value also provides one. */
public enum MergeMode {
    /** The new value replaces the existing one (default). */
    OVERWRITE,
    /** The new value is only used if the field has no value yet. */
    FILL_EMPTY
}
