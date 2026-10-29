package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagWriterContract;

/** X01: the fake writer must pass the same contract as the real one. */
class FakeTagWriterContractTest extends TagWriterContract {

    private final FakeTagReader reader = new FakeTagReader();
    private final FakeTagWriter writer = new FakeTagWriter(reader, null);

    @Override
    protected TagWriter subject() {
        return writer;
    }

    @Override
    protected TagReader reader() {
        return reader;
    }
}
