package dev.rs.slava.icxc.musicstudio.storage;

import dev.rs.slava.icxc.musicstudio.core.port.ExportTarget;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.ExportTargetContract;

/** The same contract with the check-then-move fallback used where hard links are not supported (FAT/exFAT). */
class LocalExportTargetLinkFallbackTest extends ExportTargetContract {

    @Override
    protected ExportTarget subject() {
        return new LocalExportTarget(false);
    }
}
