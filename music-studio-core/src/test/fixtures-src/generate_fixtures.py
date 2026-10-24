"""Writes the MP3 fixtures. Every value here is mirrored by hand in FixtureCatalog.java."""
import os
import shutil
import struct
import sys

from mutagen.id3 import APIC, COMM, ID3, TALB, TCON, TDRC, TIT2, TPE1, TPE2, TPOS, TRCK

base, cover_jpg, out = sys.argv[1], sys.argv[2], sys.argv[3]
LATIN1, UTF16, UTF8 = 0, 1, 3


def path(name):
    return os.path.join(out, name)


def copy_base(name):
    shutil.copyfile(base, path(name))
    return path(name)


def v1_block(title, artist, album, year, comment, track, genre):
    """Hand-written ID3v1 block. track None = ID3v1.0 (30-byte comment), else ID3v1.1."""
    def field(text, size):
        data = text.encode("latin-1")
        assert len(data) <= size, text
        return data + b"\0" * (size - len(data))
    block = b"TAG" + field(title, 30) + field(artist, 30) + field(album, 30) + field(year, 4)
    if track is None:
        block += field(comment, 30)
    else:
        block += field(comment, 28) + b"\0" + bytes([track])
    block += bytes([genre])
    assert len(block) == 128
    return block


def append(name, data):
    with open(path(name), "ab") as f:
        f.write(data)


def save_v2(name, frames, version):
    tags = ID3()
    for frame in frames:
        tags.add(frame)
    if version == 3:
        tags.update_to_v23()  # mutagen keeps v2.4 frames (TDRC) otherwise: TDRC -> TYER
    tags.save(path(name), v2_version=version, v1=0, padding=lambda info: 64)


# --- untagged / ID3v1 only -----------------------------------------------------------------------------
copy_base("no-tags.mp3")

copy_base("v1_0.mp3")
append("v1_0.mp3", v1_block("V1 Title", "V1 Artist", "V1 Album", "1995",
                            "A comment of thirty bytes!!!!!", None, 13))  # 13 = Pop

copy_base("v1_1.mp3")
append("v1_1.mp3", v1_block("V1.1 Title", "V1.1 Artist", "V1.1 Album", "2003", "Short", 7, 17))  # 17 = Rock

# --- ID3v2.3 / v2.4 via mutagen ------------------------------------------------------------------------
copy_base("v23-latin1.mp3")
save_v2("v23-latin1.mp3", [
    TIT2(encoding=LATIN1, text="Café Song"),
    TPE1(encoding=LATIN1, text="Artist"),
    TPE2(encoding=LATIN1, text="Album Artist"),
    TALB(encoding=LATIN1, text="Album"),
    TRCK(encoding=LATIN1, text="3/12"),
    TPOS(encoding=LATIN1, text="1/2"),
    TDRC(encoding=LATIN1, text="2001"),
    TCON(encoding=LATIN1, text="Pop"),
    COMM(encoding=LATIN1, lang="eng", desc="", text="A comment"),
], 3)

copy_base("v23-utf16-cyrillic.mp3")
save_v2("v23-utf16-cyrillic.mp3", [
    TIT2(encoding=UTF16, text="Љубав 🎵"),
    TPE1(encoding=UTF16, text="Ђорђе Балашевић"),
    TALB(encoding=UTF16, text="čćšžđ ČĆŠŽĐ"),
    TDRC(encoding=UTF16, text="1986"),
], 3)

copy_base("v24-utf8.mp3")
save_v2("v24-utf8.mp3", [
    TIT2(encoding=UTF8, text="Ünïcödé Title"),
    TPE1(encoding=UTF8, text="UTF-8 Artist"),
    TDRC(encoding=UTF8, text="2020-05-01"),
    TCON(encoding=UTF8, text=["Pop", "Rock"]),  # v2.4: NUL-separated
], 4)

copy_base("v23-genre-ref.mp3")
save_v2("v23-genre-ref.mp3", [TIT2(encoding=LATIN1, text="Genre Ref"), TCON(encoding=LATIN1, text="(17)Rock")], 3)

copy_base("v23-genre-num.mp3")
save_v2("v23-genre-num.mp3", [TIT2(encoding=LATIN1, text="Genre Num"), TCON(encoding=LATIN1, text="17")], 3)

copy_base("v23-bad-track.mp3")
save_v2("v23-bad-track.mp3", [TIT2(encoding=LATIN1, text="Bad Track"), TRCK(encoding=LATIN1, text="A1")], 3)

with open(cover_jpg, "rb") as f:
    jpeg = f.read()

copy_base("v23-cover.mp3")
save_v2("v23-cover.mp3", [
    TIT2(encoding=LATIN1, text="With Cover"),
    APIC(encoding=LATIN1, mime="image/jpeg", type=3, desc="", data=jpeg),
], 3)

copy_base("v23-other-picture.mp3")
save_v2("v23-other-picture.mp3", [
    TIT2(encoding=LATIN1, text="Other Picture"),
    APIC(encoding=LATIN1, mime="image/jpeg", type=0, desc="", data=jpeg),
], 3)

# --- both tags -----------------------------------------------------------------------------------------
copy_base("v11-v23-same.mp3")
save_v2("v11-v23-same.mp3", [
    TIT2(encoding=UTF16, text="This Title Has Thirty One Chars"),
    TPE1(encoding=UTF16, text="Ђорђе Балашевић"),
    TALB(encoding=UTF16, text="Album"),
    TRCK(encoding=UTF16, text="3/12"),
    TDRC(encoding=UTF16, text="1986"),
    TCON(encoding=UTF16, text="Rock"),
], 3)
# v1 = transliterated / word-boundary-truncated v2, track without total
append("v11-v23-same.mp3", v1_block("This Title Has Thirty One", "Djordje Balasevic", "Album", "1986", "", 3, 17))

copy_base("v11-v23-diff.mp3")
save_v2("v11-v23-diff.mp3", [TIT2(encoding=LATIN1, text="Same Title"), TPE1(encoding=LATIN1, text="Artist A")], 3)
append("v11-v23-diff.mp3", v1_block("Same Title", "Artist B", "", "", "", 1, 255))  # track 1 makes it v1.1

# --- ID3v2.2, hand-written (mutagen cannot write v2.2) ------------------------------------------------
def v22_frame(frame_id, text):
    body = b"\0" + text.encode("latin-1")
    return frame_id.encode("ascii") + struct.pack(">I", len(body))[1:] + body


def syncsafe(n):
    return bytes([(n >> 21) & 0x7F, (n >> 14) & 0x7F, (n >> 7) & 0x7F, n & 0x7F])


frames = b"".join([
    v22_frame("TT2", "V22 Title"),
    v22_frame("TP1", "V22 Artist"),
    v22_frame("TAL", "V22 Album"),
    v22_frame("TRK", "5/10"),
    v22_frame("TYE", "1999"),
    v22_frame("TCO", "(13)"),  # 13 = Pop
])
frames += b"\0" * 32  # padding
with open(base, "rb") as f:
    audio = f.read()
with open(path("v22.mp3"), "wb") as f:
    f.write(b"ID3\x02\x00\x00" + syncsafe(len(frames)) + frames + audio)

# --- not MP3 -------------------------------------------------------------------------------------------
# 4 KiB of text: no 0xFF byte, so no MPEG frame sync can be found by accident (deterministic, unlike random bytes)
with open(path("corrupt.mp3"), "wb") as f:
    f.write((b"this is not an mp3 file. " * 200)[:4096])
open(path("empty.mp3"), "wb").close()

print("cover jpeg bytes:", len(jpeg))
