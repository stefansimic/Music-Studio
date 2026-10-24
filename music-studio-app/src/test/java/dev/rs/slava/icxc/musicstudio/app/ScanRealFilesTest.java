package dev.rs.slava.icxc.musicstudio.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Story 001 edge cases through the real wiring (local disk + jaudiotagger): symlinks, hidden files, permissions,
 * non-ASCII names, ordering, ignored files, huge folders. Everything happens inside {@code @TempDir}; fixtures are
 * copied, and every test proves that nothing on disk changed.
 */
class ScanRealFilesTest {

    @TempDir
    Path tempDir;

    private Path music() {
        return tempDir.toAbsolutePath().resolve("music");
    }

    private MusicStudio studio() {
        Path base = tempDir.toAbsolutePath().resolve("app");
        return new MusicStudioFactory(new AppConfig(base.resolve("config"), base.resolve("data"), Clock.systemUTC()))
                .create();
    }

    private record Run(ScanSummary summary, List<ScanItem> items) {

        List<String> relativePaths(Path root) {
            return items.stream().map(i -> root.relativize(i.path()).toString().replace('\\', '/')).toList();
        }

        ScanItem item(Path path) {
            return items.stream().filter(i -> i.path().equals(path)).findFirst()
                    .orElseThrow(() -> new AssertionError("no item for " + path + " in " + items));
        }
    }

    private Run scan(Path root, boolean recursive, boolean includeHidden) {
        List<ScanItem> items = new ArrayList<>();
        return new Run(scan(root, recursive, includeHidden, items::add), items);
    }

    private ScanSummary scan(Path root, boolean recursive, boolean includeHidden, Consumer<ScanItem> sink) {
        ScanRequest request = new ScanRequest(root, recursive, includeHidden, Set.of(MediaFormat.MP3), Set.of(),
                V1Comparison.LENIENT);
        return studio().scan().scan(request, sink, ProgressListener.NONE, Cancellation.NONE);
    }

    /** Content hash + mtime of every regular file, and the target of every link, below a directory. */
    private static Map<String, String> snapshot(Path root) {
        Map<String, String> result = new TreeMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String key = root.relativize(file).toString();
                    if (Files.isSymbolicLink(file)) {
                        result.put(key, "link->" + Files.readSymbolicLink(file));
                    } else if (attrs.isRegularFile() && Files.isReadable(file)) {
                        result.put(key, Fixtures.sha256(file) + "@" + attrs.lastModifiedTime());
                    } else {
                        result.put(key, "size=" + attrs.size() + "@" + attrs.lastModifiedTime());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    result.put(root.relativize(file).toString(), "unreadable");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static boolean symlink(Path link, Path target) {
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    private static boolean posix(Path any) {
        return any.getFileSystem().supportedFileAttributeViews().contains("posix")
                && !"root".equals(System.getProperty("user.name"));
    }

    // ---------------------------------------------------------------------------------------------------------

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void symlinksThroughTheFullScan() {
        Path root = music();
        Path target = Fixtures.copyTo(Fixture.V23_LATIN1, root.resolve("real"), "a.mp3");
        assumeTrue(symlink(root.resolve("link.mp3"), target), "symbolic links not supported");
        symlink(root.resolve("linkdir"), root.resolve("real"));
        symlink(root.resolve("real/loop"), root);
        Map<String, String> before = snapshot(root);

        Run run = scan(root, true, false);

        assertThat(run.summary().status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(run.relativePaths(root)).containsExactly("link.mp3", "real/a.mp3");
        assertThat(run.item(root.resolve("link.mp3"))).isInstanceOfSatisfying(ScannedFile.class, f -> {
            assertThat(f.tags().layers()).isEqualTo(FixtureCatalog.require(Fixture.V23_LATIN1).layers());
            assertThat(f.fingerprint().sizeBytes()).as("fingerprint follows the link")
                    .isEqualTo(Fixtures.bytes(Fixture.V23_LATIN1).length);
        });
        assertThat(run.summary().skipped()).containsExactlyInAnyOrder(
                new SkippedEntry(root.resolve("linkdir"), SkipReason.DIRECTORY_SYMLINK),
                new SkippedEntry(root.resolve("real/loop"), SkipReason.DIRECTORY_SYMLINK));
        assertThat(run.summary().found()).isEqualTo(2);
        assertThat(snapshot(root)).isEqualTo(before);
        assertThat(Files.isSymbolicLink(root.resolve("link.mp3"))).isTrue();
    }

    /**
     * A dangling "song.mp3" link looks like an MP3 to the user. Story: unreadable files are listed with an error,
     * not dropped. Accept any visible form: an UnreadableFile item, a skipped entry or a summary issue.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void danglingMp3SymlinkIsNotSilentlyDropped() {
        Path root = music();
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "a.mp3");
        Path dangling = root.resolve("dangling.mp3");
        assumeTrue(symlink(dangling, root.resolve("nowhere.mp3")), "symbolic links not supported");

        Run run = scan(root, true, false);

        assertThat(run.summary().status()).isEqualTo(ScanStatus.COMPLETED);
        boolean asItem = run.items().stream().anyMatch(i -> i.path().equals(dangling) && i instanceof UnreadableFile);
        boolean asSkipped = run.summary().skipped().stream().anyMatch(e -> e.path().equals(dangling));
        boolean asIssue = run.summary().issues().stream().anyMatch(i -> i.file().equals(java.util.Optional.of(dangling)));
        assertThat(asItem || asSkipped || asIssue)
                .as("dangling.mp3 must be visible; items=%s summary=%s", run.items(), run.summary()).isTrue();
        assertThat(root.resolve("nowhere.mp3")).doesNotExist();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void rootGivenAsSymlinkToADirectoryIsScanned() {
        Path real = tempDir.toAbsolutePath().resolve("real-music");
        Fixtures.copyTo(Fixture.V23_LATIN1, real, "a.mp3");
        Path link = tempDir.toAbsolutePath().resolve("music-link");
        assumeTrue(symlink(link, real), "symbolic links not supported");

        Run run = scan(link, true, false);

        assertThat(run.summary().status()).as("summary %s", run.summary()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(run.items()).singleElement().isInstanceOf(ScannedFile.class);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void filesAndFoldersWithoutReadPermission() throws IOException {
        Path root = music();
        assumeTrue(posix(tempDir), "POSIX permissions as non-root only");
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "a.mp3");
        Path noPerm = Fixtures.copyTo(Fixture.V23_COVER, root, "b-noperm.mp3");
        Fixtures.copyTo(Fixture.V1_1, root, "c.mp3");
        Path locked = root.resolve("locked");
        Fixtures.copyTo(Fixture.V22, locked, "secret.mp3");
        Fixtures.copyTo(Fixture.V22, root.resolve("open"), "d.mp3");
        Map<String, String> before = snapshot(root);
        Files.setPosixFilePermissions(noPerm, PosixFilePermissions.fromString("---------"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            Run run = scan(root, true, false);

            assertThat(run.summary().status()).isEqualTo(ScanStatus.COMPLETED);
            assertThat(run.relativePaths(root)).containsExactly("a.mp3", "b-noperm.mp3", "c.mp3", "open/d.mp3");
            assertThat(run.item(noPerm)).isInstanceOfSatisfying(UnreadableFile.class, f -> {
                assertThat(f.error().code()).isEqualTo(IssueCode.READ_FAILED);
                assertThat(f.error().message()).containsIgnoringCase("access denied");
            });
            assertThat(run.items()).filteredOn(ScannedFile.class::isInstance).hasSize(3);
            assertThat(run.summary().issues()).anySatisfy(issue -> {
                assertThat(issue.code()).isEqualTo(IssueCode.LISTING_ACCESS_DENIED);
                assertThat(issue.file()).contains(locked);
            });
            assertThat(Files.getPosixFilePermissions(noPerm)).as("permissions untouched").isEmpty();
        } finally {
            Files.setPosixFilePermissions(noPerm, PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
        assertThat(snapshot(root)).isEqualTo(before);
    }

    @Test
    void hiddenFilesAndFoldersThroughTheFullScan() {
        Path root = music();
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "visible.mp3");
        Fixtures.copyTo(Fixture.V23_LATIN1, root, ".hidden.mp3");
        Fixtures.copyTo(Fixture.CORRUPT, root, "._visible.mp3"); // macOS AppleDouble: not audio
        Fixtures.copyTo(Fixture.V1_1, root.resolve(".Trash"), "deleted.mp3");
        Fixtures.copyTo(Fixture.V1_1, root.resolve("sub"), ".y.mp3");
        Fixtures.copyTo(Fixture.V1_1, root.resolve("sub"), "ok.mp3");
        Map<String, String> before = snapshot(root);

        Run hidden = scan(root, true, false);
        assertThat(hidden.relativePaths(root)).containsExactly("sub/ok.mp3", "visible.mp3");
        assertThat(hidden.summary().skipped()).extracting(SkippedEntry::reason).containsOnly(SkipReason.HIDDEN);
        assertThat(hidden.summary().skipped()).extracting(e -> root.relativize(e.path()).toString())
                .containsExactlyInAnyOrder(".hidden.mp3", "._visible.mp3", ".Trash", "sub/.y.mp3");
        assertThat(hidden.summary().found()).as("hidden files are not counted as found").isEqualTo(2);

        Run included = scan(root, true, true);
        assertThat(included.relativePaths(root)).containsExactlyInAnyOrder("._visible.mp3", ".hidden.mp3",
                ".Trash/deleted.mp3", "sub/.y.mp3", "sub/ok.mp3", "visible.mp3");
        assertThat(included.item(root.resolve("._visible.mp3"))).isInstanceOf(UnreadableFile.class);
        assertThat(included.summary().skipped()).isEmpty();
        assertThat(snapshot(root)).isEqualTo(before);
    }

    @Test
    void hiddenRootFolderIsStillScanned() {
        Path root = tempDir.toAbsolutePath().resolve(".music");
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "a.mp3");
        Run run = scan(root, true, false);
        assertThat(run.items()).singleElement().isInstanceOf(ScannedFile.class);
        assertThat(run.summary().skipped()).isEmpty();
    }

    @Test
    void nonAsciiNamesUpperCaseExtensionsAndIgnoredFiles() throws IOException {
        Path root = music();
        Fixtures.copyTo(Fixture.V23_UTF16_CYRILLIC, root.resolve("Ђорђе Балашевић"), "Љубав 🎵.mp3");
        Fixtures.copyTo(Fixture.V24_UTF8, root, "Müller – Grüße.MP3");
        Fixtures.copyTo(Fixture.V22, root.resolve("東京事変"), "歌.Mp3");
        Fixtures.copyTo(Fixture.V1_1, root, "čćšžđ ČĆŠŽĐ.mP3");
        Files.writeString(root.resolve("song.mp3.bak"), "backup");
        Files.writeString(root.resolve("song.flac"), "flac");
        Files.writeString(root.resolve("Cover.JPG"), "jpg");
        Files.writeString(root.resolve("README"), "no extension");
        Files.writeString(root.resolve("folder.jpg"), "jpg");
        Map<String, String> before = snapshot(root);

        Run run = scan(root, true, false);

        assertThat(run.summary().status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(run.items()).hasSize(4).allMatch(ScannedFile.class::isInstance);
        assertThat(run.relativePaths(root)).containsExactly(
                "Müller – Grüße.MP3", "čćšžđ ČĆŠŽĐ.mP3", "Ђорђе Балашевић/Љубав 🎵.mp3", "東京事変/歌.Mp3");
        assertThat(run.item(root.resolve("Ђорђе Балашевић/Љубав 🎵.mp3"))).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.tags().effective().display(Field.ARTIST)).contains("Ђорђе Балашевић"));
        assertThat(run.summary().ignoredFiles()).isEqualTo(5);
        assertThat(run.summary().ignoredByExtension()).containsEntry("bak", 1).containsEntry("flac", 1)
                .containsEntry("jpg", 2);
        assertThat(snapshot(root)).isEqualTo(before);
    }

    @Test
    void resultsAreSortedByPathWithFoldersGrouped() throws IOException {
        Path root = music();
        for (String name : List.of("b.mp3", "B/z.mp3", "a.mp3", "A.mp3", "a/y.mp3", "a/sub/x.mp3", "a/B.mp3",
                "track10.mp3", "track2.mp3")) {
            Fixtures.copyTo(Fixture.NO_TAGS, root, name);
        }
        assumeTrue(!Files.isSameFile(root.resolve("a.mp3"), root.resolve("A.mp3")), "case-sensitive FS only");

        Run run = scan(root, true, false);

        assertThat(run.relativePaths(root)).containsExactly(
                "a/B.mp3", "a/sub/x.mp3", "a/y.mp3", "A.mp3", "a.mp3", "B/z.mp3", "b.mp3",
                "track10.mp3", "track2.mp3");
    }

    @Test
    void nonRecursiveScanIgnoresSubfolders() {
        Path root = music();
        Fixtures.copyTo(Fixture.V1_1, root, "top.mp3");
        Fixtures.copyTo(Fixture.V1_1, root.resolve("sub"), "nested.mp3");
        Run run = scan(root, false, false);
        assertThat(run.relativePaths(root)).containsExactly("top.mp3");
        assertThat(run.summary().ignoredFiles()).as("subfolder files are neither found nor ignored").isZero();
    }

    @Test
    void emptyFolderCompletesWithNothingFound() throws IOException {
        Path root = Files.createDirectories(music());
        Run run = scan(root, true, false);
        assertThat(run.summary().status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(run.items()).isEmpty();
        assertThat(run.summary().found()).isZero();
    }

    @Test
    void fileDeletedDuringTheScanIsListedAsUnreadableAndTheScanContinues() throws IOException {
        Path root = music();
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "a.mp3");
        Path vanishing = Fixtures.copyTo(Fixture.V23_LATIN1, root, "b.mp3");
        Fixtures.copyTo(Fixture.V23_LATIN1, root, "c.mp3");
        List<ScanItem> items = new ArrayList<>();

        ScanSummary summary = scan(root, false, false, item -> {
            items.add(item);
            if (item.path().getFileName().toString().equals("a.mp3")) {
                try {
                    Files.delete(vanishing); // inside @TempDir: simulates another program
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });

        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(items).extracting(i -> i.getClass().getSimpleName())
                .containsExactly("ScannedFile", "UnreadableFile", "ScannedFile");
        assertThat(((UnreadableFile) items.get(1)).error().message()).isNotBlank();
    }

    @Test
    void everyFixtureThroughTheScanMatchesTheCatalogue() {
        Path root = music();
        for (Fixture fixture : Fixture.values()) {
            Fixtures.copyTo(fixture, root);
        }
        Map<String, String> before = snapshot(root);

        Run run = scan(root, false, false);

        for (Fixture fixture : Fixture.values()) {
            ScanItem item = run.item(root.resolve(fixture.fileName()));
            if (fixture.readable()) {
                assertThat(item).as(fixture.name()).isInstanceOfSatisfying(ScannedFile.class,
                        f -> assertThat(f.tags().layers()).isEqualTo(FixtureCatalog.require(fixture).layers()));
            } else {
                assertThat(item).as(fixture.name()).isInstanceOfSatisfying(UnreadableFile.class,
                        f -> assertThat(f.error().message()).isNotBlank());
            }
        }
        assertThat(run.item(root.resolve("v11-v23-diff.mp3"))).isInstanceOfSatisfying(ScannedFile.class, f -> {
            assertThat(f.conflicts()).containsExactly(Field.ARTIST);
            assertThat(f.tags().effective().display(Field.ARTIST)).as("v2 is primary").contains("Artist A");
            assertThat(f.tags().layer(TagFamily.ID3V1).orElseThrow().metadata().display(Field.ARTIST))
                    .as("v1 value still available").contains("Artist B");
        });
        assertThat(run.item(root.resolve("v11-v23-same.mp3"))).isInstanceOfSatisfying(ScannedFile.class,
                f -> assertThat(f.conflicts()).isEmpty());
        assertThat(run.item(root.resolve("no-tags.mp3"))).isInstanceOfSatisfying(ScannedFile.class, f -> {
            assertThat(f.tags().layers()).isEmpty();
            assertThat(f.tags().stream()).isPresent();
        });
        assertThat(run.item(root.resolve("v23-latin1.mp3"))).isInstanceOfSatisfying(ScannedFile.class, f -> {
            for (Field field : List.of(Field.TITLE, Field.ARTIST, Field.ALBUM_ARTIST, Field.ALBUM, Field.TRACK,
                    Field.DISC, Field.YEAR, Field.GENRE, Field.COMMENT)) {
                assertThat(f.tags().effective().get(field)).as("column %s", field).isPresent();
            }
            assertThat(f.tags().effective().get(Field.TRACK)).contains((FieldValue) FieldValue.Position.of(3, 12));
        });
        assertThat(snapshot(root)).isEqualTo(before);
    }

    /** 10 000+ files: the scan completes, streams in order, and nothing is buffered in the test. */
    @Test
    void hugeFolderOfTenThousandFilesIsStreamed() throws IOException {
        Path root = music();
        Path original = Fixtures.copyTo(Fixture.V1_1, tempDir.toAbsolutePath().resolve("src"), "song.mp3");
        int dirs = 100;
        int perDir = 101;
        try {
            for (int d = 0; d < dirs; d++) {
                Path folder = Files.createDirectories(root.resolve("d%03d".formatted(d)));
                for (int f = 0; f < perDir; f++) {
                    Files.createLink(folder.resolve("f%03d.mp3".formatted(f)), original);
                }
            }
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "hard links not supported: " + e);
        }
        String originalSum = Fixtures.sha256(original);
        AtomicInteger count = new AtomicInteger();
        String[] last = {""};
        boolean[] ordered = {true};

        ScanSummary summary = scan(root, true, false, item -> {
            count.incrementAndGet();
            String rel = root.relativize(item.path()).toString();
            if (rel.compareTo(last[0]) <= 0) {
                ordered[0] = false;
            }
            last[0] = rel;
            if (!(item instanceof ScannedFile)) {
                throw new AssertionError("unexpected " + item);
            }
        });

        assertThat(summary.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(count.get()).isEqualTo(dirs * perDir);
        assertThat(summary.found()).isEqualTo(dirs * perDir);
        assertThat(summary.read()).isEqualTo(dirs * perDir);
        assertThat(ordered[0]).as("items arrive sorted by path").isTrue();
        assertThat(Fixtures.sha256(original)).isEqualTo(originalSum);
        assertThat(Files.getAttribute(original, "unix:nlink", LinkOption.NOFOLLOW_LINKS))
                .as("no link was replaced").isEqualTo(dirs * perDir + 1);
    }
}
