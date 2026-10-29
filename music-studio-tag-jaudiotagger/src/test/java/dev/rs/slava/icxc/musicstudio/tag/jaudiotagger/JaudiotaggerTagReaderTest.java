package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagReaderContract;

/** The jaudiotagger reader must fulfil the same contract as every other {@code TagReader}. */
class JaudiotaggerTagReaderTest extends TagReaderContract {

    private final JaudiotaggerTagReader reader = new JaudiotaggerTagReader();

    @Override
    protected TagReader subject() {
        return reader;
    }
}
