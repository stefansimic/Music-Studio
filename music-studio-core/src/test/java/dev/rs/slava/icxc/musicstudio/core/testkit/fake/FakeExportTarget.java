package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Fake {@link ExportTarget} on real files (under a {@code @TempDir}), with failure hooks and a call log for ordering
 * assertions. Passes {@code ExportTargetContract}. Check-then-move (no hard link).
 *
 * <p><b>Thread-safety:</b> all methods are synchronised.
 */
public final class FakeExportTarget implements ExportTarget {

    /** The operations, for {@link #failNext} and the {@link #calls()} log. */
    public enum Operation { FIND_COLLISION, CREATE_DIRECTORIES, RESERVE_TEMP, MOVE, DELETE_TEMP, FINGERPRINT }

    private final Map<Operation, Reason> failNext = new EnumMap<>(Operation.class);
    private final List<String> calls = new ArrayList<>();
    private final AtomicLong counter = new AtomicLong();
    private Path appearBeforeMove;

    /** The next call of the operation fails with the reason. */
    public synchronized FakeExportTarget failNext(Operation operation, Reason reason) {
        failNext.put(operation, reason);
        return this;
    }

    /** Before the move to this target, another file appears there (simulates a race). */
    public synchronized FakeExportTarget appearBeforeMove(Path target) {
        appearBeforeMove = target;
        return this;
    }

    /** @return the calls so far, e.g. "MOVE /out/a.mp3" */
    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    private void call(Operation operation, Path path) throws MediaAccessException {
        calls.add(operation + " " + path);
        Reason reason = failNext.remove(operation);
        if (reason != null) {
            throw new MediaAccessException(reason, path, "simulated failure: " + reason, null);
        }
    }

    @Override
    public synchronized Optional<Path> findCollision(Path target) throws MediaAccessException {
        Require.absolute(target, "target");
        call(Operation.FIND_COLLISION, target);
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return Optional.empty();
        }
        String wanted = key(target.getFileName().toString());
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.filter(p -> key(p.getFileName().toString()).equals(wanted)).findFirst();
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, target, e.getMessage(), e);
        }
    }

    private static String key(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized List<Path> createDirectories(Path directory) throws MediaAccessException {
        Require.absolute(directory, "directory");
        call(Operation.CREATE_DIRECTORIES, directory);
        List<Path> missing = new ArrayList<>();
        for (Path p = directory; p != null && !Files.isDirectory(p); p = p.getParent()) {
            if (Files.exists(p)) {
                throw new MediaAccessException(Reason.ALREADY_EXISTS, p, "a file is in the way", null);
            }
            missing.addFirst(p);
        }
        try {
            for (Path p : missing) {
                Files.createDirectory(p);
            }
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, directory, e.getMessage(), e);
        }
        return List.copyOf(missing);
    }

    @Override
    public synchronized Path reserveTempPath(Path finalTarget) throws MediaAccessException {
        Require.absolute(finalTarget, "finalTarget");
        call(Operation.RESERVE_TEMP, finalTarget);
        String name = finalTarget.getFileName().toString();
        String suffix = "." + "%06x".formatted(counter.incrementAndGet() & 0xFFFFFF) + TEMP_SUFFIX;
        String head = "." + name;
        if (head.length() + suffix.length() > 255) {
            head = head.substring(0, 255 - suffix.length());
        }
        return finalTarget.resolveSibling(head + suffix);
    }

    @Override
    public synchronized void moveNoReplace(Path temp, Path target) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.absolute(target, "target");
        Require.that(temp.getFileName().toString().endsWith(TEMP_SUFFIX), "not a temp file: %s", temp);
        Require.that(temp.getParent().equals(target.getParent()), "temp and target must share a directory");
        call(Operation.MOVE, target);
        try {
            if (target.equals(appearBeforeMove)) {
                Files.writeString(target, "appeared");
            }
            if (Files.exists(target)) {
                throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "target exists", null);
            }
            Files.move(temp, target);
        } catch (FileAlreadyExistsException e) {
            throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "target exists", e);
        } catch (NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, temp, "temp missing", e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, target, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteTemp(Path temp) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.that(temp.getFileName().toString().endsWith(TEMP_SUFFIX), "not a temp file: %s", temp);
        call(Operation.DELETE_TEMP, temp);
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, temp, e.getMessage(), e);
        }
    }

    @Override
    public synchronized FileFingerprint fingerprint(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        call(Operation.FINGERPRINT, file);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileFingerprint(attributes.size(), attributes.lastModifiedTime().toInstant());
        } catch (NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "not found", e);
        } catch (AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied", e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, file, e.getMessage(), e);
        }
    }
}
