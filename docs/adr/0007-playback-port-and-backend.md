# 0007 – Playback behind a PlaybackEngine port, JavaFX Media as first backend

Status: accepted · Date: 2026-10-22 · Details: [architecture §6.6, §7.7](../architecture.md)

## Context
Story 011 adds a player: play/pause/stop, next/previous, seek, volume and a queue. Video playback and other
backends may follow. The GUI toolkit may change, and the native CLI must not pull in JavaFX.

## Decision
- Port `PlaybackEngine` in core: open/play/pause/stop/seek/volume/status, plus a listener for state, position and
  end-of-media events delivered on an implementation thread. It covers one media item at a time.
- Queue, next/previous, "skip corrupt file" and shuffle-later logic live in core (`PlaybackQueue`,
  `PlayerUseCase`), not in the engine.
- First backend: `music-studio-player-javafx` using `javafx.scene.media.MediaPlayer` (MP3 and AAC, and H.264 MP4 for
  later video). It is wired only by the GUI's `GuiMain` and passed into `MusicStudioFactory`.
- The CLI `play` command (optional in 011) is not implemented with JavaFX. If wanted, it comes later via a pure-Java
  backend (see alternatives).

## Alternatives
- *javax.sound + JLayer/mp3spi*: pure Java and possibly native-friendly, but old libraries and audio only.
- *vlcj (libVLC)*: plays everything including video, but requires an installed VLC.
- *JavaFX Media directly in GUI controllers*: simpler, but ties playback to the GUI toolkit (story 011 forbids it).

## Consequences
- A toolkit change of the GUI does not force a player rewrite (only the engine module depends on JavaFX). The
  JavaFX runtime then has to be initialised headless by the engine itself.
- JavaFX Media has codec gaps on some Linux setups (GStreamer plugins). Errors surface as `MediaAccessException`
  and the queue skips.
