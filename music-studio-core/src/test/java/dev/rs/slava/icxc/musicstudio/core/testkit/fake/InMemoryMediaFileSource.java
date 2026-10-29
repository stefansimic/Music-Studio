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
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A {@link MediaFileSource} over an in-memory tree. It passes the same contract as the real one. Files are returned
 * in a shuffled order on purpose, so callers cannot rely on listing order.
 *
 * <p>Build the tree with the {@code add*} methods; all paths must be absolute. Parent directories are created
 * implicitly. Thread-safe (all methods synchronized).
 */
public final class InMemoryMediaFileSource implements MediaFileSource {

    private enum Kind { FILE, FILE_LINK, DIRECTORY, DIRECTORY_LINK }

    private static final class Node {
        final Kind kind;
        final FileFingerprint fingerprint;
        boolean denied;
        final List<Path> children = new ArrayList<>();

        Node(Kind kind, FileFingerprint fingerprint) {
            this.kind = kind;
            this.fingerprint = fingerprint;
        }
    }

    private final Map<Path, Node> nodes = new HashMap<>();
    private final Random random = new Random(42);

    /** Adds a regular file. */
    public synchronized void addFile(Path file, FileFingerprint fingerprint) {
        add(file, new Node(Kind.FILE, fingerprint));
    }

    /** Adds a regular file with a default fingerprint. */
    public synchronized void addFile(Path file) {
        addFile(file, new FileFingerprint(1, Instant.parse("2020-01-01T00:00:00Z")));
    }

    /** Adds a symbolic link to a regular file. */
    public synchronized void addFileLink(Path link, FileFingerprint fingerprint) {
        add(link, new Node(Kind.FILE_LINK, fingerprint));
    }

    /** Adds a directory (and its parents). */
    public synchronized void addDirectory(Path directory) {
        if (!nodes.containsKey(directory)) {
            add(directory, new Node(Kind.DIRECTORY, null));
        }
    }

    /** Adds a symbolic link to a directory. */
    public synchronized void addDirectoryLink(Path link) {
        add(link, new Node(Kind.DIRECTORY_LINK, null));
    }

    /** Makes a directory unreadable. */
    public synchronized void denyAccess(Path directory, boolean denied) {
        nodes.get(directory).denied = denied;
    }

    /** Removes a file (simulates a file that disappears). */
    public synchronized void remove(Path path) {
        nodes.remove(path);
        Path parent = path.getParent();
        if (parent != null && nodes.containsKey(parent)) {
            nodes.get(parent).children.remove(path);
        }
    }

    private void add(Path path, Node node) {
        Require.absolute(path, "path");
        Path parent = path.getParent();
        if (parent != null) {
            addDirectory(parent);
            nodes.get(parent).children.add(path);
        }
        nodes.put(path, node);
    }

    /** @return all stored paths with kind and size, to detect changes */
    public synchronized Map<String, String> describe() {
        Map<String, String> result = new java.util.TreeMap<>();
        nodes.forEach((path, node) -> result.put(path.toString(), node.kind + " " + node.fingerprint));
        return result;
    }

    @Override
    public synchronized DirectoryListing list(Path root, ListingOptions options, Cancellation cancel)
            throws MediaAccessException {
        Require.absolute(root, "root");
        Require.nonNull(options, "options");
        Require.nonNull(cancel, "cancel");
        cancel.throwIfCancelled();
        Node rootNode = nodes.get(root);
        if (rootNode == null) {
            throw new MediaAccessException(Reason.NOT_FOUND, root, "no such directory: " + root, null);
        }
        if (rootNode.kind != Kind.DIRECTORY && rootNode.kind != Kind.DIRECTORY_LINK) {
            throw new MediaAccessException(Reason.NOT_A_DIRECTORY, root, "not a directory: " + root, null);
        }
        if (rootNode.denied) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, root, "cannot read directory: " + root, null);
        }
        List<Path> files = new ArrayList<>();
        List<SkippedEntry> skipped = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        walk(root, rootNode, options, cancel, files, skipped, issues);
        Collections.shuffle(files, random);
        return new DirectoryListing(root, files, skipped, issues);
    }

    private void walk(Path dir, Node node, ListingOptions options, Cancellation cancel, List<Path> files,
                      List<SkippedEntry> skipped, List<Issue> issues) {
        cancel.throwIfCancelled();
        for (Path child : node.children) {
            Node childNode = nodes.get(child);
            if (!options.includeHidden() && child.getFileName().toString().startsWith(".")) {
                skipped.add(new SkippedEntry(child, SkipReason.HIDDEN));
                continue;
            }
            switch (childNode.kind) {
                case FILE, FILE_LINK -> files.add(child);
                case DIRECTORY_LINK -> skipped.add(new SkippedEntry(child, SkipReason.DIRECTORY_SYMLINK));
                case DIRECTORY -> {
                    if (!options.recursive()) {
                        break;
                    }
                    if (options.excludedDirectories().contains(child)) {
                        skipped.add(new SkippedEntry(child, SkipReason.EXCLUDED));
                    } else if (childNode.denied) {
                        issues.add(Issue.warning(IssueCode.LISTING_ACCESS_DENIED, "cannot read directory " + child)
                                .forFile(child));
                    } else {
                        walk(child, childNode, options, cancel, files, skipped, issues);
                    }
                }
            }
        }
    }

    @Override
    public synchronized FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        Node node = nodes.get(file);
        if (node == null || node.fingerprint == null) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "no such file: " + file, null);
        }
        return node.fingerprint;
    }
}
