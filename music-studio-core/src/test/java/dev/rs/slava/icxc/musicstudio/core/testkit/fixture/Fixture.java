package dev.rs.slava.icxc.musicstudio.core.testkit.fixture;

/** The MP3 fixture files under {@code /fixtures/mp3}. Content is documented in {@code generate_fixtures.py}. */
public enum Fixture {
    NO_TAGS("no-tags.mp3"),
    V1_0("v1_0.mp3"),
    V1_1("v1_1.mp3"),
    V23_LATIN1("v23-latin1.mp3"),
    V23_UTF16_CYRILLIC("v23-utf16-cyrillic.mp3"),
    V24_UTF8("v24-utf8.mp3"),
    V22("v22.mp3"),
    V11_V23_SAME("v11-v23-same.mp3"),
    V11_V23_DIFF("v11-v23-diff.mp3"),
    V23_GENRE_REF("v23-genre-ref.mp3"),
    V23_GENRE_NUM("v23-genre-num.mp3"),
    V23_BAD_TRACK("v23-bad-track.mp3"),
    V23_COVER("v23-cover.mp3"),
    V23_OTHER_PICTURE("v23-other-picture.mp3"),
    CORRUPT("corrupt.mp3"),
    EMPTY("empty.mp3");

    private final String fileName;

    Fixture(String fileName) {
        this.fileName = fileName;
    }

    /** @return the file name, also the resource name */
    public String fileName() {
        return fileName;
    }
}
