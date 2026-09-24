# 469. A video track is switched over the same frame queue

Date: 2026-09-23

## Status

Accepted. Phase 7 of `goldberry-media` (`docs/media-plan.md`): the video track
menu of `docs/goldberry-media.md` §6. It extends ADR-0467 from audio to video.

## Context

A source with several video tracks (camera angles, a signed version, a
different cut) plays its default one. ADR-0467 switches an audio track by
retiring the audio thread, starting one on the new track, and seeking to the
position. It refused video, because a video thread does not only own a decoder
and a packet queue: it fills the `FrameQueue` that views present from. That
queue also holds the picture on screen and the buffers handed out to views
(`VideoPicture` stays valid until two newer pictures are handed out). A new
queue per switch would blank every view until they asked again, and would lose
that promise. The old thread might also be blocked in `FrameQueue.obtain`,
waiting for room, and the only way to wake it was `abort()`, which ends the
queue for good.

## Decision

**A video switch is an audio switch, over the same frame queue.** The demux
thread:

1. checks that the track has a decoder, and keeps the playing one if not;
2. retires the video thread (a flag its waits and reports check), **releases the
   frame queue's waiters** (`FrameQueue.releaseWaiters()`: a waiting `obtain`
   returns null, and the queue carries on working), aborts the thread's packet
   queue, and joins it. The thread closes its decoder and converter on the way
   out and hands back its pending buffer;
3. selects the new stream in the demuxer, and starts a new video thread on a new
   packet queue and the **same** frame queue;
4. requests an accurate seek to where playback is.

As for audio, the seek does all the synchronising. It flushes the frame queue
to a new Serial, which drops what the old track queued and keeps the picture
shown up until the first picture of the new position replaces it. So a switch
is a cut from the old track's last picture to the new track's picture covering
the position, with no black frame between. A size change needs nothing more:
`obtain` already replaces a buffer that does not fit.

A retired thread reports nothing: no `videoReady`, `videoDone`,
`videoUnderrun`, picture length, or failure. A failure that comes from being
retired is not a failure of playback.

`Playback.select` now accepts a video track. It refuses cover art (an
attached-picture track), which the Engine never plays as video, and the
attachment and data kinds.

The track menus are shared: `Transport.trackMenu` builds both, and
`media-player` and `media-controls`, the widgets that go with a picture, add
`.media-video-track` for a source with two or more video tracks.
`audio-player` does not.

## Alternatives considered

- **A new frame queue per track.** It is simpler for the Engine, but every view
  would lose its picture at the switch, a handed-out picture's promise would
  end early, and `step` would find no picture to step from until the first
  arrives.
- **Keep one video thread and swap its decoder.** This was rejected for ADR-0467's
  reason: a provider's decoder is opened and closed on the thread that uses it.
  The thread would also have to tell the two tracks' packets apart across a
  Serial.
- **Switch without a seek, from the next keyframe of the new track.** The audio
  would not be touched. But the demuxer reads every selected stream from one
  position, and the new track's packets before the next keyframe have been read
  and discarded already. Waiting for its next keyframe could leave the old
  picture up for seconds.
- **Abort the frame queue and unabort it.** This would turn a one-way state into
  a two-way one that every other waiter would have to reason about. A release
  only wakes the waits in progress when it is called. A later `obtain` waits as
  before.

## Consequences

- `MediaPlayer.selectTrack` takes an audio, video or subtitle track, and
  `PlayerStatus.videoTrack` reports the switch.
- As with an audio switch, the audio restarts at the position when the seek's
  flush reaches the sink. Until then the sink plays what it held, so a few
  milliseconds of sound may be heard twice. This is the price of one seek being
  the whole of the synchronisation.
- A test that moves a `VirtualSink` by hand has to wait for the flush's
  `clear()` before it plays on, or it plays samples the flush then discards.
- The fixture `clip-two-angles.mkv` carries VP9 at 160×90 and VP8 at 96×54, so a
  switch shows in the picture's size alone.
- Fullscreen is now the only item of §6 still open, and it waits on `:core`.
