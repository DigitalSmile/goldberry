package dev.goldberry.gpu.offscreen;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.composite.SdlCompositor;
import dev.goldberry.log.Logs;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.render.composite.ReadbackSurface;
import dev.goldberry.render.window.GpuSurface;

/// A GPU for a picture with no window under it: what an application's test
/// hands to `Offscreen.gpu` so a `canvas3d` in the scene is rendered rather
/// than shown as its notice.
///
/// ```java
/// try (var gpu = OffscreenGpu.open()) {
///     var picture = Offscreen.of(800, 600).gpu(gpu.surface()).render(scene);
///     assertMatchesGolden("board", picture);
/// }
/// ```
///
/// The surface is the read-back kind, the one a window that presents on the
/// CPU has: each GPU layer the frame places is rendered on the device,
/// downloaded, and drawn into the picture, so the picture holds the layer's
/// pixels exactly as a composited window would show them. The device is made
/// here, on the calling thread, which everything made from it is confined to;
/// closing this closes the device and everything a renderer left on it.
///
/// The device needs SDL's video subsystem. Where no window has initialised it,
/// this does, under the driver `goldberry.gpu.videoDriver` names, or SDL's
/// default, or `offscreen` when the default cannot start -- a runner with no
/// display -- and quits it when closed.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#canvas3d).
public final class OffscreenGpu implements AutoCloseable {

    /// Names SDL's video driver, as the GPU tests' launcher does.
    public static final String VIDEO_DRIVER_PROPERTY = "goldberry.gpu.videoDriver";

    private static final Logger LOG = Logs.of(OffscreenGpu.class);

    private final SdlCompositor compositor;
    private final GpuDevice device;
    private final boolean ownsVideo;
    private @Nullable ReadbackSurface surface;
    private boolean closed;

    private OffscreenGpu(SdlCompositor compositor, GpuDevice device, boolean ownsVideo) {
        this.compositor = compositor;
        this.device = device;
        this.ownsVideo = ownsVideo;
    }

    /// Makes the device, initialising SDL's video first where nothing has.
    ///
    /// @throws IllegalStateException when no device can be made: the message
    ///                               says what the driver said
    public static OffscreenGpu open() {
        var ownsVideo = initialiseVideo();
        var compositor = new SdlCompositor();
        var device = compositor.api();
        if (device.isEmpty()) {
            var reason = compositor.unavailable().orElse("no GPU device");
            compositor.close();
            if (ownsVideo) {
                Sdl.get().quitSubsystems(EnumSet.of(SdlSubsystem.VIDEO));
            }
            throw new IllegalStateException("cannot render offscreen on the GPU: " + reason);
        }
        return new OffscreenGpu(compositor, device.get(), ownsVideo);
    }

    /// Initialises SDL's video subsystem when it is not yet, and says whether
    /// this call did.
    ///
    /// @throws IllegalStateException when it cannot start under any driver
    private static boolean initialiseVideo() {
        var sdl = Sdl.get();
        if (sdl.wasInit().contains(SdlSubsystem.VIDEO)) {
            return false;
        }
        var named = System.getProperty(VIDEO_DRIVER_PROPERTY, "").trim();
        if (!named.isEmpty()) {
            sdl.setHint(Sdl.VIDEO_DRIVER_HINT, named);
        }
        try {
            sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
            return true;
        } catch (SdlException first) {
            if (!named.isEmpty()) {
                throw new IllegalStateException(
                        "SDL's video cannot start under the " + named + " driver: " + first.getMessage(), first);
            }
            // No display, most likely: the offscreen driver needs none, and a
            // Vulkan or Metal device renders into textures without one.
            LOG.debug("SDL's default video driver did not start ({}); trying offscreen", first.getMessage());
            sdl.setHint(Sdl.VIDEO_DRIVER_HINT, "offscreen");
            try {
                sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
                return true;
            } catch (SdlException second) {
                var failure = new IllegalStateException(
                        "SDL's video cannot start: " + first.getMessage() + "; nor offscreen: " + second.getMessage(),
                        second);
                failure.addSuppressed(first);
                throw failure;
            }
        }
    }

    /// The surface for `Offscreen.gpu`: one per instance, made on first ask,
    /// and told what each render placed as a window's is.
    ///
    /// @throws IllegalStateException when this is closed
    public GpuSurface.ReadBack surface() {
        requireOpen();
        var current = surface;
        if (current == null) {
            current = compositor.readback();
            surface = current;
        }
        return current;
    }

    /// The device the surface renders on: what a renderer's `init` is given,
    /// for a test that makes resources ahead of the first render.
    ///
    /// @throws IllegalStateException when this is closed
    public GpuDevice device() {
        requireOpen();
        return device;
    }

    /// Whether [#close] has run.
    public boolean isClosed() {
        return closed;
    }

    /// Closes the surface, the device and what was made on it, and quits SDL's
    /// video when this started it. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var current = surface;
        surface = null;
        if (current != null) {
            current.close();
        }
        compositor.close();
        if (ownsVideo) {
            Sdl.get().quitSubsystems(EnumSet.of(SdlSubsystem.VIDEO));
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this offscreen GPU is closed");
        }
    }

    @Override
    public String toString() {
        return "OffscreenGpu[" + device.driver() + (closed ? ", closed]" : "]");
    }
}
