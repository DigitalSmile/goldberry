package dev.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Objects;

import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.SampleFormat;

/// Converts decoded audio to what the device plays: interleaved 32-bit float at
/// one rate and channel count. Java, no natives: a frame's samples are read,
/// mixed to the channel count, resampled when the rates differ, and written.
///
/// **Samples.** Every [SampleFormat] becomes a float in [−1, 1]: an unsigned
/// byte is offset binary, `(v − 128) / 128`; a signed integer of `bits` bits is
/// `v / 2^(bits − 1)`, the 64-bit one through a double; a float is itself and a
/// double is narrowed. Samples are read in the machine's byte order, which is
/// the order every decoder here writes. A planar format's channels are its
/// planes; an interleaved one's are strided through its single plane.
///
/// **Channels**, in FFmpeg's default order for the count ([AudioFrame]): mono
/// (FC); stereo (FL FR); 2.1 (FL FR LFE); 4.0 (FL FR FC BC); 5.0 (FL FR FC BL
/// BR); 5.1 (FL FR FC LFE BL BR); 6.1 (FL FR FC LFE BC SL SR); 7.1 (FL FR FC LFE
/// BL BR SL SR).
///
/// - the same count passes through;
/// - mono is duplicated into every output channel;
/// - anything to mono is the average of its channels;
/// - anything to stereo is the usual downmix: `L = FL + 0.707·FC + 0.707·BL +
///   0.707·SL + 0.5·BC`, `R` likewise with `FR`, `BR`, `SR`, the LFE dropped,
///   and the sum clamped to [−1, 1]. Past eight channels the order is not
///   defined, so the first two are taken as left and right;
/// - stereo, or anything else, to more channels copies the channels there are
///   into the first positions and leaves the rest silent.
///
/// **Rate.** When a frame's rate is the converter's, nothing. Otherwise a linear
/// interpolation between neighbouring input samples, which keeps its fractional
/// position and the last sample between calls, so a stream of frames resamples
/// as one signal and [#drain] writes the tail past the last sample. The SDL sink
/// is opened at the track's own rate, so this is the fallback for a sink that
/// answered another rate, not the path a clip plays through. Linear interpolation
/// is a low-pass of sorts, and aliases on a downsample; it is good enough for the
/// fallback and nothing more.
///
/// **Capacity.** [#convert] writes at most `capacity` samples per channel and
/// never fails for a frame that converts to more: what does not fit is held
/// back, and the next [#convert] or [#drain] writes it first, as swresample did.
/// [#capacityFor] says how much a frame can need, held samples included, so a
/// caller that sizes its buffer by it gets every sample at once.
///
/// Owned by the audio decode thread.
public final class PcmConverter implements AutoCloseable {

    /// `2^31` and `2^63`: what a 32-bit and a 64-bit integer sample is divided by.
    private static final double S32_FULL_SCALE = 0x1p31;
    private static final double S64_FULL_SCALE = 0x1p63;

    /// `1 / √2`: the centre's and the surrounds' share in a stereo downmix.
    private static final float SURROUND = (float) (1 / Math.sqrt(2));
    /// A back centre's share in each stereo channel: a surround's, split in two.
    private static final float BACK_CENTRE = 0.5f;

    /// The left channel's coefficient per input channel, indexed by the input
    /// channel count, for FFmpeg's default layouts of three to eight channels.
    private static final float[][] LEFT = {
        {}, // no channels
        {}, // mono: duplicated, not mixed
        {}, // stereo: passed through
        {1, 0, 0}, // 2.1: FL FR LFE
        {1, 0, SURROUND, BACK_CENTRE}, // 4.0: FL FR FC BC
        {1, 0, SURROUND, SURROUND, 0}, // 5.0: FL FR FC BL BR
        {1, 0, SURROUND, 0, SURROUND, 0}, // 5.1: FL FR FC LFE BL BR
        {1, 0, SURROUND, 0, BACK_CENTRE, SURROUND, 0}, // 6.1: FL FR FC LFE BC SL SR
        {1, 0, SURROUND, 0, SURROUND, 0, SURROUND, 0}, // 7.1: FL FR FC LFE BL BR SL SR
    };

    /// The right channel's coefficients, as [#LEFT].
    private static final float[][] RIGHT = {
        {},
        {},
        {},
        {0, 1, 0},
        {0, 1, SURROUND, BACK_CENTRE},
        {0, 1, SURROUND, 0, SURROUND},
        {0, 1, SURROUND, 0, 0, SURROUND},
        {0, 1, SURROUND, 0, BACK_CENTRE, 0, SURROUND},
        {0, 1, SURROUND, 0, 0, SURROUND, 0, SURROUND},
    };

    private final int outRate;
    private final int outChannels;

    /// A frame's samples as floats, interleaved at the frame's channel count.
    private float[] decoded = new float[0];
    /// The same, mixed to [#outChannels]; and the resampler's output after it.
    private float[] mixed = new float[0];
    private float[] resampled = new float[0];

    /// Output not yet written: interleaved, `held[heldStart, heldEnd)`.
    private float[] held = new float[0];
    private int heldStart;
    private int heldEnd;

    /// Where the last [#produce] or [#drain] left its output: [#mixed] or
    /// [#resampled].
    private float[] fresh = mixed;

    /// The resampler: the last input sample seen, mixed; where the next output
    /// sample falls after it, in input samples over [#outRate] (an integer, so
    /// that a second of samples is a second of samples and not one more); and
    /// the rate of the last frame, for the tail [#drain] writes.
    private final float[] previous;
    private boolean primed;
    private long phase;
    private int lastInRate;
    private boolean closed;

    /// A converter to `rate` Hz, `channels` channels, interleaved f32.
    public PcmConverter(int rate, int channels) {
        if (rate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("rate " + rate + ", channels " + channels);
        }
        this.outRate = rate;
        this.outChannels = channels;
        this.previous = new float[channels];
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
    /// including what the converter held back from earlier frames.
    public int capacityFor(AudioFrame frame) {
        Objects.requireNonNull(frame, "frame");
        requireOpen();
        var incoming = frame.sampleRate() == outRate
                ? frame.samples()
                : (int) Math.ceil((frame.samples() + 1) * (double) outRate / frame.sampleRate()) + 1;
        return Math.toIntExact(heldSamples() + (long) incoming);
    }

    /// Converts `frame` into `out`, which holds `capacity` samples per channel of
    /// interleaved f32: what was held back first, then the frame. What does not
    /// fit is held back for the next call.
    ///
    /// @return the samples per channel written
    /// @throws IllegalArgumentException when `out` is smaller than `capacity`
    ///                                  samples of the output format
    /// @throws IllegalStateException    after [#close()]
    public int convert(AudioFrame frame, MemorySegment out, int capacity) {
        Objects.requireNonNull(frame, "frame");
        requireOpen();
        requireCapacity(out, capacity);
        var produced = produce(frame);
        return deliver(out, capacity, produced);
    }

    /// Writes what the converter held back, and the resampler's tail past the
    /// last sample, at the end of the stream. Nothing after [#close()].
    ///
    /// @return the samples per channel written
    public int drain(MemorySegment out, int capacity) {
        if (closed) {
            return 0;
        }
        requireCapacity(out, capacity);
        var tail = 0;
        if (primed) {
            // The positions between the last sample and the one that never came:
            // the last sample, held.
            resampled = ensure(resampled, outRate / lastInRate + 2, outChannels);
            fresh = resampled;
            while (phase < outRate) {
                System.arraycopy(previous, 0, resampled, tail * outChannels, outChannels);
                tail++;
                phase += lastInRate;
            }
            primed = false;
            phase = 0;
        }
        return deliver(out, capacity, tail);
    }

    /// Drops what the converter holds back and the resampler's position: the
    /// Engine seeked.
    public void reset() {
        heldStart = 0;
        heldEnd = 0;
        primed = false;
        phase = 0;
    }

    /// Idempotent. A closed converter refuses to convert.
    @Override
    public void close() {
        closed = true;
        reset();
    }

    /// Reads, mixes and resamples `frame` into [#resampled] (or, at the
    /// converter's rate, [#mixed]).
    ///
    /// @return the samples per channel produced; they are in [#resampled] when
    ///         the rates differ and in [#mixed] otherwise
    private int produce(AudioFrame frame) {
        var samples = frame.samples();
        var inChannels = frame.channels();
        decoded = ensure(decoded, samples, inChannels);
        read(frame, decoded);
        mixed = ensure(mixed, samples, outChannels);
        mix(decoded, inChannels, mixed, samples);
        if (frame.sampleRate() == outRate) {
            // A rate change back to the converter's own leaves the interpolator's
            // last sample behind; it belongs to the old signal.
            primed = false;
            phase = 0;
            fresh = mixed;
            return samples;
        }
        return resample(samples, frame.sampleRate());
    }

    /// Linear interpolation of [#mixed] at `inRate` into [#resampled], carrying
    /// on from the sample and position the last call left. An output sample
    /// falls `phase / outRate` input samples after the previous input sample;
    /// each output moves it on by `inRate / outRate`, each input back by one.
    private int resample(int samples, int inRate) {
        lastInRate = inRate;
        var bound = (int) Math.ceil((samples + 1) * (double) outRate / inRate) + 1;
        resampled = ensure(resampled, bound, outChannels);
        fresh = resampled;
        var produced = 0;
        var from = 0;
        if (!primed) {
            if (samples == 0) {
                return 0;
            }
            System.arraycopy(mixed, 0, previous, 0, outChannels);
            primed = true;
            phase = 0;
            from = 1;
        }
        var scale = 1f / outRate;
        for (var i = from; i < samples; i++) {
            var next = i * outChannels;
            while (phase < outRate) {
                var t = phase * scale;
                var at = produced * outChannels;
                for (var c = 0; c < outChannels; c++) {
                    var a = previous[c];
                    resampled[at + c] = a + (mixed[next + c] - a) * t;
                }
                produced++;
                phase += inRate;
            }
            phase -= outRate;
            System.arraycopy(mixed, next, previous, 0, outChannels);
        }
        return produced;
    }

    /// Writes the held samples, then `produced` fresh ones from the buffer
    /// [#produce] or [#drain] filled, up to `capacity`; holds back the rest.
    private int deliver(MemorySegment out, int capacity, int produced) {
        var written = 0;
        var fromHeld = Math.min(heldSamples(), capacity);
        if (fromHeld > 0) {
            MemorySegment.copy(held, heldStart, out, JAVA_FLOAT, 0, fromHeld * outChannels);
            heldStart += fromHeld * outChannels;
            written += fromHeld;
        }
        var fromFresh = Math.min(produced, capacity - written);
        if (fromFresh > 0) {
            MemorySegment.copy(
                    fresh, 0, out, JAVA_FLOAT, (long) written * outChannels * Float.BYTES, fromFresh * outChannels);
            written += fromFresh;
        }
        var left = produced - fromFresh;
        if (left > 0) {
            hold(fresh, fromFresh * outChannels, left * outChannels);
        } else if (heldStart == heldEnd) {
            heldStart = 0;
            heldEnd = 0;
        }
        return written;
    }

    /// Appends `count` floats of `source` from `offset` to the held samples.
    private void hold(float[] source, int offset, int count) {
        var kept = heldEnd - heldStart;
        if (heldStart > 0) {
            System.arraycopy(held, heldStart, held, 0, kept);
            heldStart = 0;
            heldEnd = kept;
        }
        if (held.length < kept + count) {
            held = Arrays.copyOf(held, Math.max(kept + count, held.length * 2));
        }
        System.arraycopy(source, offset, held, heldEnd, count);
        heldEnd += count;
    }

    private int heldSamples() {
        return (heldEnd - heldStart) / outChannels;
    }

    /// `frame`'s samples as interleaved floats in `into`.
    private static void read(AudioFrame frame, float[] into) {
        var format = frame.format();
        var channels = frame.channels();
        var samples = frame.samples();
        if (format.planar()) {
            for (var c = 0; c < channels; c++) {
                readChannel(format, frame.planes().get(c), 0, 1, samples, into, c, channels);
            }
        } else {
            var plane = frame.planes().getFirst();
            for (var c = 0; c < channels; c++) {
                readChannel(format, plane, c, channels, samples, into, c, channels);
            }
        }
    }

    /// `count` samples of `format` from `plane`, the first at element `first` and
    /// each `stride` elements after the one before, into `into` at `at` and
    /// every `step` floats after.
    private static void readChannel(
            SampleFormat format,
            MemorySegment plane,
            int first,
            int stride,
            int count,
            float[] into,
            int at,
            int step) {
        switch (format) {
            case U8, U8_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] =
                            ((plane.get(JAVA_BYTE, (long) first + (long) i * stride) & 0xFF) - 128) / 128f;
                }
            }
            case S16, S16_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] =
                            plane.getAtIndex(JAVA_SHORT_UNALIGNED, (long) first + (long) i * stride) / 32768f;
                }
            }
            case S32, S32_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] = (float)
                            (plane.getAtIndex(JAVA_INT_UNALIGNED, (long) first + (long) i * stride) / S32_FULL_SCALE);
                }
            }
            case S64, S64_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] = (float)
                            (plane.getAtIndex(JAVA_LONG_UNALIGNED, (long) first + (long) i * stride) / S64_FULL_SCALE);
                }
            }
            case F32, F32_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] = plane.getAtIndex(JAVA_FLOAT_UNALIGNED, (long) first + (long) i * stride);
                }
            }
            case F64, F64_PLANAR -> {
                for (var i = 0; i < count; i++) {
                    into[at + i * step] =
                            (float) plane.getAtIndex(JAVA_DOUBLE_UNALIGNED, (long) first + (long) i * stride);
                }
            }
        }
    }

    /// `samples` interleaved samples of `inChannels` in `in`, to [#outChannels]
    /// in `out`.
    private void mix(float[] in, int inChannels, float[] out, int samples) {
        if (inChannels == outChannels) {
            System.arraycopy(in, 0, out, 0, samples * outChannels);
        } else if (inChannels == 1) {
            for (var i = 0; i < samples; i++) {
                Arrays.fill(out, i * outChannels, (i + 1) * outChannels, in[i]);
            }
        } else if (outChannels == 1) {
            for (var i = 0; i < samples; i++) {
                var sum = 0f;
                for (var c = 0; c < inChannels; c++) {
                    sum += in[i * inChannels + c];
                }
                out[i] = sum / inChannels;
            }
        } else if (outChannels == 2) {
            downmix(in, inChannels, out, samples);
        } else {
            var copied = Math.min(inChannels, outChannels);
            for (var i = 0; i < samples; i++) {
                System.arraycopy(in, i * inChannels, out, i * outChannels, copied);
                Arrays.fill(out, i * outChannels + copied, (i + 1) * outChannels, 0f);
            }
        }
    }

    /// `inChannels` (three or more) to stereo, by the coefficients in the class
    /// doc, clamped.
    private static void downmix(float[] in, int inChannels, float[] out, int samples) {
        if (inChannels >= LEFT.length) {
            for (var i = 0; i < samples; i++) {
                out[i * 2] = in[i * inChannels];
                out[i * 2 + 1] = in[i * inChannels + 1];
            }
            return;
        }
        var left = LEFT[inChannels];
        var right = RIGHT[inChannels];
        for (var i = 0; i < samples; i++) {
            var l = 0f;
            var r = 0f;
            for (var c = 0; c < inChannels; c++) {
                var sample = in[i * inChannels + c];
                l += sample * left[c];
                r += sample * right[c];
            }
            out[i * 2] = Math.clamp(l, -1f, 1f);
            out[i * 2 + 1] = Math.clamp(r, -1f, 1f);
        }
    }

    private static float[] ensure(float[] array, int samples, int channels) {
        var needed = Math.multiplyExact(samples, channels);
        return array.length >= needed ? array : new float[Math.max(needed, array.length * 2)];
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("converter closed");
        }
    }

    private void requireCapacity(MemorySegment out, int capacity) {
        Objects.requireNonNull(out, "out");
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity " + capacity);
        }
        var needed = (long) capacity * outChannels * Float.BYTES;
        if (out.byteSize() < needed) {
            throw new IllegalArgumentException("an output of " + out.byteSize() + " bytes cannot hold " + capacity
                    + " samples of " + outChannels + " channels");
        }
    }
}
