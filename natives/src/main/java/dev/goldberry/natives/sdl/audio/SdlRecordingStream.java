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

/// One SDL audio stream on a recording device, giving interleaved 32-bit float:
/// `goldberry-mic`'s recording.
///
/// SDL's device thread fills the stream, converted to the rate and channel
/// count asked for; [#read] takes what is there. What crosses is values: a
/// direct [ByteBuffer] the samples are copied into, and frame counts.
///
/// Opening one initialises SDL's audio subsystem and closing it quits it again,
/// as [SdlAudioStream] does. Every method is safe from any thread: SDL locks
/// each stream.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlRecordingStream implements AutoCloseable {

    private static final class Holder {
        private static final SdlAudioCalls CALLS =
                SdlAudioCalls.bind(NativeLibrary.get().lookup());
    }

    private final SdlAudioCalls calls;
    private final MemorySegment stream;
    private final int device;
    private final int sampleRate;
    private final int channels;
    private boolean closed;

    private SdlRecordingStream(SdlAudioCalls calls, MemorySegment stream, int device, int sampleRate, int channels) {
        this.calls = calls;
        this.stream = stream;
        this.device = device;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /// Opens a stream of `sampleRate` Hz and `channels` channels on recording
    /// device `device`, or on [SdlAudioDevices#defaultRecording()], **paused**:
    /// nothing is recorded until [#resume]. A listed `device` must come from an
    /// [SdlAudioDevices] still open: SDL refuses an id from an earlier start.
    ///
    /// @throws SdlException when SDL cannot open the device, or refuses the
    ///                      format
    public static SdlRecordingStream open(int device, int sampleRate, int channels) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("rate " + sampleRate + ", channels " + channels);
        }
        if (device == 0 || device == SdlAudioCalls.SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK) {
            throw new IllegalArgumentException("not a recording device: " + Integer.toUnsignedString(device, 16));
        }
        var sdl = Sdl.get();
        sdl.initializeSubsystems(List.of(SdlSubsystem.AUDIO));
        var calls = Holder.CALLS;
        try (var arena = Arena.ofConfined()) {
            var spec = arena.allocate(Layouts.SDL_AUDIO_SPEC.layout());
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("format"), SdlAudioCalls.SDL_AUDIO_F32);
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("channels"), channels);
            spec.set(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("freq"), sampleRate);
            var stream = calls.openAudioDeviceStream().call(device, spec, MemorySegment.NULL, MemorySegment.NULL);
            if (stream.equals(MemorySegment.NULL)) {
                var reason = sdl.lastError();
                sdl.quitSubsystems(List.of(SdlSubsystem.AUDIO));
                throw new SdlException("SDL_OpenAudioDeviceStream", reason);
            }
            return new SdlRecordingStream(calls, stream, device, sampleRate, channels);
        }
    }

    /// The device it was opened on, as asked: possibly the default.
    public int device() {
        return device;
    }

    /// The rate samples are delivered at.
    public int sampleRate() {
        return sampleRate;
    }

    /// The channels per frame samples are delivered with.
    public int channels() {
        return channels;
    }

    /// Frames recorded and not yet read.
    public long availableFrames() {
        ensureOpen();
        var bytes = calls.getAudioStreamAvailable().call(stream);
        return bytes < 0 ? 0 : bytes / ((long) channels * Float.BYTES);
    }

    /// Copies up to as many whole frames as fit between `samples`' position and
    /// limit, and moves the position past them. Never waits.
    ///
    /// @return the frames read, zero when nothing is there yet
    /// @throws IllegalArgumentException when the buffer is not direct
    /// @throws SdlException             when SDL fails the read, as it does
    ///                                  once the device has gone away
    public int read(ByteBuffer samples) {
        Objects.requireNonNull(samples, "samples");
        if (!samples.isDirect()) {
            throw new IllegalArgumentException("SDL writes native memory: pass a direct buffer");
        }
        var frame = channels * Float.BYTES;
        var room = samples.remaining() / frame * frame;
        if (room == 0) {
            return 0;
        }
        ensureOpen();
        var target = MemorySegment.ofBuffer(samples);
        var bytes = calls.getAudioStreamData().call(stream, target, room);
        if (bytes < 0) {
            throw new SdlException("SDL_GetAudioStreamData", Sdl.get().lastError());
        }
        var whole = bytes / frame * frame;
        samples.position(samples.position() + whole);
        return whole / frame;
    }

    /// Drops everything recorded and not yet read.
    public void clear() {
        ensureOpen();
        check("SDL_ClearAudioStream", calls.clearAudioStream().call(stream));
    }

    /// Starts or resumes recording.
    public void resume() {
        ensureOpen();
        check("SDL_ResumeAudioStreamDevice", calls.resumeAudioStreamDevice().call(stream));
    }

    /// Stops recording, keeping what has been recorded.
    public void pause() {
        ensureOpen();
        check("SDL_PauseAudioStreamDevice", calls.pauseAudioStreamDevice().call(stream));
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
            throw new IllegalStateException("recording stream closed");
        }
    }

    private static void check(String function, boolean ok) {
        if (!ok) {
            throw new SdlException(function, Sdl.get().lastError());
        }
    }
}
