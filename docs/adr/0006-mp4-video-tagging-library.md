# 0006 – MP4 video tagging: own adapter module, jaudiotagger first behind a verification gate

Status: accepted with verification gate (spike at the start of story 012) · Date: 2026-10-22

## Context
Story 012 writes iTunes-style atoms (`©nam`, `©day`, `©gen`, `desc`, `stik`, `tvsh`, `tvsn`, `tves`, `tven`,
`covr`) into copies of 5–20 GB MP4 files. Requirements: the write must not load the file into memory, it must work on
a copy (ADR 0004), it must show progress and be cancellable, and it should ideally put `moov` before `mdat`
(fast start). Changing `moov` size requires rewriting chunk offsets (`stco`/`co64`) when `moov` precedes `mdat`.

jaudiotagger supports MP4 metadata, including the TV fields. Its handling of very large video files is
**unverified**: in-place rewrite strategy, memory use while shifting `mdat`, correctness with 64-bit sizes/`co64`,
and int-sized offsets.

## Decision
- MP4 goes into its **own adapter module `music-studio-tag-mp4`**, implementing the existing `TagReader`/`TagWriter`
  ports for `MediaFormat.MP4`. No new port is needed: `writeCopy(source, target, …)` lets an adapter copy and tag in
  one streaming pass.
- **Spike first (gate)**, using generated test files of 5 GB (sparse `mdat`, with `moov` at the start and at the end,
  `stco` and `co64`):
  1. heap stays < 256 MB, 2. payload byte-identical, 3. offsets correct (ffprobe decodes the first/last frames),
  4. throughput ≈ plain copy, 5. works in the native CLI.
- **If jaudiotagger passes** → use it (copy with progress via `ExportTarget`-like streaming, then tag the temp copy).
- **If it fails** → write our own minimal ISO-BMFF writer in the adapter (parse top-level boxes, rebuild
  `moov/udta/meta/ilst`, patch `stco`/`co64`, stream `mdat` in one pass). It is dependency-free and native-friendly.
- `org.mp4parser:isoparser` (1.9.x) is the second alternative only. Its box factory is reflection-based (native
  metadata needed) and it is barely maintained.

## Alternatives
- *AtomicParsley / ffmpeg as external processes*: robust and fast, but an external install dependency, not portable
  inside a single native binary, and process handling in core paths.
- *mp4parser as first choice*: streaming-capable, but reflection plus low maintenance.
- *Same module as the MP3 adapter*: mixes very different I/O profiles and native metadata. Rejected.

## Consequences
- The 012 plan starts with the spike (≈1–2 days). The result is appended to this ADR as "Spike result".
- `StoreInfo.maxFileSize` (FAT32 4 GiB) and the space check become mandatory for video exports.
- The `TagWriterContract` gains MP4 fixtures. Large-file tests are tagged `large` and run outside the default build.
