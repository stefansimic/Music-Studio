#!/bin/sh
# Smoke test of the native CLI: scans a copy of the MP3 fixtures and compares the JSON with expected-scan.json.
# Proves that the native image reads real files (jaudiotagger reflection, JDK text data, Unicode paths) the same way
# the JVM does, and that scanning never modifies a file.
#
#   mvn -Pnative -pl music-studio-cli -am package
#   music-studio-cli/src/test/native/scan-smoke.sh [path/to/musicstudio]
#
# Environment:
#   MUSICSTUDIO_CMD   command to test instead of the native binary (for example "java -cp ... CliMain")
#   UPDATE=1          rewrite expected-scan.json from the tested command instead of comparing (review the diff!)
set -eu
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../../.." && pwd)
fixtures="$repo/music-studio-core/src/test/resources/fixtures/mp3"
expected="$here/expected-scan.json"
cmd=${MUSICSTUDIO_CMD:-${1:-$repo/music-studio-cli/target/musicstudio}}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
music="$work/music"
mkdir -p "$music/sub/Ђорђе"
cp "$fixtures"/*.mp3 "$music/"
cp "$fixtures/v23-cover.mp3" "$music/sub/Ђорђе/ünï 🎵.mp3"
echo "just a text file" > "$music/notes.txt"
echo "hidden" > "$music/.hidden.mp3"

checksums() { (cd "$music" && find . -type f | LC_ALL=C sort | while read -r f; do printf '%s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$f"; done); }
before=$(checksums)

set +e
# shellcheck disable=SC2086
$cmd scan "$music" --recursive --format json > "$work/actual.raw" 2> "$work/stderr.txt"
code=$?
set -e
# 16 fixtures + 1 nested file; corrupt.mp3 and empty.mp3 cannot be read, so the exit code is 1 (item errors)
[ "$code" -eq 1 ] || { echo "FAIL: exit code $code, expected 1"; cat "$work/stderr.txt"; exit 1; }
[ ! -s "$work/stderr.txt" ] || { echo "FAIL: unexpected output on stderr:"; cat "$work/stderr.txt"; exit 1; }

# make the output independent of the temp directory and of the copy time
sed -e "s#$music#<ROOT>#g" -e 's#"lastModified": "[^"]*"#"lastModified": "<TIME>"#g' "$work/actual.raw" > "$work/actual.json"

if [ "${UPDATE:-0}" = 1 ]; then
    cp "$work/actual.json" "$expected"
    echo "updated $expected - review it with git diff"
else
    if ! diff -u "$expected" "$work/actual.json" > "$work/diff.txt"; then
        echo "FAIL: output differs from expected-scan.json"
        head -40 "$work/diff.txt"
        exit 1
    fi
fi

after=$(checksums)
[ "$before" = "$after" ] || { echo "FAIL: scanning changed a file"; exit 1; }

# the human-readable table must work as well
$cmd scan "$music" --recursive > "$work/table.txt" 2>&1 || [ $? -eq 1 ]
grep -q "Ђорђе Балашевић" "$work/table.txt" || { echo "FAIL: Cyrillic artist missing from the table"; exit 1; }
grep -q "Skipped (not scanned)" "$work/table.txt" || { echo "FAIL: skipped section missing"; exit 1; }

echo "OK: $cmd"
