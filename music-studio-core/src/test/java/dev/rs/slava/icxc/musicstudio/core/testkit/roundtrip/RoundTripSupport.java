package dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Genre;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/** Shared helpers of the write tests: snapshots (data safety), metadata builders, request building. */
public final class RoundTripSupport {

    private RoundTripSupport() {}

    /** SHA-256 + size + mtime + permissions of a file at one moment. */
    public record Snapshot(String sha256, long size, long mtimeMillis, Set<PosixFilePermission> permissions) {}

    public static Snapshot snapshot(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getFileStore(file).supportsFileAttributeView("posix")
                    ? Files.getPosixFilePermissions(file) : Set.of();
            return new Snapshot(dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures.sha256(file),
                    Files.size(file), Files.getLastModifiedTime(file).toMillis(), perms);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Asserts content, mtime and permissions of {@code file} equal the snapshot. */
    public static void assertUnchanged(Path file, Snapshot before) {
        assertThat(snapshot(file)).as("source %s must be untouched", file).isEqualTo(before);
    }

    /** @return sorted "relative path : sha256" lines of everything under {@code dir} (directories as "name/") */
    public static List<String> tree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> !p.equals(dir)).sorted().map(p -> {
                String name = dir.relativize(p).toString();
                return Files.isDirectory(p) ? name + "/" : name + " : "
                        + dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixtures.sha256(p);
            }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return names of files directly in {@code dir}, sorted */
    public static List<String> names(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- metadata builders

    public static FieldValue text(String value) {
        return new FieldValue.Text(value);
    }

    public static FieldValue year(int year) {
        return new FieldValue.YearValue(year);
    }

    public static FieldValue position(int number, int total) {
        return new FieldValue.Position(number, total == 0 ? OptionalInt.empty() : OptionalInt.of(total));
    }

    public static FieldValue genres(String... names) {
        return new FieldValue.Genres(Arrays.stream(names).map(Genre::new).toList());
    }

    /** @param fieldsAndValues alternating {@link Field} and {@link FieldValue} (a String is wrapped as Text) */
    public static MediaMetadata meta(Object... fieldsAndValues) {
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            Object v = fieldsAndValues[i + 1];
            values.put((Field) fieldsAndValues[i], v instanceof String s ? text(s) : (FieldValue) v);
        }
        return new MediaMetadata(values);
    }

    /** @return the ID3v2 layer metadata of a read result, empty metadata if none */
    public static MediaMetadata v2(TagReadResult result) {
        return result.layer(TagFamily.ID3V2).map(TagLayer::metadata).orElse(MediaMetadata.empty());
    }

    /** @return the ID3v1 layer metadata of a read result, empty metadata if none */
    public static MediaMetadata v1(TagReadResult result) {
        return result.layer(TagFamily.ID3V1).map(TagLayer::metadata).orElse(MediaMetadata.empty());
    }

    /** All fields a writer is expected to be able to write. */
    public static Set<Field> allFields() {
        return EnumSet.allOf(Field.class);
    }

    /**
     * Builds the request the use case would build: field changes between the current ID3v2 metadata and
     * {@code desired}, ID3v1 values from the projection (v1 dropped from the options when the projection skips it).
     */
    public static TagWriteRequest request(MediaMetadata currentV2, MediaMetadata desired, TagWriteOptions options) {
        FieldChanges changes = FieldChanges.between(currentV2, desired, allFields());
        Id3v1Projection.Projection projection = Id3v1Projection.project(desired, options);
        TagWriteOptions effective = options;
        if (projection.skipped()) {
            effective = new TagWriteOptions(options.id3v2(),
                    dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice.NONE, options.v2Encoding(),
                    options.v1TextMode(), options.v1NonLatinPolicy(), options.removeOtherVersions());
        }
        return new TagWriteRequest(MediaFormat.MP3, changes, new CoverChange.Keep(), effective, projection.values());
    }

    /** Request that changes exactly {@code set}/{@code clear}-free: sets the given metadata over empty current. */
    public static TagWriteRequest requestFor(MediaMetadata desired, TagWriteOptions options) {
        return request(MediaMetadata.empty(), desired, options);
    }

    /** @return a list copy */
    public static <T> List<T> list(Iterable<T> it) {
        List<T> out = new ArrayList<>();
        it.forEach(out::add);
        return out;
    }

    public static Optional<TagLayer> layer(TagReadResult r, TagFamily family) {
        return r.layer(family);
    }
}
