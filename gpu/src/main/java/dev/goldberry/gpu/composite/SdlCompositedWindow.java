package dev.goldberry.gpu.composite;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.render.mapped.InPlaceLayout;
import dev.goldberry.gpu.render.mapped.MappedRaster;
import dev.goldberry.gpu.render.staging.StagingBuffer;
import dev.goldberry.log.Logs;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.SdlGpuWindow;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPresentMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.PresentTimings;
import dev.goldberry.render.composite.CompositedWindow;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.PresentMode;

/// A window [SdlCompositor] claimed: each painted frame's damage uploaded into
/// the window's UI texture, its GPU layers rendered, then the composite pass
/// drawn into its swapchain texture and presented.
///
/// The UI texture is `B8G8R8A8_UNORM`, what the frame is painted in, so the
/// bytes go up unconverted. It is made at the frame's size and remade when the
/// size changes, and then the whole frame goes up. A damage-only upload keeps
/// the rest of the texture, which is what makes a blinking caret cost a caret.
///
/// Each layer renders into a texture of its own ([LayerTextures]), in a frame
/// submitted before the composite that samples them: one queue runs what is
/// submitted to it in order. That work is counted in the present's submit time.
///
/// **Painting in place.** Where the device's check at creation said so, the
/// window lends the frame loop a [MappedRaster] to paint into
/// ([#lendFrame]), and a present of that frame copies its damage straight out
/// of it, in a command buffer submitted ahead of the composite, with no copy
/// on the CPU. A frame of the caller's own is staged as before. A raster SDL
/// refuses the memory for puts the window on the staged path for good.
final class SdlCompositedWindow implements CompositedWindow {

    private static final Logger LOG = Logs.of(SdlCompositedWindow.class);

    private final SdlGpuDevice device;
    private final GpuDevice api;
    private final SdlGpuWindow window;
    private final StagingBuffer staging;
    private final UiComposite composite;
    private final LayerTextures layerTextures = new LayerTextures();
    /// How a frame painted in place is laid out, or null where frames are
    /// staged.
    private @Nullable InPlaceLayout inPlace;
    /// The frame painted in place, at the size last lent.
    private @Nullable MappedRaster raster;
    /// What [#lendFrame] last handed out, which [#present] knows by identity.
    private @Nullable PixelBuffer lent;
    private @Nullable SdlGpuTexture ui;
    /// The layers the last present drew, for [#capture] to draw again.
    private List<UiComposite.Layer> lastDrawn = List.of();
    private PresentTimings last = PresentTimings.NONE;
    private boolean closed;

    /// Whether the window system lost what the window showed since it was last
    /// presented to: [#exposed]. Cleared by the present that shows it again.
    private boolean stale;

    SdlCompositedWindow(
            SdlGpuDevice device, GpuDevice api, SdlGpuWindow window, StagingBuffer staging, UiComposite composite) {
        this(device, api, window, staging, composite, null);
    }

    /// A window that paints in place laid out as `inPlace` says, or stages
    /// its frames when it is null.
    SdlCompositedWindow(
            SdlGpuDevice device,
            GpuDevice api,
            SdlGpuWindow window,
            StagingBuffer staging,
            UiComposite composite,
            @Nullable InPlaceLayout inPlace) {
        this.device = device;
        this.api = api;
        this.window = window;
        this.staging = staging;
        this.composite = composite;
        this.inPlace = inPlace;
    }

    /// The raster at `size`, made for it when the size changed, where this
    /// window paints in place.
    @Override
    public Optional<PixelBuffer> lendFrame(PhysicalSize size) {
        if (closed) {
            throw new IllegalStateException("the window was given back to its surface");
        }
        var layout = inPlace;
        if (layout == null || size.isEmpty()) {
            return Optional.empty();
        }
        var current = raster;
        if (current == null || !current.size().equals(size)) {
            raster = null;
            lent = null;
            if (current != null) {
                current.close();
            }
            try {
                current = MappedRaster.create(device, size, layout);
            } catch (SdlException | IllegalArgumentException e) {
                LOG.warn("[GPU] no {} frame in upload memory ({}): the window stages its frames", size, e.getMessage());
                inPlace = null;
                return Optional.empty();
            }
            raster = current;
        }
        var pixels = current.lend();
        lent = pixels;
        return Optional.of(pixels);
    }

    /// Whether this window paints in place, for the tests.
    boolean paintsInPlace() {
        return inPlace != null;
    }

    @Override
    public void present(PixelBuffer frame, List<DamageRect> damage, List<GpuPlacement> layers) {
        if (closed) {
            throw new IllegalStateException("the window was given back to its surface");
        }
        var started = System.nanoTime();
        var width = frame.size().width();
        var height = frame.size().height();
        var previous = ui;
        SdlGpuTexture texture;
        boolean whole;
        if (previous != null && !previous.isClosed() && previous.width() == width && previous.height() == height) {
            texture = previous;
            whole = false;
        } else {
            if (previous != null) {
                previous.close();
            }
            texture = device.createTexture(
                    SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
            ui = texture;
            whole = true;
        }
        var regions = whole ? List.of(new SdlGpuRegion(0, 0, width, height)) : regions(damage);
        if (regions.isEmpty() && layers.isEmpty() && !stale) {
            // Nothing changed, and the swapchain still shows the last frame.
            layerTextures.retain(layers);
            last = PresentTimings.NONE;
            return;
        }
        // Layers may have changed with no damage at all -- a video's next
        // picture -- so with any on screen the window is composited again.
        var beforeLayers = System.nanoTime();
        var drawn = layerTextures.renderAll(api, layers);
        lastDrawn = drawn;
        var layered = System.nanoTime();
        var bytes = 0L;
        var own = frame == lent ? raster : null;
        var painted = own != null;
        if (own != null && !regions.isEmpty()) {
            // Painted in place: the copy goes in a command buffer of its own,
            // submitted ahead of the composite, and the next lend waits on it.
            for (var copied : own.upload(texture, regions)) {
                bytes += texture.byteSize(copied);
            }
        }
        var commands = device.acquireCommandBuffer();
        try {
            if (!painted && !regions.isEmpty()) {
                var staged = staging.stage(frame.pixels(), frame.stride(), 4, regions);
                try (var pass = commands.beginCopyPass()) {
                    for (var placed : staged.regions()) {
                        var region = placed.region();
                        // A whole frame may take fresh texture memory; a partial
                        // one must keep what the texture holds around the damage.
                        // The rows are as long as the staging laid them out:
                        // padded on Direct3D 12, so it copies them as they are.
                        pass.upload(staging.buffer(), placed.offset(), placed.pixelsPerRow(4), texture, region, whole);
                        bytes += texture.byteSize(region);
                    }
                }
            }
            var uploaded = System.nanoTime();
            var swapchain = commands.acquireSwapchainTexture(window);
            var acquired = System.nanoTime();
            if (swapchain.isPresent()) {
                // Shown again, so the window system has it back. Only here: a
                // minimised window has no swapchain texture, and it is still
                // owed the present when it comes back.
                stale = false;
                var target = swapchain.get();
                var format = target.format();
                if (format.isPresent()) {
                    composite.draw(commands, target, format.get(), texture, drawn);
                } else {
                    UiComposite.blit(commands, target, texture);
                }
            }
            commands.submit();
            var submitted = System.nanoTime();
            // The layers' rendering is a stage of its own: a `canvas3d`'s
            // renderer recording its passes is the application's time, not the
            // compositor's.
            last = new PresentTimings(
                    uploaded - layered,
                    acquired - uploaded,
                    (submitted - acquired) + (beforeLayers - started),
                    bytes,
                    true,
                    layered - beforeLayers);
        } finally {
            if (!commands.isFinished()) {
                // Something above threw with the buffer still recording. SDL
                // requires it to be submitted or cancelled, and the caller is
                // about to leave the GPU for good: submitting what was recorded
                // is the one of the two that is allowed after an acquire.
                try {
                    commands.submit();
                } catch (RuntimeException ignored) {
                    // The original failure is the one worth reporting.
                }
            }
        }
    }

    @Override
    public Optional<PixelBuffer> capture() {
        var texture = ui;
        if (closed || texture == null) {
            return Optional.empty();
        }
        var width = texture.width();
        var height = texture.height();
        var usages = EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER);
        try (var target = device.createTexture(SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, usages);
                var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, width * height * 4)) {
            var commands = device.acquireCommandBuffer();
            composite.draw(commands, target, SdlGpuTextureFormat.B8G8R8A8_UNORM, texture, lastDrawn);
            try (var pass = commands.beginCopyPass()) {
                pass.download(target, SdlGpuRegion.of(target), download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var mapped = download.map(false);
            try {
                var copy = ByteBuffer.allocateDirect(mapped.remaining()).order(mapped.order());
                copy.put(0, mapped, 0, mapped.remaining());
                return Optional.of(new PixelBuffer(
                        new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED, width * 4, copy));
            } finally {
                download.unmap();
            }
        }
    }

    /// The textures this window's layers render into, for the tests.
    LayerTextures layerTextures() {
        return layerTextures;
    }

    /// The non-empty damage rectangles, as regions.
    private static List<SdlGpuRegion> regions(List<DamageRect> damage) {
        var regions = new ArrayList<SdlGpuRegion>(damage.size());
        for (var rect : damage) {
            if (rect.width() > 0 && rect.height() > 0) {
                regions.add(new SdlGpuRegion(rect.x(), rect.y(), rect.width(), rect.height()));
            }
        }
        return regions;
    }

    /// The UI texture, for the tests that read it back; null before the first
    /// frame and after [#close].
    @Nullable
    SdlGpuTexture uiTexture() {
        return ui;
    }

    @Override
    public PresentTimings lastPresent() {
        return last;
    }

    @Override
    public boolean supports(PresentMode mode) {
        return window.supports(sdl(mode));
    }

    @Override
    public void presentMode(PresentMode mode) {
        window.setPresentMode(sdl(mode));
    }

    private static SdlGpuPresentMode sdl(PresentMode mode) {
        return switch (mode) {
            case VSYNC -> SdlGpuPresentMode.VSYNC;
            case MAILBOX -> SdlGpuPresentMode.MAILBOX;
            case IMMEDIATE -> SdlGpuPresentMode.IMMEDIATE;
        };
    }

    @Override
    public String driver() {
        return device.driver();
    }

    @Override
    public void exposed() {
        stale = true;
    }

    /// Whether the next [#present] shows the frame even with no damage, for
    /// the tests.
    boolean isStale() {
        return stale;
    }

    /// Releases the UI texture and gives the window back. Idempotent; does
    /// nothing once the device is gone, which gave the window back itself.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var own = raster;
        raster = null;
        lent = null;
        if (own != null) {
            try {
                own.close();
            } catch (RuntimeException e) {
                // A lost device: the window still goes back to its surface.
                LOG.debug("[GPU] waiting for the last copy out of the frame failed", e);
            }
        }
        var texture = ui;
        ui = null;
        if (texture != null) {
            texture.close();
        }
        layerTextures.close();
        window.close();
    }
}
