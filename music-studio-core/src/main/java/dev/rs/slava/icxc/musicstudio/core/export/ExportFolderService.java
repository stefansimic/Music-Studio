package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.ExtractionSpec;
import dev.rs.slava.icxc.musicstudio.core.extract.ManualEdits;
import dev.rs.slava.icxc.musicstudio.core.extract.MergeMode;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeMetadataUseCase;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposeRequest;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.V1Comparison;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanItem;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanMediaUseCase;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanRequest;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@link ExportFolderUseCase} composed from the scan, propose, options, plan and apply use cases. */
public final class ExportFolderService implements ExportFolderUseCase {

    private final ScanMediaUseCase scan;
    private final ProposeMetadataUseCase propose;
    private final WriteOptionsUseCase writeOptions;
    private final PlanExportUseCase planExport;
    private final ApplyExportUseCase applyExport;

    /** @param scan scans; @param propose derives metadata; @param writeOptions validates options; @param planExport plans; @param applyExport applies. All not null. */
    public ExportFolderService(ScanMediaUseCase scan, ProposeMetadataUseCase propose,
                               WriteOptionsUseCase writeOptions, PlanExportUseCase planExport,
                               ApplyExportUseCase applyExport) {
        this.scan = Require.nonNull(scan, "scan");
        this.propose = Require.nonNull(propose, "propose");
        this.writeOptions = Require.nonNull(writeOptions, "writeOptions");
        this.planExport = Require.nonNull(planExport, "planExport");
        this.applyExport = Require.nonNull(applyExport, "applyExport");
    }

    @Override
    public ExportFolderResult export(ExportFolderRequest request, ProgressListener progress, Cancellation cancel) {
        Require.nonNull(request, "request");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");
        Set<MediaFormat> formats = writeOptions.writableFormats();
        List<Issue> unsupported = writeOptions.validate(request.options(), formats);
        if (!unsupported.isEmpty()) {
            return new ExportFolderResult(unsupported, Optional.empty(), List.of(), Optional.empty(),
                    Optional.empty());
        }
        List<ScannedFile> files = new ArrayList<>();
        List<UnreadableFile> unreadable = new ArrayList<>();
        ScanSummary summary = scan.scan(new ScanRequest(request.source(), request.recursive(),
                request.includeHidden(), formats, Set.of(), V1Comparison.LENIENT),
                item -> collect(item, files, unreadable), progress, cancel);
        if (summary.status() != ScanStatus.COMPLETED) {
            return new ExportFolderResult(List.of(), Optional.of(summary), unreadable, Optional.empty(),
                    Optional.empty());
        }
        ExtractionSpec extraction = request.extraction().orElseGet(() -> new ExtractionSpec(Optional.empty(),
                Map.of(), MergeMode.OVERWRITE, Optional.empty(), false));
        ProposalSet proposals = propose.propose(new ProposeRequest(files, extraction, ManualEdits.NONE));
        ExportPlan plan = planExport.plan(new PlanExportRequest(proposals.proposals(), Set.of(request.source()),
                new ExportSpec(request.outputRoot()), request.options()), progress, cancel);
        Optional<ExportReport> report = request.apply() && plan.executable()
                ? Optional.of(applyExport.apply(plan, progress, cancel)) : Optional.empty();
        return new ExportFolderResult(List.of(), Optional.of(summary), unreadable, Optional.of(plan), report);
    }

    private static void collect(ScanItem item, List<ScannedFile> files, List<UnreadableFile> unreadable) {
        switch (item) {
            case ScannedFile file -> files.add(file);
            case UnreadableFile file -> unreadable.add(file);
        }
    }
}
