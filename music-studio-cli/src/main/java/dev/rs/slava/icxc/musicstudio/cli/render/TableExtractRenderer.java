package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fixed-width preview table: the status, the path, the proposed values (a {@code *} marks a value that differs from
 * the file's current tag) and, for rows without a clean match, the reason or the chosen split.
 */
public final class TableExtractRenderer implements ExtractRenderer {

    private record Column(String header, int width, Function<Proposal, String> value) {}

    private static String shown(Proposal proposal, Field field) {
        String text = ExtractFormat.proposed(proposal, field);
        return text.isEmpty() || !ExtractFormat.changed(proposal, field) ? text : text + "*";
    }

    private static final int UNBOUNDED = -1;
    private static final int STATUS_WIDTH = 19;
    private static final int PATH_WIDTH = 44;
    private static final String GAP = "  ";

    private final PrintWriter out;

    /** @param out where the table goes; not null */
    public TableExtractRenderer(PrintWriter out) {
        this.out = Require.nonNull(out, "out");
    }

    @Override
    public void render(Path root, ProposalSet proposals, List<UnreadableFile> unreadable) {
        Require.nonNull(root, "root");
        Require.nonNull(proposals, "proposals");
        Require.nonNull(unreadable, "unreadable");
        List<Column> columns = List.of(
                new Column("TITLE", 28, p -> shown(p, Field.TITLE)),
                new Column("ARTIST", 22, p -> shown(p, Field.ARTIST)),
                new Column("ALBUM ARTIST", 18, p -> shown(p, Field.ALBUM_ARTIST)),
                new Column("ALBUM", 24, p -> shown(p, Field.ALBUM)),
                new Column("YEAR", 5, p -> shown(p, Field.YEAR)),
                new Column("TRACK", 6, p -> shown(p, Field.TRACK)),
                new Column("DISC", 5, p -> shown(p, Field.DISC)),
                new Column("GENRE", 14, p -> shown(p, Field.GENRE)),
                new Column("DETAIL", UNBOUNDED, ExtractFormat::detail));
        List<String> header = new ArrayList<>(List.of(Text.fit("STATUS", STATUS_WIDTH), Text.fit("PATH", PATH_WIDTH)));
        List<String> rule = new ArrayList<>(List.of("-".repeat(STATUS_WIDTH), "-".repeat(PATH_WIDTH)));
        for (Column column : columns) {
            header.add(column.width() == UNBOUNDED ? column.header() : Text.fit(column.header(), column.width()));
            rule.add("-".repeat(column.width() == UNBOUNDED ? column.header().length() : column.width()));
        }
        line(header);
        line(rule);
        for (Proposal proposal : proposals.proposals()) {
            List<String> cells = new ArrayList<>();
            cells.add(Text.fit(status(proposal), STATUS_WIDTH));
            cells.add(Text.fit(ExtractFormat.path(root, proposal.file().path()), PATH_WIDTH));
            for (Column column : columns) {
                String value = column.value().apply(proposal);
                cells.add(column.width() == UNBOUNDED ? Text.oneLine(value) : Text.fit(value, column.width()));
            }
            line(cells);
        }
        for (UnreadableFile file : unreadable) {
            out.println("UNREADABLE " + ExtractFormat.path(root, file.path()) + ": " + file.error().message());
        }
        out.println();
        out.println(proposals.proposals().size() + " file(s): " + proposals.matched() + " matched, "
                + proposals.ambiguous() + " ambiguous (skipped unless accepted), " + proposals.noMatch()
                + " no match" + (unreadable.isEmpty() ? "" : ", " + unreadable.size() + " unreadable")
                + ". Preview only: nothing was written.");
        out.flush();
    }

    private static String status(Proposal proposal) {
        String status = ExtractFormat.status(proposal.status());
        return proposal.status() == MatchStatus.AMBIGUOUS ? status + " (skipped)" : status;
    }

    private void line(List<String> cells) {
        out.println(String.join(GAP, cells).stripTrailing());
    }
}
