package dev.rs.slava.icxc.musicstudio.core.model;

/**
 * How ID3v1 values are compared with ID3v2 values. Settings checkbox "Count transliteration/truncation as v1/v2
 * difference": unchecked = {@link #LENIENT}; CLI {@code --strict-v1-compare} = {@link #STRICT}.
 */
public enum V1Comparison {
    /** Values that are only the transliterated/truncated v2 value, or limited by what v1 can hold, are equal. */
    LENIENT,
    /** Every inequality is a difference. */
    STRICT
}
