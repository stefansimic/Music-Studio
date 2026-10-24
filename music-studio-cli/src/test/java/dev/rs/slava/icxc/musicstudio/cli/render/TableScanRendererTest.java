package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableScanRendererTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final TableScanRenderer renderer =
            new TableScanRenderer(new PrintWriter(out), new PrintWriter(err), Samples.ROOT);

    @Test
    void rowsAlignAndMarkConflicts() {
        renderer.begin();
        renderer.item(Samples.cyrillic());
        renderer.item(Samples.special());
        renderer.item(Samples.unreadable());
        renderer.end(Samples.summary());
        List<String> lines = out.toString().lines().toList();

        String header = lines.get(0);
        assertThat(header).startsWith("PATH").contains("TITLE").contains("TAGS").endsWith("NOTES");
        int tagsColumn = Text.width(header.substring(0, header.indexOf("TAGS")));
        String cyrillic = lines.get(2);
        assertThat(cyrillic).startsWith("Балашевић/01 Љубав.mp3").contains("Ђорђе Балашевић*")
                .contains("3/12").contains("Pop; Rock").contains("yes").contains("3:45").contains("192")
                .contains("artist: v1 'Someone Else'");
        // terminal columns line up even with emoji (2 columns) in the title
        assertThat(Text.width(cyrillic.substring(0, cyrillic.indexOf("ID3v1.1")))).isEqualTo(tagsColumn);
        assertThat(lines.get(3)).startsWith("a,b.mp3").contains("line 1 line 2").contains("'A1'");
        assertThat(lines.get(4)).startsWith("broken.mp3").contains("ERROR: not a readable MP3 file");
        assertThat(out.toString())
                .contains("3 MP3 files: 2 read, 1 unreadable.")
                .contains("Ignored 2 other files (jpg: 1, txt: 1).")
                .contains("Skipped (not scanned):")
                .containsPattern("hidden +\\.cache");
    }

    @Test
    void controlCharactersFromTagsAndNamesNeverReachTheTerminal() {
        renderer.begin();
        renderer.item(Samples.special());
        renderer.item(Samples.unreadable());
        renderer.end(Samples.summary());
        assertThat(out.toString()).doesNotContain("\u0001").doesNotContain("\u001B");
    }

    @Test
    void failedScanPrintsOnlyTheError() {
        renderer.begin();
        renderer.end(new ScanSummary(Samples.ROOT, ScanStatus.FAILED, 0, 0, 0, 0, 0, Map.of(), List.of(),
                List.of(Issue.error(IssueCode.ROOT_NOT_FOUND, "folder not found: /music"))));
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).contains("Error: folder not found: /music");
    }
}
