package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsService;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.StreamInfo;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * All fakes wired to a {@link WriteTagsService}, over absolute but non-existing paths (nothing touches the disk).
 * {@link #events} holds the ordered calls of reader / writer / export target.
 */
public final class FakeStudio {

    public static final Path IN = Path.of("/music/in");
    public static final Path OUT = Path.of("/music/out");
    public static final Instant MTIME = Instant.parse("2021-02-03T04:05:06Z");

    public final List<String> events = Collections.synchronizedList(new ArrayList<>());
    public final FakeTagReader fakeReader = new FakeTagReader();
    public final InMemoryMediaFileSource files = new InMemoryMediaFileSource();
    public final InMemoryExportTarget target = new InMemoryExportTarget();
    public final FakeTagWriter writer;
    public final WriteTagsService service;

    public FakeStudio() {
        this(FakeTagWriter.fullCapabilities());
    }

    public FakeStudio(FormatCapabilities capabilities) {
        fakeReader.acceptTempNames(true);
        TagReader recording = new TagReader() {
            @Override
            public Set<MediaFormat> readableFormats() {
                return fakeReader.readableFormats();
            }

            @Override
            public TagReadResult read(Path file) throws MediaAccessException {
                events.add("read:" + file);
                return fakeReader.read(file);
            }
        };
        writer = new FakeTagWriter(fakeReader, target, capabilities);
        writer.logTo(events::add);
        target.logTo(events::add);
        target.onMove((temp, dest) -> {
            files.addFile(dest, new FileFingerprint(10, MTIME));
            try {
                fakeReader.register(dest, fakeReader.read(temp));
            } catch (MediaAccessException e) {
                throw new IllegalStateException(e);
            }
        });
        service = new WriteTagsService(files, recording, writer, target);
    }

    /** A readable source in {@link #IN}. */
    public Path addSource(String name, TagReadResult result) {
        Path path = IN.resolve(name);
        files.addFile(path, new FileFingerprint(10, MTIME));
        fakeReader.register(path, result);
        return path;
    }

    /** Pretends the source was modified after it was planned. */
    public void touch(Path path) {
        files.addFile(path, new FileFingerprint(11, MTIME.plusSeconds(5)));
    }

    public static TagReadResult read(TagLayer... layers) {
        return new TagReadResult(MediaFormat.MP3, List.of(layers),
                Optional.of(new StreamInfo(Duration.ofSeconds(1), 128, false)), Optional.empty(), List.of());
    }

    public static TagLayer layer(TagVersion version, MediaMetadata metadata) {
        return new TagLayer(version, metadata);
    }
}
