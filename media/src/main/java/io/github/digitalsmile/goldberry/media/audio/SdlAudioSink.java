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
public final class SdlAudioSink implements AudioSink {

    private @Nullable SdlAudioStream stream;
    private float gain = 1f;

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
        return preferred;
    }

    @Override
    public void write(MemorySegment data, int samples) {
        var bytes = (long) samples * stream().channels() * Float.BYTES;
        stream().put(data.asSlice(0, bytes).asByteBuffer());
    }

    @Override
    public long queuedSamples() {
        var current = current();
        return current == null ? 0 : current.queuedFrames();
    }

    @Override
    public void clear() {
        stream().clear();
    }

    @Override
    public void pause() {
        var current = current();
        if (current != null) {
            current.pause();
        }
    }

    @Override
    public void resume() {
        var current = current();
        if (current != null) {
            current.resume();
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
