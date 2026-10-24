package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.util.List;
import java.util.Optional;

/** Renders the result of {@code extract}. */
public interface ProposalRenderer {

    /**
     * Renders the proposals.
     *
     * @param proposals  the proposals, in scan order; not null
     * @param unreadable files that could not be read; not null
     * @param summary    the scan summary (ignored/skipped counts); not null
     * @param columns    the fields to show, in order; not null
     * @param pattern    the pattern text; not null
     */
    void render(ProposalSet proposals, List<UnreadableFile> unreadable, ScanSummary summary, List<Field> columns,
                Optional<String> pattern);
}
