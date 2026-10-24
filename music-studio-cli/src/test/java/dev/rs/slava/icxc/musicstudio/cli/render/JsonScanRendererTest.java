package dev.rs.slava.icxc.musicstudio.cli.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonScanRendererTest {

    @Test
    void golden() {
        StringWriter out = new StringWriter();
        JsonScanRenderer renderer = new JsonScanRenderer(new PrintWriter(out), Samples.ROOT);
        renderer.begin();
        renderer.item(Samples.cyrillic());
        renderer.item(Samples.special());
        renderer.item(Samples.unreadable());
        renderer.end(Samples.summary());

        String root = Json.string(Samples.ROOT.toString());
        assertThat(out.toString()).isEqualTo("{\"root\":" + root + ",\"items\":[\n"
                + "{\"path\":\"Балашевић/01 Љубав.mp3\",\"format\":\"mp3\",\"versions\":[\"ID3v1.1\",\"ID3v2.3\"],"
                + "\"effective\":{\"title\":\"Љубав 🎵\",\"artist\":\"Ђорђе Балашевић\",\"year\":1986,"
                + "\"track\":{\"number\":3,\"total\":12},\"genre\":[\"Pop\",\"Rock\"]},"
                + "\"layers\":[{\"version\":\"ID3v2.3\",\"fields\":{\"title\":\"Љубав 🎵\",\"artist\":\"Ђорђе Балашевић\","
                + "\"year\":1986,\"track\":{\"number\":3,\"total\":12},\"genre\":[\"Pop\",\"Rock\"]}},"
                + "{\"version\":\"ID3v1.1\",\"fields\":{\"title\":\"Ljubav\",\"artist\":\"Someone Else\"}}],"
                + "\"conflicts\":[\"artist\"],"
                + "\"stream\":{\"durationSeconds\":225.000,\"bitrateKbps\":192,\"variableBitrate\":false},"
                + "\"cover\":{\"type\":\"front_cover\",\"mimeType\":\"image/jpeg\",\"sizeBytes\":1234},\"issues\":[]},\n"
                + "{\"path\":\"a,b.mp3\",\"format\":\"mp3\",\"versions\":[\"ID3v2.4\"],"
                + "\"effective\":{\"title\":\"Hello, \\\"World\\\"\",\"comment\":\"line 1\\nline 2\\u0001\"},"
                + "\"layers\":[{\"version\":\"ID3v2.4\",\"fields\":{\"title\":\"Hello, \\\"World\\\"\","
                + "\"comment\":\"line 1\\nline 2\\u0001\"}}],\"conflicts\":[],\"stream\":null,\"cover\":null,"
                + "\"issues\":[{\"severity\":\"warning\",\"code\":\"READ_VALUE_INVALID\",\"field\":\"track\","
                + "\"message\":\"TRACK: 'A1' is not a valid track value; ignored\"}]},\n"
                + "{\"path\":\"broken.mp3\",\"format\":\"mp3\",\"error\":{\"severity\":\"error\",\"code\":\"READ_FAILED\","
                + "\"field\":null,\"message\":\"not a readable MP3 file\"}}\n"
                + "],\"summary\":{\"status\":\"completed\",\"found\":3,\"read\":2,\"failed\":1,\"ignored\":2,"
                + "\"ignoredByExtension\":{\"jpg\":1,\"txt\":1},\"skipped\":[{\"path\":\".cache\",\"reason\":\"hidden\"}],"
                + "\"issues\":[]}}" + System.lineSeparator());
    }

    @Test
    void emptyScanIsValidJson() {
        StringWriter out = new StringWriter();
        JsonScanRenderer renderer = new JsonScanRenderer(new PrintWriter(out), Samples.ROOT);
        renderer.begin();
        renderer.end(new ScanSummary(Samples.ROOT, ScanStatus.COMPLETED, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of()));
        assertThat(out.toString()).contains("\"items\":[],\"summary\"");
    }

    @Test
    void escapesControlCharacters() {
        assertThat(Json.string("a\"b\\c\n\t\u0000 é")).isEqualTo("\"a\\\"b\\\\c\\n\\t\\u0000\\u2028é\"");
        assertThat(Json.string(null)).isEqualTo("null");
    }
}
