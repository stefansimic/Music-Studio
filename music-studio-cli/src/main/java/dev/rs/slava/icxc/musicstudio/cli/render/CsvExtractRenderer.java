package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposedChange;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** RFC 4180 CSV, one row per file with the proposed values; for scripting. Unreadable files get status UNREADABLE. */
public final class CsvExtractRenderer implements ExtractRenderer {

    private static final String EOL = "\r\n";
    private static final List<Field> FIELDS = List.of(Field.TITLE, Field.ARTIST, Field.ALBUM_ARTIST, Field.ALBUM,
            Field.YEAR, Field.TRACK, Field.DISC, Field.GENRE, Field.COMMENT);

    private final PrintWriter out;

    /** @param out where the CSV goes; not null */
    public CsvExtractRenderer(PrintWriter out) {
        this.out = Require.nonNull(out, "out");
    }

    @Override
    public void render(Path root, ProposalSet proposals, List<UnreadableFile> unreadable) {
        Require.nonNull(root, "root");
        Require.nonNull(proposals, "proposals");
        Require.nonNull(unreadable, "unreadable");
        List<String> header = new ArrayList<>(List.of("status", "path"));
        FIELDS.forEach(field -> header.add(field.placeholder()));
        header.addAll(List.of("detail", "changed"));
        row(header);
        for (Proposal proposal : proposals.proposals()) {
            List<String> cells = new ArrayList<>();
            cells.add(ExtractFormat.status(proposal.status()));
            cells.add(ExtractFormat.path(root, proposal.file().path()));
            FIELDS.forEach(field -> cells.add(ExtractFormat.proposed(proposal, field)));
            cells.add(ExtractFormat.detail(proposal));
            cells.add(proposal.changes().stream().map(ProposedChange::field).map(Field::placeholder)
                    .collect(Collectors.joining(",")));
            row(cells);
        }
        for (UnreadableFile file : unreadable) {
            List<String> cells = new ArrayList<>(List.of("UNREADABLE", ExtractFormat.path(root, file.path())));
            FIELDS.forEach(field -> cells.add(""));
            cells.add(file.error().message());
            cells.add("");
            row(cells);
        }
        out.flush();
    }

    private void row(List<String> cells) {
        out.print(cells.stream().map(CsvScanRenderer::escape).collect(Collectors.joining(",")) + EOL);
    }
}
