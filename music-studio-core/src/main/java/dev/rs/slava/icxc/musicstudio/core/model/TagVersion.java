package dev.rs.slava.icxc.musicstudio.core.model;

/** The exact version of a tag found in or written to a file. */
public enum TagVersion {
    /** ID3v1.0: 30-byte comment, no track number. */
    ID3V1_0(TagFamily.ID3V1, "ID3v1.0"),
    /** ID3v1.1: 28-byte comment plus track byte. */
    ID3V1_1(TagFamily.ID3V1, "ID3v1.1"),
    /** ID3v2.2: 3-character frame IDs. */
    ID3V2_2(TagFamily.ID3V2, "ID3v2.2"),
    /** ID3v2.3: the most compatible v2 version. */
    ID3V2_3(TagFamily.ID3V2, "ID3v2.3"),
    /** ID3v2.4: UTF-8, multi-values. */
    ID3V2_4(TagFamily.ID3V2, "ID3v2.4"),
    /** MP4 ilst atoms. */
    MP4_ILST(TagFamily.MP4, "MP4");

    private final TagFamily family;
    private final String label;

    TagVersion(TagFamily family, String label) {
        this.family = family;
        this.label = label;
    }

    /** @return the slot this version occupies; never null */
    public TagFamily family() {
        return family;
    }

    /** @return the human-readable label, e.g. "ID3v2.3"; never null */
    public String label() {
        return label;
    }
}
