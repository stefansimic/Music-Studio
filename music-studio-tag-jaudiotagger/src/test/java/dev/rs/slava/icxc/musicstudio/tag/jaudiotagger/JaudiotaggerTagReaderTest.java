package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagReaderContract;

/** The jaudiotagger adapter fulfils the {@link TagReader} contract on the real fixture files. */
class JaudiotaggerTagReaderTest extends TagReaderContract {

    @Override
    protected TagReader subject() {
        return new JaudiotaggerTagReader();
    }
}
