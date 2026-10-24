package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.export.ExportPlan;
import dev.rs.slava.icxc.musicstudio.core.export.ExportReport;
import dev.rs.slava.icxc.musicstudio.core.export.ExportSpec;
import dev.rs.slava.icxc.musicstudio.core.export.ExportStatus;
import dev.rs.slava.icxc.musicstudio.core.export.ExportedFile;
import dev.rs.slava.icxc.musicstudio.core.export.FailedFile;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedAction;
import dev.rs.slava.icxc.musicstudio.core.export.PlannedFile;
import dev.rs.slava.icxc.musicstudio.core.export.SkippedFile;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Golden tests of the four export renderers (story 003: dry-run shows field, old to new, warnings). */
class ExportRenderersTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();
    private static final Path OUT = Path.of("/out").toAbsolutePath();
    private static final String NL = System.lineSeparator();

    private static MediaMetadata meta(String title, String artist) {
        MediaMetadata m = MediaMetadata.empty();
        if (title != null) {
            m = m.with(Field.TITLE, new Text(title));
        }
        if (artist != null) {
            m = m.with(Field.ARTIST, new Text(artist));
        }
        return m;
    }

    private static PlannedFile write(String source, String target, List<TagLayer> before, List<TagLayer> after,
                                     List<Issue> issues) {
        TagWriteOptions options = new TagWriteOptions(Id3v2Choice.V2_3, Id3v1Choice.NONE, V2Encoding.AUTO,
                V1TextMode.ASCII, false);
        return new PlannedFile(ROOT.resolve(source), new FileFingerprint(1, Instant.EPOCH), MediaFormat.MP3,
                PlannedAction.WRITE, Optional.of(OUT.resolve(target)), List.of(),
                Optional.of(new TagWriteRequest(MediaFormat.MP3, new FieldChanges(Map.of(), Set.of()),
                        new CoverChange.Keep(), options, Optional.empty())), before, after, issues);
    }

    private static ExportPlan plan() {
        List<TagLayer> before1 = List.of(
                new TagLayer(TagVersion.ID3V2_4, meta("Љубав 🎵", "Ђорђе").with(Field.GENRE, Genres.of("Pop"))),
                new TagLayer(TagVersion.ID3V1_1, meta("Ljubav", null)));
        List<TagLayer> after1 = List.of(new TagLayer(TagVersion.ID3V2_3,
                meta("Say \"hi\", now", "Ђорђе").with(Field.GENRE, Genres.of("Pop")).with(Field.COMMENT, new Text("a\nb"))));
        PlannedFile first = write("Балашевић/Љубав.mp3", "Љубав.mp3", before1, after1, List.of(
                Issue.warning(IssueCode.V1_TRUNCATED, "TITLE: truncated to 30 characters").forField(Field.TITLE),
                Issue.info(IssueCode.OTHER_VERSION_REMOVED, "ID3v1.1 tag removed")));
        List<TagLayer> same1 = List.of(new TagLayer(TagVersion.ID3V1_1, meta("Same", null)));
        List<TagLayer> after2 = List.of(new TagLayer(TagVersion.ID3V2_3, meta("Same", null)),
                new TagLayer(TagVersion.ID3V1_1, meta("Same", null)));
        PlannedFile second = write("b.mp3", "b.mp3", same1, after2, List.of());
        Issue exists = Issue.error(IssueCode.TARGET_EXISTS, "the target already exists");
        List<TagLayer> layers = List.of(new TagLayer(TagVersion.ID3V2_3, meta("X", null)));
        PlannedFile skipped = new PlannedFile(ROOT.resolve("c, \"d\".mp3"), new FileFingerprint(1, Instant.EPOCH),
                MediaFormat.MP3, PlannedAction.SKIP, Optional.empty(), List.of(), Optional.empty(), layers, layers,
                List.of(exists, Issue.error(IssueCode.TARGET_IS_SOURCE, "second reason")));
        return new ExportPlan(Instant.EPOCH, new ExportSpec(OUT), TagWriteOptions.DEFAULT,
                List.of(first, second, skipped), List.of(Issue.warning(IssueCode.OTHER_VERSION_KEPT, "kept\ttab")));
    }

    private static List<UnreadableFile> unreadable() {
        return List.of(new UnreadableFile(ROOT.resolve("broken.mp3"), MediaFormat.MP3,
                Issue.error(IssueCode.READ_FAILED, "not a readable MP3 file")));
    }

    private static ExportReport report() {
        Issue warn = Issue.warning(IssueCode.V1_TRANSLITERATED, "Ђ -> Dj");
        Issue failure = Issue.error(IssueCode.WRITE_FAILED, "disk \"full\"\nsorry");
        return new ExportReport(ExportStatus.COMPLETED_WITH_ERRORS, OUT,
                List.of(new ExportedFile(ROOT.resolve("Балашевић/Љубав.mp3"), OUT.resolve("Љубав.mp3"), List.of(warn))),
                List.of(new SkippedFile(ROOT.resolve("s.mp3"), List.of(Issue.error(IssueCode.TARGET_EXISTS, "exists")))),
                List.of(new FailedFile(ROOT.resolve("f.mp3"), Optional.of(OUT.resolve("f.mp3")), failure)),
                List.of(OUT), List.of(Issue.warning(IssueCode.OTHER_VERSION_KEPT, "global")), Duration.ofMillis(1500));
    }

    private static String run(java.util.function.Consumer<PrintWriter> action) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        action.accept(writer);
        writer.flush();
        return out.toString();
    }

    @Test
    void tablePlanGolden() {
        String text = run(w -> new TablePlanRenderer(w, ROOT, false).render(plan(), unreadable()));
        assertThat(text).isEqualTo("Балашевић/Љубав.mp3 → /out/Љубав.mp3" + NL
                + "  ID3v2.4 → ID3v2.3" + NL
                + "    title        Љубав 🎵 → Say \"hi\", now" + NL
                + "    comment      (none) → a b" + NL
                + "  ID3v1.1 (removed)" + NL
                + "    title        Ljubav → (none)" + NL
                + "  ! warning V1_TRUNCATED title: TITLE: truncated to 30 characters" + NL
                + "b.mp3 → /out/b.mp3" + NL
                + "  ID3v2.3 (new)" + NL
                + "    title        (none) → Same" + NL
                + "  ID3v1.1 (kept, unchanged)" + NL
                + "SKIP c, \"d\".mp3: the target already exists; second reason" + NL
                + "" + NL
                + "  ! warning OTHER_VERSION_KEPT: kept tab" + NL
                + "" + NL
                + "Unreadable (not processed):" + NL
                + "  broken.mp3  not a readable MP3 file" + NL
                + "" + NL
                + "3 files: 2 to write, 1 skipped" + NL);
    }

    @Test
    void jsonPlanGolden() {
        String text = run(w -> new JsonPlanRenderer(w, ROOT).render(plan(), unreadable()));
        assertThat(text).isEqualTo("{\"files\":[{\"source\":\"Балашевић/Љубав.mp3\",\"target\":\"/out/Љубав.mp3\",\"action\":\"write\",\"slots\":[{\"family\":\"id3v2\",\"before\":\"ID3v2.4\",\"after\":\"ID3v2.3\",\"values\":[{\"field\":\"title\",\"before\":\"Љубав 🎵\",\"after\":\"Say \\\"hi\\\", now\"},{\"field\":\"comment\",\"before\":null,\"after\":\"a\\nb\"}]},{\"family\":\"id3v1\",\"before\":\"ID3v1.1\",\"after\":null,\"values\":[{\"field\":\"title\",\"before\":\"Ljubav\",\"after\":null}]}],\"issues\":[{\"severity\":\"warning\",\"code\":\"V1_TRUNCATED\",\"field\":\"title\",\"message\":\"TITLE: truncated to 30 characters\"},{\"severity\":\"info\",\"code\":\"OTHER_VERSION_REMOVED\",\"field\":null,\"message\":\"ID3v1.1 tag removed\"}]},{\"source\":\"b.mp3\",\"target\":\"/out/b.mp3\",\"action\":\"write\",\"slots\":[{\"family\":\"id3v2\",\"before\":null,\"after\":\"ID3v2.3\",\"values\":[{\"field\":\"title\",\"before\":null,\"after\":\"Same\"}]},{\"family\":\"id3v1\",\"before\":\"ID3v1.1\",\"after\":\"ID3v1.1\",\"values\":[]}],\"issues\":[]},{\"source\":\"c, \\\"d\\\".mp3\",\"target\":null,\"action\":\"skip\",\"slots\":[{\"family\":\"id3v2\",\"before\":\"ID3v2.3\",\"after\":\"ID3v2.3\",\"values\":[]}],\"issues\":[{\"severity\":\"error\",\"code\":\"TARGET_EXISTS\",\"field\":null,\"message\":\"the target already exists\"},{\"severity\":\"error\",\"code\":\"TARGET_IS_SOURCE\",\"field\":null,\"message\":\"second reason\"}]}],\"unreadable\":[{\"path\":\"broken.mp3\",\"message\":\"not a readable MP3 file\"}],\"issues\":[{\"severity\":\"warning\",\"code\":\"OTHER_VERSION_KEPT\",\"field\":null,\"message\":\"kept\\ttab\"}],\"summary\":{\"files\":3,\"write\":2,\"skip\":1,\"executable\":true}}" + NL);
    }

    @Test
    void tableReportGolden() {
        assertThat(run(w -> new TableReportRenderer(w, ROOT).render(report()))).isEqualTo("Created folders:" + NL
                + "  /out" + NL
                + "Written files:" + NL
                + "  /out/Љубав.mp3  (from Балашевић/Љубав.mp3)" + NL
                + "FAILED f.mp3: disk \"full\" sorry" + NL
                + "SKIPPED s.mp3: exists" + NL
                + "! warning OTHER_VERSION_KEPT: global" + NL
                + "COMPLETED_WITH_ERRORS: 1 written, 1 skipped, 1 failed" + NL);
    }

    @Test
    void jsonReportGolden() {
        assertThat(run(w -> new JsonReportRenderer(w, ROOT).render(report()))).isEqualTo("{\"status\":\"completed_with_errors\",\"outputRoot\":\"/out\",\"createdDirectories\":[\"/out\"],\"written\":[{\"source\":\"Балашевић/Љубав.mp3\",\"target\":\"/out/Љубав.mp3\",\"issues\":[{\"severity\":\"warning\",\"code\":\"V1_TRANSLITERATED\",\"field\":null,\"message\":\"Ђ -> Dj\"}]}],\"skipped\":[{\"source\":\"s.mp3\",\"issues\":[{\"severity\":\"error\",\"code\":\"TARGET_EXISTS\",\"field\":null,\"message\":\"exists\"}]}],\"failed\":[{\"source\":\"f.mp3\",\"issue\":{\"severity\":\"error\",\"code\":\"WRITE_FAILED\",\"field\":null,\"message\":\"disk \\\"full\\\"\\nsorry\"}}],\"issues\":[{\"severity\":\"warning\",\"code\":\"OTHER_VERSION_KEPT\",\"field\":null,\"message\":\"global\"}],\"elapsedMillis\":1500}" + NL);
    }

    @Test
    void verboseTablePlanAddsInfoIssues() {
        String quiet = run(w -> new TablePlanRenderer(w, ROOT, false).render(plan(), List.of()));
        String verbose = run(w -> new TablePlanRenderer(w, ROOT, true).render(plan(), List.of()));
        assertThat(quiet).doesNotContain("OTHER_VERSION_REMOVED");
        assertThat(verbose).contains("OTHER_VERSION_REMOVED");
    }
}
