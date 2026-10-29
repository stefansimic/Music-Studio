package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagWriterContract;

/** X01: the jaudiotagger writer fulfils the same contract as every other {@code TagWriter}. */
class JaudiotaggerTagWriterTest extends TagWriterContract {

    private final JaudiotaggerTagWriter writer = new JaudiotaggerTagWriter();
    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    @Override
    protected TagWriter subject() {
        return writer;
    }

    @Override
    protected TagReader reader() {
        return reader;
    }
}
