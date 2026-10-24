package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.port.TagReader;
import dev.rs.slava.icxc.musicstudio.core.testkit.contract.TagReaderEdgeCaseContract;

/** The jaudiotagger adapter on hand-built edge-case files (encodings, genres, damaged tags, huge values). */
class JaudiotaggerEdgeCaseTest extends TagReaderEdgeCaseContract {

    @Override
    protected TagReader subject() {
        return new JaudiotaggerTagReader();
    }
}
