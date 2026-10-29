package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.export.WriteOptionsUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsPlan;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsReport;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsRequest;
import dev.rs.slava.icxc.musicstudio.core.export.WriteTagsUseCase;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.util.List;
import java.util.Set;

/** A {@link MusicStudio} for CLI tests that only exercise the scan use case: the write use cases must not be called. */
final class TestStudios {

    private TestStudios() {}

    static MusicStudio scanOnly(ScanMediaUseCase scan) {
        return new MusicStudio(scan, new WriteOptionsUseCase() {
            @Override
            public FormatCapabilities capabilities(MediaFormat format) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public Set<MediaFormat> writableFormats() {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats) {
                throw new UnsupportedOperationException("not used by this test");
            }
        }, new WriteTagsUseCase() {
            @Override
            public WriteTagsPlan plan(WriteTagsRequest request, Cancellation cancel) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public WriteTagsReport apply(WriteTagsPlan plan, ProgressListener progress, Cancellation cancel) {
                throw new UnsupportedOperationException("not used by this test");
            }
        });
    }
}
