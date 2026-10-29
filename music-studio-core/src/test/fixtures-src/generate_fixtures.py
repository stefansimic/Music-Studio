#!/usr/bin/env python3
"""Writes the MP3 fixtures. Usage: generate_fixtures.py AUDIO.mp3 COVER.jpg OUTDIR

Every fixture is audio.mp3 plus tags. ID3v1 blocks and the ID3v2.2 tag are written by hand below so that they do not
depend on how a tag library decides to serialise them.
"""
import random
import shutil
import struct
import sys
from pathlib import Path

from mutagen.id3 import (APIC, COMM, ID3, TALB, TCON, TDRC, TIT2, TPE1, TPE2, TPOS, TRCK, TYER, Encoding)

audio_path, cover_path, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])


def strip_id3(data):
    """Removes a leading ID3v2 tag and a trailing ID3v1 block: ffmpeg adds a TSSE frame even when told not to."""
    if data[:3] == b"ID3":
        size = (data[6] << 21) | (data[7] << 14) | (data[8] << 7) | data[9]
        data = data[10 + size + (10 if data[5] & 0x10 else 0):]
    if len(data) >= 128 and data[-128:-125] == b"TAG":
        data = data[:-128]
    return data


audio = strip_id3(audio_path.read_bytes())
assert audio[:3] != b"ID3" and audio[0] == 0xFF, "audio must start with an MPEG frame"
cover = cover_path.read_bytes()


def write(name, data=None, v2=None, v1=None):
    """audio (+ ID3v2 prefix) (+ ID3v1 suffix) -> out/name"""
    target = out / name
    body = audio if data is None else data
    target.write_bytes(body)
    if v2 is not None:
        v2.save(target, v1=0, v2_version=v2.version[1])
    if v1 is not None:
        with open(target, "ab") as f:
            f.write(v1)


def tag(version, *frames):
    t = ID3()
    t.version = (2, version, 0)
    for frame in frames:
        t.add(frame)
    return t


def id3v1(title="", artist="", album="", year="", comment="", track=None, genre=255):
    def field(text, size):
        raw = text.encode("latin-1")
        assert len(raw) <= size, (text, size)
        return raw.ljust(size, b"\0")

    if track is None:
        comment_bytes = field(comment, 30)
    else:
        comment_bytes = field(comment, 28) + b"\0" + bytes([track])
    block = (b"TAG" + field(title, 30) + field(artist, 30) + field(album, 30) + field(year, 4) + comment_bytes
             + bytes([genre]))
    assert len(block) == 128
    return block


def id3v22(frames):
    """ID3v2.2: 3-character frame IDs, 3-byte sizes, Latin-1 text (encoding byte 0)."""
    body = b""
    for frame_id, text in frames:
        payload = b"\0" + text.encode("latin-1")
        body += frame_id.encode("ascii") + struct.pack(">I", len(payload))[1:] + payload
    size = bytes([(len(body) >> 21) & 0x7F, (len(body) >> 14) & 0x7F, (len(body) >> 7) & 0x7F, len(body) & 0x7F])
    return b"ID3" + bytes([2, 0, 0]) + size + body


LATIN1, UTF16, UTF8 = Encoding.LATIN1, Encoding.UTF16, Encoding.UTF8

write("no-tags.mp3")

comment30 = "Comment of exactly 30 bytes!!!"
assert len(comment30) == 30
write("v1_0.mp3", v1=id3v1("Title v1.0", "Artist v1", "Album v1", "1999", comment30, track=None, genre=255))
write("v1_1.mp3", v1=id3v1("Title v1.1", "Artist v1.1", "Album v1.1", "2001", "Comment v1.1", track=7, genre=17))

write("v23-latin1.mp3", v2=tag(
    3,
    TIT2(encoding=LATIN1, text="Café au lait"),
    TPE1(encoding=LATIN1, text="Artist Latin1"),
    TPE2(encoding=LATIN1, text="Album Artist"),
    TALB(encoding=LATIN1, text="Album Latin1"),
    TYER(encoding=LATIN1, text="2005"),
    TRCK(encoding=LATIN1, text="3/12"),
    TPOS(encoding=LATIN1, text="1/2"),
    TCON(encoding=LATIN1, text="Jazz"),
    COMM(encoding=LATIN1, lang="eng", desc="", text="A comment"),
))

write("v23-utf16-cyrillic.mp3", v2=tag(
    3,
    TIT2(encoding=UTF16, text="čćšžđ"),
    TPE1(encoding=UTF16, text="Ђорђе Балашевић"),
    TALB(encoding=UTF16, text="Музика \U0001F3B5"),
    TYER(encoding=LATIN1, text="2012"),
))

write("v24-utf8.mp3", v2=tag(
    4,
    TIT2(encoding=UTF8, text="Title UTF-8 ñ"),
    TPE1(encoding=UTF8, text="Artist v24"),
    TALB(encoding=UTF8, text="Album v24"),
    TDRC(encoding=UTF8, text="2020-05-01"),
    TRCK(encoding=UTF8, text="5"),
    TCON(encoding=UTF8, text=["Rock", "Pop"]),  # two genres, NUL-separated in v2.4
))

write("v22.mp3", data=id3v22([
    ("TT2", "Title v22"), ("TP1", "Artist v22"), ("TAL", "Album v22"), ("TRK", "4/9"), ("TYE", "1995"),
    ("TCO", "Blues"),
]) + audio)

write(
    "v11-v23-same.mp3",
    v2=tag(
        3,
        TIT2(encoding=UTF16, text="A title that is definitely longer than thirty characters"),
        TPE1(encoding=UTF16, text="Ђорђе Балашевић"),
        TALB(encoding=UTF16, text="Album Šumadija"),
        TYER(encoding=LATIN1, text="2010"),
        TRCK(encoding=LATIN1, text="2/10"),
        TCON(encoding=LATIN1, text="Rock"),
    ),
    v1=id3v1("A title that is definitely", "Djordje Balasevic", "Album Sumadija", "2010", "", track=2, genre=17),
)

write(
    "v11-v23-diff.mp3",
    v2=tag(
        3,
        TIT2(encoding=LATIN1, text="Same Title"),
        TPE1(encoding=LATIN1, text="Artist Two"),
        TYER(encoding=LATIN1, text="2015"),
        TRCK(encoding=LATIN1, text="1/10"),
    ),
    v1=id3v1("Same Title", "Artist One", "", "2015", "", track=1, genre=255),
)

write("v23-genre-ref.mp3", v2=tag(3, TIT2(encoding=LATIN1, text="Genre Ref"),
                                  TCON(encoding=LATIN1, text="(17)Rock")))
write("v23-genre-num.mp3", v2=tag(3, TIT2(encoding=LATIN1, text="Genre Num"), TCON(encoding=LATIN1, text="17")))
write("v23-bad-track.mp3", v2=tag(3, TIT2(encoding=LATIN1, text="Bad Track"), TRCK(encoding=LATIN1, text="A1")))
write("v23-cover.mp3", v2=tag(3, TIT2(encoding=LATIN1, text="With Cover"),
                              APIC(encoding=LATIN1, mime="image/jpeg", type=3, desc="front", data=cover)))
write("v23-other-picture.mp3", v2=tag(3, TIT2(encoding=LATIN1, text="Other Picture"),
                                      APIC(encoding=LATIN1, mime="image/jpeg", type=0, desc="other", data=cover)))

# 4 KiB of seeded random bytes without 0xFF, so that no MPEG frame sync can occur by accident
rng = random.Random(20261022)
(out / "corrupt.mp3").write_bytes(bytes(rng.randrange(0, 255) for _ in range(4096)))
(out / "empty.mp3").write_bytes(b"")
