package dev.rs.slava.icxc.musicstudio.core.tag;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import java.util.Optional;

/**
 * Everything a {@code TagWriter} needs for one file.
 *
 * <p>Invariants: {@code id3v1} is present iff {@code format == MP3} and {@code options.id3v1() != NONE}, and its
 * version equals the option; {@code changes} only contains fields the writer can store.
 *
 * @param format  not null
 * @param changes changes to the ID3v2 (or MP4) slot; not null
 * @param cover   what happens to the cover; not null
 * @param options write options effective for this file; not null
 * @param id3v1   exact ID3v1 content; not null
 */
public record TagWriteRequest(MediaFormat format, FieldChanges changes, CoverChange cover, TagWriteOptions options,
                              Optional<Id3v1Values> id3v1) {

    public TagWriteRequest {
        Require.nonNull(format, "format");
        Require.nonNull(changes, "changes");
        Require.nonNull(cover, "cover");
        Require.nonNull(options, "options");
        Require.nonNull(id3v1, "id3v1");
        boolean wantsV1 = format == MediaFormat.MP3 && options.id3v1() != Id3v1Choice.NONE;
        Require.that(id3v1.isPresent() == wantsV1, "id3v1 values must be present exactly when ID3v1 is written");
        id3v1.ifPresent(values -> Require.that(values.version() == options.id3v1(),
                "id3v1 values version %s does not match option %s", values.version(), options.id3v1()));
    }
}
