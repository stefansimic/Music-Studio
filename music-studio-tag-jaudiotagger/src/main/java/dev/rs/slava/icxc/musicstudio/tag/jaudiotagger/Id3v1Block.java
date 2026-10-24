package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Choice;
import dev.rs.slava.icxc.musicstudio.core.tag.Id3v1Values;
import java.nio.charset.StandardCharsets;

/** Encodes core's byte-exact {@link Id3v1Values} into the 128-byte ID3v1 block (own encoder, see ADR 0011). */
final class Id3v1Block {

    static final int SIZE = 128;

    private Id3v1Block() {
    }

    static byte[] encode(Id3v1Values values) {
        byte[] block = new byte[SIZE];
        block[0] = 'T';
        block[1] = 'A';
        block[2] = 'G';
        put(block, 3, 30, values.title());
        put(block, 33, 30, values.artist());
        put(block, 63, 30, values.album());
        values.year().ifPresent(year -> put(block, 93, 4, "%04d".formatted(year)));
        if (values.version() == Id3v1Choice.V1_1) {
            put(block, 97, 28, values.comment());
            block[125] = 0;
            block[126] = (byte) values.track();
        } else {
            put(block, 97, 30, values.comment());
        }
        block[127] = (byte) values.genre();
        return block;
    }

    private static void put(byte[] block, int offset, int length, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, length));
    }
}
