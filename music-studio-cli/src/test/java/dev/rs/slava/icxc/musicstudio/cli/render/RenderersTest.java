package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RenderersTest {

    private static String render(Function<PrintWriter, ScanRenderer> factory, List<ScanItem> items, ScanSummary summary) {
        StringWriter out = new StringWriter();
        ScanRenderer renderer = factory.apply(new PrintWriter(out));
        renderer.begin(ScanSamples.REQUEST);
        items.forEach(renderer::item);
        renderer.end(summary);
        return out.toString();
    }

    @Test
    void tableGolden() {
        Golden.assertMatches("scan.table.txt",
                render(TableScanRenderer::new, ScanSamples.items(), ScanSamples.summary()));
    }

    @Test
    void jsonGolden() {
        Golden.assertMatches("scan.json",
                render(JsonScanRenderer::new, ScanSamples.items(), ScanSamples.summary()));
    }

    @Test
    void csvGolden() {
        StringWriter err = new StringWriter();
        String csv = render(out -> new CsvScanRenderer(out, new PrintWriter(err)), ScanSamples.items(),
                ScanSamples.summary());
        Golden.assertMatches("scan.csv", csv);
        Golden.assertMatches("scan.csv.stderr.txt", err.toString());
    }

    @Test
    void jsonIsWellFormed() {
        JsonSyntax.assertValid(render(JsonScanRenderer::new, ScanSamples.items(), ScanSamples.summary()));
        JsonSyntax.assertValid(render(JsonScanRenderer::new, List.of(), ScanSamples.emptySummary()));
    }

    @Test
    void jsonEscapesControlCharactersQuotesAndLoneSurrogates() {
        Path path = Path.of("/music/ta\"b\\ \u0001.mp3");
        String message = "line1\nline2\ttab \"quoted\" nul:\u0000 separator:  lone:\uD800 end";
        ScanItem item = new UnreadableFile(path, MediaFormat.MP3,
                Issue.error(IssueCode.READ_FAILED, message).forFile(path));
        String json = render(JsonScanRenderer::new, List.of(item), ScanSamples.emptySummary());
        JsonSyntax.assertValid(json);
        assertThat(json).contains("\\u0001").contains("\\ud800").contains("\\n").contains("\\t")
                .contains("\\u0000").contains("\\u2028").contains("\\\"quoted\\\"")
                .doesNotContain("\u0001").doesNotContain("\u0000").doesNotContain(" ").doesNotContain("\n  line2");
    }

    @Test
    void jsonKeepsNonAsciiAsIs() {
        String json = render(JsonScanRenderer::new, ScanSamples.items(), ScanSamples.summary());
        assertThat(json).contains("Ђорђе Балашевић").contains("🎵").contains("čćšžđ");
    }

    @Test
    void csvQuotesCommasQuotesAndLineBreaks() {
        assertThat(CsvScanRenderer.escape("plain")).isEqualTo("plain");
        assertThat(CsvScanRenderer.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvScanRenderer.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvScanRenderer.escape("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvScanRenderer.escape("cr\rhere")).isEqualTo("\"cr\rhere\"");
        assertThat(CsvScanRenderer.escape("Ђорђе")).isEqualTo("Ђорђе");
    }

    @Test
    void csvUsesCrLfAndKeepsStdoutClean() {
        StringWriter err = new StringWriter();
        String csv = render(out -> new CsvScanRenderer(out, new PrintWriter(err)), ScanSamples.items(),
                ScanSamples.summary());
        assertThat(csv).endsWith("\r\n").doesNotContain("skipped");
        assertThat(csv.split("\r\n")).hasSize(1 + ScanSamples.items().size());
        assertThat(err.toString()).contains("skipped: hidden /music/.cache");
    }

    @Test
    void tableCellsNeverBreakARow() {
        Path path = Path.of("/music/x.mp3");
        ScanItem item = new UnreadableFile(path, MediaFormat.MP3, Issue.error(IssueCode.READ_FAILED, "bad\nnews").forFile(path));
        String table = render(TableScanRenderer::new, List.of(item), ScanSamples.emptySummary());
        assertThat(table).contains("ERROR: bad news");
    }

    @Test
    void tableCutsLongValuesWithoutSplittingCodePoints() {
        assertThat(Text.fit("abcdef", 4)).isEqualTo("abc…");
        assertThat(Text.fit("ab", 4)).isEqualTo("ab  ");
        String emoji = "a🎵b🎵c";
        assertThat(Text.fit(emoji, 4)).isEqualTo("a🎵b…");
        assertThat(Text.fit("line\nbreak", 20)).doesNotContain("\n");
    }

    @Test
    void durationIsMinutesAndSeconds() {
        assertThat(Text.minutesSeconds(java.time.Duration.ofMillis(1045))).isEqualTo("0:01");
        assertThat(Text.minutesSeconds(java.time.Duration.ofSeconds(187))).isEqualTo("3:07");
        assertThat(Text.minutesSeconds(java.time.Duration.ofSeconds(3600))).isEqualTo("60:00");
    }

    @Test
    void preconditions() {
        PrintWriter out = new PrintWriter(new StringWriter());
        assertThatThrownBy(() -> new TableScanRenderer(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JsonScanRenderer(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CsvScanRenderer(out, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TableScanRenderer(out).item(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JsonScanRenderer(out).begin(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CsvScanRenderer(out, out).end(null)).isInstanceOf(NullPointerException.class);
    }
}
