package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.port.DirectoryListing;
import dev.rs.slava.icxc.musicstudio.core.port.ListingOptions;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.model.SkipReason;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * In-memory {@link MediaFileSource}. Files exist only in memory under absolute paths (use a {@code @TempDir} as
 * root so paths are absolute on every OS). Passes {@code MediaFileSourceContract}.
 *
 * <p>Test hooks: {@link #shuffleListing(long)} returns files in a random order (callers must sort);
 * {@link #denyAccess(Path)} makes a directory unreadable.
 *
 * <p><b>Thread-safety:</b> all methods are synchronised.
 */
public final class InMemoryMediaFileSource implements MediaFileSource {

    private sealed interface Node permits FileNode, DirNode, LinkNode {
    }

    private record FileNode(long size, Instant lastModified) implements Node {
    }

    private record DirNode(Instant lastModified) implements Node {
    }

    private record LinkNode(Path target) implements Node {
    }

    private static final Instant DIR_MTIME = Instant.parse("2000-01-01T00:00:00Z");

    private final Map<Path, Node> nodes = new HashMap<>();
    private final Map<Path, Set<Path>> children = new HashMap<>();
    private final Set<Path> denied = new HashSet<>();
    private Random shuffle;
    private int listCount;

    /**
     * Adds a regular file and its missing parent directories.
     *
     * @param path         absolute path
     * @param size         file size
     * @param lastModified modification time
     * @return {@code path}
     */
    public synchronized Path addFile(Path path, long size, Instant lastModified) {
        Require.absolute(path, "path");
        Require.notNegative(size, "size");
        Require.nonNull(lastModified, "lastModified");
        put(path, new FileNode(size, lastModified));
        return path;
    }

    /**
     * Adds a directory and its missing parents.
     *
     * @param path absolute path
     * @return {@code path}
     */
    public synchronized Path addDirectory(Path path) {
        Require.absolute(path, "path");
        if (!(nodes.get(path) instanceof DirNode)) {
            put(path, new DirNode(DIR_MTIME));
        }
        return path;
    }

    /**
     * Adds a symbolic link.
     *
     * @param link   absolute path of the link
     * @param target absolute path of the target (may not exist)
     * @return {@code link}
     */
    public synchronized Path addSymlink(Path link, Path target) {
        Require.absolute(link, "link");
        Require.absolute(target, "target");
        put(link, new LinkNode(target.normalize()));
        return link;
    }

    /** Makes a directory unreadable. */
    public synchronized void denyAccess(Path dir) {
        denied.add(Require.absolute(dir, "dir"));
    }

    /** Undoes all {@link #denyAccess} calls. */
    public synchronized void restoreAccess() {
        denied.clear();
    }

    /** Returns listed files in a pseudo-random order from now on. */
    public synchronized void shuffleListing(long seed) {
        shuffle = new Random(seed);
    }

    /** @return how often {@link #list} was called */
    public synchronized int listCount() {
        return listCount;
    }

    /** @return every entry with its modification time (links: epoch) */
    public synchronized Map<Path, Instant> snapshot() {
        Map<Path, Instant> snapshot = new HashMap<>();
        nodes.forEach((path, node) -> snapshot.put(path, switch (node) {
            case FileNode f -> f.lastModified();
            case DirNode d -> d.lastModified();
            case LinkNode l -> Instant.EPOCH;
        }));
        return Map.copyOf(snapshot);
    }

    private void put(Path path, Node node) {
        Path parent = path.getParent();
        if (parent != null && !(nodes.get(parent) instanceof DirNode)) {
            put(parent, new DirNode(DIR_MTIME));
        }
        nodes.put(path, node);
        if (parent != null) {
            children.computeIfAbsent(parent, p -> new TreeSet<>()).add(path);
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    @Override
    public synchronized DirectoryListing list(Path root, ListingOptions options, Cancellation cancel)
            throws MediaAccessException {
        Require.absolute(root, "root");
        Require.nonNull(options, "options");
        Require.nonNull(cancel, "cancel");
        listCount++;
        cancel.throwIfCancelled();
        Node node = resolve(root);
        if (node == null) {
            throw new MediaAccessException(Reason.NOT_FOUND, root, "folder not found: " + root, null);
        }
        if (!(node instanceof DirNode)) {
            throw new MediaAccessException(Reason.NOT_A_DIRECTORY, root, "not a folder: " + root, null);
        }
        if (denied.contains(root)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, root, "access denied: " + root, null);
        }
        List<Path> files = new ArrayList<>();
        List<SkippedEntry> skipped = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        walk(root, options, cancel, files, skipped, issues);
        if (shuffle != null) {
            Collections.shuffle(files, shuffle);
        }
        return new DirectoryListing(root, files, skipped, issues);
    }

    private void walk(Path dir, ListingOptions options, Cancellation cancel, List<Path> files,
                      List<SkippedEntry> skipped, List<Issue> issues) {
        cancel.throwIfCancelled();
        for (Path child : children.getOrDefault(dir, Set.of())) {
            Node node = nodes.get(child);
            Node target = resolve(child);
            boolean isDir = target instanceof DirNode;
            if (isDir && !options.recursive()) {
                continue;
            }
            if (!options.includeHidden() && child.getFileName().toString().startsWith(".")) {
                skipped.add(new SkippedEntry(child, SkipReason.HIDDEN));
            } else if (target == null && node instanceof LinkNode) {
                skipped.add(new SkippedEntry(child, SkipReason.BROKEN_SYMLINK));
            } else if (target instanceof FileNode) {
                files.add(child);
            } else if (isDir && node instanceof LinkNode) {
                skipped.add(new SkippedEntry(child, SkipReason.DIRECTORY_SYMLINK));
            } else if (isDir && options.excludedDirectories().contains(child)) {
                skipped.add(new SkippedEntry(child, SkipReason.EXCLUDED));
            } else if (isDir && denied.contains(child)) {
                issues.add(Issue.warning(IssueCode.LISTING_ACCESS_DENIED, "cannot read folder " + child)
                        .forFile(child));
            } else if (isDir) {
                walk(child, options, cancel, files, skipped, issues);
            }
        }
    }

    /** Follows links; null if the path or a link target does not exist. */
    private Node resolve(Path path) {
        Node node = nodes.get(path);
        for (int hops = 0; node instanceof LinkNode link && hops < 40; hops++) {
            node = nodes.get(link.target());
        }
        return node instanceof LinkNode ? null : node;
    }

    @Override
    public synchronized FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        Node node = resolve(file);
        if (node == null) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "file not found: " + file, null);
        }
        if (file.getParent() != null && denied.contains(file.getParent())) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied: " + file, null);
        }
        if (!(node instanceof FileNode regular)) {
            throw new MediaAccessException(Reason.IO, file, "not a regular file: " + file, null);
        }
        return new FileFingerprint(regular.size(), regular.lastModified());
    }
}
