# ADR-0556: A looping source is read again ahead of its end, and joined at a seam

- **Status:** Accepted
- **Date:** 2026-10-04
- **Relates to:** the Gwent clone's Goldberry issue GB-003

## Context

A Gwent clone plays every premium card as a baked loop: 1,200 pictures at
60 a second, cut to join back onto its first picture. It plays up to 48 of them
at once on the board, and one with Opus sound in the inspect view. A
`MediaPlayer` had no loop mode. A source played to `ENDED`, and `seek(ZERO)` from
there flushed every queue, went through `BUFFERING`, and showed a stall at the
seam.

## Decision

**`MediaPlayer.setLooping(boolean)`, `Builder.looping(boolean)` and
`PlayerStatus.looping()`.** A looping player never reaches `ENDED`. The setting
is kept for the next source, as the rate is.

**The demux thread starts the source over at its end, without a flush.** When it
reads the end of a looping source, it seeks to zero and queues a
`PacketQueue.Item.Seam` in every queue in place of `End`. The seam carries the
offset of the passes after it: the passes so far, end to end. The queues fill
across the seam, as they do anywhere else, so the next pass is decoded before
the last one has been heard.

**A decode thread drains at the seam and moves on by the offset.** It sends the
end to its decoder, takes what is left, flushes it, and adds the offset to every
time after the marker. This is the same drain as at the end, so a decoder that
holds pictures back gives them up, whichever provider it is. The serial does not
change, so nothing is dropped and `VideoStatistics` counts on. The clock and the
pictures' times run on, and `positionNanos()` is the time within the pass that
is playing.

**The picture sets a pass's length** (`PassLength`): the end of the last
picture. When a packet has no duration, the spacing of the pictures stands in
for it. The sound is fitted to that length at the seam. What overlaps the last
pass is trimmed, and a gap is filled with silence. So the audio clock, which is
the master clock, runs on without a jump. With no picture, the sound's last
packet sets the length.

**A seek is in the source's time.** It resets the offset to zero. The engine's
own seeks (a track switch, a fallback) are mapped from the clock back into the
source.

**`media-controls`:** `L` toggles looping. A `.media-loop` button shows only
while the player loops, and pressing it stops the loop. A player that does not
loop looks as it did, so no golden changes.

## Consequences

- No stall at the seam. `LoopingPlaybackTest` plays every picture of three
  passes at its time, with and without sound, and the state never leaves
  `PLAYING`.
- An Opus track's last packet carries the encoder's end padding, and the length
  of a pass cannot be measured from the packets more closely than that. A source
  with sound only therefore loops with the padding, a few milliseconds of
  silence, at the seam. A loop with a picture is cut to the picture, and its
  sound is fitted to it.
- `setLooping(false)` takes effect at the next end the demux thread reads, up to
  the length of a queue (two seconds) ahead of what is heard. A source that
  cannot seek, or a live one, ends anyway.
- A picture's time in a looping player is on the playback's clock, not in the
  source: the second pass's first picture is at the pass's length. The position
  is in the source.
