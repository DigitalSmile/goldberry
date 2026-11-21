package dev.goldberry.natives.sdl.camera;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/// One frame lent by an [SdlCamera]: SDL's surface, read in place.
///
/// [#pixels()] is a read-only view of the driver's buffer, valid on the thread
/// that acquired the frame and until [#close()]; reading it after that throws
/// rather than reading memory the driver has taken back. Close it as soon as
/// the bytes are copied, since a frame held is a buffer the camera cannot fill.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlCameraFrame implements AutoCloseable {

    private final SdlCamera camera;
    private final MemorySegment surface;
    private final Arena arena;
    private final ByteBuffer pixels;
    private final int format;
    private final int width;
    private final int height;
    private final SdlFrameLayout layout;
    private final long timestampNanos;
    private boolean closed;

    SdlCameraFrame(
            SdlCamera camera,
            MemorySegment surface,
            MemorySegment pixels,
            int format,
            int width,
            int height,
            int pitch,
            long timestampNanos) {
        this.camera = camera;
        this.surface = surface;
        this.format = format;
        this.width = width;
        this.height = height;
        this.layout = SdlFrameLayout.of(format, width, height, pitch);
        this.timestampNanos = timestampNanos;
        this.arena = Arena.ofConfined();
        this.pixels = view(pixels, layout.bytes(), arena);
    }

    /// The `SDL_PixelFormat` the bytes are in.
    public int format() {
        return format;
    }

    /// The format, when [SdlCameraPixelFormat] names it.
    public Optional<SdlCameraPixelFormat> pixelFormat() {
        return SdlCameraPixelFormat.of(format);
    }

    /// Pixels across.
    public int width() {
        return width;
    }

    /// Pixels down.
    public int height() {
        return height;
    }

    /// Where the planes are in [#pixels()].
    public SdlFrameLayout layout() {
        return layout;
    }

    /// When the camera took it, in nanoseconds of SDL's clock
    /// (`SDL_GetTicksNS`), or zero when the driver does not say.
    public long timestampNanos() {
        return timestampNanos;
    }

    /// The frame's bytes: every plane, read-only, from position zero.
    ///
    /// @throws IllegalStateException once the frame is closed, or from another
    ///                               thread than the one that acquired it
    public ByteBuffer pixels() {
        if (closed) {
            throw new IllegalStateException("camera frame released");
        }
        return pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    }

    /// Hands the frame back to the camera. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        arena.close();
        camera.release(surface);
    }

    // Restricted: the extent is what SDL allocated for a frame of this format,
    // size and pitch (SdlFrameLayout), scoped to an arena closed on release so
    // the view cannot outlive the buffer.
    @SuppressWarnings("restricted")
    private static ByteBuffer view(MemorySegment pixels, long bytes, Arena arena) {
        return pixels.reinterpret(bytes, arena, null).asByteBuffer().asReadOnlyBuffer();
    }
}
