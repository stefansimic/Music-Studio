package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteExpectation;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake {@link TagWriter} on real files: copies the bytes (cancellable, with progress) and registers the expected
 * read result of the copy ({@code WriteExpectation.layersAfter}) in a {@link FakeTagReader}. Passes
 * {@code TagWriterContract}.
 *
 * <p>Hooks: {@link #failOn}, {@link #writeWrongValue}, {@link #frameLoss}; {@link #writeCount()}.
 * <p><b>Thread-safety:</b> safe for concurrent use.
 */
public final class FakeTagWriter implements TagWriter {

    private static final int STEP = 64 * 1024;

    private final FakeTagReader reader;
    private final FormatCapabilities capabilities;
    private final Map<Path, Reason> failures = new ConcurrentHashMap<>();
    private final Set<Path> wrongValue = ConcurrentHashMap.newKeySet();
    private final Map<Path, List<Issue>> frameLoss = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();

    /**
     * @param reader   the reader that will answer for written copies; not null
     * @param writable the versions this writer can write; not null, non-empty
     */
    public FakeTagWriter(FakeTagReader reader, Set<TagVersion> writable) {
        this.reader = Require.nonNull(reader, "reader");
        this.capabilities = new FormatCapabilities(MediaFormat.MP3, writable, EnumSet.allOf(V2Encoding.class),
                EnumSet.allOf(Field.class), false);
    }

    /** A writer that can write every ID3 version. */
    public FakeTagWriter(FakeTagReader reader) {
        this(reader, EnumSet.of(TagVersion.ID3V1_0, TagVersion.ID3V1_1, TagVersion.ID3V2_2, TagVersion.ID3V2_3,
                TagVersion.ID3V2_4));
    }

    /** writeCopy of this source fails with the reason. */
    public FakeTagWriter failOn(Path source, Reason reason) {
        failures.put(source, reason);
        return this;
    }

    /** writeCopy of this source writes a wrong title (to provoke VERIFY_MISMATCH). */
    public FakeTagWriter writeWrongValue(Path source) {
        wrongValue.add(source);
        return this;
    }

    /** preflight of this source answers these issues. */
    public FakeTagWriter frameLoss(Path source, List<Issue> issues) {
        frameLoss.put(source, List.copyOf(issues));
        return this;
    }

    /** @return the number of successful writeCopy calls */
    public int writeCount() {
        return writes.get();
    }

    @Override
    public Set<MediaFormat> writableFormats() {
        return Set.of(MediaFormat.MP3);
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(format == MediaFormat.MP3, "not writable: %s", format);
        return capabilities;
    }

    @Override
    public List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException {
        checkRequest(source, request);
        reader.read(source);
        return frameLoss.getOrDefault(source, List.of());
    }

    @Override
    public void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel,
                          ByteProgress progress) throws MediaAccessException {
        checkRequest(source, request);
        Require.absolute(target, "target");
        Require.that(!target.equals(source), "target must differ from source");
        Require.that(target.getParent() != null && Files.isDirectory(target.getParent()),
                "the target's directory must exist: %s", target);
        Require.that(!Files.exists(target), "target exists: %s", target);
        Require.nonNull(cancel, "cancel");
        Require.nonNull(progress, "progress");
        cancel.throwIfCancelled();
        Reason failure = failures.get(source);
        if (failure != null) {
            throw new MediaAccessException(failure, source, "simulated failure: " + failure, null);
        }
        TagReadResult before = reader.read(source);
        copy(source, target, cancel, progress);
        List<TagLayer> after = new ArrayList<>(WriteExpectation.layersAfter(before.layers(), request));
        if (wrongValue.contains(source) && !after.isEmpty()) {
            TagLayer first = after.getFirst();
            after.set(0, new TagLayer(first.version(), first.metadata().with(Field.TITLE,
                    new FieldValue.Text("WRONG"))));
        }
        reader.answer(target, new TagReadResult(before.format(), after, before.stream(), before.cover(), List.of()));
        writes.incrementAndGet();
    }

    private void checkRequest(Path source, TagWriteRequest request) {
        Require.absolute(source, "source");
        Require.nonNull(request, "request");
        Optional<MediaFormat> format = MediaFormat.ofFileName(source.getFileName().toString());
        Require.that(format.isPresent() && format.get() == request.format(), "request format does not fit %s",
                source);
        Require.that(capabilities.unsupported(request.options()).isEmpty(), "unsupported options: %s",
                request.options());
    }

    private static void copy(Path source, Path target, Cancellation cancel, ByteProgress progress)
            throws MediaAccessException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            long total = Files.size(source);
            long done = 0;
            byte[] buffer = new byte[STEP];
            int read;
            while ((read = in.read(buffer)) > 0) {
                cancel.throwIfCancelled();
                out.write(buffer, 0, read);
                done += read;
                progress.onBytes(done, total);
            }
            progress.onBytes(total, total);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new MediaAccessException(Reason.NOT_FOUND, source, "file not found", e);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new MediaAccessException(Reason.ACCESS_DENIED, target, "access denied", e);
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, target, "copy failed: " + e.getMessage(), e);
        }
    }
}
