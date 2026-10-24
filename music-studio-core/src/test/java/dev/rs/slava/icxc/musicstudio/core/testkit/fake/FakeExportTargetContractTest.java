package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ExportTargetContract;

/** The fake export target fulfils the same contract as the local-disk adapter. */
class FakeExportTargetContractTest extends ExportTargetContract {

    private final FakeExportTarget target = new FakeExportTarget();

    @Override
    protected ExportTarget subject() {
        return target;
    }
}
