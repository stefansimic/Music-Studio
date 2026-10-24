package dev.rs.slava.icxc.musicstudio.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanStatus;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExportFolderServiceTest {

    private static final Path SRC = Path.of("/music").toAbsolutePath();
    private static final Path OUT = Path.of("/out").toAbsolutePath();

    private final List<String> calls = new ArrayList<>();
    private List<Issue> unsupported = List.of();
    private ScanStatus scanStatus = ScanStatus.COMPLETED;

    private ExportFolderService service() {
        return new ExportFolderService((request, sink, progress, cancel) -> {
            calls.add("scan");
            return new ScanSummary(SRC, scanStatus, 0, 0, 0, 0, 0, Map.of(), List.of(), List.of());
        }, request -> {
            calls.add("propose");
            return ProposalSet.of(List.of());
        }, new WriteOptionsUseCase() {
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
                calls.add("validate");
                return unsupported;
            }
        }, (request, progress, cancel) -> {
            calls.add("plan");
            return new ExportPlan(Instant.EPOCH, request.spec(), request.options(), List.of(), List.of());
        }, (plan, progress, cancel) -> {
            calls.add("apply");
            return new ExportReport(ExportStatus.COMPLETED, plan.spec().outputRoot(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), Duration.ZERO);
        });
    }

    private ExportFolderResult export(boolean apply) {
        return service().export(new ExportFolderRequest(SRC, false, false, Optional.empty(), OUT,
                TagWriteOptions.DEFAULT, apply), ProgressListener.NONE, Cancellation.NONE);
    }

    @Test
    void rejectedOptionsStopBeforeScanning() {
        unsupported = List.of(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION, "no"));
        ExportFolderResult result = export(true);
        assertThat(result.optionIssues()).hasSize(1);
        assertThat(result.scan()).isEmpty();
        assertThat(calls).containsExactly("validate");
    }

    @Test
    void failedScanStopsBeforePlanning() {
        scanStatus = ScanStatus.CANCELLED;
        ExportFolderResult result = export(true);
        assertThat(result.scan()).isPresent();
        assertThat(result.plan()).isEmpty();
        assertThat(calls).containsExactly("validate", "scan");
    }

    @Test
    void dryRunPlansButNeverApplies() {
        ExportFolderResult result = export(false);
        assertThat(result.plan()).isPresent();
        assertThat(result.report()).isEmpty();
        assertThat(calls).containsExactly("validate", "scan", "propose", "plan");
    }

    @Test
    void nonExecutablePlanIsNotApplied() {
        assertThat(export(true).report()).isEmpty();
        assertThat(calls).doesNotContain("apply");
    }

    @Test
    void preconditionsAreChecked() {
        assertThatThrownBy(() -> service().export(null, ProgressListener.NONE, Cancellation.NONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExportFolderRequest(Path.of("rel"), false, false, Optional.empty(), OUT,
                TagWriteOptions.DEFAULT, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
