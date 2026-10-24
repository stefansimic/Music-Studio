# 0011 – MP3 tag writing: compose the copy, core computes the expected result

Status: proposed · Date: 2026-10-23 · Details: [plan 003](../plan-003.md), architecture §5.3, §6.3, §8.2, §14.1

## Context
`TagWriter.writeCopy(source, target, request, cancel, progress)` (ADR 0003) must guarantee that the source stays
unchanged, the audio payload is byte-identical, unknown frames survive, cancellation works at least every 8 MiB, and
`preflight` lists *every* frame that will be lost. Apply then re-reads the copy and compares it with what the plan
promised (§8.2).

jaudiotagger's normal write path (`MP3File.save()` / `AudioFileIO.write`) works **in place** on an existing file:
we would have to copy first and then let the library rewrite the copy. That means two full writes, library temp
files in the user's output folder, no cancellation or progress during the library's rewrite, and a payload that the
library (not we) rewrites. Its `ID3v1Tag.setGenre(String)` uses jaudiotagger's own genre table instead of
`Id3v1Genres`, and `FieldKey.GENRE`/`FieldKey.TRACK` apply global `TagOptionSingleton` formatting (`(17)` genre
references, zero padding).

jaudiotagger 3.0.1 can also serialise a tag object on its own: `AbstractID3v2Tag.write(OutputStream)` /
`write(WritableByteChannel, int)` exist for v2.2/v2.3/v2.4, and `new ID3v23Tag(AbstractTag)` etc. convert
between versions.

For verification, architecture §14.1 planned contract hooks `addUnknownData(Path)`/`hasUnknownData(Path)` that each
adapter implements with its own library. That makes the "unknown frames survive" check depend on the library under
test.

## Decision
**Adapter (jaudiotagger):** `writeCopy` *composes* the target file in one streaming pass:
1. Under the library lock: parse the source tags read-only, build the target ID3v2 tag object (convert the
   existing tag with the `ID3v2xTag(AbstractTag)` constructors, or start empty), apply `FieldChanges` at frame
   level (TRCK/TPOS/TCON/COMM raw text, never `(nn)` references), set text encodings per frame, and serialise
   the tag to a byte array. Release the lock.
2. Write `[new ID3v2 bytes | original v2 bytes (family NONE, kept) | nothing]`, then the source **payload**
   (everything between the end of the source's ID3v2 tag and the start of its ID3v1 block, incl. APE/Lyrics3)
   with our own 1 MiB-buffer copy loop (cancel check + progress at least every 8 MiB), then
   `[ID3v1 block encoded by the adapter from Id3v1Values | original 128 bytes (kept) | nothing]`.
3. The source is only opened read-only. jaudiotagger never writes a file.

`preflight` and `writeCopy` share one package-private conversion function that returns the converted tag *and*
the list of source frames without a target equivalent, so "writeCopy drops nothing preflight did not report" holds
by construction. A source whose ID3v2 tag is damaged (unparsable frames) and would have to be rewritten is
rejected with `CORRUPT` instead of being rewritten lossily.

**Core** computes everything that must be identical for every adapter: the ID3v1 bytes (`Id3v1Projection`, ADR
0003), the slot semantics (`WriteExpectation.layersAfter`: which versions exist after the write and which values
they hold, as a `TagReader` reports them), the slot warnings `OTHER_VERSION_KEPT`/`OTHER_VERSION_REMOVED`, and the
comparison (`WriteVerifier`). `preflight` reports only adapter knowledge: `FRAME_NOT_REPRESENTABLE`.

**Contracts:** split like the reader's: `TagWriterContract` (semantic, via a `TagReader`; fakes pass it too) and
`Id3WriterContract` (byte level, real MP3 writers only). The byte-level contract builds inputs with the existing
`Id3Bytes` and inspects outputs with a new library-independent `Id3Inspector` in the test-jar. It replaces the
`addUnknownData`/`hasUnknownData` hooks.

## Alternatives
- *Copy the file, then `MP3File.save()` on the copy*: least adapter code, but no cancellation/progress during the
  library's rewrite, library temp files in the output folder, a second full write, and jaudiotagger's own ID3v1
  genre table.
- *`AudioFileIO.writeAs(target)`*: same in-place rewrite internally; same problems.
- *Own complete ID3v2 serialiser*: full control, but re-implements frame conversion for 3 versions. Not justified
  while jaudiotagger can serialise a tag object.
- *Library-specific contract hooks*: an mp3agic adapter would prove frame preservation with mp3agic itself, so a
  shared blind spot would go unnoticed.
- *Adapter reports OTHER_VERSION_\* (as §6.3 said)*: every adapter would re-implement the same slot rule and could
  drift from the dry-run that core shows.

## Consequences
- The payload is byte-identical by construction, and only our copy loop touches it. Byte progress and cancellation
  work the same way for MP3 now and for multi-GB MP4 later (012).
- The adapter owns a small ID3v1 encoder (128 bytes) and the payload boundary logic (v2 header size + footer flag,
  trailing `TAG`). Both are tested at byte level with `Id3Inspector`.
- The library lock is held only while parsing and serialising tags, not while copying the payload.
- An mp3agic adapter can do the same: `ID3v2.toBytes()`, payload copy, `ID3v1.toBytes()`. It proves itself with both
  contracts.
- The architecture text changes: §6.3 (preflight scope; slot issues computed by core) and §14.1 (contract split,
  no hooks).
