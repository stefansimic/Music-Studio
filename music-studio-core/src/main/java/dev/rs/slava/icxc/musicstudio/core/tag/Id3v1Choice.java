package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.Optional;

/** The ID3v1 version to write, or none. */
public enum Id3v1Choice {
    /** No ID3v1 tag is written. */
    NONE(null),
    /** ID3v1.0 (30-character comment, no track number). */
    V1_0(TagVersion.ID3V1_0),
    /** ID3v1.1 (28-character comment plus track number; default). */
    V1_1(TagVersion.ID3V1_1);

    private final TagVersion version;

    Id3v1Choice(TagVersion version) {
        this.version = version;
    }

    /** @return the tag version this choice writes, empty for {@link #NONE}; never null */
    public Optional<TagVersion> version() {
        return Optional.ofNullable(version);
    }
}
