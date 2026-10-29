#!/bin/sh
# Regenerates the MP3 fixtures in ../resources/fixtures/mp3 with tools that are independent of the code under test:
#   ffmpeg (1 s of silence, 128 kbps CBR), Python mutagen (ID3v2.3/2.4 tags), and a small hand-written byte writer
#   (ID3v1/v1.1 blocks, ID3v2.2 tag, random garbage).
# All outputs are committed. Expected values live in FixtureCatalog.java and are written by hand from
# generate_fixtures.py - never produced by running our own reader.
#
# Requirements: ffmpeg with libmp3lame, python3 with mutagen (pip install mutagen). Override the interpreter with
# PYTHON=/path/to/python.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out="$here/../resources/fixtures/mp3"
mkdir -p "$out"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# audio only, no ID3v2 header, no Xing/Info frame: a pure CBR stream
ffmpeg -loglevel error -y -f lavfi -i anullsrc=r=44100:cl=stereo -t 1 -codec:a libmp3lame -b:a 128k \
    -write_id3v2 0 -write_id3v1 0 -write_xing 0 -f mp3 "$work/audio.mp3"
# a 1x1 JPEG for the cover fixtures
ffmpeg -loglevel error -y -f lavfi -i color=c=red:s=2x2 -frames:v 1 -vf scale=1:1 "$work/cover.jpg"

"${PYTHON:-python3}" "$here/generate_fixtures.py" "$work/audio.mp3" "$work/cover.jpg" "$out"
echo "cover.jpg size: $(wc -c < "$work/cover.jpg") bytes"
