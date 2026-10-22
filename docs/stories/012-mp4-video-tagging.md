# 012 – Tag and organize MP4 videos (movies, episodes)

**As a** user who downloads movies and TV episodes as MP4 **I want** to extract title, show, season and episode from
the filenames, write them as MP4 metadata and export into a clean folder structure **so that** my PC, phone and media
players show and sort them correctly.

## Context
Same workflow as for MP3 (002 extract → 005 clean up → 004 export), different metadata. Download names typically
look like `Show.Name.S01E02.Episode.Title.720p.WEB.x264-GROUP.mp4` or `Movie.Title.2019.1080p.BluRay.mp4`.
MP4 uses iTunes-style atoms, not ID3 (see `audio-metadata` skill → "MP4 video metadata").

## Acceptance criteria
- [ ] Media kind per batch: **Movie** or **TV episode**.
- [ ] Additional pattern placeholders: `{show}` `{season}` `{episode}` `{episodetitle}`; `S{season}E{episode}`,
      `{season}x{episode}` and dots as separators work.
- [ ] Cleanup (005) has a video junk list: resolutions (`720p`, `1080p`, `2160p`), sources (`WEB`, `WEB-DL`, `BluRay`,
      `HDTV`), codecs (`x264`, `x265`, `HEVC`, `AAC`), release groups (`-GROUP` at the end).
- [ ] Written metadata: title, year, genre, description (optional), media kind; for episodes also show, season,
      episode number, episode ID (`S01E02`); poster image as cover (009).
- [ ] Output patterns for videos, defaults compatible with Plex/Jellyfin/Kodi naming:
  - Movies: `{title} ({year})/{title} ({year})`
  - Episodes: `{show}/Season {season:00}/{show} - S{season:00}E{episode:00} - {episodetitle}`
- [ ] Export copies the video and writes metadata into the copy; originals untouched. Large files (several GB):
      progress per file, free-space check before starting.
- [ ] Optionally copy matching subtitle files (`.srt` with the same base name) along with the video.

## Edge cases
- Double episodes `S01E01E02` / `S01E01-E02`
- Year in the movie title (`2001 A Space Odyssey 1968`)
- Episode title missing → filename uses `{show} - S01E02`
- Files of 5–20 GB: metadata write must not load the whole file into memory

## Data safety
Same as 004. Video copies are big → disk space check, cancel cleans up partial files.

## Interfaces
- CLI: `musicstudio export ~/Downloads/Series --kind episode --pattern "{show}.S{season}E{episode}.{episodetitle}.{*}"
  --out ~/Videos --apply`
- GUI: same workspace (010) with video columns when the batch contains MP4.

## Out of scope
Video playback (later idea), MKV/AVI, conversion/re-encoding, online lookup (TMDb/TVDb — later idea).

## Assumptions
- MP4 tagging library to be decided by the architect (jaudiotagger supports MP4 atoms, but its handling of very large
  video files must be verified; alternatives: mp4parser). Behind its own port/adapter either way.
