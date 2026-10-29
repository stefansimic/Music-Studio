package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.meta;
import static dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport.position;
import static org.assertj.core.api.Assertions.assertThat;

import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.V1NonLatinPolicy;
import dev.rs.slava.icxc.musicstudio.core.tag.V1TextMode;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.testkit.fixture.Fixture;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.Id3Raw;
import dev.rs.slava.icxc.musicstudio.core.testkit.roundtrip.RoundTripSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jaudiotagger.tag.TagOptionSingleton;
import org.junit.jupiter.api.Test;

/** D10: jaudiotagger keeps global options; parallel writes with different options must not bleed into each other. */
class JaudiotaggerConcurrencyTest extends WriterTestSupport {

    private static final MediaMetadata M = meta(Field.TITLE, "Ђорђе Title", Field.ARTIST, "Artist",
            Field.TRACK, position(4, 0));

    @Test
    void eightThreadsWithDifferentOptionsEachGetTheirOwnResultAndGlobalOptionsAreRestored() throws Exception {
        TagOptionSingleton singleton = TagOptionSingleton.getInstance();
        boolean v1Before = singleton.isId3v1Save();
        boolean v2Before = singleton.isId3v2Save();
        List<TagWriteOptions> options = new ArrayList<>();
        for (Id3v2Choice v2 : new Id3v2Choice[] {Id3v2Choice.V2_2, Id3v2Choice.V2_3, Id3v2Choice.V2_4,
                Id3v2Choice.NONE}) {
            for (Id3v1Choice v1 : new Id3v1Choice[] {Id3v1Choice.V1_1, Id3v1Choice.NONE}) {
                if (v2 == Id3v2Choice.NONE && v1 == Id3v1Choice.NONE) {
                    continue;
                }
                options.add(new TagWriteOptions(v2, v1, V2Encoding.AUTO, V1TextMode.ASCII,
                        V1NonLatinPolicy.TRANSLITERATE, false));
            }
        }
        while (options.size() < 8) {
            options.add(TagWriteOptions.DEFAULT);
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                TagWriteOptions mine = options.get(t);
                int thread = t;
                Callable<Void> task = () -> {
                    start.await();
                    for (int round = 0; round < 6; round++) {
                        Path source = copy(Fixture.NO_TAGS);
                        Path target = out().resolve("t" + thread + "-" + round + ".mp3");
                        var request = RoundTripSupport.request(MediaMetadata.empty(), M, mine);
                        writer.writeCopy(source, target, request,
                                dev.rs.slava.icxc.musicstudio.core.task.Cancellation.NONE,
                                dev.rs.slava.icxc.musicstudio.core.task.ByteProgress.NONE);
                        byte[] bytes = RoundTripSupport.bytes(target);
                        var v2 = Id3Raw.v2(bytes);
                        assertThat(v2.isPresent()).as("v2 of thread %d %s", thread, mine).isEqualTo(
                                mine.id3v2() != Id3v2Choice.NONE);
                        v2.ifPresent(tag -> assertThat(tag.major()).isEqualTo(
                                mine.id3v2() == Id3v2Choice.V2_2 ? 2 : mine.id3v2() == Id3v2Choice.V2_3 ? 3 : 4));
                        assertThat(Id3Raw.v1(bytes).isPresent()).as("v1 of thread %d %s", thread, mine)
                                .isEqualTo(request.options().id3v1() != Id3v1Choice.NONE);
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(singleton.isId3v1Save()).isEqualTo(v1Before);
        assertThat(singleton.isId3v2Save()).isEqualTo(v2Before);
    }
}
