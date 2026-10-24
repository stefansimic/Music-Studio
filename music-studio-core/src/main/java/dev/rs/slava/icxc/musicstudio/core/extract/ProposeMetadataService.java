package dev.rs.slava.icxc.musicstudio.core.extract;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.FieldValue;
import dev.rs.slava.icxc.musicstudio.core.model.Issue;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.MediaMetadata;
import dev.rs.slava.icxc.musicstudio.core.model.PathOrder;
import dev.rs.slava.icxc.musicstudio.core.pattern.MatchKind;
import dev.rs.slava.icxc.musicstudio.core.pattern.MatchResult;
import dev.rs.slava.icxc.musicstudio.core.pattern.PathMatcher;
import dev.rs.slava.icxc.musicstudio.core.scan.ScannedFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Implements {@link ProposeMetadataUseCase}: per file match → pattern values → fixed values; per batch
 * auto-numbering; per file manual edits, changes and issues. Pure and stateless.
 *
 * <p><b>Thread-safety:</b> stateless, safe for concurrent use.
 */
public final class ProposeMetadataService implements ProposeMetadataUseCase {

    /** The proposal under construction for one file (local to one propose call). */
    private record Draft(ScannedFile file, MatchStatus status, Optional<MatchResult> match,
                         Map<Field, FieldValue> values, Map<Field, ChangeOrigin> origins, List<Issue> issues) {
    }

    /** Creates the service. */
    public ProposeMetadataService() {
        // stateless
    }

    @Override
    public ProposalSet propose(ProposeRequest request) {
        Require.nonNull(request, "request");
        ExtractionSpec spec = request.spec();
        List<Draft> drafts = new ArrayList<>(request.files().size());
        for (ScannedFile file : request.files()) {
            drafts.add(draft(file, spec, request.edits()));
        }
        spec.autoNumbering().ifPresent(numbering -> autoNumber(drafts, numbering, spec.mergeMode()));
        List<Proposal> proposals = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            if (draft.status() != MatchStatus.NO_MATCH) {
                applyEdits(draft, request.edits().of(draft.file().path()));
            }
            proposals.add(finish(draft));
        }
        ProposalSet set = ProposalSet.of(proposals);
        assert set.proposals().size() == request.files().size() : "one proposal per file";
        return set;
    }

    private static Draft draft(ScannedFile file, ExtractionSpec spec, ManualEdits edits) {
        MediaMetadata existing = file.tags().effective();
        Map<Field, FieldValue> values = new EnumMap<>(Field.class);
        values.putAll(existing.values());
        Map<Field, ChangeOrigin> origins = new EnumMap<>(Field.class);
        Optional<MatchResult> match = spec.pattern().map(pattern -> PathMatcher.match(pattern, file.path()));
        MatchStatus status = match.map(m -> switch (m.kind()) {
            case MATCHED -> MatchStatus.MATCHED;
            case AMBIGUOUS -> spec.acceptAmbiguous() || edits.acceptedAmbiguous().contains(file.path())
                    ? MatchStatus.AMBIGUOUS_ACCEPTED : MatchStatus.AMBIGUOUS;
            case NO_MATCH -> MatchStatus.NO_MATCH;
        }).orElse(MatchStatus.NO_PATTERN);
        Draft draft = new Draft(file, status, match, values, origins, new ArrayList<>());
        if (status == MatchStatus.NO_MATCH) {
            return draft; // never written: no pattern, fixed or manual values (proposed == existing)
        }
        match.ifPresent(m -> m.values().forEach((field, value) -> {
            if (!(spec.overrides().get(field) instanceof FieldOverride.KeepExisting)) {
                merge(draft, existing, field, value, ChangeOrigin.PATTERN, spec.mergeMode());
            }
        }));
        spec.overrides().forEach((field, override) -> {
            if (override instanceof FieldOverride.Fixed fixed) {
                merge(draft, existing, field, fixed.value(), ChangeOrigin.FIXED, spec.mergeMode());
            } else {
                existing.get(field).ifPresentOrElse(value -> values.put(field, value), () -> values.remove(field));
                origins.remove(field);
            }
        });
        return draft;
    }

    private static void merge(Draft draft, MediaMetadata existing, Field field, FieldValue value, ChangeOrigin origin,
                              MergeMode mode) {
        if (mode == MergeMode.FILL_EMPTY && existing.get(field).isPresent()) {
            return;
        }
        draft.values().put(field, value);
        draft.origins().put(field, origin);
    }

    private static void autoNumber(List<Draft> drafts, AutoNumbering numbering, MergeMode mode) {
        Map<Path, List<Draft>> byFolder = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            if (draft.status().exportable()) {
                Path folder = draft.file().path().getParent();
                byFolder.computeIfAbsent(folder, f -> new ArrayList<>()).add(draft);
            }
        }
        Comparator<Draft> byPath = Comparator.comparing(d -> d.file().path(), PathOrder.NATURAL);
        Comparator<Draft> order = switch (numbering.order()) {
            case FILE_NAME -> byPath;
            case EXISTING_TRACK -> Comparator.<Draft>comparingInt(d -> existingTrack(d).orElse(Integer.MAX_VALUE))
                    .thenComparing(byPath);
        };
        for (List<Draft> folder : byFolder.values()) {
            folder.sort(order);
            for (int i = 0; i < folder.size(); i++) {
                Draft draft = folder.get(i);
                FieldValue.Position position = numbering.setTotal()
                        ? FieldValue.Position.of(i + 1, folder.size())
                        : FieldValue.Position.of(i + 1);
                merge(draft, draft.file().tags().effective(), Field.TRACK, position, ChangeOrigin.AUTO_NUMBER, mode);
            }
            warnAboutDuplicateTracks(folder);
        }
    }

    /** FILL_EMPTY keeps existing numbers, so a new number can repeat an existing one. */
    private static void warnAboutDuplicateTracks(List<Draft> folder) {
        Map<Integer, Long> counts = folder.stream()
                .map(d -> d.values().get(Field.TRACK))
                .filter(v -> v instanceof FieldValue.Position)
                .collect(Collectors.groupingBy(v -> ((FieldValue.Position) v).number(), Collectors.counting()));
        for (Draft draft : folder) {
            if (draft.values().get(Field.TRACK) instanceof FieldValue.Position position
                    && counts.get(position.number()) > 1) {
                draft.issues().add(Issue.warning(IssueCode.DUPLICATE_TRACK_NUMBER, "track " + position.number()
                        + " occurs more than once in this folder").forFile(draft.file().path()).forField(Field.TRACK));
            }
        }
    }

    private static OptionalInt existingTrack(Draft draft) {
        return draft.file().tags().effective().get(Field.TRACK)
                .map(value -> OptionalInt.of(((FieldValue.Position) value).number()))
                .orElse(OptionalInt.empty());
    }

    private static void applyEdits(Draft draft, Map<Field, FieldEdit> edits) {
        edits.forEach((field, edit) -> {
            switch (edit) {
                case FieldEdit.Assign assign -> draft.values().put(field, assign.value());
                case FieldEdit.Clear clear -> draft.values().remove(field);
            }
            draft.origins().put(field, ChangeOrigin.MANUAL);
        });
    }

    private static Proposal finish(Draft draft) {
        ScannedFile file = draft.file();
        MediaMetadata existing = file.tags().effective();
        MediaMetadata proposed = new MediaMetadata(draft.values());
        List<FieldChange> changes = new ArrayList<>();
        for (Field field : existing.differingFields(proposed)) {
            ChangeOrigin origin = draft.origins().get(field);
            if (origin == null) {
                throw new IllegalStateException("no origin recorded for the changed field " + field);
            }
            changes.add(new FieldChange(field, existing.get(field), proposed.get(field), origin));
        }
        List<Issue> issues = new ArrayList<>(draft.issues());
        Optional<String> detail = Optional.empty();
        Map<Field, String> extracted = Map.of();
        Map<Field, String> alternative = Map.of();
        if (draft.match().isPresent()) {
            MatchResult match = draft.match().get();
            extracted = match.raw();
            alternative = match.alternative();
            if (match.kind() == MatchKind.NO_MATCH) {
                detail = match.reason();
                issues.add(Issue.error(IssueCode.NO_MATCH, "no match: " + match.reason().orElseThrow())
                        .forFile(file.path()));
            } else if (match.kind() == MatchKind.AMBIGUOUS) {
                String also = "ambiguous; also possible: " + describe(match.alternative());
                issues.add((draft.status() == MatchStatus.AMBIGUOUS_ACCEPTED
                        ? Issue.warning(IssueCode.AMBIGUOUS_MATCH, also + " (accepted)")
                        : Issue.error(IssueCode.AMBIGUOUS_MATCH, also)).forFile(file.path()));
            }
        }
        for (FieldChange change : changes) {
            if (change.after().orElse(null) instanceof FieldValue.Position position && position.total().isPresent()
                    && position.number() > position.total().getAsInt()) {
                issues.add(Issue.warning(IssueCode.POSITION_EXCEEDS_TOTAL, change.field().placeholder() + " "
                        + position.display() + ": number is greater than the total")
                        .forFile(file.path()).forField(change.field()));
            }
        }
        return new Proposal(file, draft.status(), detail, extracted, alternative, proposed, changes, issues);
    }

    private static String describe(Map<Field, String> values) {
        return values.entrySet().stream()
                .map(e -> e.getKey().placeholder() + "=\"" + e.getValue() + "\"")
                .collect(Collectors.joining(", "));
    }
}
