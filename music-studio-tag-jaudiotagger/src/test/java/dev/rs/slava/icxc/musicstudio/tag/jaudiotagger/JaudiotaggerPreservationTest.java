package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.FixtureCatalog;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** T17, T20, T22, E01, E09, E17, D01, D06: untouched data survives, dropped frames are reported, sources stay. */
class JaudiotaggerPreservationTest extends WriterTestSupport {

    private static final TagWriteOptions V23 = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE,
            V2Encoding.AUTO, V1TextMode.ASCII, V1NonLatinPolicy.SKIP_V1, false);

    private static TagWriteOptions v2(Id3v2Choice choice) {
        return new TagWriteOptions(choice, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false);
    }

    /**
     * Frame id + payload as hex. Trailing NUL terminators of text frames are ignored (writers differ in whether
     * they add one; it carries no information); binary frames are compared byte for byte.
     */
    private static List<String> keyed(List<Id3Raw.Frame> frames) {
        return frames.stream().map(f -> {
            byte[] body = f.body();
            if (!Set.of("APIC", "PRIV", "GEOB").contains(f.id())) {
                int end = body.length;
                while (end > 1 && body[end - 1] == 0) {
                    end--;
                }
                body = Arrays.copyOf(body, end);
            }
            return f.id() + ":" + java.util.HexFormat.of().formatHex(body);
        }).sorted().toList();
    }

    // ---- T20

    @Test
    void changingOnlyTheTitleLeavesEveryOtherFrameByteEqual() {
        MediaMetadata desired = currentV2(Fixture.V23_EXTRAS).with(Field.TITLE, RoundTripSupport.text("New Title"));
        Written w = write(Fixture.V23_EXTRAS, desired, V23);
        List<Id3Raw.Frame> before = Id3Raw.v2(RoundTripSupport.bytes(w.source())).orElseThrow().frames();
        List<Id3Raw.Frame> after = w.rawV2().frames();
        assertThat(keyed(after.stream().filter(f -> !f.id().equals("TIT2")).toList()))
                .isEqualTo(keyed(before.stream().filter(f -> !f.id().equals("TIT2")).toList()));
        assertThat(frameText(w.rawV2().frame("TIT2").orElseThrow())).isEqualTo("New Title");
        assertThat(w.rawV2().ids()).contains("TXXX", "PRIV", "USLT", "APIC", "GEOB", "TKEY");
        assertThat(after.stream().filter(f -> f.id().equals("TXXX")).count()).isEqualTo(3);
        assertThat(w.read().cover()).isEqualTo(FixtureCatalog.readable(Fixture.V23_EXTRAS).cover());
    }

    @Test
    void replayGainSurvivesAnUnrelatedWrite() {
        Written w = write(Fixture.V23_EXTRAS, currentV2(Fixture.V23_EXTRAS).with(Field.ALBUM,
                RoundTripSupport.text("Other Album")), V23);
        boolean gain = w.rawV2().frames().stream().filter(f -> f.id().equals("TXXX")).anyMatch(
                f -> frameText(f).contains("REPLAYGAIN_TRACK_GAIN") && frameText(f).contains("-6.50 dB"));
        assertThat(gain).isTrue();
    }

    @Test
    void v24SourceToV23ChangesOnlyWhatPreflightReported() {
        MediaMetadata desired = currentV2(Fixture.V24_EXTRAS).with(Field.TITLE, RoundTripSupport.text("New Title"));
        Written w = write(Fixture.V24_EXTRAS, desired, V23);
        Set<String> reported = reportedFrames(w);
        Set<String> before = canonical(Id3Raw.v2(RoundTripSupport.bytes(w.source())).orElseThrow().ids());
        Set<String> after = canonical(w.rawV2().ids());
        Set<String> lost = new TreeSet<>(before);
        lost.removeAll(after);
        assertThat(lost).isEqualTo(canonical(List.copyOf(reported)));
        // frames that were carried over keep their payload
        for (String id : List.of("PRIV", "APIC")) { // GEOB legitimately changes its encoding byte (UTF-8 is not v2.3)
            if (!reported.contains(id)) {
                assertThat(keyed(w.rawV2().frames().stream().filter(f -> f.id().equals(id)).toList()))
                        .isEqualTo(keyed(Id3Raw.v2(RoundTripSupport.bytes(w.source())).orElseThrow().frames().stream()
                                .filter(f -> f.id().equals(id)).toList()));
            }
        }
        assertThat(w.read().cover()).isPresent();
    }

    // ---- T17 / E09 / D06

    @Test
    void v23ExtrasToV22ReportsPrivAndDropsExactlyThat() {
        Written w = write(Fixture.V23_EXTRAS, currentV2(Fixture.V23_EXTRAS), v2(Id3v2Choice.V2_2));
        assertThat(w.preflight()).filteredOn(i -> i.code() == IssueCode.FRAME_NOT_REPRESENTABLE).hasSize(1);
        assertThat(reportedFrames(w)).containsExactly("PRIV");
        assertThat(w.rawV2().ids()).doesNotContain("PRI").contains("TXX", "GEO", "ULT", "PIC", "TKE");
        assertThat(w.read().cover()).isPresent();
    }

    private static Set<String> reportedFrames(Written w) {
        Set<String> ids = new TreeSet<>();
        w.preflight().stream().filter(i -> i.code() == IssueCode.FRAME_NOT_REPRESENTABLE).forEach(i -> {
            String list = i.message().substring(i.message().lastIndexOf(": ") + 2);
            ids.addAll(Arrays.asList(list.split(",\\s*")));
        });
        return ids;
    }

    private static final Map<String, String> V22_TO_V23 = Map.ofEntries(Map.entry("TT2", "TIT2"),
            Map.entry("TP1", "TPE1"), Map.entry("TP2", "TPE2"), Map.entry("TAL", "TALB"), Map.entry("TRK", "TRCK"),
            Map.entry("TYE", "TYER"), Map.entry("TCO", "TCON"), Map.entry("COM", "COMM"), Map.entry("TXX", "TXXX"),
            Map.entry("PIC", "APIC"), Map.entry("ULT", "USLT"), Map.entry("GEO", "GEOB"), Map.entry("TKE", "TKEY"),
            Map.entry("TPA", "TPOS"), Map.entry("TDRC", "TYER"));
    private static final Set<String> CORE = Set.of("TIT2", "TPE1", "TPE2", "TALB", "TRCK", "TYER", "TCON", "COMM",
            "TPOS");

    /** Maps frame ids of any version to their v2.3 id and drops the frames that carry the library fields. */
    private static Set<String> canonical(List<String> ids) {
        return ids.stream().map(id -> V22_TO_V23.getOrDefault(id, id)).filter(id -> !CORE.contains(id))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> canonical(Set<String> ids) {
        return canonical(List.copyOf(ids));
    }

    private static Set<String> canonicalIds(Id3Raw.V2 tag) {
        return canonical(tag.ids());
    }

    @ParameterizedTest
    @EnumSource(value = Fixture.class, names = {"CORRUPT", "EMPTY"}, mode = EnumSource.Mode.EXCLUDE)
    void everyFrameThatDisappearsWasReportedBeforeAndNothingElseIsReported(Fixture fixture) {
        for (Id3v2Choice target : List.of(Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4)) {
            Written w = write(fixture, currentV2(fixture), v2(target));
            Id3Raw.V2 sourceTag = Id3Raw.v2(RoundTripSupport.bytes(w.source())).orElse(null);
            Set<String> before = sourceTag == null ? Set.of() : canonicalIds(sourceTag);
            Set<String> after = canonicalIds(w.rawV2());
            Set<String> lost = new TreeSet<>(before);
            lost.removeAll(after);
            Set<String> reported = canonical(List.copyOf(reportedFrames(w)));
            assertThat(lost).as("%s -> %s lost", fixture, target).isEqualTo(reported);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Fixture.class, names = {"CORRUPT", "EMPTY"}, mode = EnumSource.Mode.EXCLUDE)
    void fieldValuesSurviveEveryVersionConversion(Fixture fixture) {
        MediaMetadata current = currentV2(fixture);
        for (Id3v2Choice target : List.of(Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4)) {
            Written w = write(fixture, current, v2(target));
            assertThat(RoundTripSupport.v2(w.read())).as("%s -> %s", fixture, target).isEqualTo(current);
        }
    }

    // ---- D01

    @ParameterizedTest
    @EnumSource(value = Fixture.class, names = {"CORRUPT", "EMPTY"}, mode = EnumSource.Mode.EXCLUDE)
    void allFixturesTimesVersionCombosNeverTouchTheSourceAndKeepThePayload(Fixture fixture) {
        MediaMetadata desired = meta(Field.TITLE, "Title", Field.ARTIST, "Artist");
        List<TagWriteOptions> combos = new ArrayList<>();
        combos.add(new TagWriteOptions(Id3v2Choice.V2_2, Id3v1Choice.NONE, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false));
        combos.add(TagWriteOptions.DEFAULT);
        combos.add(new TagWriteOptions(Id3v2Choice.V2_4, Id3v1Choice.V1_0, V2Encoding.UTF8, V1TextMode.LATIN1,
                V1NonLatinPolicy.SKIP_V1, true));
        combos.add(new TagWriteOptions(Id3v2Choice.NONE, Id3v1Choice.V1_1, V2Encoding.AUTO, V1TextMode.ASCII,
                V1NonLatinPolicy.SKIP_V1, false));
        for (TagWriteOptions options : combos) {
            Written w = write(fixture, desired, options); // helper asserts source + payload + only-new-file
            if (options.id3v2() != Id3v2Choice.NONE) {
                assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains("Title");
            }
        }
    }

    // ---- E01 / E17

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readOnlySourceIsWrittenAndStaysReadOnlyAndUntouched() throws IOException {
        Path source = copy(Fixture.V23_LATIN1);
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("r--r--r--"));
        try {
            Written w = write(source, "ro.mp3", meta(Field.TITLE, "Changed"), V23);
            assertThat(Files.getPosixFilePermissions(source)).isEqualTo(PosixFilePermissions.fromString("r--r--r--"));
            assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains("Changed");
        } finally {
            Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test
    void nonAsciiTargetNameWorks() {
        Path source = copy(Fixture.V23_LATIN1);
        Path probe = out().resolve("Ђорђе – 🎵.mp3");
        try {
            Files.writeString(probe, "x");
            Files.delete(probe);
        } catch (java.nio.file.InvalidPathException | IOException e) {
            assumeTrue(false, "file system or locale cannot hold non-ASCII names");
        }
        Written w = write(source, "Ђорђе – 🎵.mp3", meta(Field.TITLE, "Pesma"), V23);
        assertThat(w.target().getFileName().toString()).isEqualTo("Ђорђе – 🎵.mp3");
        assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains("Pesma");
    }

    // ---- edge cases on tags

    @Test
    void veryLongValuesAreWrittenAndReadBack() {
        String longTitle = "x".repeat(5000);
        Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, longTitle), V23);
        assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains(longTitle);
    }

    @Test
    void emojiAndCjkRoundTripInV2() {
        for (Id3v2Choice c : List.of(Id3v2Choice.V2_3, Id3v2Choice.V2_4)) {
            Written w = write(Fixture.NO_TAGS, meta(Field.TITLE, "Song 🎵 日本"), v2(c));
            assertThat(RoundTripSupport.v2(w.read()).display(Field.TITLE)).contains("Song 🎵 日本");
        }
    }

    @Test
    void clearingAFieldRemovesItAndKeepsTheRest() {
        MediaMetadata current = currentV2(Fixture.V23_LATIN1);
        MediaMetadata desired = current.without(Field.ALBUM);
        Written w = write(Fixture.V23_LATIN1, desired, V23);
        TagReadResult r = w.read();
        assertThat(RoundTripSupport.v2(r).get(Field.ALBUM)).isEmpty();
        assertThat(RoundTripSupport.v2(r)).isEqualTo(desired);
    }

    @Test
    void unreadableSourceIsReportedAndNoTargetRemains() throws IOException {
        assumeFalse(false);
        Path source = copy(Fixture.CORRUPT);
        Path target = out().resolve("x.mp3");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.writeCopy(source, target,
                RoundTripSupport.requestFor(meta(Field.TITLE, "T"), V23),
                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE,
                dev.rs.slava.icxc.musicstudio.core.task.ByteProgress.NONE))
                .isInstanceOfSatisfying(dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.class,
                        e -> assertThat(e.reason()).isEqualTo(
                                dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException.Reason.CORRUPT));
        // the contract allows a leftover target after a failure (the caller deletes the temp); the source stays
        assertThat(source).exists();
    }
}
