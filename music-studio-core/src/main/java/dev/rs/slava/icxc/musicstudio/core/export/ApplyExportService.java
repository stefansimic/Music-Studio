package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteVerifier;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ApplyExportUseCase}: fingerprint → temp → write → verify → move without replace, per file.
 * Only new files are created; nothing existing is touched.
 */
public final class ApplyExportService implements ApplyExportUseCase {

    private static final String PHASE = "write";

    private final TagWriter writer;
    private final TagReader reader;
    private final ExportTarget target;
    private final Clock clock;
    private final Set<Path> running = ConcurrentHashMap.newKeySet();

    /** @param writer the tag writer; @param reader re-reads written copies; @param target file operations; @param clock timing */
    public ApplyExportService(TagWriter writer, TagReader reader, ExportTarget target, Clock clock) {
        this.writer = Require.nonNull(writer, "writer");
        this.reader = Require.nonNull(reader, "reader");
        this.target = Require.nonNull(target, "target");
        this.clock = Require.nonNull(clock, "clock");
    }

    @Override
    public ExportReport apply(ExportPlan plan, ProgressListener progress, Cancellation cancel) {
        Require.nonNull(plan, "plan");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");
        Require.that(plan.executable(), "the plan is not executable");
        Path root = plan.spec().outputRoot().normalize();
        Require.state(running.add(root), "an export to " + root + " is already running");
        try {
            return run(plan, root, progress, cancel);
        } finally {
            running.remove(root);
        }
    }

    private ExportReport run(ExportPlan plan, Path root, ProgressListener progress, Cancellation cancel) {
        Instant start = clock.instant();
        Run run = new Run();
        try {
            run.createdDirectories.addAll(target.createDirectories(root));
        } catch (MediaAccessException e) {
            run.status.set(ExportStatus.ABORTED);
            run.issues.add(Issue.error(IssueCode.WRITE_FAILED, "cannot create the output folder: " + e.getMessage())
                    .forFile(root));
        }
        int total = plan.files().size();
        int done = 0;
        for (PlannedFile file : plan.files()) {
            if (file.action() == PlannedAction.SKIP) {
                run.skipped.add(new SkippedFile(file.source(), file.issues()));
            } else if (run.status.get() == ExportStatus.ABORTED) {
                run.skipped.add(notAttempted(file, IssueCode.WRITE_FAILED, "run aborted"));
            } else if (run.status.get() == ExportStatus.CANCELLED || cancel.isCancelled()) {
                run.status.set(ExportStatus.CANCELLED);
                run.skipped.add(notAttempted(file, IssueCode.CANCELLED, "cancelled"));
            } else {
                progress.onProgress(ProgressEvent.of(PHASE, done, total, file.source()));
                writeOne(file, run, progress, cancel, done, total);
            }
            done++;
        }
        progress.onProgress(ProgressEvent.of(PHASE, done, total));
        if (run.status.get() == ExportStatus.COMPLETED && !run.failed.isEmpty()) {
            run.status.set(ExportStatus.COMPLETED_WITH_ERRORS);
        }
        assert run.written.size() + run.skipped.size() + run.failed.size() == total : "every file is accounted for";
        return new ExportReport(run.status.get(), root, run.written, run.skipped, run.failed, run.createdDirectories,
                run.issues, Duration.between(start, clock.instant()));
    }

    private static SkippedFile notAttempted(PlannedFile file, IssueCode code, String why) {
        return new SkippedFile(file.source(),
                List.of(Issue.warning(code, "not attempted: " + why).forFile(file.source())));
    }

    private void writeOne(PlannedFile file, Run run, ProgressListener progress, Cancellation cancel, int done,
                          int total) {
        Path source = file.source();
        Path out = file.target().orElseThrow();
        Path temp = null;
        try {
            checkSource(file);
            temp = target.reserveTempPath(out);
            ByteProgress bytes = (written, size) -> progress.onProgress(
                    new ProgressEvent(PHASE, done, total, Optional.of(source), written, size));
            writer.writeCopy(source, temp, file.writeRequest().orElseThrow(), cancel, bytes);
            checkSource(file);
            TagReadResult actual = reader.read(temp);
            List<Issue> mismatches = WriteVerifier.verify(file.expectedLayers(),
                    reader.read(source).stream(), actual);
            if (!mismatches.isEmpty()) {
                run.failed.add(new FailedFile(source, Optional.of(out), mismatches.getFirst().forFile(source)));
                return;
            }
            target.moveNoReplace(temp, out);
            run.written.add(new ExportedFile(source, out,
                    file.issues().stream().filter(i -> i.severity() != Severity.ERROR).toList()));
        } catch (OperationCancelledException e) {
            run.status.set(ExportStatus.CANCELLED);
            run.skipped.add(notAttempted(file, IssueCode.CANCELLED, "cancelled"));
        } catch (SourceChanged e) {
            run.failed.add(new FailedFile(source, Optional.of(out), Issue.error(IssueCode.SOURCE_CHANGED,
                    "the source changed or disappeared since the plan").forFile(source)));
        } catch (MediaAccessException e) {
            fail(file, out, e, run);
        } catch (RuntimeException e) {
            run.failed.add(new FailedFile(source, Optional.of(out), Issue.error(IssueCode.WRITE_FAILED,
                    "unexpected error: " + e).forFile(source)));
        } finally {
            if (temp != null) {
                deleteQuietly(temp, run, source);
            }
        }
    }

    private void checkSource(PlannedFile file) throws MediaAccessException {
        try {
            FileFingerprint now = target.fingerprint(file.source());
            if (!now.equals(file.sourceFingerprint())) {
                throw new SourceChanged();
            }
        } catch (MediaAccessException e) {
            if (e.reason() == MediaAccessException.Reason.NOT_FOUND) {
                throw new SourceChanged();
            }
            throw e;
        }
    }

    private void fail(PlannedFile file, Path out, MediaAccessException e, Run run) {
        IssueCode code = switch (e.reason()) {
            case ALREADY_EXISTS -> IssueCode.TARGET_EXISTS;
            case NO_SPACE -> IssueCode.INSUFFICIENT_SPACE;
            case FILE_TOO_LARGE -> IssueCode.FILE_TOO_LARGE_FOR_TARGET_FS;
            default -> IssueCode.WRITE_FAILED;
        };
        run.failed.add(new FailedFile(file.source(), Optional.of(out),
                Issue.error(code, e.getMessage()).forFile(file.source())));
        if (e.reason() == MediaAccessException.Reason.NO_SPACE) {
            run.status.set(ExportStatus.ABORTED);
            run.issues.add(Issue.error(IssueCode.INSUFFICIENT_SPACE, "the disk is full; the run was stopped"));
        }
    }

    private void deleteQuietly(Path temp, Run run, Path source) {
        try {
            target.deleteTemp(temp);
        } catch (MediaAccessException e) {
            run.issues.add(Issue.warning(IssueCode.WRITE_FAILED, "could not delete the temp file " + temp + ": "
                    + e.getMessage()).forFile(source));
        }
    }

    /** Signals that a source differs from its plan-time fingerprint. */
    private static final class SourceChanged extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SourceChanged() {
            super(null, null, false, false);
        }
    }

    /** Mutable state of one run. */
    private static final class Run {
        final AtomicReference<ExportStatus> status = new AtomicReference<>(ExportStatus.COMPLETED);
        final List<ExportedFile> written = new ArrayList<>();
        final List<SkippedFile> skipped = new ArrayList<>();
        final List<FailedFile> failed = new ArrayList<>();
        final List<Path> createdDirectories = new ArrayList<>();
        final List<Issue> issues = new ArrayList<>();
    }
}
