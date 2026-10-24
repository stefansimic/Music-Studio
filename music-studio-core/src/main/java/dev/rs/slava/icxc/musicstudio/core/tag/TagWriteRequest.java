package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.util.Optional;

/**
 * Everything a tag writer needs for one file.
 *
 * <p>Invariants: {@code id3v1} present iff {@code options.id3v1() != NONE}, with the same version and mode;
 * {@code options.id3v2() == NONE} ⇒ {@code changes} is empty (there is no ID3v2 tag to change).
 *
 * @param format  the file's format; not null
 * @param changes the field changes relative to the source's ID3v2 slot; not null
 * @param cover   what happens to the cover; not null
 * @param options the versions and encodings to write; not null
 * @param id3v1   the byte-exact ID3v1 content (core-computed); not null
 */
public record TagWriteRequest(MediaFormat format, FieldChanges changes, CoverChange cover, TagWriteOptions options,
                              Optional<Id3v1Values> id3v1) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException     if a component is null
     * @throws IllegalArgumentException if an invariant is violated
     */
    public TagWriteRequest {
        Require.nonNull(format, "format");
        Require.nonNull(changes, "changes");
        Require.nonNull(cover, "cover");
        Require.nonNull(options, "options");
        Require.nonNull(id3v1, "id3v1");
        Require.that(id3v1.isPresent() == (options.id3v1() != Id3v1Choice.NONE),
                "id3v1 values must be present exactly when an ID3v1 version is selected");
        id3v1.ifPresent(values -> Require.that(values.version() == options.id3v1()
                && values.mode() == options.v1TextMode(), "id3v1 values do not fit the options"));
        Require.that(options.id3v2() != Id3v2Choice.NONE || changes.isEmpty(),
                "field changes need an ID3v2 version");
    }
}
