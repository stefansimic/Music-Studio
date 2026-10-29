package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagReaderContract;

/** The fake reader must pass the same contract as the real readers. */
class FakeTagReaderContractTest extends TagReaderContract {

    private final FakeTagReader reader = new FakeTagReader();

    @Override
    protected TagReader subject() {
        return reader;
    }
}
