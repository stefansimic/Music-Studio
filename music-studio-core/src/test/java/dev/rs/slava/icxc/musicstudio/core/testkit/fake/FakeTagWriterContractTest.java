package dev.rs.slava.icxc.musicstudio.core.testkit.fake;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagWriterContract;

/** The fake writer fulfils the same contract as the jaudiotagger adapter. */
class FakeTagWriterContractTest extends TagWriterContract {

    private final FakeTagReader reader = new FakeTagReader();
    private final FakeTagWriter writer = new FakeTagWriter(reader);

    @Override
    protected TagWriter subject() {
        return writer;
    }

    @Override
    protected TagReader reader() {
        return reader;
    }
}
