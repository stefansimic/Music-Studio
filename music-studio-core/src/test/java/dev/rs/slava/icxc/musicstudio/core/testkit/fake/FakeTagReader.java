package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.CoverArt;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake {@link TagReader}. Passes {@code TagReaderContract}.
 *
 * <p>Answers, in this order: a failure registered with {@link #fail}; a result registered with {@link #answer};
 * for a real file: the {@link FixtureCatalog} result of the fixture with the same content (identified by SHA-256),
 * {@code CORRUPT} for unknown content. Counts every read in {@link #readCount()}.
 *
 * <p><b>Thread-safety:</b> safe for concurrent use.
 */
public final class FakeTagReader implements TagReader {

    private static final Map<String, Fixture> FIXTURES_BY_SHA256 = fixturesBySha256();

    private final Map<Path, TagReadResult> answers = new ConcurrentHashMap<>();
    private final Map<Path, Reason> failures = new ConcurrentHashMap<>();
    private final AtomicLong readCount = new AtomicLong();

    private static Map<String, Fixture> fixturesBySha256() {
        Map<String, Fixture> map = new HashMap<>();
        for (Fixture fixture : Fixture.values()) {
            map.put(Fixtures.sha256(Fixtures.bytes(fixture)), fixture);
        }
        return Map.copyOf(map);
    }

    /**
     * Registers the result for a path (no file needs to exist).
     *
     * @return this
     */
    public FakeTagReader answer(Path file, TagReadResult result) {
        answers.put(Require.absolute(file, "file"), Require.nonNull(result, "result"));
        return this;
    }

    /**
     * Registers a failure for a path.
     *
     * @return this
     */
    public FakeTagReader fail(Path file, Reason reason) {
        failures.put(Require.absolute(file, "file"), Require.nonNull(reason, "reason"));
        return this;
    }

    /** @return the number of {@link #read} calls so far */
    public long readCount() {
        return readCount.get();
    }

    @Override
    public Set<MediaFormat> readableFormats() {
        return Set.of(MediaFormat.MP3);
    }

    @Override
    public TagReadResult read(Path file) throws MediaAccessException {
        checkPreconditions(file);
        readCount.incrementAndGet();
        Reason failure = failures.get(file);
        if (failure != null) {
            throw new MediaAccessException(failure, file, "simulated failure: " + failure, null);
        }
        TagReadResult answer = answers.get(file);
        if (answer != null) {
            return answer;
        }
        Fixture fixture = FIXTURES_BY_SHA256.get(Fixtures.sha256(readBytes(file)));
        if (fixture == null || !fixture.readable()) {
            throw new MediaAccessException(Reason.CORRUPT, file, "not an MP3 file: " + file, null);
        }
        return FixtureCatalog.require(fixture);
    }

    @Override
    public Optional<CoverArt> readCover(Path file) throws MediaAccessException {
        checkPreconditions(file);
        read(file);
        return Optional.empty();
    }

    private void checkPreconditions(Path file) {
        Require.absolute(file, "file");
        Optional<MediaFormat> format = MediaFormat.ofFileName(file.getFileName().toString());
        Require.that(format.isPresent() && readableFormats().contains(format.get()), "not a readable format: %s",
                file);
    }

    private static byte[] readBytes(Path file) throws MediaAccessException {
        if (!Files.exists(file)) {
            throw new MediaAccessException(Reason.NOT_FOUND, file, "file not found: " + file, null);
        }
        if (!Files.isReadable(file)) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, file, "access denied: " + file, null);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, file, "cannot read " + file + ": " + e.getMessage(), e);
        }
    }
}
