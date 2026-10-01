package dev.goldberry.gpu.composite;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.render.StagingBuffer;
import dev.goldberry.log.Logs;
import dev.goldberry.log.Startup;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlWindowHandle;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPresentMode;
import dev.goldberry.render.composite.Claim;
import dev.goldberry.render.composite.Compositor;
import dev.goldberry.render.composite.ReadbackSurface;

/// `:gpu`'s [Compositor], which the sdl3 backend finds by `ServiceLoader`
/// (`docs/gpu-plan.md`, phase 3; ADR-0479).
///
/// Owns the process's one GPU device, made at the first claim, or at the first
/// GPU layer a read-back surface renders, with the options [DeviceOptions]
/// reads; never at start-up. A device that cannot be made is remembered and
/// never asked for again: every window stays on the CPU, and every GPU layer
/// shows its painter's fallback. The staging memory and the composite pass are
/// the device's and shared by every window claimed on it, and so is the public
/// [GpuDevice] over it that layers render with (ADR-0481).
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
    private @Nullable GpuDevice api;
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
            return new Claim.Claimed(
                    new SdlCompositedWindow(gpu, requireApi(), claimed, requireStaging(), requireComposite()));
        } catch (SdlException | IllegalStateException e) {
            return new Claim.Refused(gpu.driver() + " would not claim the window: " + e.getMessage());
        }
    }

    /// A read-back surface on this compositor's device, which it makes when its
    /// first layer renders (ADR-0481).
    @Override
    public ReadbackSurface readback() {
        return new SdlReadbackSurface(this);
    }

    /// The device as the GPU API sees it, made the first time: empty when there
    /// is none and cannot be one, or this is closed.
    Optional<GpuDevice> api() {
        return open() ? Optional.of(requireApi()) : Optional.empty();
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
        if (closed || unavailable != null) {
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
            api = ApiAccess.device(made);
            device = made;
            LOG.info(
                    "[GPU] device ready in {} ms: {}{}, taking {}; windows and GPU layers use it",
                    (System.nanoTime() - started) / 1_000_000,
                    made.driver(),
                    options.debugMode() ? " with validation" : "",
                    made.shaderFormats());
            return true;
        } catch (SdlException | IllegalStateException | IllegalArgumentException e) {
            unavailable = "no GPU device (" + e.getMessage() + ")";
            LOG.warn(
                    "[CPU] {}: windows present on the CPU. {} names a driver; -Dgoldberry.gpu=off stops asking",
                    unavailable,
                    DeviceOptions.DRIVER_PROPERTY);
            if (made != null) {
                made.close();
            }
            staging = null;
            composite = null;
            api = null;
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

    private GpuDevice requireApi() {
        var current = api;
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
        api = null;
    }
}
