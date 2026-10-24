package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagWriterContract;

/** The jaudiotagger writer fulfils the semantic {@link TagWriter} contract on real files. */
class JaudiotaggerTagWriterTest extends TagWriterContract {

    @Override
    protected TagWriter subject() {
        return new JaudiotaggerTagWriter();
    }

    @Override
    protected TagReader reader() {
        return new JaudiotaggerTagReader();
    }
}
