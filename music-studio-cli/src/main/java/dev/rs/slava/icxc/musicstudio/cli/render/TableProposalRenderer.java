package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.model.IssueCode;
import dev.rs.slava.icxc.musicstudio.core.model.SkippedEntry;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The {@code extract} preview as a table: PATH, STATUS (ok / accepted / AMBIGUOUS / NO MATCH – upper case = will
 * be skipped), one column per touched field with the proposed value ({@code *} = differs from the current tag), and
 * NOTES (why no match, the other possible split, warnings, and problems of the current tags such as ignored invalid
 * values or ID3v1/v2 differences). Ends with "Preview only – no file was changed."
 */
public final class TableProposalRenderer implements ProposalRenderer {

    private static final String GAP = "  ";
    private static final int PATH_WIDTH = 36;
    private static final int STATUS_WIDTH = 9;

    private final PrintWriter out;
    private final Path root;

    /**
     * @param out  receives the table; not null
     * @param root the scanned folder; absolute, not null
     */
    public TableProposalRenderer(PrintWriter out, Path root) {
        this.out = Require.nonNull(out, "out");
        this.root = Require.absolute(root, "root");
    }

    @Override
    public void render(ProposalSet proposals, List<UnreadableFile> unreadable, ScanSummary summary,
                       List<Field> columns, Optional<String> pattern) {
        Require.nonNull(proposals, "proposals");
        Require.noNullElements(unreadable, "unreadable");
        Require.nonNull(summary, "summary");
        Require.noNullElements(columns, "columns");
        Require.nonNull(pattern, "pattern");
        pattern.ifPresent(p -> out.println("Pattern: " + Text.oneLine(p)));
        if (!proposals.proposals().isEmpty()) {
            header(columns);
            for (Proposal proposal : proposals.proposals()) {
                row(proposal, columns);
            }
            out.println();
            out.println("* = differs from the current tag");
        }
        if (!unreadable.isEmpty()) {
            out.println("Unreadable (not processed):");
            for (UnreadableFile file : unreadable) {
                out.println("  " + Text.oneLine(Rows.relative(root, file.path())) + GAP
                        + Text.oneLine(file.error().message()));
            }
        }
        if (summary.ignoredFiles() > 0) {
            out.println("Ignored %d other file%s (%s).".formatted(summary.ignoredFiles(),
                    summary.ignoredFiles() == 1 ? "" : "s", TableScanRenderer.ignored(summary.ignoredByExtension())));
        }
        if (!summary.skipped().isEmpty()) {
            out.println("Skipped (not scanned):");
            for (SkippedEntry entry : summary.skipped()) {
                out.println("  " + Text.fit(Rows.reason(entry.reason()), 17) + GAP
                        + Text.oneLine(Rows.relative(root, entry.path())));
            }
        }
        out.println(counts(proposals, pattern.isPresent()));
        out.println("Preview only – no file was changed.");
    }

    private void header(List<Field> columns) {
        List<String> titles = new ArrayList<>(List.of(Text.fit("PATH", PATH_WIDTH), Text.fit("STATUS", STATUS_WIDTH)));
        List<String> rules = new ArrayList<>(List.of("-".repeat(PATH_WIDTH), "-".repeat(STATUS_WIDTH)));
        for (Field field : columns) {
            titles.add(Text.fit(Rows.header(field), width(field)));
            rules.add("-".repeat(width(field)));
        }
        out.println((String.join(GAP, titles) + GAP + "NOTES").stripTrailing());
        out.println(String.join(GAP, rules) + GAP + "-".repeat(12));
    }

    private static int width(Field field) {
        return Math.max(Rows.width(field), Rows.header(field).length()) + 1; // room for the "*" marker
    }

    private void row(Proposal proposal, List<Field> columns) {
        StringBuilder line = new StringBuilder(Text.fit(Text.oneLine(Rows.relative(root, proposal.file().path())),
                PATH_WIDTH));
        line.append(GAP).append(Text.fit(status(proposal.status()), STATUS_WIDTH));
        for (Field field : columns) {
            String value = Text.oneLine(proposal.proposed().display(field).orElse(""));
            boolean changed = proposal.changes().stream().anyMatch(c -> c.field() == field);
            int width = width(field);
            line.append(GAP).append(changed ? Text.fit(Text.cut(value, width - 1) + "*", width)
                    : Text.fit(value, width));
        }
        line.append(GAP).append(notes(proposal));
        out.println(line.toString().stripTrailing());
    }

    private static String status(MatchStatus status) {
        return switch (status) {
            case NO_PATTERN, MATCHED -> "ok";
            case AMBIGUOUS_ACCEPTED -> "accepted";
            case AMBIGUOUS -> "AMBIGUOUS";
            case NO_MATCH -> "NO MATCH";
        };
    }

    private static String notes(Proposal proposal) {
        List<String> notes = new ArrayList<>();
        proposal.matchDetail().ifPresent(notes::add);
        if (!proposal.alternative().isEmpty()) {
            notes.add("also possible: " + proposal.alternative().entrySet().stream()
                    .map(e -> e.getKey().placeholder() + "=\"" + e.getValue() + "\"")
                    .collect(Collectors.joining(", ")));
        }
        if (proposal.status() == MatchStatus.AMBIGUOUS) {
            notes.add("skipped (use --accept-ambiguous)");
        }
        proposal.issues().stream()
                .filter(i -> i.code() != IssueCode.NO_MATCH && i.code() != IssueCode.AMBIGUOUS_MATCH)
                .forEach(i -> notes.add(i.message()));
        String existing = Rows.notes(proposal.file()); // read problems and v1/v2 differences of the current tags
        if (!existing.isEmpty()) {
            notes.add("current tags: " + existing);
        }
        return Text.oneLine(String.join("; ", notes));
    }

    private static String counts(ProposalSet set, boolean withPattern) {
        int files = set.proposals().size();
        String total = files + (files == 1 ? " file" : " files");
        if (!withPattern) {
            return total + ".";
        }
        long unaccepted = set.proposals().stream().filter(p -> p.status() == MatchStatus.AMBIGUOUS).count();
        return total + ": " + set.matched() + " matched, " + set.ambiguous() + " ambiguous"
                + (unaccepted > 0 ? " (" + unaccepted + " skipped – use --accept-ambiguous)" : "") + ", "
                + set.noMatch() + " no match.";
    }
}
