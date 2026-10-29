package dev.rs.slava.icxc.musicstudio.core.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeTagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.InMemoryMediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScanMediaServiceTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private final InMemoryMediaFileSource files = new InMemoryMediaFileSource();
    private final FakeTagReader reader = new FakeTagReader();
    private final ScanMediaService service = new ScanMediaService(files, reader);
    private final List<ScanItem> items = new ArrayList<>();

    private Path add(String relative, Fixture fixture) {
        Path path = ROOT.resolve(relative);
        files.addFile(path);
        if (FixtureCatalog.expected(fixture) instanceof FixtureCatalog.Expectation.Readable readable) {
            reader.register(path, readable.result());
        } else {
            reader.failWith(path, Reason.CORRUPT);
        }
        return path;
    }

    private Path addNoTags(String relative) {
        return add(relative, Fixture.NO_TAGS);
    }

    private ScanSummary scan(ScanRequest request) {
        return service.scan(request, items::add, ProgressListener.NONE, Cancellation.NONE);
    }

    @Test
    void itemsAreSortedByPathOrderEvenThoughTheSourceListsInRandomOrder() {
        for (String name : List.of("b.mp3", "A.mp3", "c/z.mp3", "c/B.mp3", "a.mp3", "d.mp3", "C.mp3")) {
            addNoTags(name);
        }
        scan(ScanRequest.of(ROOT).withRecursive(true));
        List<Path> paths = items.stream().map(ScanItem::path).toList();
        assertThat(paths).hasSize(7).isSortedAccordingTo(PathOrder.COMPARATOR);
    }

    @Test
    void untaggedFileIsDeliveredWithEmptyLayers() {
        Path file = addNoTags("plain.mp3");
        ScanSummary summary = scan(ScanRequest.of(ROOT));
        assertThat(items).singleElement().isInstanceOfSatisfying(ScannedFile.class, scanned -> {
            assertThat(scanned.path()).isEqualTo(file);
            assertThat(scanned.tags().layers()).isEmpty();
            assertThat(scanned.conflicts()).isEmpty();
        });
        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(summary.read()).isEqualTo(1);
    }

    @Test
    void corruptFileIsReportedAndScanContinues() {
        addNoTags("a.mp3");
        Path bad = add("b.mp3", Fixture.CORRUPT);
        addNoTags("c.mp3");
        ScanSummary summary = scan(ScanRequest.of(ROOT));
        assertThat(items).hasSize(3);
        assertThat(items.get(1)).isInstanceOfSatisfying(UnreadableFile.class, unreadable -> {
            assertThat(unreadable.path()).isEqualTo(bad);
            assertThat(unreadable.error().code()).isEqualTo(IssueCode.READ_FAILED);
            assertThat(unreadable.error().message()).contains("CORRUPT");
        });
        assertThat(items.get(2)).isInstanceOf(ScannedFile.class);
        assertThat(summary.found()).isEqualTo(3);
        assertThat(summary.read()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    void otherExtensionsAreCountedNotRead() {
        addNoTags("song.mp3");
        for (String name : List.of("cover.jpg", "cover2.JPG", "notes.txt", "other.flac", "README")) {
            files.addFile(ROOT.resolve(name));
        }
        ScanSummary summary = scan(ScanRequest.of(ROOT));
        assertThat(items).hasSize(1);
        assertThat(summary.ignoredFiles()).isEqualTo(5);
        assertThat(summary.ignoredByExtension())
                .containsExactlyInAnyOrderEntriesOf(Map.of("jpg", 2, "txt", 1, "flac", 1, ScanSummary.NO_EXTENSION, 1));
        assertThat(reader.readCount()).isEqualTo(1);
    }

    @Test
    void recursiveFlagIsPassedThrough() {
        addNoTags("top.mp3");
        addNoTags("sub/deep.mp3");
        scan(ScanRequest.of(ROOT));
        assertThat(items).hasSize(1);
        items.clear();
        scan(ScanRequest.of(ROOT).withRecursive(true));
        assertThat(items).hasSize(2);
    }

    @Test
    void hiddenFlagIsPassedThrough() {
        addNoTags("visible.mp3");
        addNoTags(".hidden.mp3");
        ScanSummary withoutHidden = scan(ScanRequest.of(ROOT));
        assertThat(items).hasSize(1);
        assertThat(withoutHidden.skipped()).containsExactly(new SkippedEntry(ROOT.resolve(".hidden.mp3"), SkipReason.HIDDEN));
        items.clear();
        ScanSummary withHidden = scan(ScanRequest.of(ROOT).withIncludeHidden(true));
        assertThat(items).hasSize(2);
        assertThat(withHidden.skipped()).isEmpty();
    }

    @Test
    void skippedEntriesAreSortedAndComeFromTheListing() {
        addNoTags("a.mp3");
        files.addFile(ROOT.resolve(".z"));
        files.addDirectory(ROOT.resolve(".b"));
        files.addDirectoryLink(ROOT.resolve("link"));
        files.addFile(ROOT.resolve(".A"));
        ScanSummary summary = scan(ScanRequest.of(ROOT));
        assertThat(summary.skipped()).extracting(SkippedEntry::path)
                .containsExactly(ROOT.resolve(".A"), ROOT.resolve(".b"), ROOT.resolve(".z"), ROOT.resolve("link"));
        assertThat(summary.skipped()).extracting(SkippedEntry::reason)
                .containsExactly(SkipReason.HIDDEN, SkipReason.HIDDEN, SkipReason.HIDDEN, SkipReason.DIRECTORY_SYMLINK);
    }

    @Test
    void v1ComparisonModeDecidesTheConflicts() {
        addNoTags("a.mp3");
        add("same.mp3", Fixture.V11_V23_SAME);
        scan(ScanRequest.of(ROOT).withV1Comparison(V1Comparison.LENIENT));
        assertThat(items).filteredOn(i -> i.path().endsWith("same.mp3")).singleElement()
                .isInstanceOfSatisfying(ScannedFile.class, f -> assertThat(f.conflicts()).isEmpty());
        items.clear();
        scan(ScanRequest.of(ROOT).withV1Comparison(V1Comparison.STRICT));
        assertThat(items).filteredOn(i -> i.path().endsWith("same.mp3")).singleElement()
                .isInstanceOfSatisfying(ScannedFile.class,
                        f -> assertThat(f.conflicts()).containsExactlyInAnyOrder(Field.TITLE, Field.ARTIST, Field.ALBUM));
    }

    @Test
    void differentArtistIsAConflictInBothModes() {
        add("diff.mp3", Fixture.V11_V23_DIFF);
        scan(ScanRequest.of(ROOT));
        assertThat(items).singleElement().isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.conflicts()).containsExactly(Field.ARTIST));
    }

    @Test
    void missingRootFailsWithoutCallingTheSink() {
        ScanSummary summary = scan(ScanRequest.of(ROOT.resolve("nowhere")));
        assertThat(summary.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(summary.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.ROOT_NOT_FOUND);
            assertThat(issue.file()).contains(ROOT.resolve("nowhere"));
        });
        assertThat(items).isEmpty();
        assertThat(reader.readCount()).isZero();
    }

    @Test
    void cancellationStopsAfterAPrefix() {
        for (int i = 0; i < 10; i++) {
            addNoTags("f" + i + ".mp3");
        }
        CancellationSource cancel = new CancellationSource();
        ScanSummary summary = service.scan(ScanRequest.of(ROOT), item -> {
            items.add(item);
            if (items.size() == 3) {
                cancel.cancel();
            }
        }, ProgressListener.NONE, cancel);
        assertThat(summary.status()).isEqualTo(ScanStatus.CANCELLED);
        assertThat(items).extracting(ScanItem::path)
                .containsExactly(ROOT.resolve("f0.mp3"), ROOT.resolve("f1.mp3"), ROOT.resolve("f2.mp3"));
        assertThat(summary.found()).isEqualTo(10);
        assertThat(summary.read()).isEqualTo(3);
        assertThat(reader.readCount()).isEqualTo(3);
    }

    @Test
    void cancelledBeforeStartDeliversNothing() {
        addNoTags("a.mp3");
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        ScanSummary summary = service.scan(ScanRequest.of(ROOT), items::add, ProgressListener.NONE, cancel);
        assertThat(summary.status()).isEqualTo(ScanStatus.CANCELLED);
        assertThat(items).isEmpty();
    }

    @Test
    void resultsAreStreamedNotBuffered() {
        int count = 20_000;
        for (int i = 0; i < count; i++) {
            addNoTags(String.format("d%02d/f%05d.mp3", i % 50, i));
        }
        int[] delivered = {0};
        ScanSummary summary = service.scan(ScanRequest.of(ROOT).withRecursive(true), item -> {
            delivered[0]++;
            // lazy: exactly as many files were read as have been delivered so far
            assertThat(reader.readCount()).isEqualTo(delivered[0]);
        }, ProgressListener.NONE, Cancellation.NONE);
        assertThat(delivered[0]).isEqualTo(count);
        assertThat(summary.read()).isEqualTo(count);
    }

    @Test
    void sinkFailurePropagatesAndStopsReading() {
        for (int i = 0; i < 5; i++) {
            addNoTags("f" + i + ".mp3");
        }
        IllegalStateException boom = new IllegalStateException("sink broke");
        assertThatThrownBy(() -> service.scan(ScanRequest.of(ROOT), item -> {
            if (items.size() == 1) {
                throw boom;
            }
            items.add(item);
        }, ProgressListener.NONE, Cancellation.NONE)).isSameAs(boom);
        assertThat(reader.readCount()).isEqualTo(2);
    }

    @Test
    void progressReportsListThenReadAndEndsAtTotal() {
        for (int i = 0; i < 4; i++) {
            addNoTags("f" + i + ".mp3");
        }
        files.addFile(ROOT.resolve("x.txt"));
        List<ProgressEvent> events = new ArrayList<>();
        service.scan(ScanRequest.of(ROOT), items::add, events::add, Cancellation.NONE);
        List<String> phases = events.stream().map(ProgressEvent::phase).distinct().toList();
        assertThat(phases).containsExactly("list", "read");
        assertThat(events.stream().map(ProgressEvent::phase).toList().lastIndexOf("list"))
                .isLessThan(events.stream().map(ProgressEvent::phase).toList().indexOf("read"));
        ProgressEvent last = events.get(events.size() - 1);
        assertThat(last.phase()).isEqualTo("read");
        assertThat(last.done()).isEqualTo(4);
        assertThat(last.total()).isEqualTo(4);
        assertThat(events).filteredOn(e -> e.phase().equals("read")).extracting(ProgressEvent::done)
                .isSorted();
    }

    @Test
    void formatsNotReadableByTheReaderAreIgnored() {
        addNoTags("a.mp3");
        ScanRequest request = new ScanRequest(ROOT, false, false, Set.of(dev.rs.slava.icxc.musicstudio.core.model.MediaFormat.MP3), Set.of(),
                V1Comparison.LENIENT);
        assertThat(scan(request).found()).isEqualTo(1);
    }

    @Test
    void preconditions() {
        ScanRequest request = ScanRequest.of(ROOT);
        assertThatThrownBy(() -> new ScanMediaService(null, reader)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScanMediaService(files, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(null, items::add, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, null, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, items::add, null, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.scan(request, items::add, ProgressListener.NONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requestRules() {
        assertThatThrownBy(() -> ScanRequest.of(Path.of("relative"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanRequest(ROOT, false, false, Set.of(), Set.of(), V1Comparison.LENIENT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
