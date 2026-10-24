package dev.rs.slava.icxc.musicstudio.tag.jaudiotagger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Where the parts of an MP3 file are: the leading ID3v2 tag {@code [0, v2End)}, the payload {@code [v2End,
 * v1Start)} (audio plus anything else, e.g. APE or Lyrics3, copied byte for byte) and the trailing 128-byte ID3v1
 * block {@code [v1Start, length)}.
 *
 * @param length  file size
 * @param v2End   end of the ID3v2 tag including footer; 0 without tag
 * @param v1Start start of the ID3v1 block; {@code length} without block
 */
record SourceLayout(long length, long v2End, long v1Start) {

    private static final int V2_HEADER = 10;
    private static final int V1_SIZE = 128;

    boolean hasV2() {
        return v2End > 0;
    }

    boolean hasV1() {
        return v1Start < length;
    }

    /** @throws IOException if the file cannot be read, or the ID3v2 tag claims more bytes than the file has */
    static SourceLayout of(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            long v2End = 0;
            if (length >= V2_HEADER) {
                byte[] header = new byte[V2_HEADER];
                raf.readFully(header);
                if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                    long size = ((header[6] & 0x7FL) << 21) | ((header[7] & 0x7FL) << 14)
                            | ((header[8] & 0x7FL) << 7) | (header[9] & 0x7FL);
                    boolean footer = header[3] == 4 && (header[5] & 0x10) != 0;
                    v2End = V2_HEADER + size + (footer ? V2_HEADER : 0);
                }
            }
            long v1Start = length;
            if (length - V1_SIZE >= v2End && length >= V1_SIZE) {
                byte[] tag = new byte[3];
                raf.seek(length - V1_SIZE);
                raf.readFully(tag);
                if (tag[0] == 'T' && tag[1] == 'A' && tag[2] == 'G') {
                    v1Start = length - V1_SIZE;
                }
            }
            if (v2End > v1Start) {
                throw new IOException("the ID3v2 tag claims " + v2End + " bytes but the file has only " + length);
            }
            return new SourceLayout(length, v2End, v1Start);
        }
    }
}
