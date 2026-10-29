#!/bin/sh
# Regenerates src/main/resources/META-INF/native-image/dev.rs.slava.icxc/music-studio-tag-jaudiotagger/
# reachability-metadata.json, the GraalVM native-image reflection metadata for jaudiotagger.
#
# Why this is not just the tracing agent's output: jaudiotagger creates frame bodies by name
# (Class.forName("...framebody.FrameBody" + frameId)) and copies frames, frame bodies and data types through copy
# constructors (ID3Tags.copyObject). The agent only sees the frame types that the exercised files contain, so a
# real-world file with another frame (TCOM, TXXX, USLT, PRIV, ...) would fail in the native binary. Therefore the
# classes of those packages are enumerated from the jar (found by disassembling the library: the reflective calls are
# in AbstractID3v2Frame, ID3v22Frame, ID3Tags and the ASF ChunkContainerReader). Whatever the agent traced for
# org.jaudiotagger is merged in as well.
#
# Usage: generate-reachability-metadata.sh JAUDIOTAGGER.jar [AGENT/reachability-metadata.json]
#   The agent file comes from running the JVM CLI over a folder of varied MP3 files with
#   -agentlib:native-image-agent=config-merge-dir=AGENT
# Requires: unzip, python3.
set -eu
jar=${1:?usage: $0 JAUDIOTAGGER.jar [agent-reachability-metadata.json]}
agent=${2:-}
here=$(cd "$(dirname "$0")" && pwd)
out="$here/../src/main/resources/META-INF/native-image/dev.rs.slava.icxc/music-studio-tag-jaudiotagger/reachability-metadata.json"
mkdir -p "$(dirname "$out")"
unzip -Z1 "$jar" | python3 "$here/generate_reachability_metadata.py" "$agent" > "$out"
echo "wrote $out"
