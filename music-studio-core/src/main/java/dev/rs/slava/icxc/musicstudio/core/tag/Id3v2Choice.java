package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.Optional;

/** The ID3v2 version to write, or none. */
public enum Id3v2Choice {
    /** No ID3v2 tag is written. */
    NONE(null),
    /** ID3v2.2 (3-character frame ids). */
    V2_2(TagVersion.ID3V2_2),
    /** ID3v2.3 (the most compatible version; default). */
    V2_3(TagVersion.ID3V2_3),
    /** ID3v2.4 (UTF-8 capable). */
    V2_4(TagVersion.ID3V2_4);

    private final TagVersion version;

    Id3v2Choice(TagVersion version) {
        this.version = version;
    }

    /** @return the tag version this choice writes, empty for {@link #NONE}; never null */
    public Optional<TagVersion> version() {
        return Optional.ofNullable(version);
    }
}
