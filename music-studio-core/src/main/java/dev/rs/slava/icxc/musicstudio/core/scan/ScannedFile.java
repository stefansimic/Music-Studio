package dev.rs.slava.icxc.musicstudio.core.scan;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import java.nio.file.Path;
import java.util.Set;

/**
 * A file that was read successfully, with or without tags.
 *
 * @param path        absolute; not null
 * @param format      equals {@code tags.format()}
 * @param fingerprint size and modification time when the file was read; not null
 * @param tags        everything the reader found; not null
 * @param conflicts   fields where the ID3v1 value disagrees with the ID3v2 value (as selected by the request); must be
 *                    a subset of the fields present in both layers
 */
public record ScannedFile(Path path, MediaFormat format, FileFingerprint fingerprint, TagReadResult tags,
                          Set<Field> conflicts) implements ScanItem {
    public ScannedFile {
        Require.absolute(path, "path");
        Require.nonNull(format, "format");
        Require.nonNull(fingerprint, "fingerprint");
        Require.nonNull(tags, "tags");
        Require.noNullElements(conflicts, "conflicts");
        Require.that(format == tags.format(), "format %s does not match the tags (%s)", format, tags.format());
        conflicts = Set.copyOf(conflicts);
        Require.that(tags.conflicts(V1Comparison.STRICT).containsAll(conflicts),
                "conflicts must be fields present in both the ID3v1 and the ID3v2 layer");
    }
}
