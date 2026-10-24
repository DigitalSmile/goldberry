# ADR-0657: A network source fetches half a minute of itself ahead

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (the Java engine), ADR-0465 (network media is read through a cache)

## Context

`ffmpeg-out.md` phase 3 asks for "a buffering model for `Source` over HTTP
range requests; adaptive prefetch window". The buffering model was built
before the plan: `HttpIO` fetches ahead of the demuxer into a cache, resumes
by `Range`, and the seek bar shades what it holds. The window was not
adaptive. It was a byte count fixed when the source opened, 8 MB in a 32 MB
cache by default, and a byte count is a different stretch of time for every
stream: eight minutes of a 128 kbit/s podcast, three seconds of a 20 Mbit/s
film. The Engine's own high-water mark holds a second of packets in time, so
the film had about four seconds in hand, which one slow request on a mobile
connection drains.

## Decision

**Once a source is open and its bitrate is known, the Engine asks for half a
minute of it ahead.**

- `MediaIO.readAhead(long bytes)`, a default method that does nothing: a hint,
  which a local file and an application's own source may ignore.
- The Engine calls it once, after the container opens, with 30 s at the
  source's mean bitrate: its length over its duration
  (`engine.PrefetchWindow`). Nothing is asked of a live stream or a source
  whose length or duration is unknown.
- `HttpIO` keeps the window between its `Options.readAhead()`, which is now
  the floor, and its cache less the two chunks `Options` already keeps free,
  and wakes the fetcher, so a raise takes effect at once. A podcast fetches
  what it did before; the film fetches 30 MB, as much as the default cache
  holds.

## Alternatives

- **Measure throughput and size the window from it.** What a window is for is
  surviving the requests that are slow, which an average of the fast ones does
  not predict; and it adds a feedback loop to a class whose tests are about
  byte positions. Time at the stream's bitrate is the quantity a viewer feels.
- **Lower the window for a slow stream too.** It saves a few megabytes on a
  podcast and makes the first seek after a pause more likely to need a
  request. The floor stays where an application set it.
- **A bigger default cache.** Every player would hold 75 MB for a 20 Mbit/s
  film whether it plays one or not; an application that does can pass bigger
  `Options`, and the window grows into them.

## Consequences

- A source's window changes once, early; the seek bar's shaded stretch grows
  past where it stopped before on a fast stream.
- An application `MediaIO` that wraps `HttpIO` forwards the hint or loses it;
  the module's own wrapper does.
