package dev.goldberry.mic.source;

import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// A [SampleSource] that makes its samples from a [Signal], and delivers them
/// only when it is advanced, on the thread that advances it.
///
/// No device and no clock: [#advance] hands over the blocks that much time
/// holds, at once, so a test or a golden image of a level meter sees exactly
/// the same samples every run. Every channel carries the same signal, and a
/// block's [SampleBlock#nanoTime()] is the source's own clock, counted from
/// zero by the frames delivered.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class SyntheticSource implements SampleSource {

    private static final Logger LOG = Logs.of(SyntheticSource.class);

    private final SampleFormat format;
    private final int blockFrames;
    private final List<SampleListener> listeners = new CopyOnWriteArrayList<>();
    private final float[] block;
    private volatile Signal signal;
    private long position;
    private long nanoTime;
    /// Frames owed to the next block by earlier advances.
    private long pending;

    /// A source of `signal` in `format`, delivered `blockFrames` frames at a
    /// time.
    public SyntheticSource(SampleFormat format, Signal signal, int blockFrames) {
        this.format = Objects.requireNonNull(format, "format");
        this.signal = Objects.requireNonNull(signal, "signal");
        if (blockFrames < 1) {
            throw new IllegalArgumentException("blocks of " + blockFrames + " frames");
        }
        this.blockFrames = blockFrames;
        this.block = new float[blockFrames * format.channels()];
    }

    /// A source of `signal` in 48 kHz mono, in 10 ms blocks.
    public static SyntheticSource of(Signal signal) {
        return new SyntheticSource(SampleFormat.VOICE, signal, SampleFormat.VOICE.sampleRate() / 100);
    }

    @Override
    public SampleFormat format() {
        return format;
    }

    @Override
    public AutoCloseable onSamples(SampleListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /// Plays `signal` from the next block on, without starting it over.
    public void play(Signal signal) {
        this.signal = Objects.requireNonNull(signal, "signal");
    }

    /// Frames delivered so far.
    public synchronized long position() {
        return position;
    }

    /// Delivers the whole blocks `duration` holds, and answers how many frames
    /// that was. A remainder shorter than a block waits for the next call.
    public synchronized long advance(Duration duration) {
        var due = format.framesIn(duration.toNanos()) + pending;
        var whole = due / blockFrames;
        pending = due % blockFrames;
        for (long index = 0; index < whole; index++) {
            deliver();
        }
        return whole * blockFrames;
    }

    private void deliver() {
        var channels = format.channels();
        var current = signal;
        for (var frame = 0; frame < blockFrames; frame++) {
            var value = current.valueAt(position + frame, format.sampleRate());
            for (var channel = 0; channel < channels; channel++) {
                block[frame * channels + channel] = value;
            }
        }
        nanoTime += format.nanosOf(blockFrames);
        var view = FloatBuffer.wrap(block).asReadOnlyBuffer();
        var sample = new SampleBlock(view, format, blockFrames, position, nanoTime);
        position += blockFrames;
        for (var listener : listeners) {
            try {
                listener.onSamples(sample);
            } catch (RuntimeException e) {
                LOG.warn("a sample listener failed", e);
            }
        }
    }
}
