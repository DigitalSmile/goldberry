# 485. The audio clock never jumps, and 4K60 plays every picture

Date: 2026-09-25

## Status

Accepted. Closes `docs/gpu-plan.md`'s phase 6 measurement and
`docs/media-plan.md` phase 5's exit criterion: "4K60 VP9 without dropped frames
on GPU present". It refines
[ADR-0463](0463-video-is-converted-as-it-is-decoded-and-paced-by-the-picture.md)'s
smoothing of the SDL sink, and
[ADR-0474](0474-the-audio-clock-is-what-is-heard.md)'s latency counted in pulls.

## Context

The exit criterion needs a count of dropped pictures, and nothing counted them.
The Engine dropped pictures in two places:

- the video thread, which skips preparing a picture late by a whole picture
  (ADR-0463);
- the frame queue, which passes over a picture the clock has moved beyond
  before any view asked for it.

The first run of a minute of 4K60 VP9 was:

- decoded on VideoToolbox;
- shown by a `video-view` composited through the GPU (ADR-0484);
- in a 2560×1440 window at 120 Hz.

The video thread dropped nothing and the UI painted no late frame, yet **117 of
3599 pictures were passed over**. Recording each pass showed the reason: between
two asks by the view about 9.5 ms of wall time passed, and the stream clock
moved 21.3 ms. That is 1024 samples at 48 kHz, exactly one pull of SDL's audio
device.

Three faults were behind it:

- **The smoothing re-anchored at every pull.** ADR-0463 drained the queue from
  the moment the last pull was seen, capped at that pull. CoreAudio's pulls do
  not come evenly. When one came early, the clock jumped forward by the part of
  the last pull not yet drained, up to 21 ms. A picture lasts 16.7 ms at 60 fps.
  At 25–30 fps the jump hid inside a picture.
- **Latency counted the last pull, not a typical one.** ADR-0474 counts SDL's
  buffers as three pulls of the last pull's size. A reading that saw two pulls
  at once doubled that for a moment, and the clock swung by 64 ms and back.
- **The audio end and the queue were read apart.** The audio thread writes to
  the sink, then records the end it wrote to. A reading between the two found
  the queue grown and the end not yet moved: the clock went back by a packet,
  and the view's timer, set from that reading, woke a picture late.

## Decision

**The drain is a line that never jumps.** It lives in `DrainEstimate`, which
takes the time as an argument so that it is testable:

- **The measurement** is ADR-0463's: samples taken up to the last pull, plus what
  has played of that pull since.
- **The estimate** runs at the stream's rate. It is steered toward the
  measurement, faster or slower by at most 10%, in proportion to the error, and
  it is at full slew at 10 ms off.
- It never goes back, and it never gets more than a pull past what the device
  has taken, so a late pull stalls it rather than making it overshoot.
- It is set outright at the first pull after an open or a clear, and whenever it
  is more than 100 ms off, as after an underrun or pulls nobody read.
- Punctual pulls make the two the same line, so the latency still holds.

**Latency counts a typical pull**: the median of the last fifteen seen
(`typicalPull`).

**The audio end and the queue are written and read under one lock.**
`Playback.writeAudio` writes to the sink and records the end under the lock that
`audioClockNanos` reads both under. The sink's write does not block.

**The Engine counts its pictures.** `MediaPlayer.videoStatistics()` returns
`VideoStatistics`:

| Count | What it counts |
|-------|----------------|
| `decoded` | pictures decoded |
| `late` | dropped before being prepared |
| `passed` | queued, due, and replaced before any view was handed them |
| `shown` | handed out to a view, each counted once |

A seek's flushed pictures and an accurate seek's run-up to its target count
only as decoded. `dropped()` is `late + passed`.

**The run is a probe**, `:media:videoPresentProbe`:

- it plays the clip `media/src/test/fixtures/make-4k60.sh` makes: a minute of
  `testsrc2` at 3840×2160 and 60 fps, VP9 at about 17 Mb/s, with an Opus tone so
  the audio clock is the master;
- the clip is not committed, and the script writes it into `build/`;
- the probe reports the counts and each thread's CPU time per picture shown;
- the launcher's `frames:` and `presents:` lines follow;
- it logs through Logback, which only it has on its class path.

## Measured

On an M1 Pro, macOS, a 120 Hz display, a 2560×1440 window. VideoToolbox decodes
in another process, so its CPU is not counted here.

| One minute of 4K60 VP9 | GPU present, 8-bit | GPU present, 10-bit | CPU present, 8-bit |
|---|---|---|---|
| decoded / shown | 3600 / 3600 | 3600 / 3600 | 3598 / 2018 |
| dropped late / passed over | 0 / 0 | 0 / 0 | 1581 / 0 |
| video thread, a picture shown | 1.4 ms (9% of a core) | 2.0 ms (12%) | 13.9 ms (47%) |
| UI thread, a picture shown | 2.8 ms (17%) | 3.1 ms (19%) | 17.6 ms (59%) |
| process | 7.5 ms (46%) | 8.5 ms (51%) | 66.6 ms (224%) |
| frames painted, late | 4077, 0 | 4031, 0 | 5286, 0 |

GPU present, 8-bit, was clean on five runs after the fixes; the counts in the
table are the last. Before them it passed over 107–117 pictures a run. After the
first two fixes it still passed over 1–4, which the lock removed. The only pass
seen since came with a late UI frame (a 19 ms gap between paints), on one 10-bit
run of three.

## Alternatives considered

- **Smoothing in the Engine rather than the sink**, as ADR-0463 already argued
  against: the Engine cannot tell a sink that pulls from one that does not, and
  the virtual sink the tests use would stop being exact.
- **SDL's own playback position.** SDL 3.4 reports how much is queued, not a
  timestamped position. CoreAudio's `AudioQueueGetCurrentTime` would give one,
  but only on macOS, and bound beside SDL's own backend.
- **Waking the view on every display refresh**, at 120 Hz. That would hide a
  jump of the clock by asking more often, at twice ADR-0463's cost in frames.
  The clock is what was wrong.
- **D8's second step**, the video thread writing into mapped transfer buffers.
  At 2.8–3.1 ms of UI time per 4K picture, 17–19% of a core, nothing is dropped
  and no frame is late. It stays unbuilt until something shows a need.

## Consequences

- **Phase 5's exit criterion is met on this Mac,** and phase 6's measurement
  with it. Windows and Linux are measured when a host exists.
- **Every clip plays more smoothly**, not only 4K60. Pictures at any rate are
  timed against a clock without 21 ms steps.
- **The audio clock trails an early pull by up to about 100 ms** before it has
  caught up at 10% faster. On average it is where it was, and an A/V offset of a
  few milliseconds for a moment is below what anyone hears.
- **`VideoStatistics` is public API**, for a `hud` or an application's own
  diagnostics.
- **A regression test for the race.** It reads the position while a sink lingers
  a millisecond after each write. It fails 3 of 3 runs without the lock and
  passes with it.
