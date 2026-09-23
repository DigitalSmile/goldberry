package io.github.digitalsmile.goldberry.media.audio;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.sdl.audio.SdlAudioStream;

/// The desktop's [AudioSink]: an SDL audio stream on the default playback device.
///
/// SDL is already inside `libgoldberry`, so this costs no second audio library
/// (`docs/goldberry-media.md` §3, "Audio decode"). The stream is fed interleaved
/// f32 at the rate and channel count the Engine asked for, and SDL converts to
/// whatever the device runs at. The format the Engine converts to is therefore
/// always the one it asked for, and the OS mixer, which runs at 48 kHz on every
/// desktop, does the last step.
///
/// The stream follows the default device: headphones plugged in mid-song take the
/// song with them.
///
/// ## A queue that drains smoothly
///
/// SDL's device takes samples in pulls, 1024 at a time at 48 kHz, so the raw
/// queue, and the audio clock made from it, moves in steps of 21 ms. A picture
/// timed against that clock is shown up to a step late, and a view that wakes when
/// the next picture falls due finds the clock not yet there and wakes again.
/// So between pulls [#queuedSamples()] reports the queue draining at the stream's
/// rate from the moment of the last pull, never by more than that pull took: what
/// the device is playing, rather than what it last fetched. ffplay corrects its
/// audio clock by the callback time for the same reason. Paused, the estimate
/// stands still, and a clear starts it over.
public final class SdlAudioSink implements AudioSink {

    private @Nullable SdlAudioStream stream;
    private float gain = 1f;
    /// The raw queue as last seen, plus what has been written since.
    private long lastRaw;
    /// How many samples the last pull took: the most the estimate drains by.
    private long pull;
    /// When the last pull was seen, on [System#nanoTime()].
    private long pulledAt;
    /// While paused, how far into the current pull the device had got.
    private long pausedAfter = -1;
    private int sampleRate;

    /// A sink that opens nothing until [#open].
    public SdlAudioSink() {}

    @Override
    public synchronized AudioFormat open(AudioFormat preferred) {
        Objects.requireNonNull(preferred, "preferred");
        if (stream != null) {
            throw new IllegalStateException("already open");
        }
        var opened = SdlAudioStream.open(preferred.sampleRate(), preferred.channels());
        opened.gain(gain);
        opened.resume();
        stream = opened;
        sampleRate = preferred.sampleRate();
        lastRaw = 0;
        pull = 0;
        pulledAt = System.nanoTime();
        pausedAfter = -1;
        return preferred;
    }

    /// Under the lock with the bookkeeping, so a reading between the put and the
    /// count cannot mistake the new samples for a pull.
    @Override
    public synchronized void write(MemorySegment data, int samples) {
        var bytes = (long) samples * stream().channels() * Float.BYTES;
        stream().put(data.asSlice(0, bytes).asByteBuffer());
        lastRaw += samples;
    }

    /// The samples written and not yet played, drained smoothly between the
    /// device's pulls (see the class note).
    @Override
    public synchronized long queuedSamples() {
        if (stream == null) {
            return 0;
        }
        var raw = stream.queuedFrames();
        var now = System.nanoTime();
        if (raw < lastRaw) {
            pull = lastRaw - raw;
            pulledAt = now;
        }
        lastRaw = raw;
        var after = pausedAfter >= 0 ? pausedAfter : now - pulledAt;
        var drained = Math.min(Math.round(after * (double) sampleRate / 1e9), pull);
        return Math.max(raw - drained, 0);
    }

    @Override
    public synchronized void clear() {
        stream().clear();
        lastRaw = 0;
        pull = 0;
        pulledAt = System.nanoTime();
        if (pausedAfter >= 0) {
            pausedAfter = 0;
        }
    }

    @Override
    public synchronized void pause() {
        if (stream != null) {
            stream.pause();
            if (pausedAfter < 0) {
                pausedAfter = System.nanoTime() - pulledAt;
            }
        }
    }

    @Override
    public synchronized void resume() {
        if (stream != null) {
            stream.resume();
            if (pausedAfter >= 0) {
                pulledAt = System.nanoTime() - pausedAfter;
                pausedAfter = -1;
            }
        }
    }

    @Override
    public synchronized void setGain(float gain) {
        this.gain = gain;
        if (stream != null) {
            stream.gain(gain);
        }
    }

    @Override
    public synchronized void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    private synchronized @Nullable SdlAudioStream current() {
        return stream;
    }

    private SdlAudioStream stream() {
        var current = current();
        if (current == null) {
            throw new IllegalStateException("the sink is not open");
        }
        return current;
    }
}
