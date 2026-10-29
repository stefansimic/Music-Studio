package dev.rs.slava.icxc.musicstudio.core.model;

/** A concrete tag version found in or written to a file. */
public enum TagVersion {
    ID3V1_0(TagFamily.ID3V1, "ID3v1.0"),
    ID3V1_1(TagFamily.ID3V1, "ID3v1.1"),
    ID3V2_2(TagFamily.ID3V2, "ID3v2.2"),
    ID3V2_3(TagFamily.ID3V2, "ID3v2.3"),
    ID3V2_4(TagFamily.ID3V2, "ID3v2.4"),
    MP4_ILST(TagFamily.MP4, "MP4");

    private final TagFamily family;
    private final String label;

    TagVersion(TagFamily family, String label) {
        this.family = family;
        this.label = label;
    }

    /** @return the family (slot) this version belongs to; never null */
    public TagFamily family() {
        return family;
    }

    /** @return human-readable label such as "ID3v2.3"; never null */
    public String label() {
        return label;
    }
}
