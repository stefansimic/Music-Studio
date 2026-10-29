package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.FileFingerprint;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagReadResult;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteVerifier;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.OperationCancelledException;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Default {@link WriteTagsUseCase}. Stateless apart from its ports. */
public final class WriteTagsService implements WriteTagsUseCase {

    private final MediaFileSource files;
    private final TagReader reader;
    private final TagWriter writer;
    private final ExportTarget target;
    private final SafeCopyWriter safeWriter;

    /**
     * @param files  reads fingerprints; not null
     * @param reader reads the tags of sources and of written files; not null
     * @param writer writes tagged copies; not null
     * @param target creates temp names and moves finished files; not null
     */
    public WriteTagsService(MediaFileSource files, TagReader reader, TagWriter writer, ExportTarget target) {
        this.files = Require.nonNull(files, "files");
        this.reader = Require.nonNull(reader, "reader");
        this.writer = Require.nonNull(writer, "writer");
        this.target = Require.nonNull(target, "target");
        this.safeWriter = new SafeCopyWriter(files, reader, writer, target);
    }

    @Override
    public WriteTagsPlan plan(WriteTagsRequest request, Cancellation cancel) {
        Require.nonNull(request, "request");
        Require.nonNull(cancel, "cancel");
        TagWriteOptions options = request.options();
        List<Issue> global = new ArrayList<>();
        Set<MediaFormat> formats = EnumSet.noneOf(MediaFormat.class);
        for (WriteItem item : request.items()) {
            MediaFormat.ofFileName(item.source().getFileName().toString())
                    .filter(f -> writer.writableFormats().contains(f)).ifPresent(formats::add);
        }
        for (MediaFormat format : formats) {
            for (Issue issue : writer.capabilities(format).unsupported(options)) {
                if (!global.contains(issue)) {
                    global.add(issue);
                }
            }
        }
        if (global.stream().anyMatch(issue -> issue.severity() == Severity.ERROR)) {
            return new WriteTagsPlan(request, List.of(), global);
        }
        List<PlannedWrite> items = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (WriteItem item : request.items()) {
            if (cancel.isCancelled()) {
                break;
            }
            items.add(planOne(item, options, targets));
        }
        return new WriteTagsPlan(request, items, global);
    }

    private PlannedWrite planOne(WriteItem item, TagWriteOptions options, Set<String> targets) {
        Path source = item.source();
        Optional<MediaFormat> format = MediaFormat.ofFileName(source.getFileName().toString())
                .filter(f -> writer.writableFormats().contains(f));
        List<Issue> issues = new ArrayList<>();
        if (format.isEmpty()) {
            issues.add(Issue.error(IssueCode.UNSUPPORTED_FORMAT, "This file type cannot be written"));
            return skip(item, format, Optional.empty(), List.of(), MediaMetadata.empty(), issues);
        }
        String key = item.target().normalize().toString().toLowerCase(Locale.ROOT);
        if (key.equals(source.normalize().toString().toLowerCase(Locale.ROOT))) {
            issues.add(Issue.error(IssueCode.TARGET_IS_SOURCE, "The target is the original file"));
        } else if (!targets.add(key)) {
            issues.add(Issue.error(IssueCode.TARGET_EXISTS, "The target is also the target of another file"));
        } else if (target.exists(item.target())) {
            issues.add(Issue.error(IssueCode.TARGET_EXISTS, "The target already exists and is never replaced"));
        }
        if (!issues.isEmpty()) {
            return skip(item, format, Optional.empty(), List.of(), MediaMetadata.empty(), forFile(issues, source));
        }

        FileFingerprint fingerprint;
        TagReadResult read;
        try {
            fingerprint = files.fingerprint(source);
            read = reader.read(source);
        } catch (MediaAccessException e) {
            issues.add(Issue.error(IssueCode.READ_FAILED, e.reason() + ": " + e.getMessage()));
            return skip(item, format, Optional.empty(), List.of(), MediaMetadata.empty(), forFile(issues, source));
        }
        FormatCapabilities capabilities = writer.capabilities(format.get());
        MediaMetadata currentV2 = read.layers().stream().filter(l -> l.version().family() == TagFamily.ID3V2)
                .findFirst().map(TagLayer::metadata).orElse(MediaMetadata.empty());
        FieldChanges changes = FieldChanges.between(currentV2, item.desired(), capabilities.writableFields());
        MediaMetadata expectedV2 = changes.applyTo(currentV2);
        List<FieldChange> described = changes.describe(currentV2);

        if (options.v2Encoding() == V2Encoding.LATIN1) {
            for (var entry : changes.set().entrySet()) {
                if (!fitsLatin1(entry.getValue())) {
                    issues.add(Issue.error(IssueCode.V2_NOT_ENCODABLE, entry.getKey().placeholder()
                            + " contains characters that Latin-1 cannot hold; choose UTF-16 or automatic").forField(
                            entry.getKey()));
                }
            }
        }
        Id3v1Projection.Projection projection = Id3v1Projection.project(item.desired(), options);
        issues.addAll(projection.issues());
        TagWriteOptions effective = options;
        if (projection.skipped()) {
            if (!TagWriteOptions.problems(options.id3v2(), Id3v1Choice.NONE, options.v2Encoding(),
                    options.v1TextMode(), options.removeOtherVersions()).isEmpty()) {
                issues.add(Issue.info(IssueCode.NOTHING_TO_DO, "Nothing left to write for this file"));
                return skip(item, format, Optional.of(fingerprint), described, expectedV2, forFile(issues, source));
            }
            effective = new TagWriteOptions(options.id3v2(), Id3v1Choice.NONE, options.v2Encoding(),
                    options.v1TextMode(), options.v1NonLatinPolicy(), options.removeOtherVersions());
        }
        if (issues.stream().anyMatch(i -> i.severity() == Severity.ERROR)) {
            return skip(item, format, Optional.of(fingerprint), described, expectedV2, forFile(issues, source));
        }
        TagWriteRequest writeRequest = new TagWriteRequest(format.get(), changes, new CoverChange.Keep(), effective,
                projection.values());
        try {
            issues.addAll(writer.preflight(source, writeRequest));
        } catch (MediaAccessException e) {
            issues.add(Issue.error(IssueCode.READ_FAILED, e.reason() + ": " + e.getMessage()));
            return skip(item, format, Optional.of(fingerprint), described, expectedV2, forFile(issues, source));
        }
        boolean losesNothing = issues.stream().noneMatch(i -> i.code() == IssueCode.FRAME_NOT_REPRESENTABLE
                || i.code() == IssueCode.OTHER_VERSION_REMOVED);
        if (changes.isEmpty() && losesNothing && WriteVerifier.verify(read, effective, expectedV2,
                capabilities.writableFields(), projection.values()).isEmpty()) {
            issues.add(Issue.info(IssueCode.NOTHING_TO_DO, "The file already has these tags"));
            return skip(item, format, Optional.of(fingerprint), described, expectedV2, forFile(issues, source));
        }
        return new PlannedWrite(source, item.target(), format, PlannedAction.WRITE, Optional.of(fingerprint),
                described, Optional.of(writeRequest), expectedV2, forFile(issues, source));
    }

    private static boolean fitsLatin1(FieldValue value) {
        return value.display().chars().allMatch(c -> c <= 0xFF);
    }

    private static List<Issue> forFile(List<Issue> issues, Path source) {
        return issues.stream().map(issue -> issue.file().isPresent() ? issue : issue.forFile(source)).toList();
    }

    private static PlannedWrite skip(WriteItem item, Optional<MediaFormat> format,
                                     Optional<FileFingerprint> fingerprint, List<FieldChange> changes,
                                     MediaMetadata expectedV2, List<Issue> issues) {
        return new PlannedWrite(item.source(), item.target(), format, PlannedAction.SKIP, fingerprint, changes,
                Optional.empty(), expectedV2, forFile(issues, item.source()));
    }

    @Override
    public WriteTagsReport apply(WriteTagsPlan plan, ProgressListener progress, Cancellation cancel) {
        Require.nonNull(plan, "plan");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");
        if (!plan.executable()) {
            return new WriteTagsReport(WriteStatus.REJECTED, List.of(), plan.issues());
        }
        List<WriteOutcome> outcomes = new ArrayList<>();
        WriteStatus status = WriteStatus.COMPLETED;
        int total = plan.items().size();
        for (PlannedWrite planned : plan.items()) {
            if (cancel.isCancelled()) {
                status = WriteStatus.CANCELLED;
                break;
            }
            if (planned.action() == PlannedAction.SKIP) {
                outcomes.add(new WriteOutcome.Skipped(planned));
            } else {
                try {
                    outcomes.add(safeWriter.write(planned, cancel));
                } catch (OperationCancelledException e) {
                    status = WriteStatus.CANCELLED;
                    break;
                }
            }
            progress.onProgress(ProgressEvent.of("write", outcomes.size(), total, planned.target()));
        }
        return new WriteTagsReport(status, outcomes, plan.issues());
    }
}
