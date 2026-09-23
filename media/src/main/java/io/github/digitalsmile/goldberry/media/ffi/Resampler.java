package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.codec.AudioFrame;
import io.github.digitalsmile.goldberry.media.codec.SampleFormat;

/// Converts decoded audio to what the device plays: interleaved 32-bit float at
/// one rate and channel count (`docs/goldberry-media.md` §3, "Audio decode").
///
/// One `SwrContext`, rebuilt when the input's format, rate or channel count
/// changes. That happens in chained Ogg, and in a stream whose first frames
/// report a different layout from the rest. Rate, layout and format are converted
/// in one pass, so a provider never converts anything itself.
///
/// Owned by the audio decode thread.
public final class Resampler implements AutoCloseable {

    private final Ffmpeg ffmpeg;
    private final int outRate;
    private final int outChannels;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment holder = arena.allocate(ADDRESS);
    private final MemorySegment inPlanes = arena.allocate(ADDRESS, AvFrameView.DATA_POINTERS);
    private final MemorySegment outPlanes = arena.allocate(ADDRESS);
    private final MemorySegment inLayout = arena.allocate(FfmpegStructs.AV_CHANNEL_LAYOUT);
    private final MemorySegment outLayout = arena.allocate(FfmpegStructs.AV_CHANNEL_LAYOUT);
    private MemorySegment context = MemorySegment.NULL;
    private SampleFormat inFormat = SampleFormat.F32;
    private int inRate;
    private int inChannels;
    private boolean closed;

    /// A resampler to `rate` Hz, `channels` channels, interleaved f32.
    public Resampler(Ffmpeg ffmpeg, int rate, int channels) {
        if (rate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("rate " + rate + ", channels " + channels);
        }
        this.ffmpeg = Objects.requireNonNull(ffmpeg, "ffmpeg");
        this.outRate = rate;
        this.outChannels = channels;
        ffmpeg.util().channelLayoutDefault().call(outLayout, channels);
    }

    /// The output rate.
    public int rate() {
        return outRate;
    }

    /// The output channel count.
    public int channels() {
        return outChannels;
    }

    /// An upper bound on the samples per channel that converting `frame` writes,
    /// including what the resampler held back from earlier frames.
    public int capacityFor(AudioFrame frame) {
        configure(frame);
        return ffmpeg.check(
                "swr_get_out_samples", ffmpeg.swResample().getOutSamples().call(context, frame.samples()));
    }

    /// Converts `frame` into `out`, which holds `capacity` samples per channel of
    /// interleaved f32.
    ///
    /// @return the samples per channel written
    public int convert(AudioFrame frame, MemorySegment out, int capacity) {
        configure(frame);
        requireCapacity(out, capacity);
        for (var plane = 0; plane < frame.planes().size(); plane++) {
            inPlanes.setAtIndex(ADDRESS, plane, frame.planes().get(plane));
        }
        outPlanes.set(ADDRESS, 0, out);
        return ffmpeg.check(
                "swr_convert",
                ffmpeg.swResample().convert().call(context, outPlanes, capacity, inPlanes, frame.samples()));
    }

    /// Writes what the resampler held back, at the end of the stream.
    ///
    /// @return the samples per channel written
    public int drain(MemorySegment out, int capacity) {
        if (context.equals(MemorySegment.NULL)) {
            return 0;
        }
        requireCapacity(out, capacity);
        outPlanes.set(ADDRESS, 0, out);
        return ffmpeg.check(
                "swr_convert", ffmpeg.swResample().convert().call(context, outPlanes, capacity, MemorySegment.NULL, 0));
    }

    /// Drops what the resampler holds back: the Engine seeked.
    public void reset() {
        free();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        free();
        arena.close();
    }

    private void configure(AudioFrame frame) {
        if (closed) {
            throw new IllegalStateException("resampler closed");
        }
        if (!context.equals(MemorySegment.NULL)
                && frame.format() == inFormat
                && frame.sampleRate() == inRate
                && frame.channels() == inChannels) {
            return;
        }
        free();
        ffmpeg.util().channelLayoutDefault().call(inLayout, frame.channels());
        var constants = ffmpeg.constants();
        var result = ffmpeg.swResample()
                .allocSetOpts2()
                .call(
                        holder,
                        outLayout,
                        constants.avSampleFormat(SampleFormat.F32),
                        outRate,
                        inLayout,
                        constants.avSampleFormat(frame.format()),
                        frame.sampleRate(),
                        0,
                        MemorySegment.NULL);
        if (result < 0) {
            throw new MediaException(new MediaError.InvalidData("cannot convert " + frame.format() + " at "
                    + frame.sampleRate() + " Hz, " + frame.channels() + " channels: " + ffmpeg.describe(result)));
        }
        context = holder.get(ADDRESS, 0);
        ffmpeg.check("swr_init", ffmpeg.swResample().init().call(context));
        inFormat = frame.format();
        inRate = frame.sampleRate();
        inChannels = frame.channels();
    }

    private void requireCapacity(MemorySegment out, int capacity) {
        var needed = (long) capacity * outChannels * Float.BYTES;
        if (out.byteSize() < needed) {
            throw new IllegalArgumentException("an output of " + out.byteSize() + " bytes cannot hold " + capacity
                    + " samples of " + outChannels + " channels");
        }
    }

    private void free() {
        if (!context.equals(MemorySegment.NULL)) {
            holder.set(ADDRESS, 0, context);
            ffmpeg.swResample().free().call(holder);
            context = MemorySegment.NULL;
        }
    }
}
