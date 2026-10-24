package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import java.util.Optional;

/** The ID3v1 version to write, or none. */
public enum Id3v1Choice {
    /** Write no ID3v1 tag (an existing one is kept or removed). */
    NONE(null),
    /** ID3v1.0: 30-byte comment, no track. */
    V1_0(TagVersion.ID3V1_0),
    /** ID3v1.1: 28-byte comment plus track (default). */
    V1_1(TagVersion.ID3V1_1);

    private final TagVersion version;

    Id3v1Choice(TagVersion version) {
        this.version = version;
    }

    /** @return the tag version written, or empty for NONE */
    public Optional<TagVersion> version() {
        return Optional.ofNullable(version);
    }
}
