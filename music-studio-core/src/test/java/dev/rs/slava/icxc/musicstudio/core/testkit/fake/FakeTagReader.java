package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link TagReader} that answers from {@link FixtureCatalog}. A file is recognised by the SHA-256 of its content,
 * so a fixture copy keeps working under any name. Paths can also be registered explicitly with a canned result or
 * failure (they need not exist on disk), which is what the service tests use. Passes the same contract as real readers.
 */
public final class FakeTagReader implements TagReader {

    private sealed interface Outcome {
        record Result(TagReadResult result) implements Outcome {}

        record Failure(Reason reason) implements Outcome {}
    }

    private final Map<Path, Outcome> registered = new ConcurrentHashMap<>();
    private final AtomicLong readCount = new AtomicLong();

    /** Makes {@code file} read as {@code result}. */
    public void register(Path file, TagReadResult result) {
        registered.put(file, new Outcome.Result(result));
    }

    /** Makes reading {@code file} fail with {@code reason}. */
    public void failWith(Path file, Reason reason) {
        registered.put(file, new Outcome.Failure(reason));
    }

    /** @return how many times {@link #read} was called (successful or not) */
    public long readCount() {
        return readCount.get();
    }

    @Override
    public Set<MediaFormat> readableFormats() {
        return Set.of(MediaFormat.MP3);
    }

    @Override
    public TagReadResult read(Path file) throws MediaAccessException {
        Require.absolute(file, "file");
        Require.that(MediaFormat.ofFileName(file.getFileName().toString()).filter(readableFormats()::contains)
                .isPresent(), "unsupported file type: %s", file);
        readCount.incrementAndGet();
        Outcome outcome = registered.get(file);
        if (outcome instanceof Outcome.Result result) {
            return result.result();
        }
        if (outcome instanceof Outcome.Failure failure) {
            throw new MediaAccessException(failure.reason(), file, "configured failure: " + failure.reason(), null);
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "no such file: " + file, e);
        } catch (AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied: " + file, e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, file, "cannot read " + file + ": " + e.getMessage(), e);
        }
        Optional<Fixture> fixture = FixtureCatalog.bySha256(Fixtures.sha256(content));
        if (fixture.isPresent() && FixtureCatalog.expected(fixture.get()) instanceof FixtureCatalog.Expectation.Readable r) {
            return r.result();
        }
        throw new MediaAccessException(Reason.CORRUPT, file, "not a readable MP3 file: " + file, null);
    }
}
