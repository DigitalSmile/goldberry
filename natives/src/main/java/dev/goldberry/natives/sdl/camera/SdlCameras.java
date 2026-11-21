package dev.goldberry.natives.sdl.camera;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.calls.SdlCameraCalls;
import dev.goldberry.natives.sdl.calls.SdlClipboardCalls;

/// SDL's cameras, for `goldberry-camera`: the ones there are now, what each
/// offers, and opening one.
///
/// Holding one keeps SDL's camera subsystem up, which is reference-counted, so
/// the list stays live and hot-plug events keep being queued while the window
/// loop pumps. Closing it releases the subsystem; a camera opened through it
/// holds a reference of its own. Every method reads SDL's list fresh.
///
/// Exported to `:camera` and to nobody else. No `MemorySegment` crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlCameras implements AutoCloseable {

    private static final class Holder {
        private static final SdlCameraCalls CALLS =
                SdlCameraCalls.bind(NativeLibrary.get().lookup());
        private static final SdlClipboardCalls.Free FREE =
                SdlClipboardCalls.bind(NativeLibrary.get().lookup()).free();
    }

    /// Bound on a name read back from SDL.
    private static final long MAX_STRING_LENGTH = 4096;

    private final SdlCameraCalls calls;
    private boolean closed;

    private SdlCameras(SdlCameraCalls calls) {
        this.calls = calls;
    }

    /// Starts SDL's camera subsystem, or takes another reference on it.
    ///
    /// @throws SdlException when SDL has no camera driver to start
    public static SdlCameras open() {
        Sdl.get().initializeSubsystems(List.of(SdlSubsystem.CAMERA));
        return new SdlCameras(Holder.CALLS);
    }

    /// The driver the subsystem started with: `v4l2`, `pipewire`,
    /// `mediafoundation`, `coremedia`, `dummy`.
    public Optional<String> driver() {
        ensureOpen();
        var name = calls.getCurrentCameraDriver().call();
        return MemorySegment.NULL.equals(name) ? Optional.empty() : Optional.of(readString(name));
    }

    /// The cameras there are now, and what each offers. A camera gone between
    /// the list and the questions is left out.
    public List<SdlCameraInfo> cameras() {
        ensureOpen();
        try (var arena = Arena.ofConfined()) {
            var count = arena.allocate(JAVA_INT);
            var array = calls.getCameras().call(count);
            if (MemorySegment.NULL.equals(array)) {
                return List.of();
            }
            try {
                var n = Math.max(0, count.get(JAVA_INT, 0));
                var ids = resize(array, JAVA_INT.byteSize() * n);
                var cameras = new ArrayList<SdlCameraInfo>(n);
                for (var index = 0; index < n; index++) {
                    var id = ids.getAtIndex(JAVA_INT, index);
                    if (id != 0) {
                        describe(id, arena).ifPresent(cameras::add);
                    }
                }
                return List.copyOf(cameras);
            } finally {
                Holder.FREE.call(array);
            }
        }
    }

    /// Opens camera `id`, in `spec` (SDL converts to it) or, with null, in the
    /// mode SDL picks. The camera may not give frames until the user allows it:
    /// see [SdlCamera#permission()].
    ///
    /// @throws SdlException when SDL cannot open it
    public SdlCamera open(int id, @Nullable SdlCameraSpec spec) {
        ensureOpen();
        return SdlCamera.open(calls, id, spec);
    }

    private Optional<SdlCameraInfo> describe(int id, Arena arena) {
        var name = calls.getCameraName().call(id);
        if (MemorySegment.NULL.equals(name)) {
            return Optional.empty();
        }
        var position = SdlCameraPosition.of(calls.getCameraPosition().call(id));
        return Optional.of(new SdlCameraInfo(id, readString(name), position, formats(id, arena)));
    }

    private List<SdlCameraSpec> formats(int id, Arena arena) {
        var count = arena.allocate(JAVA_INT);
        var array = calls.getCameraSupportedFormats().call(id, count);
        if (MemorySegment.NULL.equals(array)) {
            return List.of();
        }
        try {
            var n = Math.max(0, count.get(JAVA_INT, 0));
            var pointers = resize(array, ADDRESS.byteSize() * n);
            var formats = new ArrayList<SdlCameraSpec>(n);
            for (var index = 0; index < n; index++) {
                var spec = pointers.getAtIndex(ADDRESS, index);
                if (!MemorySegment.NULL.equals(spec)) {
                    read(resize(spec, Layouts.SDL_CAMERA_SPEC.byteSize())).ifPresent(formats::add);
                }
            }
            return formats;
        } finally {
            Holder.FREE.call(array);
        }
    }

    /// The spec in `segment`, or empty for one with no size, which SDL never
    /// reports for a real mode.
    static Optional<SdlCameraSpec> read(MemorySegment segment) {
        var layout = Layouts.SDL_CAMERA_SPEC;
        var width = segment.get(JAVA_INT, layout.offsetOf("width"));
        var height = segment.get(JAVA_INT, layout.offsetOf("height"));
        var numerator = segment.get(JAVA_INT, layout.offsetOf("framerate_numerator"));
        var denominator = segment.get(JAVA_INT, layout.offsetOf("framerate_denominator"));
        if (width <= 0 || height <= 0 || numerator < 0 || denominator < 0) {
            return Optional.empty();
        }
        return Optional.of(new SdlCameraSpec(
                segment.get(JAVA_INT, layout.offsetOf("format")),
                segment.get(JAVA_INT, layout.offsetOf("colorspace")),
                width,
                height,
                numerator,
                denominator));
    }

    /// Writes `spec` into `segment`, an `SDL_CameraSpec`.
    static void write(SdlCameraSpec spec, MemorySegment segment) {
        var layout = Layouts.SDL_CAMERA_SPEC;
        segment.set(JAVA_INT, layout.offsetOf("format"), spec.format());
        segment.set(JAVA_INT, layout.offsetOf("colorspace"), spec.colorspace());
        segment.set(JAVA_INT, layout.offsetOf("width"), spec.width());
        segment.set(JAVA_INT, layout.offsetOf("height"), spec.height());
        segment.set(JAVA_INT, layout.offsetOf("framerate_numerator"), spec.framerateNumerator());
        segment.set(JAVA_INT, layout.offsetOf("framerate_denominator"), spec.framerateDenominator());
    }

    /// Releases this reference on the camera subsystem. Idempotent.
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Sdl.get().quitSubsystems(List.of(SdlSubsystem.CAMERA));
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("cameras closed");
        }
    }

    // Restricted: every extent here is SDL's own allocation of exactly that
    // many ids or pointers, or one SDL_CameraSpec the layout probe verified.
    @SuppressWarnings("restricted")
    private static MemorySegment resize(MemorySegment pointer, long bytes) {
        return pointer.reinterpret(bytes);
    }

    // Restricted: a bounded window over SDL's NUL-terminated string.
    @SuppressWarnings("restricted")
    private static String readString(MemorySegment pointer) {
        return pointer.reinterpret(MAX_STRING_LENGTH).getString(0);
    }
}
