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

/** Default {@link ProposeMetadataUseCase}. Pure and stateless. */
public final class ProposeMetadataService implements ProposeMetadataUseCase {

    /** The working state of one file while the pipeline runs. */
    private static final class State {
        private final ScannedFile file;
        private final MatchStatus status;
        private final Optional<String> detail;
        private final Map<Field, String> extracted;
        private final Map<Field, FieldValue> proposed = new EnumMap<>(Field.class);
        private final Map<Field, ChangeOrigin> origin = new EnumMap<>(Field.class);
        private final List<Issue> issues = new ArrayList<>();

        private State(ScannedFile file, MatchStatus status, Optional<String> detail, Map<Field, String> extracted) {
            this.file = file;
            this.status = status;
            this.detail = detail;
            this.extracted = extracted;
        }

        private boolean exportable() {
            return status == MatchStatus.NO_PATTERN || status == MatchStatus.MATCHED
                    || status == MatchStatus.AMBIGUOUS_ACCEPTED;
        }
    }

    @Override
    public ProposalSet propose(ProposeRequest request) {
        Require.nonNull(request, "request");
        ExtractionSpec spec = request.spec();
        List<State> states = new ArrayList<>();
        for (ScannedFile file : request.files()) {
            states.add(prepare(file, spec, request.edits()));
        }
        if (spec.autoNumbering().isPresent()) {
            number(states, spec);
        }
        List<Proposal> proposals = new ArrayList<>();
        int matched = 0;
        int ambiguous = 0;
        int noMatch = 0;
        for (State state : states) {
            applyEdits(state, request.edits());
            proposals.add(build(state));
            switch (state.status) {
                case MATCHED, AMBIGUOUS_ACCEPTED -> matched++;
                case AMBIGUOUS -> ambiguous++;
                case NO_MATCH -> noMatch++;
                case NO_PATTERN -> { }
            }
        }
        return new ProposalSet(proposals, matched, ambiguous, noMatch);
    }

    private static State prepare(ScannedFile file, ExtractionSpec spec, ManualEdits edits) {
        MediaMetadata existing = file.tags().effective();
        State state;
        Map<Field, FieldValue> patternValues = Map.of();
        if (spec.pattern().isEmpty()) {
            state = new State(file, MatchStatus.NO_PATTERN, Optional.empty(), Map.of());
        } else {
            MatchResult match = PathMatcher.match(spec.pattern().get(), file.path());
            if (match.kind() == MatchKind.NO_MATCH) {
                state = new State(file, MatchStatus.NO_MATCH, match.reason(), Map.of());
                state.issues.add(Issue.warning(IssueCode.PATTERN_NO_MATCH, match.reason().orElse("no match"))
                        .forFile(file.path()));
            } else if (match.kind() == MatchKind.AMBIGUOUS) {
                boolean accepted = spec.acceptAmbiguous() || edits.acceptedAmbiguous().contains(file.path());
                Optional<String> detail = Optional.of("Chosen split: " + describe(match.raw())
                        + " (the name can be split in another way too)");
                state = new State(file, accepted ? MatchStatus.AMBIGUOUS_ACCEPTED : MatchStatus.AMBIGUOUS, detail,
                        match.raw());
                state.issues.add((accepted ? Issue.info(IssueCode.AMBIGUOUS_MATCH, detail.get())
                        : Issue.warning(IssueCode.AMBIGUOUS_MATCH, detail.get())).forFile(file.path()));
                patternValues = match.values();
            } else {
                state = new State(file, MatchStatus.MATCHED, Optional.empty(), match.raw());
                patternValues = match.values();
            }
        }
        if (!file.conflicts().isEmpty()) {
            state.issues.add(Issue.info(IssueCode.V1_V2_CONFLICT,
                    "The ID3v1 and ID3v2 tags disagree; the ID3v2 values are used as the current values")
                    .forFile(file.path()));
        }
        state.proposed.putAll(existing.values());
        if (state.status == MatchStatus.NO_MATCH) {
            return state; // a file that does not match gets nothing, so a batch cannot be half-tagged
        }
        for (Map.Entry<Field, FieldOverride> entry : spec.overrides().entrySet()) {
            if (entry.getValue() instanceof FieldOverride.KeepExisting && existing.get(entry.getKey()).isEmpty()) {
                state.issues.add(Issue.info(IssueCode.KEEP_EMPTY, "Keeping " + entry.getKey().placeholder()
                        + ": the file has no value, so the field stays empty").forField(entry.getKey())
                        .forFile(file.path()));
            }
        }
        for (Map.Entry<Field, FieldValue> entry : patternValues.entrySet()) {
            put(state, spec, existing, entry.getKey(), entry.getValue(), ChangeOrigin.PATTERN);
        }
        for (Map.Entry<Field, FieldOverride> entry : spec.overrides().entrySet()) {
            if (entry.getValue() instanceof FieldOverride.Fixed fixed) {
                put(state, spec, existing, entry.getKey(), fixed.value(), ChangeOrigin.FIXED);
            }
        }
        return state;
    }

    private static void put(State state, ExtractionSpec spec, MediaMetadata existing, Field field, FieldValue value,
                            ChangeOrigin origin) {
        if (spec.overrides().get(field) instanceof FieldOverride.KeepExisting) {
            return;
        }
        if (spec.mergeMode() == MergeMode.FILL_EMPTY && existing.get(field).isPresent()) {
            return;
        }
        state.proposed.put(field, value);
        state.origin.put(field, origin);
    }

    private static String describe(Map<Field, String> raw) {
        List<String> parts = new ArrayList<>();
        raw.forEach((field, text) -> parts.add(field.placeholder() + "=\"" + text + "\""));
        return String.join(", ", parts);
    }

    // ---- auto-numbering -------------------------------------------------------------------------------------------

    private static void number(List<State> states, ExtractionSpec spec) {
        AutoNumbering numbering = spec.autoNumbering().orElseThrow();
        Map<Path, List<State>> folders = new LinkedHashMap<>();
        for (State state : states) {
            if (state.exportable() && wantsNumber(state, spec)) {
                Path folder = state.file.path().getParent();
                folders.computeIfAbsent(folder, key -> new ArrayList<>()).add(state);
            }
        }
        for (List<State> folder : folders.values()) {
            folder.sort(comparator(numbering.order()));
            int total = folder.size();
            for (int i = 0; i < folder.size(); i++) {
                State state = folder.get(i);
                FieldValue.Position position = new FieldValue.Position(i + 1,
                        numbering.setTotal() ? OptionalInt.of(total) : OptionalInt.empty());
                state.proposed.put(Field.TRACK, position);
                state.origin.put(Field.TRACK, ChangeOrigin.AUTO_NUMBER);
            }
        }
    }

    /** A track number from the name, a fixed value or keep-existing wins; FILL_EMPTY leaves an existing number. */
    private static boolean wantsNumber(State state, ExtractionSpec spec) {
        if (state.origin.containsKey(Field.TRACK)
                || spec.overrides().get(Field.TRACK) instanceof FieldOverride.KeepExisting) {
            return false;
        }
        return !(spec.mergeMode() == MergeMode.FILL_EMPTY && state.file.tags().effective().get(Field.TRACK).isPresent());
    }

    private static Comparator<State> comparator(AutoNumbering.NumberingOrder order) {
        Comparator<State> byName = Comparator.<State, String>comparing(s -> s.file.path().getFileName().toString(),
                ProposeMetadataService::naturalCompare).thenComparing(s -> s.file.path(), PathOrder.COMPARATOR);
        if (order == AutoNumbering.NumberingOrder.FILE_NAME) {
            return byName;
        }
        return Comparator.<State>comparingInt(s -> existingTrack(s) < 0 ? Integer.MAX_VALUE : existingTrack(s))
                .thenComparing(byName);
    }

    /** Case-insensitive order in which a run of digits counts by its value ("2" before "10"). */
    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int ei = i;
                while (ei < a.length() && isDigit(a.charAt(ei))) {
                    ei++;
                }
                int ej = j;
                while (ej < b.length() && isDigit(b.charAt(ej))) {
                    ej++;
                }
                String na = withoutLeadingZeros(a.substring(i, ei));
                String nb = withoutLeadingZeros(b.substring(j, ej));
                int byLength = Integer.compare(na.length(), nb.length());
                int byValue = byLength != 0 ? byLength : na.compareTo(nb);
                if (byValue != 0) {
                    return byValue;
                }
                i = ei;
                j = ej;
                continue;
            }
            int la = Character.toLowerCase(ca);
            int lb = Character.toLowerCase(cb);
            if (la != lb) {
                return Integer.compare(la, lb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static String withoutLeadingZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }

    private static boolean isDigit(int cp) {
        return cp >= '0' && cp <= '9';
    }

    private static int existingTrack(State state) {
        return state.file.tags().effective().get(Field.TRACK).orElse(null) instanceof FieldValue.Position p
                ? p.number() : -1;
    }

    // ---- manual edits and result ----------------------------------------------------------------------------------

    private static void applyEdits(State state, ManualEdits edits) {
        if (state.status == MatchStatus.NO_MATCH) {
            return;
        }
        Map<Field, FieldEdit> fileEdits = edits.edits().get(state.file.path());
        if (fileEdits == null) {
            return;
        }
        fileEdits.forEach((field, edit) -> {
            if (edit instanceof FieldEdit.Set set) {
                state.proposed.put(field, set.value());
            } else {
                state.proposed.remove(field);
            }
            state.origin.put(field, ChangeOrigin.MANUAL);
        });
    }

    private static Proposal build(State state) {
        MediaMetadata existing = state.file.tags().effective();
        MediaMetadata proposed = new MediaMetadata(state.proposed);
        List<ProposedChange> changes = new ArrayList<>();
        for (Field field : Field.values()) {
            Optional<FieldValue> before = existing.get(field);
            Optional<FieldValue> after = proposed.get(field);
            if (!before.equals(after)) {
                changes.add(new ProposedChange(field, before, after,
                        state.origin.getOrDefault(field, ChangeOrigin.PATTERN)));
            }
        }
        return new Proposal(state.file, state.status, state.detail, state.extracted, proposed, changes, state.issues);
    }
}
