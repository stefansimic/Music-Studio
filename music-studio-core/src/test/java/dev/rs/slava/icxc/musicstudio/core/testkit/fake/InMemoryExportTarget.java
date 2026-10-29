package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An {@link ExportTarget} over an in-memory set of paths. Records every call in {@link #events()} (for order
 * assertions) and lets tests pre-create files and hook moves. Thread-safe.
 */
public final class InMemoryExportTarget implements ExportTarget {

    private final Set<Path> files = new TreeSet<>();
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final List<BiConsumer<Path, Path>> moveHooks = new ArrayList<>();
    private final AtomicLong counter = new AtomicLong();
    private Consumer<String> log = e -> {};

    /** Also forwards every event to {@code sink} (shared event log of a test). */
    public void logTo(Consumer<String> sink) {
        this.log = sink;
    }

    /** Called with (temp, target) after a successful move. */
    public synchronized void onMove(BiConsumer<Path, Path> hook) {
        moveHooks.add(hook);
    }

    /** Test setup: a file exists at {@code path}. */
    public synchronized void create(Path path) {
        files.add(path);
    }

    /** @return a snapshot of all existing paths */
    public synchronized Set<Path> files() {
        return new TreeSet<>(files);
    }

    /** @return the recorded calls, e.g. {@code "exists:/a/b.mp3"}, {@code "reserve:..."}, {@code "move:temp->target"} */
    public List<String> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    private void record(String event) {
        events.add(event);
        log.accept(event);
    }

    @Override
    public synchronized boolean exists(Path path) {
        Require.absolute(path, "path");
        record("exists:" + path);
        return files.contains(path);
    }

    @Override
    public synchronized Path reserveTempPath(Path target) {
        Require.absolute(target, "target");
        Path temp = target.resolveSibling("." + target.getFileName() + "." + counter.incrementAndGet() + TEMP_MARKER);
        record("reserve:" + temp);
        return temp;
    }

    @Override
    public void moveNoReplace(Path temp, Path target) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.absolute(target, "target");
        Require.that(temp.getFileName().toString().contains(TEMP_MARKER), "not a temp file: %s", temp);
        List<BiConsumer<Path, Path>> hooks;
        synchronized (this) {
            record("move:" + temp + "->" + target);
            if (!files.contains(temp)) {
                throw new MediaAccessException(Reason.NOT_FOUND, temp, "no such temp file", null);
            }
            if (files.contains(target)) {
                throw new MediaAccessException(Reason.ALREADY_EXISTS, target, "target exists", null);
            }
            files.remove(temp);
            files.add(target);
            hooks = List.copyOf(moveHooks);
        }
        hooks.forEach(h -> h.accept(temp, target));
    }

    @Override
    public synchronized void deleteTemp(Path temp) throws MediaAccessException {
        Require.absolute(temp, "temp");
        Require.that(temp.getFileName().toString().contains(TEMP_MARKER), "not a temp file: %s", temp);
        record("delete:" + temp);
        files.remove(temp);
    }
}
