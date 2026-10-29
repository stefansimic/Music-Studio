package dev.rs.slava.icxc.musicstudio.cli.render;

import dev.rs.slava.icxc.musicstudio.core.extract.ProposalSet;
import dev.rs.slava.icxc.musicstudio.core.scan.UnreadableFile;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders the result of an extraction preview. A renderer holds no business logic; it only formats what the use case
 * returned.
 */
public interface ExtractRenderer {

    /**
     * @param root       the scanned folder, used to show paths relative to it; not null
     * @param proposals  the proposals in path order; not null
     * @param unreadable files that could not be read and so have no proposal; not null
     */
    void render(Path root, ProposalSet proposals, List<UnreadableFile> unreadable);
}
