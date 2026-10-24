package dev.rs.slava.icxc.musicstudio.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.extract.AutoNumbering;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldOverride;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataService;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Genres;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Position;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue.Text;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.ScannedFiles;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Option parsing, pattern errors and exit codes of {@code extract}, with fake use cases. */
class ExtractCommandTest {

    private static final Path ROOT = Path.of("/music").toAbsolutePath();

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final AtomicReference<ProposeRequest> request = new AtomicReference<>();
    private ScanStatus scanStatus = ScanStatus.COMPLETED;

    private int run(String... args) {
        ScanMediaUseCase scan = (scanRequest, sink, progress, cancel) -> {
            if (scanStatus != ScanStatus.COMPLETED) {
                return new ScanSummary(scanRequest.root(), scanStatus, 0, 0, 0, 0, 0, Map.of(), List.of(),
                        scanStatus == ScanStatus.FAILED
                                ? List.of(Issue.error(IssueCode.ROOT_NOT_FOUND, "folder not found")) : List.of());
            }
            sink.accept(ScannedFiles.untagged(scanRequest.root().resolve("Artist - Song.mp3")));
            sink.accept(ScannedFiles.untagged(scanRequest.root().resolve("Bad.mp3")));
            return new ScanSummary(scanRequest.root(), ScanStatus.COMPLETED, 2, 2, 0, 0, 0, Map.of(), List.of(),
                    List.of());
        };
        ProposeMetadataService real = new ProposeMetadataService();
        CommandLine commandLine = MusicStudioCommand.commandLine(new MusicStudio(scan, proposeRequest -> {
            request.set(proposeRequest);
            return real.propose(proposeRequest);
        }), Cancellation.NONE);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    private ExtractionSpec spec() {
        return request.get().spec();
    }

    @Test
    void patternAndFixedValues() {
        int code = run("extract", ROOT.toString(), "--pattern", "{artist} - {title}", "--set", "genre=Pop; Rock",
                "--set", "albumartist=Various Artists", "--set", "disc=1/2");
        assertThat(code).isEqualTo(ExitCode.ITEM_ERRORS); // Bad.mp3 does not match
        assertThat(spec().pattern()).hasValueSatisfying(p -> assertThat(p.source()).isEqualTo("{artist} - {title}"));
        assertThat(spec().overrides()).containsEntry(Field.GENRE, new FieldOverride.Fixed(Genres.of("Pop", "Rock")))
                .containsEntry(Field.ALBUM_ARTIST, new FieldOverride.Fixed(new Text("Various Artists")))
                .containsEntry(Field.DISC, new FieldOverride.Fixed(Position.of(1, 2)));
        assertThat(spec().mergeMode()).isEqualTo(MergeMode.OVERWRITE);
        assertThat(spec().acceptAmbiguous()).isFalse();
        assertThat(out.toString()).contains("NO MATCH").contains("Preview only – no file was changed.");
    }

    @Test
    void keepMergeAutoNumberAccept() {
        run("extract", ROOT.toString(), "--pattern", "{artist} - {title}", "--keep", "artist,album",
                "--merge", "fill-empty", "--auto-number", "tag", "--track-total", "--accept-ambiguous");
        assertThat(spec().overrides()).containsEntry(Field.ARTIST, new FieldOverride.KeepExisting())
                .containsEntry(Field.ALBUM, new FieldOverride.KeepExisting());
        assertThat(spec().mergeMode()).isEqualTo(MergeMode.FILL_EMPTY);
        assertThat(spec().autoNumbering()).contains(new AutoNumbering(AutoNumbering.NumberingOrder.EXISTING_TRACK,
                true));
        assertThat(spec().acceptAmbiguous()).isTrue();
    }

    @Test
    void relativeDirectoryIsResolved() {
        run("extract", "rel/../music", "--set", "genre=Pop");
        assertThat(request.get().files().getFirst().path().getParent())
                .isEqualTo(Path.of("music").toAbsolutePath());
    }

    @Test
    void allMatchedExitsWithZero() {
        assertThat(run("extract", ROOT.toString(), "--set", "genre=Pop")).isEqualTo(ExitCode.OK);
    }

    @Test
    void usageErrors() {
        assertUsageError("give at least one", "extract", ROOT.toString());
        assertUsageError("unknown field", "extract", ROOT.toString(), "--set", "colour=red");
        assertUsageError("expected <field>=<value>", "extract", ROOT.toString(), "--set", "genre");
        assertUsageError("not a valid year", "extract", ROOT.toString(), "--set", "year=20x0");
        assertUsageError("empty", "extract", ROOT.toString(), "--set", "title=");
        assertUsageError("both --set and --keep", "extract", ROOT.toString(), "--set", "genre=Pop", "--keep",
                "genre");
        assertUsageError("--track-total needs --auto-number", "extract", ROOT.toString(), "--set", "genre=Pop",
                "--track-total");
        assertUsageError("pattern and auto-numbering", "extract", ROOT.toString(), "--pattern",
                "{track} - {title}", "--auto-number", "filename");
        assertUsageError("fill-empty", "extract", ROOT.toString(), "--set", "genre=Pop", "--merge", "sometimes");
        assertUsageError("filename or tag", "extract", ROOT.toString(), "--auto-number", "random");
        assertUsageError("apply", "extract", ROOT.toString(), "--set", "genre=Pop", "--apply");
    }

    private void assertUsageError(String message, String... args) {
        err.getBuffer().setLength(0);
        request.set(null);
        assertThat(run(args)).as("exit code of %s", String.join(" ", args)).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).as("stderr of %s", String.join(" ", args)).contains(message);
        assertThat(request.get()).isNull();
    }

    @Test
    void invalidPatternShowsACaretUnderTheError() {
        assertThat(run("extract", ROOT.toString(), "--pattern", "{artist} - {titl}")).isEqualTo(ExitCode.USAGE);
        List<String> lines = err.toString().lines().toList();
        assertThat(lines).contains("  {artist} - {titl}");
        String caret = lines.get(lines.indexOf("  {artist} - {titl}") + 1);
        assertThat(caret.indexOf('^')).isEqualTo(2 + "{artist} - ".length());
        assertThat(caret).contains("unknown placeholder {titl}");
    }

    @Test
    void caretColumnCountsTerminalColumnsAfterCyrillicAndEmoji() {
        run("extract", ROOT.toString(), "--pattern", "Ђорђе 🎵 {foo}");
        String caret = err.toString().lines().filter(l -> l.contains("^")).findFirst().orElseThrow();
        assertThat(caret.indexOf('^')).isEqualTo(2 + "Ђорђе ".length() + 2 + 1);
    }

    @Test
    void scanFailureAndCancellation() {
        scanStatus = ScanStatus.FAILED;
        assertThat(run("extract", ROOT.toString(), "--set", "genre=Pop")).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("folder not found");
        scanStatus = ScanStatus.CANCELLED;
        assertThat(run("extract", ROOT.toString(), "--set", "genre=Pop")).isEqualTo(ExitCode.CANCELLED);
    }

    @Test
    void jsonFormat() {
        run("extract", ROOT.toString(), "--pattern", "{artist} - {title}", "--format", "json");
        assertThat(out.toString()).startsWith("{\"root\":").contains("\"pattern\":\"{artist} - {title}\"")
                .contains("\"status\":\"matched\"").contains("\"status\":\"no_match\"")
                .contains("\"noMatch\":1").doesNotContain("Preview only");
    }

    @Test
    void exitCodeOfProposals() {
        ProposalSet empty = ProposalSet.of(List.of());
        ScanSummary ok = new ScanSummary(ROOT, ScanStatus.COMPLETED, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of());
        assertThat(ExitCode.of(ok, empty)).isEqualTo(ExitCode.OK);
    }
}
