package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.port.TagWriter;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.Id3WriterContract;

/** The jaudiotagger writer fulfils the byte-level MP3 writer contract. */
class JaudiotaggerId3WriterTest extends Id3WriterContract {

    @Override
    protected TagWriter subject() {
        return new JaudiotaggerTagWriter();
    }

    @Override
    protected TagReader reader() {
        return new JaudiotaggerTagReader();
    }
}
