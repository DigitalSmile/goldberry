package dev.goldberry.natives.sdl.camera;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.layout.Layouts;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.calls.SdlCameraCalls;

/// One opened camera: its permission, its format, and its frames.
///
/// SDL's camera thread fills a few driver buffers; [#acquire()] lends the newest
/// as an [SdlCameraFrame], and closing that hands it back. A frame held keeps a
/// driver buffer from the camera, so the caller copies and closes at once.
///
/// [#acquire()] and the frame it lends belong to one thread: the frame's bytes
/// are readable on the thread that acquired it, and nowhere after it is closed.
/// The other methods are safe from any thread.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlCamera implements AutoCloseable {

    private final SdlCameraCalls calls;
    private final MemorySegment camera;
    private final int id;
    private int lent;
    private boolean closed;

    private SdlCamera(SdlCameraCalls calls, MemorySegment camera, int id) {
        this.calls = calls;
        this.camera = camera;
        this.id = id;
    }

    static SdlCamera open(SdlCameraCalls calls, int id, @Nullable SdlCameraSpec spec) {
        var sdl = Sdl.get();
        sdl.initializeSubsystems(List.of(SdlSubsystem.CAMERA));
        try (var arena = Arena.ofConfined()) {
            var asked = MemorySegment.NULL;
            if (spec != null) {
                asked = arena.allocate(Layouts.SDL_CAMERA_SPEC.layout());
                SdlCameras.write(spec, asked);
            }
            var camera = calls.openCamera().call(id, asked);
            if (MemorySegment.NULL.equals(camera)) {
                var reason = sdl.lastError();
                sdl.quitSubsystems(List.of(SdlSubsystem.CAMERA));
                throw new SdlException("SDL_OpenCamera", reason);
            }
            return new SdlCamera(calls, camera, id);
        }
    }

    /// The camera's id.
    public int id() {
        return id;
    }

    /// Whether the user has let this camera be used.
    public SdlCameraPermission permission() {
        ensureOpen();
        return SdlCameraPermission.of(calls.getCameraPermissionState().call(camera));
    }

    /// The format frames arrive in, after any conversion SDL does; empty
    /// until the camera is approved.
    public Optional<SdlCameraSpec> format() {
        ensureOpen();
        try (var arena = Arena.ofConfined()) {
            var spec = arena.allocate(Layouts.SDL_CAMERA_SPEC.layout());
            if (!calls.getCameraFormat().call(camera, spec)) {
                return Optional.empty();
            }
            return SdlCameras.read(spec);
        }
    }

    /// The newest frame, or empty when none has arrived since the last one.
    /// Close the frame to hand it back.
    public Optional<SdlCameraFrame> acquire() {
        ensureOpen();
        try (var arena = Arena.ofConfined()) {
            var timestamp = arena.allocate(JAVA_LONG);
            var surface = calls.acquireCameraFrame().call(camera, timestamp);
            if (MemorySegment.NULL.equals(surface)) {
                return Optional.empty();
            }
            var view = surfaceView(surface);
            var layout = Layouts.SDL_SURFACE;
            var format = view.get(JAVA_INT, layout.offsetOf("format"));
            var width = view.get(JAVA_INT, layout.offsetOf("w"));
            var height = view.get(JAVA_INT, layout.offsetOf("h"));
            var pitch = view.get(JAVA_INT, layout.offsetOf("pitch"));
            var pixels = view.get(ADDRESS, layout.offsetOf("pixels"));
            if (width <= 0 || height <= 0 || pitch <= 0 || MemorySegment.NULL.equals(pixels)) {
                calls.releaseCameraFrame().call(camera, surface);
                return Optional.empty();
            }
            synchronized (this) {
                lent++;
            }
            return Optional.of(new SdlCameraFrame(
                    this, surface, pixels, format, width, height, pitch, timestamp.get(JAVA_LONG, 0)));
        }
    }

    /// Hands `surface` back to SDL. Called once per frame by the frame.
    void release(MemorySegment surface) {
        synchronized (this) {
            lent--;
            if (closed) {
                // SDL_CloseCamera released every frame still out.
                return;
            }
        }
        calls.releaseCameraFrame().call(camera, surface);
    }

    /// Frames lent and not yet handed back.
    public synchronized int framesLent() {
        return lent;
    }

    /// Closes the camera, and releases its reference on the subsystem. A frame
    /// still lent is released with it. Idempotent.
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        calls.closeCamera().call(camera);
        Sdl.get().quitSubsystems(List.of(SdlSubsystem.CAMERA));
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("camera closed");
        }
    }

    // Restricted: the surface SDL returned, read at the size the layout probe
    // verified for SDL_Surface.
    @SuppressWarnings("restricted")
    private static MemorySegment surfaceView(MemorySegment surface) {
        return surface.reinterpret(Layouts.SDL_SURFACE.byteSize());
    }
}
