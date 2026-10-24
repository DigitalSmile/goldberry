package dev.goldberry.media.engine;

import dev.goldberry.media.codec.Frame;

/// Where a decoded run of audio samples goes in stream time.
///
/// **The samples run on.** A frame's time comes from its packet's, and a
/// container with a coarse timescale rounds those: Matroska's millisecond stamps
/// an Opus stream's codec delay of 6.5 ms as -7 ms and its second packet's 13.5
/// ms as 14 ms. Placed at its stated time, each frame would leave a gap or an
/// overlap of up to a millisecond against the last, and the audio clock, which
/// counts samples, would jump by it. So a frame whose stated time is within
/// [#JITTER_NANOS] of where the last one ended follows it directly.
///
/// **A real gap is honoured**: a frame further off than that is placed at its
/// own time, as is the first frame of a looping source's next pass, which the
/// seam trims or fills against the last pass.
final class AudioPlacement {

    /// How far a frame's stated time may be from the end of the last one and
    /// still follow it: a millisecond timescale's rounding on both stamps, and
    /// less than the shortest Opus frame, so a lost packet still shows.
    static final long JITTER_NANOS = 2_000_000L;

    private AudioPlacement() {}

    /// The stream time a frame's first sample plays at.
    ///
    /// @param statedNanos   the frame's own time, already moved by the passes of
    ///                      a looping source, or [Frame#NO_PTS]
    /// @param expectedNanos the stream time just past the last sample written
    /// @param resync        whether to take the stated time whatever it is: the
    ///                      first frame after a seam
    static long place(long statedNanos, long expectedNanos, boolean resync) {
        if (statedNanos == Frame.NO_PTS) {
            return expectedNanos;
        }
        if (resync) {
            return statedNanos;
        }
        return Math.abs(statedNanos - expectedNanos) <= JITTER_NANOS ? expectedNanos : statedNanos;
    }
}
