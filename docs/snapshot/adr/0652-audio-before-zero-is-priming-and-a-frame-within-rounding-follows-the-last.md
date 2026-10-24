# ADR-0652: Audio before zero is priming, and a frame within rounding follows the last

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (media decodes with the reference codecs bound directly, and FFmpeg leaves)

## Context

Two timing faults came out of FFmpeg leaving `goldberry-media`, both in what
libavcodec used to do between a packet and the samples it decodes to.

**Priming.** An encoder puts samples before the music that a decoder needs and
a listener must not hear: an MP3's encoder delay plus the layer III decoder's
own 529 samples (1105 at 48 kHz for a LAME encode), AAC's 1024 or 2112, Opus's
pre-skip. FFmpeg's readers say how much with `skip_samples` side data on the
first packet, or by timing the first packets before zero, and libavcodec drops
them. With FFmpeg gone, the Java MP3 reader timed its first packet at 0 and
had no way to say "drop my first samples", so 23 ms of priming played at the
start of every MP3 with a LAME tag. The MP4 reader already handed an edit
list's priming over at negative times, and its doc said the Engine drops what
is before zero; the Engine did not, so AAC priming through the system decoders
played too. The Opus decoder drops its own pre-skip.

**Rounding.** A frame's time is its packet's, and Matroska's default timescale
is the millisecond. An Opus track's codec delay of 6.5 ms is stamped -7 ms, its
second packet's 13.5 ms is stamped 14 ms, and so on through the track. The audio
thread placed each decoded frame at its stated time and the audio clock counts
the samples written, so every rounding left a gap or an overlap of up to a
millisecond and the clock jumped by it. `LoopingPlaybackTest` caught it: after
two passes of a one-second WebM, the position was 2.5 ms ahead of the samples
played, against the millisecond the test allows.

## Decision

**Nothing before stream time zero plays.** The audio thread starts with its
discard point at zero, as an accurate seek to zero would set it, and drops
every sample before it.

**A container says "priming" with negative timestamps,** and only with them.
The MP3 reader, given a LAME tag, times its first packet at minus the encoder's
delay plus 529 samples, and a seek adds the same back before its frame
arithmetic. The MP4 reader already does this for an edit list. The padding at
the end stays where it was, in the last packet's duration.

**A frame within 2 ms of where the last one ended follows it directly.** The
audio thread places a frame at its stated time only when that is further off
than 2 ms, when the frame is the first after a looping source's seam, or when
it has no time; otherwise its first sample is the one after the last one
written. 2 ms covers a millisecond timescale's rounding of both stamps, and is
less than the shortest Opus frame, so a lost packet still shows as a gap.

## Alternatives

- **A "skip my first N samples" field on `Packet`**, FFmpeg's `skip_samples`.
  `ffmpeg-out.md` named it as the fix. It is a second way of saying what a
  negative timestamp already says, and every decoder, an application's
  included, would have to honour it; a provider that did not would play the
  priming. Negative timestamps need nothing of a decoder, and the Engine
  drops before zero whoever decoded.
- **Rounding every packet time to the sample in the reader.** The reader does
  not know the sample rate a decoder will produce, and the rounding is the
  container's, so every reader with a coarse timescale would carry the same
  code. The audio thread sees every frame and the samples it wrote, which is
  what the rule needs.
- **Ignoring stated times after the first frame.** A source with a real gap,
  or a provider that drops a damaged packet, would then drift from its
  pictures by the gap for the rest of the play.

## Consequences

- An MP3 with a LAME tag plays gapless: one second of tone is 48 000 samples
  from the sink, where it was 49 105. `Mp3Container`'s packet times differ from
  `ffprobe`'s by the priming, and its test moves them back before comparing.
- AAC in MP4 through AudioToolbox, GStreamer or Media Foundation no longer
  reaches the sink with the edit list's priming in front of it.
- A provider whose frames are stamped within 2 ms of contiguous is played
  contiguous; its stamps are not checked closer than that.
- The looping seam lands where the samples say: `LoopingPlaybackTest` holds
  the position to the millisecond after two passes.
