package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.model.CoverInfo;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.YearValue;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PictureType;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A fixed set of scan results for the renderer golden tests. */
final class Samples {

    static final Path ROOT = Path.of("/music").toAbsolutePath();

    private Samples() {
    }

    /** Cyrillic + emoji, both tag families, artist conflict, front cover. */
    static ScannedFile cyrillic() {
        MediaMetadata v2 = MediaMetadata.empty()
                .with(Field.TITLE, new Text("Љубав 🎵"))
                .with(Field.ARTIST, new Text("Ђорђе Балашевић"))
                .with(Field.TRACK, Position.of(3, 12))
                .with(Field.YEAR, new YearValue(1986))
                .with(Field.GENRE, Genres.of("Pop", "Rock"));
        MediaMetadata v1 = MediaMetadata.empty()
                .with(Field.TITLE, new Text("Ljubav"))
                .with(Field.ARTIST, new Text("Someone Else"));
        TagReadResult tags = new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, v2), new TagLayer(TagVersion.ID3V1_1, v1)),
                Optional.of(new StreamInfo(Duration.ofSeconds(225), 192, false)),
                Optional.of(new CoverInfo(PictureType.FRONT_COVER, "image/jpeg", 1234)), List.of());
        return new ScannedFile(ROOT.resolve("Балашевић/01 Љубав.mp3"), MediaFormat.MP3,
                new FileFingerprint(1000, Instant.EPOCH), tags, Set.of(Field.ARTIST));
    }

    /** Commas, quotes, a multi-line comment and control characters. */
    static ScannedFile special() {
        MediaMetadata v2 = MediaMetadata.empty()
                .with(Field.TITLE, new Text("Hello, \"World\""))
                .with(Field.COMMENT, new Text("line 1\nline 2\u0001"));
        TagReadResult tags = new TagReadResult(MediaFormat.MP3, List.of(new TagLayer(TagVersion.ID3V2_4, v2)),
                Optional.empty(), Optional.empty(), List.of(Issue.warning(IssueCode.READ_VALUE_INVALID,
                        "TRACK: 'A1' is not a valid track value; ignored").forField(Field.TRACK)));
        return new ScannedFile(ROOT.resolve("a,b.mp3"), MediaFormat.MP3, new FileFingerprint(1, Instant.EPOCH), tags,
                Set.of());
    }

    static UnreadableFile unreadable() {
        return new UnreadableFile(ROOT.resolve("broken.mp3"), MediaFormat.MP3,
                Issue.error(IssueCode.READ_FAILED, "not a readable MP3 file").forFile(ROOT.resolve("broken.mp3")));
    }

    static ScanSummary summary() {
        return new ScanSummary(ROOT, ScanStatus.COMPLETED, 3, 2, 1, 0, 2, Map.of("jpg", 1, "txt", 1),
                List.of(new SkippedEntry(ROOT.resolve(".cache"), SkipReason.HIDDEN)), List.of());
    }
}
