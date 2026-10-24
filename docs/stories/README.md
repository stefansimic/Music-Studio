# Story backlog

Vision: a Java app (GUI + CLI on the same use cases) that makes preparing downloaded music for my devices
(old car stereo, MP3 player, phone, PC) fast — mainly batch tagging from file/folder names and exporting into a
defined structure — plus a music player with a library.

| # | Story | Depends on | Status |
|---|---|---|---|
| 001 | [Scan a folder and read tags](001-scan-and-read-tags.md) | – | done (native image build pending: needs a C toolchain) |
| 002 | [Extract metadata from file and folder names](002-extract-metadata-from-names.md) | 001 | done |
| 003 | [Write tags with selectable ID3 versions](003-write-tags-with-version-selection.md) | 001 | done (native image re-trace pending: needs a C toolchain) |
| 004 | [Export into a defined output structure](004-export-to-output-structure.md) | 002, 003 | open |
| 005 | [Clean up and transform values](005-clean-up-and-transform-values.md) | 002 | open |
| 006 | [Device profiles](006-device-profiles.md) | 003, 004 | open |
| 007 | [Save and reuse jobs as presets](007-save-and-reuse-presets.md) | 004, 005 | open |
| 008 | [Genre normalization](008-genre-normalization.md) | 003 | open |
| 009 | [Cover art](009-cover-art.md) | 003 | open |
| 010 | [GUI: batch tagging workspace](010-gui-batch-tagging.md) | 002–004 | open |
| 011 | [Library and MP3 player](011-library-and-player.md) | 001 | open |
| 012 | [Tag and organize MP4 videos (movies, episodes)](012-mp4-video-tagging.md) | 002–004 | open |

**Later / ideas** (not yet stories): video playback in the player · online lookup (MusicBrainz, AcoustID, Cover Art Archive) ·
tags → filename rename in place · duplicate detection · M3U playlists for devices · volume normalization (MP3Gain-style,
lossless) · transcoding for old devices (needs ffmpeg — not needed so far, MP3s play fine in the car) · copy to USB in sorted order.
