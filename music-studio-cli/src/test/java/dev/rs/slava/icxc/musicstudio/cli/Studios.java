package dev.rs.slava.icxc.musicstudio.cli;

import dev.rs.slava.icxc.musicstudio.app.MusicStudio;
import dev.rs.slava.icxc.musicstudio.core.export.ApplyExportUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.PlanExportUseCase;
import dev.rs.slava.icxc.musicstudio.core.export.WriteOptionsUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.util.List;
import java.util.Set;

/** Test wiring: a {@link MusicStudio} whose export parts must not be used. */
final class Studios {

    private Studios() {
    }

    static MusicStudio of(ScanMediaUseCase scan, ProposeMetadataUseCase propose) {
        return new MusicStudio(scan, propose, new WriteOptionsUseCase() {
            @Override
            public FormatCapabilities capabilities(MediaFormat format) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<MediaFormat> writableFormats() {
                return Set.of(MediaFormat.MP3);
            }

            @Override
            public List<Issue> validate(TagWriteOptions options, Set<MediaFormat> formats) {
                return List.of();
            }
        }, (PlanExportUseCase) (request, progress, cancel) -> {
            throw new UnsupportedOperationException();
        }, (ApplyExportUseCase) (plan, progress, cancel) -> {
            throw new UnsupportedOperationException();
        }, (request, progress, cancel) -> {
            throw new UnsupportedOperationException();
        });
    }
}
