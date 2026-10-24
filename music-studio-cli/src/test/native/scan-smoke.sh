#!/usr/bin/env bash
# Smoke test of the native CLI: scans copies of all fixtures and compares the JSON output with the JVM reference
# (expected-scan.json), then checks that no file changed. Linux/macOS.
#
# Requires: GraalVM JDK 25 with native-image and a C toolchain (Fedora: sudo dnf install gcc glibc-devel
# zlib-ng-compat-devel libstdc++-static).
# Usage (from the repository root): music-studio-cli/src/test/native/scan-smoke.sh [--no-build]
set -euo pipefail

repo="$(cd "$(dirname "$0")/../../../.." && pwd)"
here="$repo/music-studio-cli/src/test/native"
binary="$repo/music-studio-cli/target/musicstudio"

if [[ "${1:-}" != "--no-build" ]]; then
  (cd "$repo" && ./mvnw -q -Pnative -pl music-studio-cli -am package -DskipTests)
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
music="$work/music"
mkdir -p "$music"
cp "$repo"/music-studio-core/src/test/resources/fixtures/mp3/*.mp3 "$music/"
echo "not a jpeg" > "$music/cover.jpg"
cp "$music/v23-latin1.mp3" "$music/._hidden.mp3"

before="$(cd "$music" && find . -type f -exec sha256sum {} + | sort)"

set +e
"$binary" scan "$music" --recursive --format json > "$work/actual.json"
code=$?
set -e
if [[ $code -ne 1 ]]; then
  echo "FAIL: expected exit code 1 (two unreadable fixtures), got $code" >&2
  exit 1
fi

# the root differs per run; everything else must match the JVM reference exactly
sed -E 's/^\{"root":"[^"]*"/{"root":"<root>"/' "$work/actual.json" > "$work/actual-normalised.json"
if ! diff -u "$here/expected-scan.json" "$work/actual-normalised.json"; then
  echo "FAIL: native output differs from the JVM reference" >&2
  exit 1
fi

# extract (story 002): a preview over named copies, compared with the JVM reference
named="$work/Best Of 2020"
mkdir -p "$named"
cp "$music/v23-latin1.mp3" "$named/Artist - Song - 2020.mp3"
cp "$music/no-tags.mp3" "$named/A - B - C - 2020.mp3"
cp "$music/no-tags.mp3" "$named/Bad Name.mp3"
set +e
"$binary" extract "$named" --pattern "{album}/{artist} - {title} - {year}" --set genre=Pop --format json \
  > "$work/extract.json"
set -e
sed -E 's/^\{"root":"[^"]*"/{"root":"<root>"/' "$work/extract.json" > "$work/extract-normalised.json"
if ! diff -u "$here/expected-extract.json" "$work/extract-normalised.json"; then
  echo "FAIL: native extract output differs from the JVM reference" >&2
  exit 1
fi

after="$(cd "$music" && find . -type f -exec sha256sum {} + | sort)"
if [[ "$before" != "$after" ]]; then
  echo "FAIL: a file was modified by scanning" >&2
  exit 1
fi
echo "OK: native scan matches the reference and changed nothing"
