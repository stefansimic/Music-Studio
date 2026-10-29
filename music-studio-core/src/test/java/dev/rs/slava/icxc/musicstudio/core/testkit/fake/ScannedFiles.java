package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds in-memory {@link ScannedFile}s for use case tests (no disk). Paths are relative to {@link #ROOT}. */
public final class ScannedFiles {

    public static final Path ROOT = Path.of("/music");
    public static final Instant MTIME = Instant.parse("2021-02-03T04:05:06Z");

    private ScannedFiles() {}

    /** @return a file without any tag */
    public static ScannedFile file(String relative) {
        return of(relative);
    }

    /** @return a file with the given ID3v2.3 tag (empty metadata means no tag layer) */
    public static ScannedFile file(String relative, MediaMetadata v2) {
        return v2.isEmpty() ? of(relative) : of(relative, new TagLayer(TagVersion.ID3V2_3, v2));
    }

    /** @return a file with exactly these layers (any order) */
    public static ScannedFile of(String relative, TagLayer... layers) {
        List<TagLayer> sorted = new ArrayList<>(List.of(layers));
        sorted.sort(Comparator.comparing(l -> l.version().family()));
        TagReadResult tags = new TagReadResult(MediaFormat.MP3, sorted, Optional.empty(), Optional.empty(), List.of());
        return new ScannedFile(ROOT.resolve(relative), MediaFormat.MP3, new FileFingerprint(1234, MTIME), tags,
                tags.conflicts(V1Comparison.LENIENT));
    }

    /** @return a file whose ID3v1 and ID3v2 layers disagree */
    public static ScannedFile conflicting(String relative, MediaMetadata v1, MediaMetadata v2) {
        return of(relative, new TagLayer(TagVersion.ID3V1_1, v1), new TagLayer(TagVersion.ID3V2_3, v2));
    }
}
