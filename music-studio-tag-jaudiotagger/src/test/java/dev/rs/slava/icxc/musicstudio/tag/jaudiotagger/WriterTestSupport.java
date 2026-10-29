package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.Snapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared set-up of the real-file write tests: a fixture is copied into {@code in/}, written to {@code out/} and the
 * data-safety invariants (source untouched, out/ holds only the target, audio payload identical) are asserted for
 * every write.
 */
abstract class WriterTestSupport {

    @TempDir
    Path tempDir;

    final JaudiotaggerTagWriter writer = new JaudiotaggerTagWriter();
    final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    /** Result of one write. */
    record Written(Path source, Path target, byte[] bytes, TagReadResult read, TagWriteRequest request,
                   List<Issue> preflight) {
        Id3Raw.V2 rawV2() {
            return Id3Raw.v2(bytes).orElseThrow(() -> new AssertionError("no ID3v2 tag in the target"));
        }

        byte[] rawV1() {
            return Id3Raw.v1(bytes).orElseThrow(() -> new AssertionError("no ID3v1 tag in the target"));
        }
    }

    final Path in() {
        return dir("in");
    }

    final Path out() {
        return dir("out");
    }

    private Path dir(String name) {
        Path dir = tempDir.resolve(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

    final Path copy(Fixture fixture) {
        Path dir = in().resolve("c" + counter.incrementAndGet());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Fixtures.copyTo(fixture, dir);
    }

    final TagReadResult read(Path file) {
        try {
            return reader.read(file);
        } catch (MediaAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** The current ID3v2 metadata of a fixture, as a base for "desired" in tests that change single fields. */
    final MediaMetadata currentV2(Fixture fixture) {
        return RoundTripSupport.v2(read(copy(fixture)));
    }

    final Written write(Fixture fixture, MediaMetadata desired, TagWriteOptions options) {
        return write(copy(fixture), "target-" + counter.incrementAndGet() + ".mp3", desired, options);
    }

    final Written write(Path source, String targetName, MediaMetadata desired, TagWriteOptions options) {
        TagReadResult before = read(source);
        TagWriteRequest request = RoundTripSupport.request(RoundTripSupport.v2(before), desired, options);
        return write(source, out().resolve(targetName), request);
    }

    final Written write(Path source, Path target, TagWriteRequest request) {
        Snapshot snapshot = RoundTripSupport.snapshot(source);
        byte[] sourceBytes = RoundTripSupport.bytes(source);
        List<String> outBefore = RoundTripSupport.names(target.getParent());
        try {
            List<Issue> preflight = writer.preflight(source, request);
            assertThat(RoundTripSupport.names(target.getParent())).as("preflight writes nothing")
                    .isEqualTo(outBefore);
            writer.writeCopy(source, target, request, Cancellation.NONE, ByteProgress.NONE);
        } catch (MediaAccessException e) {
            throw new AssertionError(e);
        }
        RoundTripSupport.assertUnchanged(source, snapshot);
        byte[] bytes = RoundTripSupport.bytes(target);
        assertThat(Id3Raw.audioPayload(bytes)).as("audio payload of %s, file head %s", target.getFileName(),
                java.util.HexFormat.of().formatHex(bytes, 0, Math.min(24, bytes.length)))
                .isEqualTo(Id3Raw.audioPayload(sourceBytes));
        assertThat(RoundTripSupport.names(target.getParent())).as("only the target is new")
                .containsExactlyInAnyOrder(concat(outBefore, target.getFileName().toString()));
        List<Issue> preflight;
        try {
            preflight = writer.preflight(source, request);
        } catch (MediaAccessException e) {
            throw new AssertionError(e);
        }
        return new Written(source, target, bytes, read(target), request, preflight);
    }

    private static String[] concat(List<String> names, String extra) {
        String[] all = new String[names.size() + 1];
        for (int i = 0; i < names.size(); i++) {
            all[i] = names.get(i);
        }
        all[names.size()] = extra;
        return all;
    }

    static Charset charsetOfEncodingByte(int encoding) {
        return switch (encoding) {
            case 0 -> StandardCharsets.ISO_8859_1;
            case 1 -> StandardCharsets.UTF_16;
            case 2 -> StandardCharsets.UTF_16BE;
            case 3 -> StandardCharsets.UTF_8;
            default -> throw new AssertionError("unknown text encoding byte " + encoding);
        };
    }

    /** Decodes a text frame body (encoding byte + text) and strips a trailing NUL. */
    static String frameText(Id3Raw.Frame frame) {
        String text = new String(frame.body(), 1, frame.body().length - 1, charsetOfEncodingByte(frame.encodingByte()));
        return text.endsWith("\0") ? text.substring(0, text.length() - 1) : text;
    }

    static MediaFormat mp3() {
        return MediaFormat.MP3;
    }
}
