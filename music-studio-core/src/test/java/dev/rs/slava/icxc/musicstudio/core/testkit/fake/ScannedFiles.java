package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Builds {@link ScannedFile}s for tests without a reader. */
public final class ScannedFiles {

    private static final FileFingerprint FINGERPRINT = new FileFingerprint(1000, Instant.parse("2020-01-01T00:00:00Z"));

    private ScannedFiles() {
    }

    /** A file with one ID3v2.3 layer holding {@code v2}. */
    public static ScannedFile of(Path path, MediaMetadata v2) {
        return new ScannedFile(path, MediaFormat.MP3, FINGERPRINT, new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, v2)), Optional.empty(), Optional.empty(), List.of()),
                Set.of());
    }

    /** A file without tags. */
    public static ScannedFile untagged(Path path) {
        return new ScannedFile(path, MediaFormat.MP3, FINGERPRINT, TagReadResult.untagged(MediaFormat.MP3), Set.of());
    }
}
