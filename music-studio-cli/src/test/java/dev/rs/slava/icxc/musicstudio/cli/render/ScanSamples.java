package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A fixed set of scan results for the renderer tests. Paths are POSIX; the renderer tests run on POSIX systems. */
final class ScanSamples {

    static final Path ROOT = Path.of("/music");
    static final ScanRequest REQUEST = ScanRequest.of(ROOT).withRecursive(true);
    private static final FileFingerprint FINGERPRINT = new FileFingerprint(16718, Instant.parse("2020-01-02T03:04:05Z"));

    private ScanSamples() {}

    private static ScannedFile scanned(String relative, Fixture fixture, V1Comparison mode) {
        var tags = FixtureCatalog.readable(fixture);
        return new ScannedFile(ROOT.resolve(relative), MediaFormat.MP3, FINGERPRINT, tags, tags.conflicts(mode));
    }

    /** Every kind of row: full tags, Cyrillic and emoji, v1/v2 conflict, no tags, invalid value, unreadable. */
    static List<ScanItem> items() {
        return List.of(
                scanned("Album/01 - café.mp3", Fixture.V23_LATIN1, V1Comparison.LENIENT),
                scanned("Album/02 - Ђорђе.mp3", Fixture.V23_UTF16_CYRILLIC, V1Comparison.LENIENT),
                scanned("Album/03 - strict.mp3", Fixture.V11_V23_SAME, V1Comparison.STRICT),
                scanned("Album/04 - differs.mp3", Fixture.V11_V23_DIFF, V1Comparison.LENIENT),
                scanned("Album/05 - genres.mp3", Fixture.V24_UTF8, V1Comparison.LENIENT),
                scanned("Album/06 - cover.mp3", Fixture.V23_COVER, V1Comparison.LENIENT),
                scanned("Album/07 - bad track.mp3", Fixture.V23_BAD_TRACK, V1Comparison.LENIENT),
                scanned("no tags.mp3", Fixture.NO_TAGS, V1Comparison.LENIENT),
                scanned("Weird, \"name\".mp3", Fixture.V1_1, V1Comparison.LENIENT),
                new UnreadableFile(ROOT.resolve("broken.mp3"), MediaFormat.MP3,
                        Issue.error(IssueCode.READ_FAILED, "CORRUPT: not a readable MP3 file: /music/broken.mp3")
                                .forFile(ROOT.resolve("broken.mp3"))));
    }

    static ScanSummary summary() {
        return new ScanSummary(ROOT, ScanStatus.COMPLETED, 10, 9, 1, 3, Map.of("jpg", 2, "txt", 1),
                List.of(new SkippedEntry(ROOT.resolve(".cache"), SkipReason.HIDDEN),
                        new SkippedEntry(ROOT.resolve("._02.mp3"), SkipReason.HIDDEN)),
                List.of(Issue.warning(IssueCode.LISTING_ACCESS_DENIED, "cannot read directory /music/locked")
                        .forFile(ROOT.resolve("locked"))));
    }

    static ScanSummary emptySummary() {
        return new ScanSummary(ROOT, ScanStatus.COMPLETED, 0, 0, 0, 0, Map.of(), List.of(), List.of());
    }
}
