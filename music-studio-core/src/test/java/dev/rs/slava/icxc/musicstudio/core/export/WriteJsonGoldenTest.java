package dev.rs.slava.icxc.musicstudio.core.export;

import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.OUT;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.layer;
import static dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio.read;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import dev.rs.slava.icxc.musicstudio.core.testkit.fake.FakeStudio;
import java.util.List;
import org.junit.jupiter.api.Test;

/** X03: golden JSON of the write records; guards that they stay expressible without reflection. */
class WriteJsonGoldenTest {

    private static final String OPTIONS = "{\"id3v2\":\"V2_3\",\"id3v1\":\"V1_1\",\"v2Encoding\":\"AUTO\","
            + "\"v1TextMode\":\"ASCII\",\"v1NonLatinPolicy\":\"SKIP_V1\",\"removeOtherVersions\":false}";

    @Test
    void options() {
        assertThat(WriteJson.options(TagWriteOptions.DEFAULT)).isEqualTo(OPTIONS);
    }

    @Test
    void requestPlanAndReportAreStable() {
        FakeStudio studio = new FakeStudio();
        var source = studio.addSource("a.mp3", read(layer(TagVersion.ID3V2_3, meta(Field.TITLE, "Old"))));
        var request = new WriteTagsRequest(List.of(new WriteItem(source, OUT.resolve("a.mp3"),
                meta(Field.TITLE, "Ελευθερία", Field.ARTIST, "Art \"q\""))), TagWriteOptions.DEFAULT);

        assertThat(WriteJson.request(request)).isEqualTo("{\"items\":[{\"source\":\"/music/in/a.mp3\","
                + "\"target\":\"/music/out/a.mp3\",\"desired\":{\"TITLE\":{\"text\":\"Ελευθερία\"},"
                + "\"ARTIST\":{\"text\":\"Art \\\"q\\\"\"}}}],\"options\":" + OPTIONS + "}");

        WriteTagsPlan plan = studio.service.plan(request, Cancellation.NONE);
        String planned = "{\"source\":\"/music/in/a.mp3\",\"target\":\"/music/out/a.mp3\",\"format\":\"MP3\","
                + "\"action\":\"WRITE\",\"sourceFingerprint\":{\"sizeBytes\":10,\"lastModified\":"
                + "\"2021-02-03T04:05:06Z\"},\"changes\":[{\"field\":\"TITLE\",\"old\":{\"text\":\"Old\"},"
                + "\"new\":{\"text\":\"Ελευθερία\"}},{\"field\":\"ARTIST\",\"old\":null,\"new\":{\"text\":"
                + "\"Art \\\"q\\\"\"}}],\"request\":{\"format\":\"MP3\",\"changes\":{\"set\":{\"TITLE\":{\"text\":"
                + "\"Ελευθερία\"},\"ARTIST\":{\"text\":\"Art \\\"q\\\"\"}},\"clear\":[]},\"cover\":\"KEEP\","
                + "\"options\":" + OPTIONS.replace("\"id3v1\":\"V1_1\"", "\"id3v1\":\"NONE\"") + ",\"id3v1\":null},"
                + "\"expectedV2\":{\"TITLE\":{\"text\":\"Ελευθερία\"},\"ARTIST\":{\"text\":\"Art \\\"q\\\"\"}},"
                + "\"issues\":[{\"severity\":\"WARNING\",\"code\":\"V1_SKIPPED_NON_LATIN\",\"message\":"
                + q(plan.items().get(0).issues().get(0).message()) + ",\"file\":\"/music/in/a.mp3\",\"field\":null}]}";
        assertThat(WriteJson.plan(plan)).isEqualTo("{\"request\":" + WriteJson.request(request) + ",\"items\":["
                + planned + "],\"issues\":[]}");

        WriteTagsReport report = studio.service.apply(plan, ProgressListener.NONE, Cancellation.NONE);
        assertThat(WriteJson.report(report)).isEqualTo("{\"status\":\"COMPLETED\",\"outcomes\":[{\"type\":"
                + "\"written\",\"target\":\"/music/out/a.mp3\",\"targetFingerprint\":{\"sizeBytes\":10,"
                + "\"lastModified\":\"2021-02-03T04:05:06Z\"}}],\"issues\":[]}");
    }

    private static String q(String s) {
        return WriteJson.q(s);
    }
}
