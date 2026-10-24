package dev.rs.slava.icxc.musicstudio.core.export;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaFormat;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.Severity;
import dev.rs.slava.icxc.musicstudio.core.model.TagFamily;
import dev.rs.slava.icxc.musicstudio.core.model.TagLayer;
import dev.rs.slava.icxc.musicstudio.core.model.TagVersion;
import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.port.MediaAccessException;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import dev.rs.slava.icxc.musicstudio.core.tag.CoverChange;
import dev.rs.slava.icxc.musicstudio.core.tag.FieldChanges;
import dev.rs.slava.icxc.musicstudio.core.tag.FormatCapabilities;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Projection;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteOptions;
import dev.rs.slava.icxc.musicstudio.core.tag.TagWriteRequest;
import dev.rs.slava.icxc.musicstudio.core.tag.V2Encoding;
import dev.rs.slava.icxc.musicstudio.core.tag.WriteExpectation;
import dev.rs.slava.icxc.musicstudio.core.task.Cancellation;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressEvent;
import dev.rs.slava.icxc.musicstudio.core.task.ProgressListener;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * {@link PlanExportUseCase}: per proposal decides target, tag changes and problems. Uses {@link ExportTarget}
 * only for {@code findCollision}; never writes.
 */
public final class PlanExportService implements PlanExportUseCase {

    private static final String PHASE = "plan";
    private static final int LATIN1_MAX = 0xFF;

    private final TagWriter writer;
    private final ExportTarget target;
    private final Clock clock;

    /** @param writer the tag writer; @param target the export target; @param clock for the plan's timestamp */
    public PlanExportService(TagWriter writer, ExportTarget target, Clock clock) {
        this.writer = Require.nonNull(writer, "writer");
        this.target = Require.nonNull(target, "target");
        this.clock = Require.nonNull(clock, "clock");
    }

    @Override
    public ExportPlan plan(PlanExportRequest request, ProgressListener progress, Cancellation cancel) {
        Require.nonNull(request, "request");
        Require.nonNull(progress, "progress");
        Require.nonNull(cancel, "cancel");
        TagWriteOptions options = request.options();
        Path root = request.spec().outputRoot();
        List<Issue> global = new ArrayList<>();
        for (MediaFormat format : formatsOf(request)) {
            if (writer.writableFormats().contains(format)) {
                global.addAll(writer.capabilities(format).unsupported(options));
            }
        }
        boolean blocked = global.stream().anyMatch(i -> i.severity() == Severity.ERROR);
        if (request.sourceRoots().stream().anyMatch(root::startsWith)) {
            global.add(Issue.warning(IssueCode.OUTPUT_INSIDE_SOURCE,
                    "the output folder is inside a scanned folder: " + root));
        }
        Set<String> sources = new HashSet<>();
        request.proposals().forEach(p -> sources.add(key(p.file().path().toString())));
        Set<String> claimed = new HashSet<>();
        List<PlannedFile> files = new ArrayList<>();
        int done = 0;
        for (Proposal proposal : request.proposals()) {
            cancel.throwIfCancelled();
            Path source = proposal.file().path();
            progress.onProgress(ProgressEvent.of(PHASE, done, request.proposals().size(), source));
            files.add(blocked ? skip(proposal, global.stream().filter(i -> i.severity() == Severity.ERROR).toList())
                    : planOne(proposal, request, sources, claimed));
            done++;
        }
        progress.onProgress(ProgressEvent.of(PHASE, done, request.proposals().size()));
        return new ExportPlan(clock.instant(), request.spec(), options, files, global);
    }

    private PlannedFile planOne(Proposal proposal, PlanExportRequest request, Set<String> sources,
                                Set<String> claimed) {
        ScannedFile scanned = proposal.file();
        Path source = scanned.path();
        TagWriteOptions options = request.options();
        List<Issue> issues = new ArrayList<>();
        proposal.issues().stream().filter(i -> i.severity() != Severity.ERROR).forEach(issues::add);
        List<Issue> errors = new ArrayList<>(proposal.issues().stream()
                .filter(i -> i.severity() == Severity.ERROR).toList());
        if (!proposal.exportable() && errors.isEmpty()) {
            errors.add(Issue.error(proposal.status() == dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus.NO_MATCH ? IssueCode.NO_MATCH
                    : IssueCode.AMBIGUOUS_MATCH, "not exported: " + proposal.status()));
        }
        scanned.tags().issues().stream().filter(i -> i.severity() == Severity.ERROR).forEach(errors::add);
        if (!writer.writableFormats().contains(scanned.format())) {
            errors.add(Issue.error(IssueCode.UNSUPPORTED_WRITE_VERSION, scanned.format() + " cannot be written"));
        }
        if (!errors.isEmpty()) {
            return skip(proposal, errors, issues);
        }
        Path out = request.spec().outputRoot().resolve(source.getFileName().toString());
        String outKey = key(out.toString());
        if (sources.contains(outKey)) {
            errors.add(Issue.error(IssueCode.TARGET_IS_SOURCE, "the target is the source file itself: " + out));
        } else if (!claimed.add(outKey)) {
            errors.add(Issue.error(IssueCode.COLLISION_SKIPPED,
                    "another file already uses the target name " + out.getFileName()));
        } else {
            try {
                if (target.findCollision(out).isPresent()) {
                    errors.add(Issue.error(IssueCode.TARGET_EXISTS, "the target already exists: " + out));
                }
            } catch (MediaAccessException e) {
                errors.add(Issue.error(IssueCode.READ_FAILED, "cannot check the target: " + e.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            return skip(proposal, errors, issues);
        }
        FormatCapabilities caps = writer.capabilities(scanned.format());
        List<TagLayer> current = scanned.tags().layers();
        MediaMetadata v2 = scanned.tags().layer(TagFamily.ID3V2).map(TagLayer::metadata)
                .orElse(MediaMetadata.empty());
        MediaMetadata proposed = proposal.proposed();
        FieldChanges changes = FieldChanges.NONE;
        if (options.id3v2() != dev.rs.slava.icxc.musicstudio.core.tag.Id3v2Choice.NONE) {
            changes = FieldChanges.between(v2, proposed, caps.writableFields());
            for (Field field : v2.differingFields(proposed)) {
                if (!caps.writableFields().contains(field)) {
                    issues.add(Issue.warning(IssueCode.UNSUPPORTED_FIELD, field + " cannot be written")
                            .forField(field));
                }
            }
        }
        Optional<dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values> v1 = Optional.empty();
        if (options.id3v1() != Id3v1Choice.NONE) {
            Id3v1Projection.Result projected = Id3v1Projection.project(proposed, options.id3v1(),
                    options.v1TextMode());
            v1 = Optional.of(projected.values());
            issues.addAll(projected.issues());
        }
        TagWriteRequest writeRequest = new TagWriteRequest(scanned.format(), changes, new CoverChange.Keep(),
                options, v1);
        try {
            issues.addAll(writer.preflight(source, writeRequest));
        } catch (MediaAccessException e) {
            return skip(proposal, List.of(Issue.error(IssueCode.READ_FAILED, "cannot read the file: "
                    + e.getMessage())), issues);
        }
        issues.addAll(WriteExpectation.slotIssues(current, options, proposed));
        List<TagLayer> expected = WriteExpectation.layersAfter(current, writeRequest);
        if (options.v2Encoding() == V2Encoding.LATIN1) {
            Optional<Issue> notEncodable = notLatin1(expected);
            if (notEncodable.isPresent()) {
                return skip(proposal, List.of(notEncodable.get()), issues);
            }
        }
        return new PlannedFile(source, scanned.fingerprint(), scanned.format(), PlannedAction.WRITE,
                Optional.of(out), proposal.changes(), Optional.of(writeRequest), current, expected,
                attach(issues, source));
    }

    private static Optional<Issue> notLatin1(List<TagLayer> expected) {
        for (TagLayer layer : expected) {
            if (layer.version().family() != TagFamily.ID3V2) {
                continue;
            }
            for (Field field : EnumSet.copyOf(layer.metadata().values().keySet())) {
                Optional<String> text = layer.metadata().display(field);
                if (text.isPresent() && text.get().chars().anyMatch(c -> c > LATIN1_MAX)) {
                    return Optional.of(Issue.error(IssueCode.V2_NOT_ENCODABLE,
                            field + " has characters outside Latin-1; choose UTF-16 or UTF-8").forField(field));
                }
            }
        }
        return Optional.empty();
    }

    private static PlannedFile skip(Proposal proposal, List<Issue> errors) {
        return skip(proposal, errors, List.of());
    }

    private static PlannedFile skip(Proposal proposal, List<Issue> errors, List<Issue> others) {
        ScannedFile scanned = proposal.file();
        List<Issue> all = new ArrayList<>(others);
        all.addAll(errors);
        List<TagLayer> layers = scanned.tags().layers();
        return new PlannedFile(scanned.path(), scanned.fingerprint(), scanned.format(), PlannedAction.SKIP,
                Optional.empty(), proposal.changes(), Optional.empty(), layers, layers, attach(all, scanned.path()));
    }

    private static List<Issue> attach(List<Issue> issues, Path file) {
        return issues.stream().map(i -> i.file().isPresent() ? i : i.forFile(file)).toList();
    }

    private static Set<MediaFormat> formatsOf(PlanExportRequest request) {
        Set<MediaFormat> formats = EnumSet.noneOf(MediaFormat.class);
        request.proposals().forEach(p -> formats.add(p.file().format()));
        return formats;
    }

    private static String key(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
