package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract of {@link MediaFileSource}. Every implementation (local disk, in-memory fake, any future one) extends
 * this class and provides a tree and a subject that sees it.
 */
public abstract class MediaFileSourceContract {

    @TempDir
    protected Path tempDir;

    protected TestTree tree;
    protected MediaFileSource subject;
    private String deniedDirectory;

    /** @return an empty tree rooted below {@code dir} */
    protected abstract TestTree newTree(Path dir);

    /** @return the implementation under test, able to see everything built in {@code tree} */
    protected abstract MediaFileSource subject(TestTree tree);

    @BeforeEach
    void setUpTree() {
        tree = newTree(tempDir.resolve("root-" + System.nanoTime()));
        subject = subject(tree);
    }

    @AfterEach
    void restorePermissions() {
        if (deniedDirectory != null) {
            tree.restoreAccess(deniedDirectory);
        }
    }

    private static ListingOptions flat() {
        return ListingOptions.defaults();
    }

    private static ListingOptions recursive() {
        return ListingOptions.defaults().withRecursive(true);
    }

    private DirectoryListing list(ListingOptions options) throws MediaAccessException {
        return subject.list(tree.root(), options, Cancellation.NONE);
    }

    private Path p(String relative) {
        return tree.root().resolve(relative);
    }

    @Test
    void flatListingHasOnlyDirectChildren() throws Exception {
        tree.file("a.mp3", 3);
        tree.file("notes.txt", 1);
        tree.file("sub/c.mp3", 3);
        assertThat(list(flat()).files()).containsExactlyInAnyOrder(p("a.mp3"), p("notes.txt"));
    }

    @Test
    void recursiveListingFindsNestedFiles() throws Exception {
        tree.file("a.mp3", 3);
        tree.file("sub/c.mp3", 3);
        tree.file("sub/deeper/d.MP3", 3);
        assertThat(list(recursive()).files()).containsExactlyInAnyOrder(p("a.mp3"), p("sub/c.mp3"), p("sub/deeper/d.MP3"));
    }

    @Test
    void resultsAreAbsoluteUniqueAndUnderRoot() throws Exception {
        for (int i = 0; i < 20; i++) {
            tree.file("d" + (i % 3) + "/f" + i + ".mp3", 1);
        }
        DirectoryListing listing = list(recursive());
        assertThat(listing.root()).isEqualTo(tree.root());
        assertThat(listing.files()).hasSize(20).doesNotHaveDuplicates()
                .allSatisfy(f -> {
                    // plain java.nio checks: AssertJ's Path assertions resolve real paths on disk
                    assertThat(f.isAbsolute()).as("absolute: %s", f).isTrue();
                    assertThat(f.startsWith(tree.root())).as("under root: %s", f).isTrue();
                    assertThat(f.equals(f.normalize())).as("normalised: %s", f).isTrue();
                });
    }

    @Test
    void emptyDirectoryGivesEmptyListing() throws Exception {
        DirectoryListing listing = list(recursive());
        assertThat(listing.files()).isEmpty();
        assertThat(listing.skipped()).isEmpty();
        assertThat(listing.issues()).isEmpty();
    }

    @Test
    void hiddenEntriesAreSkippedAndReportedOnce() throws Exception {
        tree.file("visible.mp3", 1);
        tree.file(".hidden.mp3", 1);
        tree.file("._song.mp3", 1);
        tree.file(".cache/inside.mp3", 1);
        tree.file("sub/.also-hidden.mp3", 1);
        DirectoryListing listing = list(recursive());
        assertThat(listing.files()).containsExactly(p("visible.mp3"));
        assertThat(listing.skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(p(".hidden.mp3"), SkipReason.HIDDEN),
                new SkippedEntry(p("._song.mp3"), SkipReason.HIDDEN),
                new SkippedEntry(p(".cache"), SkipReason.HIDDEN),
                new SkippedEntry(p("sub/.also-hidden.mp3"), SkipReason.HIDDEN));
    }

    @Test
    void hiddenEntriesAreReportedInFlatListingToo() throws Exception {
        tree.file(".hidden.mp3", 1);
        tree.directory(".cache");
        assertThat(list(flat()).skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(p(".hidden.mp3"), SkipReason.HIDDEN), new SkippedEntry(p(".cache"), SkipReason.HIDDEN));
    }

    @Test
    void includeHiddenListsThem() throws Exception {
        tree.file("visible.mp3", 1);
        tree.file(".hidden.mp3", 1);
        tree.file(".cache/inside.mp3", 1);
        DirectoryListing listing = list(recursive().withIncludeHidden(true));
        assertThat(listing.files()).containsExactlyInAnyOrder(p("visible.mp3"), p(".hidden.mp3"), p(".cache/inside.mp3"));
        assertThat(listing.skipped()).isEmpty();
    }

    @Test
    void directorySymlinkIsSkippedNotFollowed() throws Exception {
        tree.file("real/song.mp3", 1);
        assumeTrue(tree.symlinkToDirectory("link", "real").isPresent(), "symbolic links not supported");
        DirectoryListing listing = list(recursive());
        assertThat(listing.files()).containsExactly(p("real/song.mp3"));
        assertThat(listing.skipped()).containsExactly(new SkippedEntry(p("link"), SkipReason.DIRECTORY_SYMLINK));
        assertThat(listing.files()).doesNotContainAnyElementsOf(
                listing.skipped().stream().map(SkippedEntry::path).toList());
    }

    @Test
    void fileSymlinkIsListedAsLink() throws Exception {
        tree.file("real.mp3", 1);
        assumeTrue(tree.symlinkToFile("alias.mp3", "real.mp3").isPresent(), "symbolic links not supported");
        assertThat(list(flat()).files()).containsExactlyInAnyOrder(p("real.mp3"), p("alias.mp3"));
    }

    @Test
    void symlinkLoopTerminates() throws Exception {
        tree.file("sub/song.mp3", 1);
        assumeTrue(tree.symlinkToDirectory("sub/loop", "").isPresent(), "symbolic links not supported");
        DirectoryListing listing = list(recursive());
        assertThat(listing.files()).containsExactly(p("sub/song.mp3"));
        assertThat(listing.skipped()).containsExactly(new SkippedEntry(p("sub/loop"), SkipReason.DIRECTORY_SYMLINK));
    }

    @Test
    void excludedDirectoryIsSkippedWithItsContent() throws Exception {
        tree.file("keep/a.mp3", 1);
        tree.file("drop/b.mp3", 1);
        tree.file("drop/nested/c.mp3", 1);
        DirectoryListing listing = list(recursive().withExcludedDirectories(Set.of(p("drop"))));
        assertThat(listing.files()).containsExactly(p("keep/a.mp3"));
        assertThat(listing.skipped()).containsExactly(new SkippedEntry(p("drop"), SkipReason.EXCLUDED));
    }

    @Test
    void unreadableSubdirectoryIsReportedOnceAndRestIsListed() throws Exception {
        tree.file("ok/a.mp3", 1);
        tree.file("locked/b.mp3", 1);
        tree.file("top.mp3", 1);
        assumeTrue(tree.denyAccess("locked"), "cannot make a directory unreadable here (Windows or root)");
        deniedDirectory = "locked";
        DirectoryListing listing = list(recursive());
        assertThat(listing.files()).containsExactlyInAnyOrder(p("ok/a.mp3"), p("top.mp3"));
        assertThat(listing.issues()).hasSize(1).allSatisfy(issue -> {
            assertThat(issue.code()).isEqualTo(IssueCode.LISTING_ACCESS_DENIED);
            assertThat(issue.file()).contains(p("locked"));
        });
    }

    @Test
    void missingRootIsNotFound() {
        assertThatThrownBy(() -> subject.list(p("missing"), flat(), Cancellation.NONE))
                .isInstanceOfSatisfying(MediaAccessException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND);
                    assertThat(e.path()).isEqualTo(p("missing"));
                });
    }

    @Test
    void rootThatIsAFileIsNotADirectory() {
        Path file = tree.file("plain.txt", 1);
        assertThatThrownBy(() -> subject.list(file, flat(), Cancellation.NONE))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NOT_A_DIRECTORY));
    }

    @Test
    void cancelledBeforeStartThrows() {
        tree.file("a.mp3", 1);
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        assertThatThrownBy(() -> subject.list(tree.root(), recursive(), cancel))
                .isInstanceOf(OperationCancelledException.class);
    }

    @Test
    void fingerprintMatchesTheFile() throws Exception {
        Path file = tree.file("a.mp3", 1234);
        FileFingerprint fingerprint = subject.fingerprint(file);
        assertThat(fingerprint.sizeBytes()).isEqualTo(1234);
        assertThat(fingerprint).isEqualTo(tree.expectedFingerprint(file));
    }

    @Test
    void fingerprintOfMissingFileIsNotFound() {
        assertThatThrownBy(() -> subject.fingerprint(p("missing.mp3")))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NOT_FOUND));
    }

    @Test
    void listingAndFingerprintChangeNothing() throws Exception {
        tree.file("a.mp3", 5);
        tree.file("sub/b.mp3", 6);
        tree.file(".hidden", 1);
        Map<String, String> before = tree.snapshot();
        DirectoryListing listing = list(recursive());
        for (Path file : listing.files()) {
            subject.fingerprint(file);
        }
        assertThat(tree.snapshot()).isEqualTo(before);
    }

    @Test
    void preconditions() {
        assertThatThrownBy(() -> subject.list(null, flat(), Cancellation.NONE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.list(Path.of("relative"), flat(), Cancellation.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subject.list(tree.root(), null, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.list(tree.root(), flat(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.fingerprint(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.fingerprint(Path.of("relative.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listingCanBeRepeatedWithTheSameResult() throws Exception {
        tree.file("a.mp3", 1);
        tree.file("b/c.mp3", 1);
        List<Path> first = list(recursive()).files();
        List<Path> second = list(recursive()).files();
        assertThat(second).containsExactlyInAnyOrderElementsOf(first);
    }
}
