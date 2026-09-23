# 467. An audio track is switched by retiring its thread and seeking

Date: 2026-09-23

## Status

Accepted. Phase 7 of `goldberry-media` (`docs/media-plan.md`): the audio track
menu of `docs/goldberry-media.md` §6.

## Context

A source with several audio tracks (languages, a commentary) plays its default
one. Choosing another while it plays has to change which packets the demuxer
hands over and which decoder turns them into samples, and bring the new track in
at the position, in step with the picture. The audio thread owns its decoder
(the Decoder SPI promises a decoder one thread) and its packet queue, and the
queue carries Serials that tie every packet to a seek.

A menu also has to name the tracks, and FFmpeg keeps a track's language and
title in the stream's metadata dictionary, which the bindings did not read.

## Decision

**A switch retires the audio thread and seeks.** `MediaPlayer.selectTrack`
hands the track to the demux thread, which:

1. checks that the track has a decoder, and keeps the playing one if not;
2. retires the audio thread (a flag every one of its waits checks), aborts its
   queue so a blocked wait ends, and joins it; the thread closes its own
   decoder on the way out;
3. selects the new stream in the demuxer, and starts a new audio thread on a
   new queue;
4. requests an accurate seek to where playback is.

The seek is the whole of the synchronisation: every queue is flushed at one
Serial, the new thread discards to the target like after any seek, and the
sink starts over at it. Until the flush reaches the sink, it plays what the old
thread wrote, so a switch is a change of track and not a gap.

**Tracks carry their `language` and `title`**, read with `av_dict_get` from
`AVStream.metadata` (a field and a struct, `AVDictionaryEntry`, added to the
layout probe and check). A language of `und` is no language.

**The menu names languages through ISO 639-2/T as well.** Containers mostly
write three-letter terminology codes (`fra`, `deu`), which the JDK does not name
on its own. They are mapped to their two-letter codes first.

Only audio is switched. Choosing a video track is refused for now.

## Alternatives considered

- **Reopen the whole playback on the new track.** Simple, and it restarts the
  sink and the picture, loses the read-ahead cache of a network source, and
  shows OPENING for a menu choice.
- **Keep one audio thread and swap its decoder.** The thread would need to know
  which Serial each packet's stream belongs to, and a provider's decoder must be
  opened and closed on the thread that uses it anyway; retiring the thread is
  that rule, followed.
- **Decode every audio track and mute all but one.** Instant switching, at the
  cost of decoding what nobody hears.

## Consequences

- `media-controls`, `audio-player` and `media-player` show an audio track menu
  (`.media-audio-track`) for a source with two or more audio tracks, and nothing
  more for one, so no golden changed.
- `PlayerStatus.audioTrack` and `videoTrack` report what plays.
  `PlayerStatus.videoTrack()` was derived from the source's default before, and
  is what the Engine chose now.
- Switching video tracks and subtitles remain open.
