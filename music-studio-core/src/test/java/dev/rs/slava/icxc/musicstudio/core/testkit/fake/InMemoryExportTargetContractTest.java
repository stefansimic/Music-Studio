package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ExportTargetContract;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** X02: the in-memory fake passes the same contract as the local target. */
class InMemoryExportTargetContractTest extends ExportTargetContract {

    private final InMemoryExportTarget target = new InMemoryExportTarget();
    private final Map<Path, String> contents = new HashMap<>();

    @Override
    protected ExportTarget subject() {
        return target;
    }

    @Override
    protected void create(Path path, String content) {
        target.create(path);
        contents.put(path, content);
        target.onMove((temp, dest) -> contents.put(dest, contents.get(temp)));
    }

    @Override
    protected String content(Path path) {
        return contents.get(path);
    }
}
