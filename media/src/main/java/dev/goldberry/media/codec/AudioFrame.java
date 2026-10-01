package dev.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

/// A run of decoded audio samples.
///
/// @param format     how the samples are stored
/// @param sampleRate samples per second per channel
/// @param channels   the channel count, in FFmpeg's default order for that count
///                   (front left, front right, centre, …)
/// @param samples    samples per channel in this frame
/// @param planes     the data: one plane for an interleaved format, one per
///                   channel for a planar one, each at least
///                   [SampleFormat#planeSize] bytes
/// @param ptsNanos   when the first sample plays, or [Frame#NO_PTS]
public record AudioFrame(
        SampleFormat format, int sampleRate, int channels, int samples, List<MemorySegment> planes, long ptsNanos)
        implements Frame {

    public AudioFrame {
        Objects.requireNonNull(format, "format");
        if (sampleRate <= 0 || channels <= 0 || samples < 0) {
            throw new IllegalArgumentException(
                    "rate " + sampleRate + ", channels " + channels + ", samples " + samples);
        }
        planes = List.copyOf(planes);
        if (planes.size() != format.planes(channels)) {
            throw new IllegalArgumentException(format + " with " + channels + " channels has " + format.planes(channels)
                    + " planes, not " + planes.size());
        }
        var needed = format.planeSize(channels, samples);
        for (var plane : planes) {
            if (plane.byteSize() < needed) {
                throw new IllegalArgumentException("a plane of " + plane.byteSize() + " bytes cannot hold " + samples
                        + " samples of " + format + " (" + needed + " bytes)");
            }
        }
    }
}
