package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteVerifier;
import dev.rs.slava.icxc.musicstudio.core.task.ByteProgress;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Writes one planned file safely: temp path next to the target, {@code TagWriter.writeCopy}, read back and compare
 * with the plan, move without replacing, delete the temp file in every case. The original is never opened for
 * writing and an existing file is never replaced.
 *
 * <p><b>Thread-safety:</b> stateless apart from its ports; safe for concurrent use on different targets.
 */
final class SafeCopyWriter {

    private final MediaFileSource files;
    private final TagReader reader;
    private final TagWriter writer;
    private final ExportTarget target;

    SafeCopyWriter(MediaFileSource files, TagReader reader, TagWriter writer, ExportTarget target) {
        this.files = Require.nonNull(files, "files");
        this.reader = Require.nonNull(reader, "reader");
        this.writer = Require.nonNull(writer, "writer");
        this.target = Require.nonNull(target, "target");
    }

    /**
     * @param planned a WRITE item of an executable plan; not null
     * @param cancel  not null; a cancel surfaces as {@code OperationCancelledException} after the temp was deleted
     * @return Written or Failed; never null
     */
    WriteOutcome write(PlannedWrite planned, Cancellation cancel) {
        Require.that(planned.action() == PlannedAction.WRITE, "only WRITE items can be written");
        Path source = planned.source();
        Path destination = planned.target();
        try {
            FileFingerprint now = files.fingerprint(source);
            if (!now.equals(planned.sourceFingerprint().orElseThrow())) {
                return failed(planned, IssueCode.SOURCE_CHANGED, "The source changed after the plan was made");
            }
        } catch (MediaAccessException e) {
            return failed(planned, IssueCode.SOURCE_CHANGED, "The source is no longer readable: " + e.getMessage());
        }
        Path temp = target.reserveTempPath(destination);
        try {
            var request = planned.request().orElseThrow();
            writer.writeCopy(source, temp, request, cancel, ByteProgress.NONE);
            TagReadResult written = reader.read(temp);
            List<Issue> mismatches = WriteVerifier.verify(written, request.options(), planned.expectedV2(),
                    writer.capabilities(request.format()).writableFields(), request.id3v1());
            if (!mismatches.isEmpty()) {
                String message = String.join("; ", mismatches.stream().map(Issue::message).toList());
                return failed(planned, IssueCode.VERIFY_MISMATCH, message);
            }
            target.moveNoReplace(temp, destination);
            return new WriteOutcome.Written(planned, fingerprintOrEmpty(destination));
        } catch (MediaAccessException e) {
            IssueCode code = e.reason() == MediaAccessException.Reason.ALREADY_EXISTS
                    ? IssueCode.TARGET_EXISTS : IssueCode.WRITE_FAILED;
            return failed(planned, code, e.reason() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            if (e instanceof OperationCancelledException) {
                throw e;
            }
            return failed(planned, IssueCode.WRITE_FAILED,
                    "UNEXPECTED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            try {
                target.deleteTemp(temp);
            } catch (MediaAccessException ignored) {
                // nothing more can be done; the temp name is hidden and ends with the temp suffix
            }
        }
    }

    /** The file is complete once moved; failing to describe it afterwards must not turn the export into a failure. */
    private Optional<FileFingerprint> fingerprintOrEmpty(Path file) {
        try {
            return Optional.of(files.fingerprint(file));
        } catch (MediaAccessException e) {
            return Optional.empty();
        }
    }

    private static WriteOutcome failed(PlannedWrite planned, IssueCode code, String message) {
        return new WriteOutcome.Failed(planned, Issue.error(code, message).forFile(planned.source()));
    }
}
