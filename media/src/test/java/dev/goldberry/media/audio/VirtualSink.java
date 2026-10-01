package dev.goldberry.media.audio;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

/// A sink with a virtual speaker: what the Engine writes is kept, and it is
/// "played" only when the test says so ([#advance]). In [#instant] mode it plays
/// the moment it is written.
///
/// This is the virtual clock of `docs/goldberry-media.md` §9, for audio. The
/// audio clock is the sink's queue, so a test that controls the queue controls
/// what the Engine believes is playing, with no sound card and no timing.
public final class VirtualSink implements AudioSink {

    private final AudioFormat format;
    private final boolean instant;
    private float[] captured = new float[0];
    private int capturedFloats;
    private long written;
    private long played;
    private int clears;
    private boolean paused;
    private float gain = 1f;
    private float rate = 1f;
    private long latencyNanos;
    private boolean closed;

    /// A sink of `format`; `instant` plays everything as it is written.
    public VirtualSink(AudioFormat format, boolean instant) {
        this.format = format;
        this.instant = instant;
    }

    @Override
    public synchronized AudioFormat open(AudioFormat preferred) {
        return format;
    }

    @Override
    public synchronized void write(MemorySegment data, int samples) {
        var floats = samples * format.channels();
        if (capturedFloats + floats > captured.length) {
            captured = Arrays.copyOf(captured, Math.max(captured.length * 2, capturedFloats + floats));
        }
        MemorySegment.copy(data, JAVA_FLOAT, 0, captured, capturedFloats, floats);
        capturedFloats += floats;
        written += samples;
        if (instant) {
            played = written;
        }
    }

    @Override
    public synchronized long queuedSamples() {
        return written - played;
    }

    @Override
    public synchronized void clear() {
        played = written;
        capturedFloats = 0;
        clears++;
    }

    @Override
    public synchronized void pause() {
        paused = true;
    }

    @Override
    public synchronized void resume() {
        paused = false;
    }

    @Override
    public synchronized void setGain(float gain) {
        this.gain = gain;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    /// Takes any rate: the test says what is played, at whatever speed.
    @Override
    public synchronized boolean setRate(float rate) {
        this.rate = rate;
        return true;
    }

    /// The rate the Engine last set.
    public synchronized float rate() {
        return rate;
    }

    /// A speaker `nanos` behind the queue, as a Bluetooth headset is: what
    /// [#latencyNanos()] reports from now on.
    public synchronized void latency(long nanos) {
        latencyNanos = nanos;
    }

    @Override
    public synchronized long latencyNanos() {
        return latencyNanos;
    }

    /// Plays `samples` more of what is queued.
    public synchronized void advance(long samples) {
        played = Math.min(written, played + samples);
    }

    /// Plays everything queued.
    public synchronized void playAll() {
        played = written;
    }

    /// Every sample written since the last [#clear()], interleaved.
    public synchronized float[] captured() {
        return Arrays.copyOf(captured, capturedFloats);
    }

    /// Samples per channel written since the last clear.
    public synchronized int capturedSamples() {
        return capturedFloats / format.channels();
    }

    public synchronized int clears() {
        return clears;
    }

    public synchronized boolean paused() {
        return paused;
    }

    public synchronized float gain() {
        return gain;
    }

    public synchronized boolean closed() {
        return closed;
    }
}
