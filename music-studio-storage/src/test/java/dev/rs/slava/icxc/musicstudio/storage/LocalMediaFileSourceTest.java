package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.port.MediaFileSource;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.LocalDiskTree;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.MediaFileSourceContract;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TestTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The local-disk listing must fulfil the same contract as the in-memory fake. */
class LocalMediaFileSourceTest extends MediaFileSourceContract {

    @Override
    protected TestTree newTree(Path dir) {
        try {
            return new LocalDiskTree(Files.createDirectories(dir));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected MediaFileSource subject(TestTree tree) {
        return new LocalMediaFileSource();
    }
}
