package dev.rs.slava.icxc.musicstudio.core.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.CancellationSource;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Executable form of the {@link MediaFileSource} Javadoc contract. The local-disk adapter and the in-memory fake
 * both extend it; they build the test tree through the {@link Tree} hook (on disk under a {@code @TempDir}, or in
 * memory under a {@code @TempDir}-rooted absolute path).
 */
public abstract class MediaFileSourceContract {

    /** Builds the directory tree the subject sees. Relative paths use "/" and are resolved against the root. */
    protected interface Tree {

        /** @return the absolute root directory; it exists */
        Path root();

        /** Creates a regular file (and missing parents) with the given content and modification time. */
        Path file(String relative, byte[] content, Instant lastModified);

        /** Creates a directory (and missing parents). */
        Path dir(String relative);

        /** Creates a symbolic link to a file or directory. */
        Path symlink(String relative, String targetRelative);

        /** Makes a directory unreadable. Afterwards {@link #restoreAccess()} must undo it. */
        void denyAccess(String relativeDir);

        /** Undoes every {@link #denyAccess}. */
        void restoreAccess();

        /** @return every entry below the root with its modification time; used to prove nothing changed */
        Map<Path, Instant> snapshot();
    }

    private static final Instant MTIME = Instant.parse("2020-01-02T03:04:05Z");
    private static final byte[] CONTENT = {1, 2, 3, 4, 5};

    private Tree tree;

    /** @return a new, empty tree whose root directory exists */
    protected abstract Tree newTree();

    /** @return the source under test */
    protected abstract MediaFileSource subject();

    /** @return true if the implementation and platform support symbolic links */
    protected boolean supportsSymlinks() {
        return true;
    }

    /** @return true if {@link Tree#denyAccess} has an effect on this platform/user */
    protected boolean supportsAccessDenial() {
        return true;
    }

    @BeforeEach
    void createTree() {
        tree = newTree();
    }

    private Path root() {
        return tree.root();
    }

    private Path p(String relative) {
        return root().resolve(relative);
    }

    private Path file(String relative) {
        return tree.file(relative, CONTENT, MTIME);
    }

    private DirectoryListing list(boolean recursive, boolean includeHidden) throws MediaAccessException {
        return subject().list(root(), new ListingOptions(recursive, includeHidden, Set.of()), Cancellation.NONE);
    }

    // ---------------------------------------------------------------------------------------------------------

    @Test
    void flatListingContainsOnlyTheRootsFiles() throws Exception {
        file("a.mp3");
        file("notes.txt");
        file("sub/b.mp3");
        DirectoryListing listing = list(false, false);
        assertThat(listing.root()).isEqualTo(root());
        assertThat(listing.files()).containsExactlyInAnyOrder(p("a.mp3"), p("notes.txt"));
        assertThat(listing.skipped()).isEmpty();
        assertThat(listing.issues()).isEmpty();
    }

    @Test
    void recursiveListingContainsAllFiles() throws Exception {
        file("a.mp3");
        file("sub/b.mp3");
        file("sub/deeper/c.txt");
        tree.dir("empty");
        DirectoryListing listing = list(true, false);
        assertThat(listing.files()).containsExactlyInAnyOrder(p("a.mp3"), p("sub/b.mp3"), p("sub/deeper/c.txt"))
                .allSatisfy(f -> {
                    assertThat(f.isAbsolute()).isTrue();
                    assertThat(f.startsWith(root())).isTrue();
                    assertThat(f.normalize()).isEqualTo(f);
                })
                .doesNotHaveDuplicates();
    }

    @Test
    void hiddenEntriesAreSkippedAndRecordedOnce() throws Exception {
        file("visible.mp3");
        file(".hidden.mp3");
        file("._song.mp3");
        file(".cache/x.mp3");
        file(".cache/y.mp3");
        file("sub/.h.mp3");
        file("sub/ok.mp3");
        DirectoryListing listing = list(true, false);
        assertThat(listing.files()).containsExactlyInAnyOrder(p("visible.mp3"), p("sub/ok.mp3"));
        assertThat(listing.skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(p(".hidden.mp3"), SkipReason.HIDDEN),
                new SkippedEntry(p("._song.mp3"), SkipReason.HIDDEN),
                new SkippedEntry(p(".cache"), SkipReason.HIDDEN),
                new SkippedEntry(p("sub/.h.mp3"), SkipReason.HIDDEN));
    }

    @Test
    void hiddenEntriesAreListedWhenIncluded() throws Exception {
        file("visible.mp3");
        file(".hidden.mp3");
        file(".cache/x.mp3");
        DirectoryListing listing = list(true, true);
        assertThat(listing.files()).containsExactlyInAnyOrder(p("visible.mp3"), p(".hidden.mp3"), p(".cache/x.mp3"));
        assertThat(listing.skipped()).isEmpty();
    }

    @Test
    void flatListingRecordsHiddenFilesButNotSubdirectories() throws Exception {
        file(".hidden.mp3");
        file(".cache/x.mp3");
        file("sub/a.mp3");
        DirectoryListing listing = list(false, false);
        assertThat(listing.files()).isEmpty();
        assertThat(listing.skipped()).containsExactly(new SkippedEntry(p(".hidden.mp3"), SkipReason.HIDDEN));
    }

    @Test
    void excludedDirectoriesAreSkipped() throws Exception {
        file("a.mp3");
        file("out/x.mp3");
        file("out/deeper/y.mp3");
        DirectoryListing listing = subject().list(root(),
                new ListingOptions(true, false, Set.of(p("out"))), Cancellation.NONE);
        assertThat(listing.files()).containsExactly(p("a.mp3"));
        assertThat(listing.skipped()).containsExactly(new SkippedEntry(p("out"), SkipReason.EXCLUDED));
    }

    @Test
    void fileSymlinksAreIncludedAndDirectorySymlinksAreNotFollowed() throws Exception {
        assumeTrue(supportsSymlinks(), "symbolic links not supported");
        file("real/a.mp3");
        tree.symlink("link.mp3", "real/a.mp3");
        tree.symlink("linkdir", "real");
        tree.symlink("real/loop", ".");
        DirectoryListing listing = list(true, false);
        assertThat(listing.files()).containsExactlyInAnyOrder(p("real/a.mp3"), p("link.mp3"));
        assertThat(listing.skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(p("linkdir"), SkipReason.DIRECTORY_SYMLINK),
                new SkippedEntry(p("real/loop"), SkipReason.DIRECTORY_SYMLINK));
    }

    @Test
    void brokenSymlinksAreRecordedAsSkipped() throws Exception {
        assumeTrue(supportsSymlinks(), "symbolic links not supported");
        file("a.mp3");
        tree.symlink("dangling.mp3", "nowhere.mp3");
        tree.symlink("sub/dangling-too.mp3", "../gone.mp3");
        DirectoryListing flat = list(false, false);
        assertThat(flat.files()).containsExactly(p("a.mp3"));
        assertThat(flat.skipped()).containsExactly(new SkippedEntry(p("dangling.mp3"), SkipReason.BROKEN_SYMLINK));
        DirectoryListing recursive = list(true, false);
        assertThat(recursive.skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(p("dangling.mp3"), SkipReason.BROKEN_SYMLINK),
                new SkippedEntry(p("sub/dangling-too.mp3"), SkipReason.BROKEN_SYMLINK));
    }

    @Test
    void unreadableSubdirectoryIsReportedOnceAndListingContinues() throws Exception {
        assumeTrue(supportsAccessDenial(), "access denial not supported");
        file("a.mp3");
        file("locked/secret.mp3");
        file("open/b.mp3");
        tree.denyAccess("locked");
        try {
            DirectoryListing listing = list(true, false);
            assertThat(listing.files()).containsExactlyInAnyOrder(p("a.mp3"), p("open/b.mp3"));
            assertThat(listing.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.code()).isEqualTo(IssueCode.LISTING_ACCESS_DENIED);
                assertThat(issue.severity()).isEqualTo(Severity.WARNING);
                assertThat(issue.file()).contains(p("locked"));
            });
        } finally {
            tree.restoreAccess();
        }
    }

    @Test
    void missingRootIsNotFound() {
        assertThatThrownBy(() -> subject().list(p("missing"), ListingOptions.defaults(), Cancellation.NONE))
                .isInstanceOfSatisfying(MediaAccessException.class, e -> {
                    assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND);
                    assertThat(e.path()).isEqualTo(p("missing"));
                });
    }

    @Test
    void rootThatIsAFileIsNotADirectory() {
        Path file = file("a.mp3");
        assertThatThrownBy(() -> subject().list(file, ListingOptions.defaults(), Cancellation.NONE))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_A_DIRECTORY));
    }

    @Test
    void cancellationBeforeStartThrows() {
        file("a.mp3");
        CancellationSource cancel = new CancellationSource();
        cancel.cancel();
        assertThatThrownBy(() -> subject().list(root(), ListingOptions.defaults(), cancel))
                .isInstanceOf(OperationCancelledException.class);
    }

    @Test
    void fingerprintMatchesSizeAndModificationTime() throws Exception {
        Path file = tree.file("a.mp3", new byte[1234], MTIME);
        assertThat(subject().fingerprint(file)).isEqualTo(new FileFingerprint(1234, MTIME));
    }

    @Test
    void fingerprintFollowsFileSymlinks() throws Exception {
        assumeTrue(supportsSymlinks(), "symbolic links not supported");
        tree.file("a.mp3", new byte[77], MTIME);
        Path link = tree.symlink("link.mp3", "a.mp3");
        assertThat(subject().fingerprint(link)).isEqualTo(new FileFingerprint(77, MTIME));
    }

    @Test
    void fingerprintOfMissingFileIsNotFound() {
        assertThatThrownBy(() -> subject().fingerprint(p("missing.mp3")))
                .isInstanceOfSatisfying(MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(MediaAccessException.Reason.NOT_FOUND));
    }

    @Test
    void listingChangesNothing() throws Exception {
        file("a.mp3");
        file(".hidden/b.mp3");
        file("sub/c.mp3");
        Map<Path, Instant> before = tree.snapshot();
        list(true, false);
        list(true, true);
        subject().fingerprint(p("a.mp3"));
        assertThat(tree.snapshot()).isEqualTo(before);
    }

    @Test
    void preconditions() {
        MediaFileSource source = subject();
        assertThatThrownBy(() -> source.list(null, ListingOptions.defaults(), Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> source.list(Path.of("relative"), ListingOptions.defaults(), Cancellation.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.list(root(), null, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> source.list(root(), ListingOptions.defaults(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> source.fingerprint(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> source.fingerprint(Path.of("relative.mp3")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
