# 465. Network media is read through a cache, and buffered to a high water mark

Date: 2026-09-23

## Status

Accepted. Phase 6 of `goldberry-media` (`docs/media-plan.md`), building
`docs/goldberry-media.md` §4 and scenarios S3 and S6.

## Context

FFmpeg is built with no network layer (ADR-0460), so every byte of an
`http:` or `https:` source has to come through a `MediaIO` written in Java. §4
asks for four things from it: Range requests for seeking, a read-ahead cache
that the seek bar's buffered ranges come from, reconnects that resume at the
last byte, and ICY radio metadata. It asks two things of the Engine: water
marks that move playback between BUFFERING and PLAYING, and a live source that
the widgets show as `LIVE` with what is playing.

Three questions had to be settled while building it:

- How a cache, a fetcher and a demuxer that seeks at will share one stream
  without a connection per seek.
- What the water marks are measured in, and what the low one is.
- Whether ICY needs a `MediaIO` of its own, as §4's table has it (`IcyIO`).

## Decision

**`HttpIO` is one `MediaIO` with a fetcher thread and a cache of extents.**

- The first request asks for `bytes=0-`. A `206` makes the stream seekable and
  says its length; a `200` means the server ignores `Range`.
- A virtual thread fetches into a `ReadAheadCache`: disjoint extents of 64 KB
  chunks, which merge when one grows into the next. The thread never calls
  native code, which is the only reason the Engine's own threads are platform
  threads (§3).
- The fetcher always fetches **the first byte the reader lacks**: the end of
  the extent holding the read position. It reads at most `readAhead` (8 MB) past
  the reader and then waits, which is the backpressure. When the reader moves so
  that the connection in hand no longer brings that byte (a seek outside the
  cache, or into a cached extent with a gap after it), the connection is
  abandoned and a Range request opens at the new place. A seek inside the cache
  opens nothing.
- Eviction keeps the cache under `cacheSize` (32 MB): other extents first,
  farthest from the reader first, then what the reader has already read of its
  own. Never the bytes ahead of it.
- A connection that fails, closes short of the length, or delivers nothing for
  `stallTimeout` (10 s) is made again after a doubling backoff, from the byte it
  broke at, up to `maxReconnects` in a row. A `4xx` other than `408` and `429` is
  final at once. A seek resets the count.
- A read waits no longer than the `Source`'s timeout.

**ICY is a property of the response, not a protocol.** A radio station is an
ordinary HTTP server that adds `icy-metaint` when asked with
`Icy-MetaData: 1`. `HttpIO` asks by default and, when the header comes back,
reads the body through `IcyStream`, which strips the metadata before the cache.
Titles are kept by the byte offset they took effect at, so `nowPlaying()`
answers for the demuxer's position rather than the fetcher's, which may be
megabytes ahead. There is no `IcyIO` class.

**`MediaIO` grows three default methods**: `isLive()`, `buffered()` (byte
ranges) and `nowPlaying()`. `FileIO` and every application protocol keep
working unchanged.

**The water marks are measured in demuxed time.** Each packet queue knows the
end time of the latest packet queued since the last flush. Less the clock, the
least of these over the playing tracks is `bufferedAhead`: what plays on if the
source stops delivering now.

- **High water mark**, `MediaPlayer.Builder.highWaterMark`, 1 s by default.
  BUFFERING becomes PLAYING only when every track is demuxed that far ahead, or
  the source has ended, or the queues are full and could not hold more. The same
  rule holds at the start and after a stall.
- **The low water mark is empty.** A track stalls when its decoder runs out of
  packets with the source not at its end. Only then does playback go back to
  BUFFERING, pausing the sink and holding the free-running clock.

**`bufferedRanges` maps bytes to time in proportion** to the source's length
and duration.

## Alternatives considered

- **One connection per seek, and no cache.** FFmpeg seeks to a container's tail
  on open (Matroska's Cues, MP4's `moov`) and back, so every open would cost two
  or three connections, and the seek bar would have nothing to show as buffered.
- **A cache of fixed blocks keyed by index.** A Range request lands at any
  offset, so its first and last blocks would be partial and need their own
  bookkeeping. Extents that start where the request did avoid it.
- **An `IcyIO` wrapping `HttpIO`.** A wrapper sees the bytes after the cache,
  so it would have to strip metadata from cached data on every read, and a seek
  would lose track of where the metadata falls. Stripping before the cache keeps
  offsets in audio bytes.
- **A low water mark above zero.** Pausing with media still in hand trades one
  long silence for a later one, and after every seek the queues are empty for a
  moment, so a local file would flicker through BUFFERING on each one.
- **Water marks in bytes.** Bytes do not say how long they play: 2 MB is two
  minutes of Opus and a second of 4K VP9.
- **Mapping bytes to time through the container's index.** Exact, but it would
  need `av_index_search_timestamp` and a per-format fallback. A proportion is
  exact for constant bit rates and close for the rest.

## Consequences

- S3 and S6 pass: against a local server that drops connections, stalls and
  speaks ICY; and against a fake `MediaIO` that stalls at an exact byte, so the
  BUFFERING, PLAYING and BUFFERING sequence is checked sample for sample.
- `PlayerStatus` gains `bufferedAhead`, `bufferedRanges`, `nowPlaying` and
  `live()`; `MediaInfo` gains `live`. The widgets show `LIVE` only for a live
  source. One that cannot seek but ends shows what remains, and the title is a
  line over the controls.
- FFmpeg's probe (`avformat_find_stream_info`) reads up to its default analyse
  duration before anything plays: about 4 s of PCM, less for compressed audio.
  Over a slow link that is start-up time. Lowering `probesize` for network
  sources is left open.
- The seek bar did not draw `bufferedRanges` at first: `slider` in `:widgets`
  had no second range to show. ADR-0466 gave it one.
