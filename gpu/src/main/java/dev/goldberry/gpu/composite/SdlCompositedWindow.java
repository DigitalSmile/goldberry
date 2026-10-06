package dev.goldberry.gpu.composite;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.render.StagingBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.SdlGpuWindow;
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
final class SdlCompositedWindow implements CompositedWindow {

    private final SdlGpuDevice device;
    private final GpuDevice api;
    private final SdlGpuWindow window;
    private final StagingBuffer staging;
    private final UiComposite composite;
    private final LayerTextures layerTextures = new LayerTextures();
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
        this.device = device;
        this.api = api;
        this.window = window;
        this.staging = staging;
        this.composite = composite;
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
        var drawn = layerTextures.renderAll(api, layers);
        lastDrawn = drawn;
        var layered = System.nanoTime();
        var commands = device.acquireCommandBuffer();
        try {
            var bytes = 0L;
            if (!regions.isEmpty()) {
                var offsets = staging.stage(frame.pixels(), frame.stride(), 4, regions);
                try (var pass = commands.beginCopyPass()) {
                    for (var i = 0; i < regions.size(); i++) {
                        var region = regions.get(i);
                        // A whole frame may take fresh texture memory; a partial
                        // one must keep what the texture holds around the damage.
                        pass.upload(staging.buffer(), offsets[i], texture, region, whole);
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
            last = new PresentTimings(
                    uploaded - layered, acquired - uploaded, (submitted - acquired) + (layered - started), bytes, true);
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
        var texture = ui;
        ui = null;
        if (texture != null) {
            texture.close();
        }
        layerTextures.close();
        window.close();
    }
}
