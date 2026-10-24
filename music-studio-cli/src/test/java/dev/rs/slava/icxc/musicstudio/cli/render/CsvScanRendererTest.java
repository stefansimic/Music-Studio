package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class CsvScanRendererTest {

    @Test
    void golden() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CsvScanRenderer renderer = new CsvScanRenderer(new PrintWriter(out), new PrintWriter(err), Samples.ROOT);
        renderer.begin();
        renderer.item(Samples.cyrillic());
        renderer.item(Samples.special());
        renderer.item(Samples.unreadable());
        renderer.end(Samples.summary());

        assertThat(out.toString()).isEqualTo(
                "path,title,artist,albumartist,album,track,disc,year,genre,comment,cover,duration,bitrate,tags,"
                        + "conflicts,differs,issues,error\r\n"
                        + "Балашевић/01 Љубав.mp3,Љубав 🎵,Ђорђе Балашевић,,,3/12,,1986,Pop; Rock,,yes,3:45,192,"
                        + "ID3v1.1 + ID3v2.3,artist,artist: v1 'Someone Else',,\r\n"
                        + "\"a,b.mp3\",\"Hello, \"\"World\"\"\",,,,,,,,\"line 1\nline 2\u0001\",no,,,ID3v2.4,,,"
                        + "TRACK: 'A1' is not a valid track value; ignored,\r\n"
                        + "broken.mp3,,,,,,,,,,,,,,,,,not a readable MP3 file\r\n");
        assertThat(err.toString()).isEqualTo("skipped: hidden .cache" + System.lineSeparator());
    }

    @Test
    void quoting() {
        assertThat(CsvScanRenderer.field("plain")).isEqualTo("plain");
        assertThat(CsvScanRenderer.field("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvScanRenderer.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvScanRenderer.field("a\r\nb")).isEqualTo("\"a\r\nb\"");
    }
}
