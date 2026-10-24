package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagReaderContract;

/** The fake reader fulfils the same contract as the jaudiotagger adapter. */
class FakeTagReaderContractTest extends TagReaderContract {

    @Override
    protected TagReader subject() {
        return new FakeTagReader();
    }
}
