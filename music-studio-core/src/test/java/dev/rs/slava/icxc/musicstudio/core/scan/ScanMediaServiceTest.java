package dev.rs.slava.icxc.musicstudio.core.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.InMemoryMediaFileSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanMediaServiceTest {

    private static final Instant MTIME = Instant.parse("2020-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    private Path root;
    private InMemoryMediaFileSource source;
    private FakeTagReader reader;
    private ScanMediaService service;
    private final List<ScanItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        root = tempDir.toAbsolutePath().resolve("music");
        source = new InMemoryMediaFileSource();
        source.addDirectory(root);
        reader = new FakeTagReader();
        service = new ScanMediaService(source, reader);
    }

    private Path mp3(String relative, String title) {
        Path file = source.addFile(root.resolve(relative), 100, MTIME);
        reader.answer(file, tagged(title));
        return file;
    }

    private static TagReadResult tagged(String title) {
        return new TagReadResult(MediaFormat.MP3,
                List.of(new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty().with(Field.TITLE, new Text(title)))),
                Optional.empty(), Optional.empty(), List.of());
    }

    private ScanSummary scan(ScanRequest request) {
        return service.scan(request, items::add, ProgressListener.NONE, Cancellation.NONE);
    }

    private List<Path> paths() {
        return items.stream().map(ScanItem::path).toList();
    }

    @Test
    void resultsAreSortedByPathOrderAlthoughTheSourceListsRandomly() {
        List<Path> expected = new ArrayList<>();
        for (String name : List.of("b/02.mp3", "a/B.mp3", "a/c.mp3", "A.mp3", "a/a.mp3", "z.mp3", "b/01.mp3")) {
            expected.add(mp3(name, name));
        }
        expected.sort(PathOrder.COMPARATOR);
        source.shuffleListing(42);
        ScanSummary summary = scan(ScanRequest.of(root, true));
        assertThat(paths()).containsExactlyElementsOf(expected);
        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(summary.found()).isEqualTo(7);
        assertThat(summary.read()).isEqualTo(7);
    }

    @Test
    void untaggedAndUnreadableFilesAreDeliveredAndTheScanContinues() {
        Path untagged = source.addFile(root.resolve("1-untagged.mp3"), 10, MTIME);
        reader.answer(untagged, TagReadResult.untagged(MediaFormat.MP3));
        Path corrupt = source.addFile(root.resolve("2-corrupt.mp3"), 10, MTIME);
        reader.fail(corrupt, Reason.CORRUPT);
        Path ok = mp3("3-ok.mp3", "OK");

        ScanSummary summary = scan(ScanRequest.of(root, false));

        assertThat(paths()).containsExactly(untagged, corrupt, ok);
        assertThat(items.get(0)).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.tags().layers()).isEmpty());
        assertThat(items.get(1)).isInstanceOfSatisfying(UnreadableFile.class, f -> {
            assertThat(f.error().code()).isEqualTo(IssueCode.READ_FAILED);
            assertThat(f.error().severity()).isEqualTo(Severity.ERROR);
            assertThat(f.error().file()).contains(corrupt);
        });
        assertThat(items.get(2)).isInstanceOf(ScannedFile.class);
        assertThat(summary.found()).isEqualTo(3);
        assertThat(summary.read()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
    }

    @Test
    void scannedFileCarriesTheFingerprint() {
        Path file = mp3("a.mp3", "A");
        scan(ScanRequest.of(root, false));
        assertThat(items.getFirst()).isInstanceOfSatisfying(ScannedFile.class, f -> {
            assertThat(f.fingerprint().sizeBytes()).isEqualTo(100);
            assertThat(f.fingerprint().lastModified()).isEqualTo(MTIME);
            assertThat(f.path()).isEqualTo(file);
        });
    }

    @Test
    void otherFormatsAreIgnoredAndCountedByExtension() {
        mp3("a.mp3", "A");
        source.addFile(root.resolve("cover.jpg"), 1, MTIME);
        source.addFile(root.resolve("back.JPG"), 1, MTIME);
        source.addFile(root.resolve("notes.txt"), 1, MTIME);
        source.addFile(root.resolve("song.flac"), 1, MTIME);
        source.addFile(root.resolve("README"), 1, MTIME);

        ScanSummary summary = scan(ScanRequest.of(root, false));

        assertThat(summary.ignoredFiles()).isEqualTo(5);
        assertThat(summary.ignoredByExtension()).isEqualTo(Map.of("jpg", 2, "txt", 1, "flac", 1, "", 1));
        assertThat(reader.readCount()).isEqualTo(1);
    }

    @Test
    void recursiveFlagIsPassedThrough() {
        mp3("a.mp3", "A");
        mp3("sub/b.mp3", "B");
        assertThat(scan(ScanRequest.of(root, false)).found()).isEqualTo(1);
        items.clear();
        assertThat(scan(ScanRequest.of(root, true)).found()).isEqualTo(2);
    }

    @Test
    void hiddenEntriesAreReportedSortedAndScannedOnRequest() {
        mp3("a.mp3", "A");
        mp3("._a.mp3", "resource fork");
        mp3(".cache/x.mp3", "X");
        source.addDirectory(root.resolve("out"));

        ScanSummary summary = scan(new ScanRequest(root, true, false, Set.of(MediaFormat.MP3),
                Set.of(root.resolve("out")), V1Comparison.LENIENT));

        assertThat(paths()).containsExactly(root.resolve("a.mp3"));
        assertThat(summary.skipped()).containsExactly(
                new SkippedEntry(root.resolve("._a.mp3"), SkipReason.HIDDEN),
                new SkippedEntry(root.resolve(".cache"), SkipReason.HIDDEN),
                new SkippedEntry(root.resolve("out"), SkipReason.EXCLUDED));

        items.clear();
        ScanSummary withHidden = scan(new ScanRequest(root, true, true, Set.of(MediaFormat.MP3), Set.of(),
                V1Comparison.LENIENT));
        assertThat(withHidden.found()).isEqualTo(3);
        assertThat(withHidden.skipped()).isEmpty();
    }

    @Test
    void v1ComparisonModeDecidesTheConflicts() {
        Path file = source.addFile(root.resolve("a.mp3"), 1, MTIME);
        reader.answer(file, new TagReadResult(MediaFormat.MP3, List.of(
                new TagLayer(TagVersion.ID3V2_3, MediaMetadata.empty().with(Field.ARTIST, new Text("Ђорђе"))),
                new TagLayer(TagVersion.ID3V1_1, MediaMetadata.empty().with(Field.ARTIST, new Text("Djordje")))),
                Optional.empty(), Optional.empty(), List.of()));

        scan(ScanRequest.of(root, false));
        scan(new ScanRequest(root, false, false, Set.of(MediaFormat.MP3), Set.of(), V1Comparison.STRICT));

        assertThat(((ScannedFile) items.get(0)).conflicts()).isEmpty();
        assertThat(((ScannedFile) items.get(1)).conflicts()).containsExactly(Field.ARTIST);
    }

    @Test
    void missingRootFailsWithoutCallingTheSink() {
        ScanSummary summary = service.scan(ScanRequest.of(root.resolve("missing"), true),
                item -> { throw new AssertionError("sink must not be called"); },
                ProgressListener.NONE, Cancellation.NONE);
        assertThat(summary.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(summary.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.ROOT_NOT_FOUND);
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test
    void unreadableRootFailsWithListingAccessDenied() {
        source.denyAccess(root);
        ScanSummary summary = scan(ScanRequest.of(root, true));
        assertThat(summary.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(summary.issues()).extracting(i -> i.code()).containsExactly(IssueCode.LISTING_ACCESS_DENIED);
    }

    @Test
    void cancellationDeliversAPrefix() {
        List<Path> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            all.add(mp3("%02d.mp3".formatted(i), "T" + i));
        }
        CancellationSource cancel = new CancellationSource();
        ScanSummary summary = service.scan(ScanRequest.of(root, false), item -> {
            items.add(item);
            if (items.size() == 3) {
                cancel.cancel();
            }
        }, ProgressListener.NONE, cancel);
        assertThat(summary.status()).isEqualTo(ScanStatus.CANCELLED);
        assertThat(paths()).containsExactlyElementsOf(all.subList(0, 3));
        assertThat(reader.readCount()).isEqualTo(3);
    }

    @Test
    void cancellationBeforeListingIsCancelled() {
        mp3("a.mp3", "A");
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        ScanSummary summary = service.scan(ScanRequest.of(root, false), items::add, ProgressListener.NONE, cancel);
        assertThat(summary.status()).isEqualTo(ScanStatus.CANCELLED);
        assertThat(items).isEmpty();
    }

    @Test
    void resultsAreStreamedNotBuffered() {
        TagReadResult result = tagged("Same");
        for (int i = 0; i < 20_000; i++) {
            Path file = source.addFile(root.resolve("d%03d/%05d.mp3".formatted(i % 100, i)), 1, MTIME);
            reader.answer(file, result);
        }
        long[] emitted = {0};
        ScanSummary summary = service.scan(ScanRequest.of(root, true), item -> {
            emitted[0]++;
            // the reader has read exactly as many files as were emitted: nothing is read ahead or buffered
            assertThat(reader.readCount()).isEqualTo(emitted[0]);
        }, ProgressListener.NONE, Cancellation.NONE);
        assertThat(summary.found()).isEqualTo(20_000);
        assertThat(emitted[0]).isEqualTo(20_000);
    }

    @Test
    void sinkExceptionPropagatesAndStopsReading() {
        mp3("a.mp3", "A");
        mp3("b.mp3", "B");
        mp3("c.mp3", "C");
        assertThatThrownBy(() -> service.scan(ScanRequest.of(root, false), item -> {
            throw new IllegalStateException("boom");
        }, ProgressListener.NONE, Cancellation.NONE)).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThat(reader.readCount()).isEqualTo(1);
    }

    @Test
    void progressReportsListThenRead() {
        mp3("a.mp3", "A");
        mp3("b.mp3", "B");
        List<ProgressEvent> events = new ArrayList<>();
        service.scan(ScanRequest.of(root, false), items::add, events::add, Cancellation.NONE);
        assertThat(events.getFirst().phase()).isEqualTo("list");
        List<String> phases = events.stream().map(ProgressEvent::phase).distinct().toList();
        assertThat(phases).containsExactly("list", "read");
        ProgressEvent last = events.getLast();
        assertThat(last.phase()).isEqualTo("read");
        assertThat(last.done()).isEqualTo(last.total()).isEqualTo(2);
    }

    @Test
    void anUnexpectedReaderExceptionBecomesAnUnreadableFileAndTheScanContinues() {
        Path bad = source.addFile(root.resolve("1-bad.mp3"), 1, MTIME);
        Path ok = mp3("2-ok.mp3", "OK");
        TagReader broken = new TagReader() {
            @Override
            public Set<MediaFormat> readableFormats() {
                return Set.of(MediaFormat.MP3);
            }

            @Override
            public TagReadResult read(Path file) throws MediaAccessException {
                if (file.equals(bad)) {
                    throw new IllegalStateException("adapter bug");
                }
                return reader.read(file);
            }

            @Override
            public Optional<CoverArt> readCover(Path file) {
                return Optional.empty();
            }
        };
        ScanSummary summary = new ScanMediaService(source, broken).scan(ScanRequest.of(root, false), items::add,
                ProgressListener.NONE, Cancellation.NONE);
        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(paths()).containsExactly(bad, ok);
        assertThat(items.getFirst()).isInstanceOfSatisfying(UnreadableFile.class,
                f -> assertThat(f.error().message()).contains("adapter bug"));
    }

    @Test
    void filesWithErrorIssuesAreCounted() {
        Path damaged = source.addFile(root.resolve("damaged.mp3"), 1, MTIME);
        reader.answer(damaged, new TagReadResult(MediaFormat.MP3, List.of(), Optional.empty(), Optional.empty(),
                List.of(Issue.error(IssueCode.TAG_DAMAGED, "damaged").forFile(damaged))));
        mp3("fine.mp3", "Fine");
        ScanSummary summary = scan(ScanRequest.of(root, false));
        assertThat(summary.read()).isEqualTo(2);
        assertThat(summary.filesWithErrors()).isEqualTo(1);
    }

    @Test
    void preconditions() {
        ScanRequest request = ScanRequest.of(root, false);
        assertThatThrownBy(() -> service.scan(null, items::add, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, null, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, items::add, null, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, items::add, ProgressListener.NONE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanMediaService(null, reader)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanMediaService(source, null)).isInstanceOf(NullPointerException.class);
    }
}
