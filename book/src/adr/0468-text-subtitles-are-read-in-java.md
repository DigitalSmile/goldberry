# 468. Text subtitles are read in Java

Date: 2026-09-23

## Status

Accepted. Phase 7 of `goldberry-media` (`docs/media-plan.md`): the subtitles of
`docs/goldberry-media.md` §6.

## Context

§6 asks for text subtitles drawn by Goldberry's own text stack over the picture,
from a container's subtitle tracks and from external `.srt` and `.vtt` files,
with the formats' markup taken out. Bitmap subtitles are post-v1.

FFmpeg has subtitle decoders, reached through `avcodec_decode_subtitle2` and
the `AVSubtitle` and `AVSubtitleRect` structs, which would join the layout table.
What they produce for a text codec is an ASS event whose markup would still have
to be stripped in Java.

## Decision

**No FFmpeg subtitle decoder is bound.** A container's text subtitle packet is
one cue: its times are the packet's, and its payload is text.

- SubRip and WebVTT: the payload is the cue's text.
- ASS: FFmpeg's demuxers hand over `ReadOrder,Layer,Style,Name,MarginL,
  MarginR,MarginV,Effect,Text`, and the text is the ninth field on.
- MP4's `mov_text`: a 16-bit length, the text, then style boxes, passed over.

`Subtitles` parses these and external SubRip and WebVTT files into `Cue`s of
plain lines. Tags, entities, WebVTT's inline timestamps and ASS's override
blocks go; `\N` is a break.

**The demux thread collects cues.** A chosen subtitle track's packets are not
queued for a decode thread. The demux thread turns each into a cue for a
`SubtitleTimeline`, kept once read, so a seek back finds them. Choosing a track
selects its stream and seeks to the position, so that the cues around it are
read. A file loaded beside the source (`MediaPlayer.loadSubtitles`) replaces the
timeline at once. A view asks `MediaPlayer.currentSubtitles()` for the cues at
the clock, as it asks for the picture.

**`media-player` draws them** as lines over the foot of the picture, out of flow
and outside the overlay, so they stay when the controls fade, and lower while
the controls are hidden. `media-player` and `media-controls` have a subtitles
menu: off, each track, and a loaded file. `audio-player`, which draws no
subtitles, has none.

## Alternatives considered

- **Bind FFmpeg's subtitle decoders.** Three structs and two functions more in
  the layout table, to arrive at the same ASS text, then strip it in Java
  anyway.
- **A decode thread and packet queue per subtitle track, as for audio and
  video.** A subtitle packet needs no decoding and no pacing: the clock decides
  what shows, and a list of cues answers that directly.
- **Draw cues with their styling.** Positions, colours and fonts belong to the
  subtitle author's player; §6 draws them in the theme's type, which is what makes
  them legible on every picture and consistent with the rest of the window.

## Consequences

- Text subtitles show from Matroska, WebM and MP4 tracks and from `.srt` and
  `.vtt` files, over any protocol a source can use.
- Bitmap subtitle tracks (PGS, DVB, VobSub) are listed and show nothing.
- A cue that started before the keyframe a choice seeks to is not read until the
  source is sought back past it; the next cue shows on time.
- New golden `media-player-subtitles`; no other golden changed.
