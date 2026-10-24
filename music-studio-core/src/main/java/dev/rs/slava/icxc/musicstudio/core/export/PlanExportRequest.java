package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Input of {@link PlanExportUseCase}.
 *
 * @param proposals   what to export, in order; not null, no null elements, each source file unique
 * @param sourceRoots the scanned root folders; not null, non-empty, all absolute
 * @param spec        the export specification; not null
 * @param options     the tag write options; not null
 */
public record PlanExportRequest(List<Proposal> proposals, Set<Path> sourceRoots, ExportSpec spec,
                                TagWriteOptions options) {

    /** @throws NullPointerException if a component or element is null; IllegalArgumentException if violated */
    public PlanExportRequest {
        Require.noNullElements(proposals, "proposals");
        Require.noNullElements(sourceRoots, "sourceRoots");
        Require.that(!sourceRoots.isEmpty(), "sourceRoots must not be empty");
        sourceRoots.forEach(root -> Require.absolute(root, "sourceRoots element"));
        Require.nonNull(spec, "spec");
        Require.nonNull(options, "options");
        Set<Path> seen = new HashSet<>();
        for (Proposal proposal : proposals) {
            Require.that(seen.add(proposal.file().path()), "duplicate source file %s", proposal.file().path());
        }
        proposals = List.copyOf(proposals);
        sourceRoots = Set.copyOf(sourceRoots);
    }
}
