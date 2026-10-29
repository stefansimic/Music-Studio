#!/usr/bin/env python3
"""Reads jar entry names on stdin, writes reachability-metadata.json on stdout. See generate-reachability-metadata.sh."""
import json
import re
import sys

# packages whose classes jaudiotagger instantiates or copies reflectively
PACKAGES = [
    "org/jaudiotagger/tag/id3/framebody/",   # Class.forName("...FrameBody" + id), newInstance, copy constructors
    "org/jaudiotagger/tag/datatype/",        # ID3Tags.copyObject on data types
    "org/jaudiotagger/tag/id3/",             # ID3Tags.copyObject on frames and tags (top-level classes only)
    "org/jaudiotagger/audio/asf/io/",        # ChunkContainerReader: Class.newInstance of the chunk readers
]

classes = set()
for line in sys.stdin:
    name = line.strip()
    if not name.endswith(".class") or "$" in name:
        continue
    for package in PACKAGES:
        # only direct members of the package
        if name.startswith(package) and "/" not in name[len(package):]:
            classes.add(name[:-len(".class")].replace("/", "."))

entries = {c: {"type": c, "allDeclaredConstructors": True} for c in sorted(classes)}

agent_file = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else None
if agent_file:
    with open(agent_file, encoding="utf-8") as f:
        traced = json.load(f)
    for entry in traced.get("reflection", []):
        type_name = entry.get("type", "")
        if isinstance(type_name, str) and re.match(r"^org\.jaudiotagger\.", type_name):
            merged = entries.setdefault(type_name, {"type": type_name})
            for key, value in entry.items():
                if key == "methods" or key == "fields":
                    existing = merged.setdefault(key, [])
                    existing.extend(v for v in value if v not in existing)
                elif key != "type":
                    merged[key] = value

json.dump({"reflection": [entries[k] for k in sorted(entries)]}, sys.stdout, indent=2, ensure_ascii=False)
sys.stdout.write("\n")
