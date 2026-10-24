package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.contract.Require;
import dev.rs.slava.icxc.musicstudio.core.extract.FieldChange;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import dev.rs.slava.icxc.musicstudio.core.scan.ScanSummary;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code extract} preview as one JSON document: {@code {"root", "pattern", "items":[{path, status, detail,
 * extracted, alternative, existing, proposed, changes[], issues[], readIssues[], v1Conflicts[]}],
 * "unreadable":[{path, message}],
 * "summary":{files, matched, ambiguous, noMatch, exportable, ignored, skipped}}}.
 */
public final class JsonProposalRenderer implements ProposalRenderer {

    private final PrintWriter out;
    private final Path root;

    /**
     * @param out  receives the document; not null
     * @param root the scanned folder; absolute, not null
     */
    public JsonProposalRenderer(PrintWriter out, Path root) {
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
        out.print("{\"root\":" + Json.string(root.toString()) + ",\"pattern\":" + Json.string(pattern.orElse(null))
                + ",\"items\":[");
        String separator = "\n";
        for (Proposal proposal : proposals.proposals()) {
            out.print(separator + item(proposal));
            separator = ",\n";
        }
        out.print(proposals.proposals().isEmpty() ? "]" : "\n]");
        out.print(",\"unreadable\":" + Json.array(unreadable.stream().map(file -> "{\"path\":"
                + Json.string(Rows.relative(root, file.path())) + ",\"message\":"
                + Json.string(file.error().message()) + "}").toList()));
        out.println(",\"summary\":{\"files\":" + proposals.proposals().size() + ",\"matched\":" + proposals.matched()
                + ",\"ambiguous\":" + proposals.ambiguous() + ",\"noMatch\":" + proposals.noMatch()
                + ",\"exportable\":" + proposals.exportable() + ",\"ignored\":" + summary.ignoredFiles()
                + ",\"skipped\":" + summary.skipped().size() + "}}");
    }

    private String item(Proposal proposal) {
        return "{\"path\":" + Json.string(Rows.relative(root, proposal.file().path()))
                + ",\"status\":" + Json.string(proposal.status().name().toLowerCase(Locale.ROOT))
                + ",\"detail\":" + Json.string(proposal.matchDetail().orElse(null))
                + ",\"extracted\":" + Json.strings(proposal.extracted())
                + ",\"alternative\":" + Json.strings(proposal.alternative())
                + ",\"existing\":" + Json.metadata(proposal.existing())
                + ",\"proposed\":" + Json.metadata(proposal.proposed())
                + ",\"changes\":" + Json.array(proposal.changes().stream().map(JsonProposalRenderer::change).toList())
                + ",\"issues\":" + Json.issues(proposal.issues())
                + ",\"readIssues\":" + Json.issues(proposal.file().tags().issues())
                + ",\"v1Conflicts\":" + Json.array(proposal.file().conflicts().stream()
                        .map(f -> Json.string(f.placeholder())).toList()) + "}";
    }

    private static String change(FieldChange change) {
        return "{\"field\":" + Json.string(change.field().placeholder())
                + ",\"before\":" + change.before().map(Json::value).orElse("null")
                + ",\"after\":" + change.after().map(Json::value).orElse("null")
                + ",\"origin\":" + Json.string(change.origin().name().toLowerCase(Locale.ROOT)) + "}";
    }
}
