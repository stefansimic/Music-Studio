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
import java.util.Map;

/** One JSON document with the proposals, the unreadable files and a summary; written by hand (no reflection). */
public final class JsonExtractRenderer implements ExtractRenderer {

    private final PrintWriter out;

    /** @param out where the JSON goes; not null */
    public JsonExtractRenderer(PrintWriter out) {
        this.out = Require.nonNull(out, "out");
    }

    @Override
    public void render(Path root, ProposalSet proposals, List<UnreadableFile> unreadable) {
        Require.nonNull(root, "root");
        Require.nonNull(proposals, "proposals");
        Require.nonNull(unreadable, "unreadable");
        List<String> rows = new ArrayList<>();
        for (Proposal proposal : proposals.proposals()) {
            rows.add(proposal(proposal));
        }
        List<String> broken = new ArrayList<>();
        for (UnreadableFile file : unreadable) {
            broken.add("{\"path\": " + Json.quote(file.path().toString()) + ", \"error\": "
                    + Json.quote(file.error().message()) + "}");
        }
        out.println("{\"root\": " + Json.quote(root.toString()) + ", \"proposals\": [\n  "
                + String.join(",\n  ", rows) + "\n], \"unreadable\": [" + String.join(", ", broken) + "]"
                + ", \"summary\": {\"files\": " + proposals.proposals().size()
                + ", \"matched\": " + proposals.matched()
                + ", \"ambiguous\": " + proposals.ambiguous()
                + ", \"noMatch\": " + proposals.noMatch()
                + ", \"unreadable\": " + unreadable.size() + "}}");
        out.flush();
    }

    private static String proposal(Proposal proposal) {
        List<String> extracted = new ArrayList<>();
        for (Map.Entry<Field, String> entry : proposal.extracted().entrySet()) {
            extracted.add(Json.quote(entry.getKey().placeholder()) + ": " + Json.quote(entry.getValue()));
        }
        List<String> changes = new ArrayList<>();
        for (ProposedChange change : proposal.changes()) {
            changes.add("{\"field\": " + Json.quote(change.field().placeholder())
                    + ", \"before\": " + change.before().map(JsonScanRenderer::value).orElse("null")
                    + ", \"after\": " + change.after().map(JsonScanRenderer::value).orElse("null")
                    + ", \"origin\": " + Json.quote(change.origin().name()) + "}");
        }
        return "{\"path\": " + Json.quote(proposal.file().path().toString())
                + ", \"status\": " + Json.quote(proposal.status().name())
                + ", \"exportable\": " + proposal.exportable()
                + ", \"detail\": " + proposal.matchDetail().map(Json::quote).orElse("null")
                + ", \"extracted\": {" + String.join(", ", extracted) + "}"
                + ", \"proposed\": " + JsonScanRenderer.fields(proposal.proposed())
                + ", \"changes\": [" + String.join(", ", changes) + "]"
                + ", \"issues\": " + JsonScanRenderer.issues(proposal.issues()) + "}";
    }
}
