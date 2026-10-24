package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link ExportTarget} on the local file system. The final move is a hard link (atomic, fails if the target
 * exists) followed by deleting the temp name; where hard links are not supported (FAT32/exFAT sticks) it falls back
 * to check-then-move without {@code REPLACE_EXISTING}.
 *
 * <p><b>Thread-safety:</b> stateless apart from a random source; safe for concurrent use.
 */
public final class LocalExportTarget implements ExportTarget {

    private static final int MAX_NAME = 255;
    private final SecureRandom random = new SecureRandom();
    private final boolean useHardLinks;

    /** Creates the target with hard-link moves where supported. */
    public LocalExportTarget() {
        this(true);
    }

    /** For tests: {@code useHardLinks = false} forces the check-then-move fallback. */
    LocalExportTarget(boolean useHardLinks) {
        this.useHardLinks = useHardLinks;
    }

    @Override
    public Optional<Path> findCollision(Path target) throws MediaAccessException {
        Require.absolute(target, "target");
        Path parent = target.getParent();
        Path name = target.getFileName();
        if (parent == null || name == null || !Files.isDirectory(parent)) {
            return Optional.empty();
        }
        String wanted = key(name.toString());
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.filter(p -> key(p.getFileName().toString()).equals(wanted)).findFirst();
        } catch (IOException e) {
            throw failure(parent, e);
        }
    }

    private static String key(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    @Override
    public List<Path> createDirectories(Path directory) throws MediaAccessException {
        Require.absolute(directory, "directory");
        List<Path> missing = new ArrayList<>();
        for (Path p = directory.normalize(); p != null && !Files.isDirectory(p); p = p.getParent()) {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                throw new MediaAccessException(Reason.ALREADY_EXISTS, p, "a file is in the way: " + p, null);
            }
            missing.addFirst(p);
        }
        List<Path> created = new ArrayList<>();
        for (Path p : missing) {
            try {
                Files.createDirectory(p);
                created.add(p);
            } catch (FileAlreadyExistsException e) {
                if (!Files.isDirectory(p)) {
                    throw new MediaAccessException(Reason.ALREADY_EXISTS, p, "a file is in the way: " + p, e);
                }
            } catch (IOException e) {
                throw failure(p, e);
            }
        }
        return List.copyOf(created);
    }

    @Override
    public Path reserveTempPath(Path finalTarget) throws MediaAccessException {
        Require.absolute(finalTarget, "finalTarget");
        Require.that(finalTarget.getFileName() != null, "finalTarget needs a file name");
        for (int attempt = 0; attempt < 100; attempt++) {
            byte[] bytes = new byte[3];
            random.nextBytes(bytes);
            String suffix = "." + HexFormat.of().formatHex(bytes) + TEMP_SUFFIX;
            String head = "." + finalTarget.getFileName();
            if (head.length() + suffix.length() > MAX_NAME) {
                head = head.substring(0, MAX_NAME - suffix.length());
            }
            Path temp = finalTarget.resolveSibling(head + suffix);
            if (!Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
                return temp;
            }
        }
        throw new MediaAccessException(Reason.IO, finalTarget, "no free temp name next to " + finalTarget, null);
    }

    @Override
    public void moveNoReplace(Path temp, Path target) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.absolute(target, "target");
        Require.that(temp.getFileName().toString().endsWith(TEMP_SUFFIX), "not a temp file: %s", temp);
        Require.that(temp.getParent().equals(target.getParent()), "temp and target must share a directory");
        if (!Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
            throw new MediaAccessException(Reason.NOT_FOUND, temp, "the temp file is missing", null);
        }
        if (useHardLinks && tryHardLink(temp, target)) {
            deleteTemp(temp);
            return;
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "the target exists: " + target, null);
        }
        try {
            Files.move(temp, target); // no REPLACE_EXISTING
        } catch (FileAlreadyExistsException e) {
            throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "the target exists: " + target, e);
        } catch (IOException e) {
            throw failure(target, e);
        }
    }

    /** @return true if linked; false if hard links are not supported here */
    private static boolean tryHardLink(Path temp, Path target) throws MediaAccessException {
        try {
            Files.createLink(target, temp);
            return true;
        } catch (FileAlreadyExistsException e) {
            throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "the target exists: " + target, e);
        } catch (UnsupportedOperationException e) {
            return false;
        } catch (AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, target, "access denied: " + target, e);
        } catch (FileSystemException e) {
            return false; // e.g. "Operation not permitted" on FAT/exFAT: fall back
        } catch (IOException e) {
            throw failure(target, e);
        }
    }

    @Override
    public void deleteTemp(Path temp) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.that(temp.getFileName() != null && temp.getFileName().toString().endsWith(TEMP_SUFFIX),
                "not a temp file: %s", temp);
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            throw failure(temp, e);
        }
    }

    @Override
    public FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileFingerprint(attributes.size(), attributes.lastModifiedTime().toInstant());
        } catch (IOException e) {
            throw failure(file, e);
        }
    }

    private static MediaAccessException failure(Path path, IOException e) {
        String message = String.valueOf(e.getMessage());
        Reason reason = switch (e) {
            case NoSuchFileException ignored -> Reason.NOT_FOUND;
            case AccessDeniedException ignored -> Reason.ACCESS_DENIED;
            case FileAlreadyExistsException ignored -> Reason.ALREADY_EXISTS;
            default -> message.contains("No space left") ? Reason.NO_SPACE : Reason.IO;
        };
        return new MediaAccessException(reason, path, reason.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + ": " + path, e);
    }
}
