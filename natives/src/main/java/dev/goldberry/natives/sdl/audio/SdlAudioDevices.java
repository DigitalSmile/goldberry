package dev.goldberry.natives.sdl.audio;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.calls.SdlAudioCalls;
import dev.goldberry.natives.sdl.calls.SdlClipboardCalls;

/// SDL's audio devices, for `goldberry-mic`: the recording devices there are
/// now, their names and formats.
///
/// Holding one keeps SDL's audio subsystem up, which is reference-counted, so
/// the device list stays live and hot-plug events keep being queued while the
/// window loop pumps. **An id lasts only while the subsystem is up**: once the
/// last reference goes, SDL forgets its devices, and the ids the next start
/// hands out are new ones. So a caller holds this open for as long as it uses
/// an id it listed, the stream it opens on one included. Closing it releases the subsystem. Every method is safe
/// from any thread, and each reads SDL's list fresh: a device can come or go at
/// any moment.
///
/// Exported to `:mic` and to nobody else. No `MemorySegment` crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlAudioDevices implements AutoCloseable {

    private static final class Holder {
        private static final SdlAudioCalls CALLS =
                SdlAudioCalls.bind(NativeLibrary.get().lookup());
        private static final SdlClipboardCalls.Free FREE =
                SdlClipboardCalls.bind(NativeLibrary.get().lookup()).free();
    }

    /// Bound on a device name read back from SDL.
    private static final long MAX_NAME_LENGTH = 4096;

    private final SdlAudioCalls calls;
    private boolean closed;

    private SdlAudioDevices(SdlAudioCalls calls) {
        this.calls = calls;
    }

    /// Starts SDL's audio subsystem, or takes another reference on it.
    ///
    /// @throws dev.goldberry.natives.sdl.SdlException when SDL has no audio
    ///                                                driver to start
    public static SdlAudioDevices open() {
        Sdl.get().initializeSubsystems(List.of(SdlSubsystem.AUDIO));
        return new SdlAudioDevices(Holder.CALLS);
    }

    /// Whether `id` is the default recording device, the one that follows the
    /// system's choice rather than naming a device.
    public static boolean isDefaultRecording(int id) {
        return id == SdlAudioCalls.SDL_AUDIO_DEVICE_DEFAULT_RECORDING;
    }

    /// The id that opens whichever recording device the system has chosen,
    /// and keeps following it.
    public static int defaultRecording() {
        return SdlAudioCalls.SDL_AUDIO_DEVICE_DEFAULT_RECORDING;
    }

    /// The recording devices there are now, in SDL's order. Empty when SDL
    /// cannot list them.
    public List<SdlAudioDevice> recordingDevices() {
        ensureOpen();
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(JAVA_INT);
            var array = calls.getAudioRecordingDevices().call(count);
            if (MemorySegment.NULL.equals(array)) {
                return List.of();
            }
            try {
                var n = Math.max(0, count.get(JAVA_INT, 0));
                var ids = ids(array, n);
                var devices = new ArrayList<SdlAudioDevice>(n);
                for (var index = 0; index < n; index++) {
                    var id = ids.getAtIndex(JAVA_INT, index);
                    if (id != 0) {
                        devices.add(new SdlAudioDevice(id, name(id).orElse("")));
                    }
                }
                return List.copyOf(devices);
            } finally {
                Holder.FREE.call(array);
            }
        }
    }

    /// The name of device `id`, or empty for one SDL does not know (gone
    /// since it was listed).
    public Optional<String> name(int id) {
        ensureOpen();
        var name = calls.getAudioDeviceName().call(id);
        return MemorySegment.NULL.equals(name) ? Optional.empty() : Optional.of(readString(name));
    }

    /// The format device `id` runs at, or empty for one SDL does not know.
    public Optional<SdlAudioDeviceFormat> format(int id) {
        ensureOpen();
        try (var arena = Arena.ofConfined()) {
            var spec = arena.allocate(Layouts.SDL_AUDIO_SPEC.layout());
            var frames = arena.allocate(JAVA_INT);
            if (!calls.getAudioDeviceFormat().call(id, spec, frames)) {
                return Optional.empty();
            }
            var rate = spec.get(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("freq"));
            var channels = spec.get(JAVA_INT, Layouts.SDL_AUDIO_SPEC.offsetOf("channels"));
            if (rate <= 0 || channels <= 0) {
                return Optional.empty();
            }
            return Optional.of(new SdlAudioDeviceFormat(rate, channels, Math.max(0, frames.get(JAVA_INT, 0))));
        }
    }

    /// Releases this reference on the audio subsystem. Idempotent.
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Sdl.get().quitSubsystems(List.of(SdlSubsystem.AUDIO));
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("audio devices closed");
        }
    }

    // Restricted: SDL's list is exactly `count` ids and a zero, which is the
    // extent read here and nothing past it.
    @SuppressWarnings("restricted")
    private static MemorySegment ids(MemorySegment array, int count) {
        return array.reinterpret(JAVA_INT.byteSize() * count);
    }

    // Restricted: a bounded window over SDL's NUL-terminated string, so a
    // corrupt pointer cannot become an unbounded read.
    @SuppressWarnings("restricted")
    private static String readString(MemorySegment pointer) {
        return pointer.reinterpret(MAX_NAME_LENGTH).getString(0);
    }
}
