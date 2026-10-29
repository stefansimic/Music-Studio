#!/usr/bin/env python3
"""Adds the fixtures used by the tag-writing tests (story 003) to ../resources/fixtures/mp3.

Usage: PYTHONPATH=<dir with mutagen> generate_write_fixtures.py OUTDIR
Reuses the audio of the committed no-tags.mp3 and the cover of v23-cover.mp3, so the existing fixtures stay
byte-identical (generate_fixtures.py is not re-run). Tags are written by Python mutagen, independent of the code
under test. Expected values live in FixtureCatalog.java (written by hand from this file).
"""
import sys
from pathlib import Path

from mutagen.id3 import (APIC, COMM, ID3, PRIV, GEOB, TALB, TCON, TDRC, TIT2, TKEY, TPE1, TPE2, TPOS, TRCK, TXXX,
                         TYER, USLT, Encoding)

out = Path(sys.argv[1])
audio = (out / "no-tags.mp3").read_bytes()
cover = [f for f in ID3(out / "v23-cover.mp3").values() if isinstance(f, APIC)][0].data
LATIN1, UTF16, UTF8 = Encoding.LATIN1, Encoding.UTF16, Encoding.UTF8


def write(name, version, *frames):
    path = out / name
    path.write_bytes(audio)
    tag = ID3()
    tag.version = (2, version, 0)
    for frame in frames:
        tag.add(frame)
    tag.save(path, v1=0, v2_version=version)


write("v23-extras.mp3", 3,
      TIT2(encoding=LATIN1, text="Extras Title"),
      TPE1(encoding=LATIN1, text="Extras Artist"),
      TALB(encoding=LATIN1, text="Extras Album"),
      TYER(encoding=LATIN1, text="2011"),
      TRCK(encoding=LATIN1, text="2/9"),
      TKEY(encoding=LATIN1, text="Am"),
      TXXX(encoding=LATIN1, desc="REPLAYGAIN_TRACK_GAIN", text="-6.50 dB"),
      TXXX(encoding=LATIN1, desc="REPLAYGAIN_TRACK_PEAK", text="0.987654"),
      TXXX(encoding=LATIN1, desc="MY_CUSTOM", text="custom value"),
      PRIV(owner="com.example.tool", data=b"\x01\x02\x03\xff\x00\x10"),
      GEOB(encoding=LATIN1, mime="application/x-test", filename="blob.bin", desc="blob", data=b"\x00\x01\x02\x03"),
      USLT(encoding=UTF16, lang="eng", desc="", text="Line one\nLine two"),
      APIC(encoding=LATIN1, mime="image/jpeg", type=3, desc="front", data=cover))

write("v24-extras.mp3", 4,
      TIT2(encoding=UTF8, text="Extras Title"),
      TPE1(encoding=UTF8, text="Extras Artist"),
      TALB(encoding=UTF8, text="Extras Album"),
      TDRC(encoding=UTF8, text="2011"),
      TRCK(encoding=UTF8, text="2/9"),
      TKEY(encoding=UTF8, text="Am"),
      TXXX(encoding=UTF8, desc="REPLAYGAIN_TRACK_GAIN", text="-6.50 dB"),
      TXXX(encoding=UTF8, desc="MY_CUSTOM", text="custom value"),
      PRIV(owner="com.example.tool", data=b"\x01\x02\x03\xff\x00\x10"),
      GEOB(encoding=UTF8, mime="application/x-test", filename="blob.bin", desc="blob", data=b"\x00\x01\x02\x03"),
      USLT(encoding=UTF8, lang="eng", desc="", text="Line one\nLine two"),
      APIC(encoding=LATIN1, mime="image/jpeg", type=3, desc="front", data=cover))

write("v23-utf16-greek.mp3", 3,
      TIT2(encoding=UTF16, text="Ελευθερία Αρβανιτάκη"),
      TPE1(encoding=UTF16, text="Μίκης Θεοδωράκης"),
      TALB(encoding=UTF16, text="Νίκος Βέρτης"),
      TYER(encoding=LATIN1, text="2008"),
      TRCK(encoding=LATIN1, text="3/10"))

write("v24-utf8-greek-cyrillic.mp3", 4,
      TIT2(encoding=UTF8, text="Ελευθερία Αρβανιτάκη"),
      TPE1(encoding=UTF8, text="Ђорђе Балашевић"),
      TPE2(encoding=UTF8, text="Μίκης Θεοδωράκης"),
      TALB(encoding=UTF8, text="ЉЊЏЂЋЈ љњџђћј čćšžđ"),
      TDRC(encoding=UTF8, text="2015-03-02"),
      TRCK(encoding=UTF8, text="4/12"),
      TCON(encoding=UTF8, text=["Rock", "Pop"]))
