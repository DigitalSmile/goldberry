package io.github.digitalsmile.goldberry.gpu.render;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.log.Startup;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuPresentMode;
import io.github.digitalsmile.goldberry.render.composite.Claim;
import io.github.digitalsmile.goldberry.render.composite.Compositor;

/// `:gpu`'s [Compositor], which the sdl3 backend finds by `ServiceLoader`
/// (`docs/gpu-plan.md`, phase 3; ADR-0479).
///
/// Owns the process's one GPU device, made at the first claim with the options
/// [DeviceOptions] reads, never at start-up. A device that cannot be made is
/// remembered and never asked for again, and every window stays on the CPU.
/// The staging memory and the composite pass are the device's and shared by
/// every window claimed on it.
///
/// Public only because `ServiceLoader` instantiates it; its package is not
/// exported.
public final class SdlCompositor implements Compositor {

    private static final Logger LOG = Logs.of(SdlCompositor.class);

    /// How many frames the CPU records ahead of the GPU: SDL's default, stated,
    /// so it is this class's decision (`docs/gpu-plan.md`, phase 3).
    static final int FRAMES_IN_FLIGHT = 2;

    /// `goldberry.backend.vsync`, which the sdl3 backend reads for the window
    /// surface: `false` presents unpaced here too.
    static final String VSYNC_PROPERTY = "goldberry.backend.vsync";

    private @Nullable SdlGpuDevice device;
    private @Nullable StagingBuffer staging;
    private @Nullable UiComposite composite;
    private @Nullable String unavailable;
    private boolean closed;

    /// For `ServiceLoader`. Makes nothing: the device comes with the first claim.
    public SdlCompositor() {}

    @Override
    public Claim claim(SdlWindowHandle window) {
        if (closed) {
            return new Claim.Refused("the compositor is closed");
        }
        if (!open()) {
            return new Claim.Refused(unavailable == null ? "no GPU device" : unavailable);
        }
        var gpu = requireDevice();
        try {
            var claimed = gpu.claimWindow(window);
            if (!Boolean.parseBoolean(System.getProperty(VSYNC_PROPERTY, "true"))) {
                for (var mode : new SdlGpuPresentMode[] {SdlGpuPresentMode.MAILBOX, SdlGpuPresentMode.IMMEDIATE}) {
                    if (claimed.supports(mode)) {
                        claimed.setPresentMode(mode);
                        break;
                    }
                }
            }
            return new Claim.Claimed(new SdlCompositedWindow(gpu, claimed, requireStaging(), requireComposite()));
        } catch (SdlException | IllegalStateException e) {
            return new Claim.Refused(gpu.driver() + " would not claim the window: " + e.getMessage());
        }
    }

    /// The device, once a claim has made it, for the tests that read back what
    /// was uploaded to it.
    Optional<SdlGpuDevice> device() {
        return Optional.ofNullable(device);
    }

    /// Why there is no device, or empty when there is one or none was asked for
    /// yet.
    Optional<String> unavailable() {
        return Optional.ofNullable(unavailable);
    }

    /// The device, made the first time; whether there is one.
    private boolean open() {
        if (device != null) {
            return true;
        }
        if (unavailable != null) {
            return false;
        }
        var options = DeviceOptions.fromProperties();
        SdlGpuDevice made = null;
        var started = System.nanoTime();
        try {
            made = Startup.time("GPU device created", () -> SdlGpuDevice.create(options));
            made.setAllowedFramesInFlight(FRAMES_IN_FLIGHT);
            staging = new StagingBuffer(made);
            composite = new UiComposite(made);
            device = made;
            LOG.info(
                    "GPU device ready in {} ms: {}{}, taking {}; windows present through it",
                    (System.nanoTime() - started) / 1_000_000,
                    made.driver(),
                    options.debugMode() ? " with validation" : "",
                    made.shaderFormats());
            return true;
        } catch (SdlException | IllegalStateException | IllegalArgumentException e) {
            unavailable = "no GPU device (" + e.getMessage() + ")";
            LOG.warn(
                    "{}: windows present on the CPU. {} names a driver; -Dgoldberry.gpu=off stops asking",
                    unavailable,
                    DeviceOptions.DRIVER_PROPERTY);
            if (made != null) {
                made.close();
            }
            staging = null;
            composite = null;
            return false;
        }
    }

    private SdlGpuDevice requireDevice() {
        var current = device;
        if (current == null) {
            throw new IllegalStateException("no device");
        }
        return current;
    }

    private StagingBuffer requireStaging() {
        var current = staging;
        if (current == null) {
            throw new IllegalStateException("no device");
        }
        return current;
    }

    private UiComposite requireComposite() {
        var current = composite;
        if (current == null) {
            throw new IllegalStateException("no device");
        }
        return current;
    }

    /// Releases the composite pass and destroys the device, which gives back
    /// every window still claimed on it. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var current = device;
        device = null;
        if (current != null) {
            if (composite != null) {
                composite.close();
            }
            current.close();
        }
        composite = null;
        staging = null;
    }
}
