# 463. Video is converted as it is decoded, timed by the master clock, and painted when a picture falls due

Date: 2026-09-23

## Status

Accepted. Builds `docs/goldberry-media.md` §3's "Presentation" and "Master
clock" for phase 3 (CPU present), and refines them where building them showed
something the design did not say. Its smoothing of the SDL sink is refined by
[ADR-0485](0485-the-audio-clock-never-jumps-and-4k60-plays-every-picture.md): a
drain re-anchored at each pull jumped by up to a pull when one came early,
which passes over pictures at 60 fps. Follows
[ADR-0462](0462-media-audio-leaves-through-a-sink-and-sdl.md), whose audio clock
the pictures are timed against.

## Context

§3 has a video decode thread feeding a small frame queue, and a `video-view`
that "on each Goldberry frame tick takes the newest frame with pts ≤ master
clock". Four things were left open, and each decides whether video is correct,
cheap or testable:

- **What the queue holds.** A decoded frame is *borrowed*: its planes are the
  decoder's until its next call (§5's frame contract, as built in phase 2). A
  queue of decoded frames would pin the decoder, and a provider's frames cannot
  be pinned at all.
- **Who moves through the queue.** If only the view takes pictures, a player
  with no view on screen never reaches its end, and its decoder blocks for good.
- **When a view paints.** "Each frame tick" is each display refresh: 120 times
  a second on a ProMotion display, for 25 pictures. Measured in the showcase,
  that was 107 frames a second of render work while a 320×180 clip played.
- **Which clock.** The audio clock is the sink's queue, which SDL drains in
  pulls of 1024 samples, so it moves in 21 ms steps. A picture timed against
  steps is shown up to a step late.

## Decision

**The video thread converts every picture it keeps, as it decodes it, to
premultiplied BGRA** (swscale, one context per stream, `SWS_BITEXACT |
SWS_ACCURATE_RND`), into one of at most seven reusable direct buffers, and
queues it with its time. The borrowed frame is done with before the decoder is
called again, the paint only blits, and the bytes are the same on every CPU, so
a golden of a decoded picture is exact (§7, S5). Scaling happens at the blit,
where the size on screen is known.

**The queue presents against the master clock, for whoever asks**
(`FrameQueue.present`): the newest picture whose time has come is shown and the
ones it passed go back to the pool. The view asks when it paints, and the
decode thread asks while it waits for room, so the queue drains with no view.
A picture handed out to a view is kept from reuse until two newer ones have
been handed out, which covers a view drawing what it asked for in the frame it
asked in, even with two views on one player. The buffers are collected, not
freed, so a view that holds a picture too long sees newer pixels and never
freed memory.

**A view paints when the next picture falls due**, not every frame:
`MediaPlayer.untilNextPicture()` says how long until a picture not yet due
becomes due, and the widget's state sets a timer for then. A 25 fps video costs
25 frames a second, and a paused one none.

**The SDL sink drains its queue smoothly between pulls**, never by more than
the last pull took, and not at all while paused. The audio clock moves
continuously, as ffplay's does through its callback time. The virtual sink the
tests use is unchanged, so the tests stay exact.

**The master clock is the audio clock while there is audio, and a free-running
clock over the `MediaClock` otherwise**: for a source with no audio, and for a
video that outlasts its sound, which hands over at the audio's last position.
`MediaClock` is §3's Clock SPI, and a test that moves it by hand gets the same
picture at the same time on every run.

Around those: the frame contract gains `I010` (10-bit planar 4:2:0), which is
what dav1d and VP9 profile 2 produce, so the common 10-bit case is lent without
a copy; the built-in decoder converts anything else to I420. Pictures are
dropped before conversion only when late by a whole picture with another packet
waiting, so the last picture of a stream is always shown. Seeks come in two
modes, accurate and keyframe, and a paused player still decodes one picture
after each seek, which is what makes scrubbing show something (§7, S2).

## Alternatives considered

- **Queue decoded frames and convert at paint time.** The paint would do the
  conversion, on the UI thread, and the decoder could not reuse its buffers
  until the view had painted; a provider's frames cannot be held at all.
- **A presenter thread of its own**, sleeping until each picture is due. It
  would pace pictures, but it would still need to wake the UI to draw one, and
  the UI's own timer does both.
- **Keep the canvas-style "animating" loop.** Simplest, and 107 frames a second
  of render work for 25 pictures.
- **Smooth the clock in the Engine** rather than in the SDL sink. The Engine
  cannot tell a sink that pulls in steps from one that does not, and smoothing
  a virtual sink would make every clock test depend on timing.

## Consequences

- CPU present is phase 3's only path. GPU present (phase 4) uploads planes
  instead, and will want the queue to hold YUV rather than BGRA; the queue's
  slots are the place that changes.
- One swscale pass per shown picture at full size. At 1080p that is a few
  milliseconds on the video thread, which is where it belongs.
- `SWS_BITEXACT` turns off swscale's fastest paths. Exact goldens were judged
  worth it; a player that cannot keep up drops conversions, not decodes.
- The paced timer and the position poll are the only frames a playing view
  asks for. Measured in the showcase: 33 frames a second while a 25 fps clip
  plays, at about 1.5 ms each.
- A file whose chosen audio or video track has no decoder fails naming every
  such codec, before anything plays (§7, S7), rather than playing half of it.
