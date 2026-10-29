package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Id3v1Genres;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@link TagWriter} that keeps the written tag state in a {@link FakeTagReader} (registered under the target
 * path) instead of in the bytes of a file. If the source exists on disk, its bytes are also copied to the target
 * (so the same contract as the real writer can run against it); otherwise only the in-memory state is produced.
 * Records every call and can inject failures.
 */
public final class FakeTagWriter implements TagWriter {

    /** One recorded {@code writeCopy} call. */
    public record Call(Path source, Path target, TagWriteRequest request) {}

    /** Injected behaviour while writing (after the partial target was created). */
    @FunctionalInterface
    public interface Hook {
        void duringWrite(Path source, Path target) throws MediaAccessException;
    }

    private final FakeTagReader reader;
    private final InMemoryExportTarget memory;
    private final FormatCapabilities capabilities;
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
    private final List<Call> preflights = Collections.synchronizedList(new ArrayList<>());
    private Consumer<String> log = e -> {};
    private volatile Hook hook = (s, t) -> {};
    private volatile String titleOverride;
    private volatile List<Issue> preflightIssues = List.of();

    /** Full capabilities (all versions, encodings, fields). */
    public FakeTagWriter(FakeTagReader reader, InMemoryExportTarget memory) {
        this(reader, memory, fullCapabilities());
    }

    /** @param memory where created files are announced; null = real disk only */
    public FakeTagWriter(FakeTagReader reader, InMemoryExportTarget memory, FormatCapabilities capabilities) {
        this.reader = reader;
        this.memory = memory;
        this.capabilities = capabilities;
    }

    public static FormatCapabilities fullCapabilities() {
        return new FormatCapabilities(MediaFormat.MP3,
                EnumSet.of(TagVersion.ID3V1_0, TagVersion.ID3V1_1, TagVersion.ID3V2_2, TagVersion.ID3V2_3,
                        TagVersion.ID3V2_4),
                EnumSet.allOf(V2Encoding.class), EnumSet.allOf(Field.class), false);
    }

    public static FormatCapabilities without(TagVersion... versions) {
        Set<TagVersion> set = EnumSet.of(TagVersion.ID3V1_0, TagVersion.ID3V1_1, TagVersion.ID3V2_2,
                TagVersion.ID3V2_3, TagVersion.ID3V2_4);
        for (TagVersion v : versions) {
            set.remove(v);
        }
        return new FormatCapabilities(MediaFormat.MP3, set, EnumSet.allOf(V2Encoding.class),
                EnumSet.allOf(Field.class), false);
    }

    public void logTo(Consumer<String> sink) {
        this.log = sink;
    }

    /** Runs {@code hook} after the partial target exists and before the write completes. */
    public void onWrite(Hook hook) {
        this.hook = hook;
    }

    /** Makes the written ID3v2 title wrong ("WRONG"), to prove that verification catches it. */
    public void writeWrongTitle() {
        this.titleOverride = "WRONG";
    }

    /** Issues returned by {@link #preflight}. */
    public void preflightReturns(List<Issue> issues) {
        this.preflightIssues = List.copyOf(issues);
    }

    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    public List<Call> preflights() {
        synchronized (preflights) {
            return List.copyOf(preflights);
        }
    }

    @Override
    public Set<MediaFormat> writableFormats() {
        return Set.of(MediaFormat.MP3);
    }

    @Override
    public FormatCapabilities capabilities(MediaFormat format) {
        Require.nonNull(format, "format");
        Require.that(format == MediaFormat.MP3, "%s cannot be written", format);
        return capabilities;
    }

    @Override
    public List<Issue> preflight(Path source, TagWriteRequest request) throws MediaAccessException {
        Require.absolute(source, "source");
        Require.nonNull(request, "request");
        Require.that(capabilities.unsupported(request.options()).isEmpty(), "unsupported options");
        preflights.add(new Call(source, null, request));
        reader.read(source);
        return preflightIssues;
    }

    @Override
    public void writeCopy(Path source, Path target, TagWriteRequest request, Cancellation cancel,
                          ByteProgress progress) throws MediaAccessException {
        Require.absolute(source, "source");
        Require.absolute(target, "target");
        Require.nonNull(request, "request");
        Require.nonNull(cancel, "cancel");
        Require.nonNull(progress, "progress");
        Require.that(capabilities.unsupported(request.options()).isEmpty(), "unsupported options");
        Require.that(MediaFormat.ofFileName(source.getFileName().toString()).filter(f -> f == request.format())
                .isPresent(), "source is not a %s file", request.format());
        Require.that(!source.normalize().equals(target.normalize()), "target must not be the source");
        Require.that(!Files.exists(target) && (memory == null || !memory.files().contains(target)),
                "target must not exist");
        Require.that(target.getParent() != null && (memory != null || Files.isDirectory(target.getParent())),
                "the folder of the target must exist");
        calls.add(new Call(source, target, request));
        log.accept("write:" + target);
        TagReadResult before = reader.read(source);
        cancel.throwIfCancelled();
        try {
            if (Files.isRegularFile(source)) {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis()));
            }
        } catch (IOException e) {
            throw new MediaAccessException(Reason.IO, target, "cannot copy: " + e.getMessage(), e);
        }
        if (memory != null) {
            memory.create(target);
        }
        hook.duringWrite(source, target);
        reader.register(target, apply(before, request));
        long size = Files.isRegularFile(target) ? sizeOf(target) : 1;
        progress.onBytes(size, size);
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 1;
        }
    }

    private TagReadResult apply(TagReadResult before, TagWriteRequest request) {
        var options = request.options();
        List<TagLayer> layers = new ArrayList<>();
        Optional<TagLayer> oldV2 = before.layer(TagFamily.ID3V2);
        Optional<TagLayer> oldV1 = before.layer(TagFamily.ID3V1);
        if (options.id3v2() != Id3v2Choice.NONE) {
            MediaMetadata current = oldV2.map(TagLayer::metadata).orElse(MediaMetadata.empty());
            MediaMetadata result = request.changes().applyTo(current);
            if (titleOverride != null) {
                result = result.with(Field.TITLE, new FieldValue.Text(titleOverride));
            }
            layers.add(new TagLayer(options.id3v2().version().orElseThrow(), result));
        } else if (!options.removeOtherVersions() && oldV2.isPresent()) {
            layers.add(oldV2.get());
        }
        if (request.id3v1().isPresent()) {
            layers.add(v1Layer(request.id3v1().get()));
        } else if (options.id3v1() == Id3v1Choice.NONE && !options.removeOtherVersions() && oldV1.isPresent()) {
            layers.add(oldV1.get());
        }
        layers.sort(Comparator.comparing(l -> l.version().family()));
        return new TagReadResult(before.format(), layers, before.stream(), before.cover(), List.of());
    }

    private static TagLayer v1Layer(Id3v1Values v) {
        java.util.Map<Field, FieldValue> m = new java.util.EnumMap<>(Field.class);
        put(m, Field.TITLE, v.title(), v);
        put(m, Field.ARTIST, v.artist(), v);
        put(m, Field.ALBUM, v.album(), v);
        put(m, Field.COMMENT, v.comment(), v);
        v.year().ifPresent(y -> m.put(Field.YEAR, new FieldValue.YearValue(y)));
        boolean asV11 = v.version() == Id3v1Choice.V1_1 && v.track() > 0;
        if (asV11) {
            m.put(Field.TRACK, new FieldValue.Position(v.track(), OptionalInt.empty()));
        }
        if (v.genre() != Id3v1Values.NO_GENRE) {
            Id3v1Genres.name(v.genre()).ifPresent(
                    g -> m.put(Field.GENRE, new FieldValue.Genres(List.of(new dev.rs.slava.icxc.musicstudio.core.model.Genre(g)))));
        }
        return new TagLayer(asV11 ? TagVersion.ID3V1_1 : TagVersion.ID3V1_0, new MediaMetadata(m));
    }

    private static void put(java.util.Map<Field, FieldValue> m, Field f, String s, Id3v1Values v) {
        String raw = new String(s.getBytes(v.encoding().charset()), StandardCharsets.ISO_8859_1).strip();
        if (!raw.isEmpty()) {
            m.put(f, new FieldValue.Text(raw));
        }
    }
}
