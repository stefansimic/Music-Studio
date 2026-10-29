package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.MediaFileSourceContract;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TestTree;
import java.nio.file.Path;

/** The in-memory fake must pass the same contract as the real implementation. */
class InMemoryMediaFileSourceContractTest extends MediaFileSourceContract {

    private InMemoryMediaFileSource source;

    @Override
    protected TestTree newTree(Path dir) {
        source = new InMemoryMediaFileSource();
        return new InMemoryTree(dir, source);
    }

    @Override
    protected MediaFileSource subject(TestTree tree) {
        return source;
    }
}
