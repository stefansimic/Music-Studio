#!/usr/bin/env bash
# Generates the MP3 test fixtures with tools that are independent of our code:
#   ffmpeg (libmp3lame) for 1 s of silence, Python mutagen for ID3v2.3/v2.4, hand-written bytes for ID3v1 and v2.2.
# Expectations in FixtureCatalog are written by hand from generate_fixtures.py, never by running our reader.
#
# Requirements: ffmpeg with libmp3lame, python3 with mutagen (python3 -m pip install mutagen).
# Usage: ./generate-fixtures.sh [python-interpreter]
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../resources/fixtures/mp3"
python="${1:-python3}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 1 s mono silence, 128 kbit/s CBR, no Xing/Info frame, no tags, deterministic output
ffmpeg -hide_banner -loglevel error -f lavfi -i anullsrc=r=44100:cl=mono -t 1 \
  -c:a libmp3lame -b:a 128k -write_xing 0 -id3v2_version 0 -write_id3v1 0 \
  -map_metadata -1 -fflags +bitexact -flags:a +bitexact "$work/base.mp3"

# 1x1 red baseline JPEG for the picture fixtures
ffmpeg -hide_banner -loglevel error -f lavfi -i color=c=red:s=1x1 -frames:v 1 \
  -fflags +bitexact -flags:v +bitexact "$work/cover.jpg"

mkdir -p "$out"
"$python" "$here/generate_fixtures.py" "$work/base.mp3" "$work/cover.jpg" "$out"
echo "fixtures written to $out"
