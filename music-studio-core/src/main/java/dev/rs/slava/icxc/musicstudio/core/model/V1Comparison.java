package dev.rs.slava.icxc.musicstudio.core.model;

/**
 * How ID3v1 and ID3v2 values are compared. Settings checkbox "Count transliteration/truncation as v1/v2
 * difference": unchecked = LENIENT.
 */
public enum V1Comparison {
    /** A v1 value that is only the transliterated/truncated v2 value is not a difference. */
    LENIENT,
    /** Every inequality is a difference. */
    STRICT
}
