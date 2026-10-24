package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ExportTargetContract;

/** The local-disk export target fulfils the contract (hard-link moves). */
class LocalExportTargetTest extends ExportTargetContract {

    @Override
    protected ExportTarget subject() {
        return new LocalExportTarget();
    }
}
