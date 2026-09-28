package io.github.digitalsmile.goldberry.media.audio;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.LongSupplier;

/// An [AudioSink] with no device behind it: what is written is played into
/// silence at the rate a device would play it, in wall time.
///
/// Where [SdlAudioSink] goes when the machine has no audio output: a headless
/// box, a container, or a desktop whose SDL has no backend for its sound server
/// (ADR-0487). A video with an audio track then plays silently, as a browser
/// plays it, instead of failing. The Engine cannot tell the difference. The
/// audio clock still comes from [#queuedSamples()], so pictures keep their
/// times, a seek and a rate apply, and a track that is audio only ends when its
/// last sample would have been heard.
///
/// Like a device, it plays only what it holds: once the queue runs dry it stands
/// still until the next write, rather than running ahead of what was written.
public final class SilentAudioSink implements AudioSink {

    private static final double NANOS_PER_SECOND = 1e9;

    private final LongSupplier nanoTime;

    private int sampleRate;
    private boolean open;
    private boolean playing;
    private float rate = 1f;
    /// Samples per channel written since the last clear.
    private long written;
    /// Samples per channel played since the last clear, fractional because the
    /// clock is read at any instant; never more than [#written].
    private double played;
    /// When [#played] was last brought up to date, on [#nanoTime].
    private long advancedAt;

    /// A sink timed by [System#nanoTime()].
    public SilentAudioSink() {
        this(System::nanoTime);
    }

    /// A sink timed by `nanoTime`, which a test moves by hand.
    public SilentAudioSink(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /// Takes `preferred` as it is, and plays at once, as an opened SDL stream does.
    @Override
    public synchronized AudioFormat open(AudioFormat preferred) {
        Objects.requireNonNull(preferred, "preferred");
        if (open) {
            throw new IllegalStateException("already open");
        }
        open = true;
        playing = true;
        sampleRate = preferred.sampleRate();
        written = 0;
        played = 0;
        advancedAt = nanoTime.getAsLong();
        return preferred;
    }

    @Override
    public synchronized void write(MemorySegment data, int samples) {
        requireOpen();
        advance();
        written += samples;
    }

    @Override
    public synchronized long queuedSamples() {
        if (!open) {
            return 0;
        }
        advance();
        return written - (long) Math.floor(played);
    }

    @Override
    public synchronized void clear() {
        requireOpen();
        advance();
        written = 0;
        played = 0;
    }

    @Override
    public synchronized void pause() {
        if (open) {
            advance();
            playing = false;
        }
    }

    @Override
    public synchronized void resume() {
        if (open) {
            advance();
            playing = true;
        }
    }

    /// Kept for nothing: there is no sound to make quieter.
    @Override
    public void setGain(float gain) {
        // Silence at any gain.
    }

    /// Plays that much faster, within the rates [SdlAudioSink] takes, so a
    /// player keeps the rates it had with a device.
    @Override
    public synchronized boolean setRate(float rate) {
        if (!(rate >= SdlAudioSink.MIN_RATE && rate <= SdlAudioSink.MAX_RATE)) {
            return false;
        }
        if (open) {
            advance();
        }
        this.rate = rate;
        return true;
    }

    @Override
    public synchronized void close() {
        open = false;
        playing = false;
    }

    /// Plays what the time since the last call allows, up to what was written.
    private void advance() {
        var now = nanoTime.getAsLong();
        if (playing) {
            var elapsed = now - advancedAt;
            played = Math.min((double) written, played + elapsed * (double) sampleRate * rate / NANOS_PER_SECOND);
        }
        advancedAt = now;
    }

    private void requireOpen() {
        if (!open) {
            throw new IllegalStateException("the sink is not open");
        }
    }
}
