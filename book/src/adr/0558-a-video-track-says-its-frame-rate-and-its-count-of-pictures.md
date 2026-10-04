# ADR-0558: A video track says its frame rate and its count of pictures

- **Status:** Accepted
- **Date:** 2026-10-04
- **Relates to:** the Gwent clone's Goldberry issue GB-001

## Context

A survey of about 400 premium loops (H.264 568×820, 60 a second, 20 s) finds
outliers by two values: the frame rate and the length in pictures. A file at 30
or 59.94 a second loops with a hitch. `Track` and `TrackParams.Video` had
neither, and `FfmpegStructs` declared `AVStream.nb_frames`, `avg_frame_rate` and
`r_frame_rate`, and `AVCodecParameters.framerate`, as padding.

## Decision

**`TrackParams.Video.frameRate()` is an `Optional<FrameRate>`**: the average
rate, else the rate the codec's headers state, else the base rate FFmpeg finds
the timestamps on. FFmpeg's `0/0` is empty.

**`FrameRate` is a type of its own**, not `Rational`. `Rational` is a time base,
a tick of `num/den` seconds, and its conversions count ticks. A rate is the
reciprocal, and reading `60/1` as a time base is a one-minute tick. `FrameRate`
has `perSecond()`, `frameDuration()` and `framesIn(Duration)`. `framesIn` is the
count for a container that does not record one.

**`Track.frameCount()` is an `OptionalLong`**, from `AVStream.nb_frames` when it
is above zero, and for a video track only: for sound, FFmpeg counts packets. It
is on `Track` and not in the parameters because it belongs to the container, not
to the codec.

The six-component `Video` constructor and the nine-component `Track` constructor
stay, each leaving the new component empty.

**The four fields are named in `FfmpegStructs`, and the layout probe reports
them.** `ffmpeg_layout.c` lists them, and so does the committed macOS probe
output. On LP64 they lie where the Linux probe puts them.

## Consequences

- MP4 records the count, and Matroska and WebM do not. For the WebM loops the
  count is `frameRate.framesIn(duration)`.
- **A bindings jar with these fields refuses natives built before this change.**
  `FfmpegLayoutCheck` reports a field the probe does not, and FFmpeg does not
  load. The FFmpeg natives of every target have to be built again from this
  commit. The superbuild does that on every target in CI, and on a developer's
  machine `cmake --build media/build/ffmpeg/<target>/cmake --target install`
  writes the new `ffmpeg-layout.properties` without rebuilding FFmpeg.
