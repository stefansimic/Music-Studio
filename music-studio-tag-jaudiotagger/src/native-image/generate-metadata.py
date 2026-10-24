#!/usr/bin/env python3
"""Generates jaudiotagger's GraalVM reachability metadata for this adapter.

jaudiotagger creates ID3 frame bodies reflectively from the frame ID (FrameBody<ID>) and copies data types through
their copy constructors. Tracing only sees the frames present in the test fixtures, so in addition to the traced
entries every class of the frame-body and datatype packages gets all declared constructors registered.

Usage:
  1. trace: mvn -pl music-studio-tag-jaudiotagger test \
       "-DargLine=-ea -agentlib:native-image-agent=config-merge-dir=<dir>"
     (and/or run the CLI with the same agent option over a music folder)
  2. python3 generate-metadata.py <jaudiotagger.jar> <dir>/reachability-metadata.json > \
       ../main/resources/META-INF/native-image/dev.rs.slava.icxc/music-studio-tag-jaudiotagger/reachability-metadata.json
Only org.jaudiotagger entries are kept.
"""
import json
import re
import sys
import zipfile

jar, traced_file = sys.argv[1], sys.argv[2]
PACKAGES = re.compile(r"^org/jaudiotagger/tag/(id3/framebody|datatype)/[^$/]+\.class$")

types = {}
with zipfile.ZipFile(jar) as z:
    for name in sorted(z.namelist()):
        if PACKAGES.match(name):
            type_name = name[:-len(".class")].replace("/", ".")
            types[type_name] = {"type": type_name, "allDeclaredConstructors": True}

with open(traced_file) as f:
    traced = json.load(f)
for entry in traced.get("reflection", []):
    type_name = entry.get("type")
    if isinstance(type_name, str) and type_name.startswith("org.jaudiotagger.") and type_name not in types:
        types[type_name] = {k: v for k, v in entry.items() if k != "condition"}

resources = [r for r in traced.get("resources", []) if "jaudiotagger" in json.dumps(r)]
metadata = {"reflection": [types[t] for t in sorted(types)]}
if resources:
    metadata["resources"] = resources
json.dump(metadata, sys.stdout, indent=2)
print()
