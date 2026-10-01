package dev.goldberry.natives.sdl.audio;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.calls.SdlAudioCalls;

/// One SDL audio stream on the default playback device, fed interleaved 32-bit
/// float: `goldberry-media`'s audio sink (ADR-0461).
///
/// Exported to `:media` and to nobody else. What crosses is values: a direct
/// [ByteBuffer] in, which SDL copies from, and sample counts out. No
/// `MemorySegment` appears in a signature, which is ADR-0280's rule.
///
/// Opening one initialises SDL's audio subsystem, which is reference-counted, and
/// closing it quits it again, so a stream can be opened with or without a window.
/// Every method is safe from any thread: SDL locks each stream.
public final class SdlAudioStream implements AutoCloseable {

    private static final class Holder {
        private static final SdlAudioCalls CALLS =
                SdlAudioCalls.bind(NativeLibrary.get().lookup());
    }

    private final SdlAudioCalls calls;
    private final MemorySegment stream;
    private final int sampleRate;
    private final int channels;
    private boolean closed;

    private SdlAudioStream(SdlAudioCalls calls, MemorySegment stream, int sampleRate, int channels) {
        this.calls = calls;
        this.stream = stream;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /// Opens a stream of `sampleRate` Hz and `channels` channels on the default
    /// playback device, paused.
    ///
    /// @throws SdlException when SDL has no audio device to open, or refuses the
    ///                      format
    public static SdlAudioStream open(int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("rate " + sampleRate + ", channels " + channels);
        }
        var sdl = Sdl.get();
        sdl.initializeSubsystems(List.of(SdlSubsystem.AUDIO));
        var calls = Holder.CALLS;
        try (var arena = Arena.ofConfined()) {
            var spec = arena.allocate(Layouts.SDL_AUDIO_SPEC.layout());
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("format"), SdlAudioCalls.SDL_AUDIO_F32);
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("channels"), channels);
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("freq"), sampleRate);
            var stream = calls.openAudioDeviceStream()
                    .call(
                            SdlAudioCalls.SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK,
                            spec,
                            MemorySegment.NULL,
                            MemorySegment.NULL);
            if (stream.equals(MemorySegment.NULL)) {
                var reason = sdl.lastError();
                sdl.quitSubsystems(List.of(SdlSubsystem.AUDIO));
                throw new SdlException("SDL_OpenAudioDeviceStream", reason);
            }
            return new SdlAudioStream(calls, stream, sampleRate, channels);
        }
    }

    /// The rate the stream is fed at.
    public int sampleRate() {
        return sampleRate;
    }

    /// The channel count the stream is fed with.
    public int channels() {
        return channels;
    }

    /// Queues the samples between `samples`' position and limit: interleaved f32,
    /// a whole number of frames. SDL copies them; the buffer is the caller's again
    /// when this returns.
    ///
    /// @throws IllegalArgumentException when the buffer is not direct, or does not
    ///                                  hold whole frames
    public void put(ByteBuffer samples) {
        Objects.requireNonNull(samples, "samples");
        if (!samples.isDirect()) {
            throw new IllegalArgumentException("SDL reads native memory: pass a direct buffer");
        }
        var frame = channels * Float.BYTES;
        if (samples.remaining() % frame != 0) {
            throw new IllegalArgumentException(
                    samples.remaining() + " bytes is not a whole number of " + channels + "-channel frames");
        }
        ensureOpen();
        var data = MemorySegment.ofBuffer(samples);
        check("SDL_PutAudioStreamData", calls.putAudioStreamData().call(stream, data, samples.remaining()));
    }

    /// Frames put and not yet played.
    public long queuedFrames() {
        ensureOpen();
        var bytes = calls.getAudioStreamQueued().call(stream);
        return bytes < 0 ? 0 : bytes / ((long) channels * Float.BYTES);
    }

    /// Drops everything queued.
    public void clear() {
        ensureOpen();
        check("SDL_ClearAudioStream", calls.clearAudioStream().call(stream));
    }

    /// Starts or resumes the device.
    public void resume() {
        ensureOpen();
        check("SDL_ResumeAudioStreamDevice", calls.resumeAudioStreamDevice().call(stream));
    }

    /// Pauses the device, keeping what is queued.
    public void pause() {
        ensureOpen();
        check("SDL_PauseAudioStreamDevice", calls.pauseAudioStreamDevice().call(stream));
    }

    /// Linear gain applied by SDL, 0 and up.
    public void gain(float gain) {
        ensureOpen();
        check("SDL_SetAudioStreamGain", calls.setAudioStreamGain().call(stream, gain));
    }

    /// Plays the input `ratio` times as fast, by resampling it, so the pitch moves
    /// with the speed. SDL takes 0.01 to 100.
    public void frequencyRatio(float ratio) {
        ensureOpen();
        check(
                "SDL_SetAudioStreamFrequencyRatio",
                calls.setAudioStreamFrequencyRatio().call(stream, ratio));
    }

    /// Closes the stream and its device, and releases the audio subsystem.
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        calls.destroyAudioStream().call(stream);
        Sdl.get().quitSubsystems(List.of(SdlSubsystem.AUDIO));
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("audio stream closed");
        }
    }

    private static void check(String function, boolean ok) {
        if (!ok) {
            throw new SdlException(function, Sdl.get().lastError());
        }
    }
}
