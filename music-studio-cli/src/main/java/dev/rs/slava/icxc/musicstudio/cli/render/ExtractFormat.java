package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.extract.MatchStatus;
import dev.rs.slava.icxc.musicstudio.core.extract.Proposal;
import dev.rs.slava.icxc.musicstudio.core.model.Field;
import java.nio.file.Path;

/** Wording shared by the extract renderers. */
final class ExtractFormat {

    private ExtractFormat() {}

    /** @return the status as shown to the user; ambiguous rows say that they are skipped unless accepted */
    static String status(MatchStatus status) {
        return switch (status) {
            case NO_PATTERN -> "OK";
            case MATCHED -> "MATCHED";
            case AMBIGUOUS -> "AMBIGUOUS";
            case AMBIGUOUS_ACCEPTED -> "ACCEPTED";
            case NO_MATCH -> "NO MATCH";
        };
    }

    /** @return the path relative to the root when it is below it, otherwise as it is */
    static String path(Path root, Path file) {
        return file.startsWith(root) ? root.relativize(file).toString() : file.toString();
    }

    /** @return true if the proposal changes this field */
    static boolean changed(Proposal proposal, Field field) {
        return proposal.changes().stream().anyMatch(change -> change.field() == field);
    }

    /** @return the proposed value of the field as text, empty if none */
    static String proposed(Proposal proposal, Field field) {
        return proposal.proposed().display(field).orElse("");
    }

    /** @return what explains the row: the reason of a no-match or the chosen split of an ambiguous match */
    static String detail(Proposal proposal) {
        return proposal.matchDetail().orElse("");
    }
}
