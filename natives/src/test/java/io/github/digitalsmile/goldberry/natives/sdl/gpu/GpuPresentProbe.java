package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag;

/// Phase 0's present measurements (`docs/gpu-plan.md`): what a frame costs on
/// today's window surface, what switching a window to a swapchain and back costs,
/// and what a composited frame costs, stage by stage.
///
/// Run by `:natives:gpuPresentProbe`, on the first thread, with a real window on
/// the screen. Not a test: the numbers are the machine's and the display's, and
/// they are read and written into the plan by a person. What *is* asserted, by
/// throwing, is that every switch succeeded and the window surface came back.
///
/// The UI frame is a CPU buffer the size of the window, changed a little every
/// frame, standing in for what Blend2D paints. Painting it is not timed; moving
/// it is.
final class GpuPresentProbe {

    private static final int FRAMES = 240;
    private static final int SWITCHES = 10;
    private static final int FRAMES_PER_SWITCH = 30;
    private static final int CARET_WIDTH = 200;
    private static final int CARET_HEIGHT = 40;

    private final SdlVideo video = SdlVideo.get();
    private final SdlWindowHandle window;
    private final SdlEventBuffer events = new SdlEventBuffer();
    private final int width;
    private final int height;

    private GpuPresentProbe(SdlWindowHandle window) {
        this.window = window;
        var pixels = video.windowSizeInPixels(window);
        this.width = pixels.width();
        this.height = pixels.height();
    }

    static void main(String[] args) {
        var sdl = Sdl.get();
        sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
        var video = SdlVideo.get();
        var window = video.createWindow(
                "goldberry: GPU present probe", 1280, 800, EnumSet.of(SdlWindowFlag.HIGH_PIXEL_DENSITY));
        video.showWindow(window);
        try (var device = SdlGpuDevice.create(SdlGpuDevice.Options.defaults())) {
            var probe = new GpuPresentProbe(window);
            probe.pumpFor(500);
            System.out.printf(
                    Locale.ROOT,
                    "video %s, GPU %s, window %dx%d px, display %.1f Hz%n%n",
                    sdl.videoDriver(),
                    device.driver(),
                    probe.width,
                    probe.height,
                    video.refreshRate(window));
            System.out.println("| Measurement | median | p95 | per frame |");
            System.out.println("|---|---|---|---|");
            probe.surfacePresent();
            probe.switchInAndOut(device);
            probe.composited(device, false);
            probe.composited(device, true);
            probe.scaledFromFourK(device);
        } finally {
            video.destroyWindow(window);
            sdl.quit();
        }
    }

    /// A: today's path. The frame is painted into SDL's surface and the whole
    /// window presented, as `Window.paint` does after a resize.
    private void surfacePresent() {
        var present = new long[FRAMES];
        var interval = new long[FRAMES];
        var damage = new int[] {0, 0, width, height};
        var size = new SdlVideo.SdlSize(width, height);
        var last = System.nanoTime();
        for (var frame = 0; frame < FRAMES; frame++) {
            var surface = video.acquireSurface(window);
            paintBand(surface.pixels(), surface.stride(), frame);
            var started = System.nanoTime();
            video.presentAcquired(window, size, damage);
            var done = System.nanoTime();
            present[frame] = done - started;
            interval[frame] = done - last;
            last = done;
            pump();
        }
        row("A. window surface: SDL_UpdateWindowSurfaceRects, whole window", present);
        row("A. window surface: frame interval", interval);
        video.invalidateSurface(window);
    }

    /// B: the switch D3 makes, and back, SWITCHES times.
    private void switchInAndOut(SdlGpuDevice device) {
        var in = new long[SWITCHES];
        var out = new long[SWITCHES];
        var damage = new int[] {0, 0, width, height};
        var size = new SdlVideo.SdlSize(width, height);
        for (var cycle = 0; cycle < SWITCHES; cycle++) {
            var started = System.nanoTime();
            video.invalidateSurface(window);
            var claimed = device.claimWindow(window);
            in[cycle] = System.nanoTime() - started;
            for (var frame = 0; frame < FRAMES_PER_SWITCH; frame++) {
                var commands = device.acquireCommandBuffer();
                commands.acquireSwapchainTexture(claimed)
                        .ifPresent(texture -> commands.clear(texture, 0.2f, 0.3f, 0.5f, 1f));
                commands.submit();
                pump();
            }
            started = System.nanoTime();
            claimed.close();
            var surface = video.acquireSurface(window);
            if (surface.width() != width || surface.height() != height) {
                throw new IllegalStateException("the surface came back " + surface.width() + "x" + surface.height());
            }
            paintBand(surface.pixels(), surface.stride(), cycle);
            video.presentAcquired(window, size, damage);
            out[cycle] = System.nanoTime() - started;
            pumpFor(50);
        }
        video.invalidateSurface(window);
        row("B. switch in: destroy window surface, claim", in);
        row("B. switch out: release, window surface, first present", out);
    }

    /// C and D: a composited frame, the UI uploaded whole or only a caret's
    /// worth, then blitted to the swapchain 1:1.
    private void composited(SdlGpuDevice device, boolean damageOnly) {
        var label = damageOnly
                ? "D. composited, damage only (" + CARET_WIDTH + "x" + CARET_HEIGHT + ")"
                : "C. composited, whole window";
        var frameBytes = width * height * 4;
        var ui = ByteBuffer.allocateDirect(frameBytes).order(ByteOrder.nativeOrder());
        var copy = new long[FRAMES];
        var record = new long[FRAMES];
        var acquire = new long[FRAMES];
        var submit = new long[FRAMES];
        var interval = new long[FRAMES];
        var uploadBytes = damageOnly ? CARET_WIDTH * CARET_HEIGHT * 4 : frameBytes;
        try (var claimed = device.claimWindow(window);
                var texture = device.createTexture(
                        SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
                var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, uploadBytes)) {
            var whole = new SdlGpuRegion(0, 0, width, height);
            var last = System.nanoTime();
            for (var frame = 0; frame < FRAMES; frame++) {
                paintBand(ui, width * 4, frame);
                var region = damageOnly
                        ? new SdlGpuRegion(
                                40, 40 + (frame * 7) % (height - CARET_HEIGHT - 80), CARET_WIDTH, CARET_HEIGHT)
                        : whole;
                var started = System.nanoTime();
                var mapped = upload.map(true);
                if (damageOnly) {
                    copyRect(ui, width * 4, region, mapped);
                } else {
                    mapped.put(ui.duplicate().clear());
                }
                upload.unmap();
                var copied = System.nanoTime();
                var commands = device.acquireCommandBuffer();
                try (var pass = commands.beginCopyPass()) {
                    // A whole frame may take fresh texture memory; a damage-only
                    // one must keep what the texture holds around the damage.
                    pass.upload(upload, 0, texture, region, !damageOnly);
                }
                var recorded = System.nanoTime();
                var swapchain = commands.acquireSwapchainTexture(claimed);
                var acquired = System.nanoTime();
                swapchain.ifPresent(target -> commands.blit(texture, whole, target, whole, SdlGpuFilter.NEAREST));
                commands.submit();
                var submitted = System.nanoTime();
                copy[frame] = copied - started;
                record[frame] = recorded - copied;
                acquire[frame] = acquired - recorded;
                submit[frame] = submitted - acquired;
                interval[frame] = submitted - last;
                last = submitted;
                pump();
            }
        }
        row(label + ": copy into the transfer buffer", copy);
        row(label + ": record the upload", record);
        row(label + ": wait for the swapchain texture", acquire);
        row(label + ": blit and submit", submit);
        row(label + ": frame interval", interval);
    }

    /// E: a 4K UI texture, uploaded whole and scaled to the window: the upload
    /// cost D8 sizes a 4K picture by.
    private void scaledFromFourK(SdlGpuDevice device) {
        var fourK = 3840 * 2160 * 4;
        var picture = ByteBuffer.allocateDirect(fourK).order(ByteOrder.nativeOrder());
        var copy = new long[FRAMES];
        var total = new long[FRAMES];
        try (var claimed = device.claimWindow(window);
                var texture = device.createTexture(
                        SdlGpuTextureFormat.B8G8R8A8_UNORM, 3840, 2160, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
                var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, fourK)) {
            var source = new SdlGpuRegion(0, 0, 3840, 2160);
            for (var frame = 0; frame < FRAMES; frame++) {
                paintBand(picture, 3840 * 4, frame);
                var started = System.nanoTime();
                var mapped = upload.map(true);
                mapped.put(picture.duplicate().clear());
                upload.unmap();
                var copied = System.nanoTime();
                var commands = device.acquireCommandBuffer();
                try (var pass = commands.beginCopyPass()) {
                    pass.upload(upload, 0, texture, source, true);
                }
                commands.acquireSwapchainTexture(claimed)
                        .ifPresent(target -> commands.blit(
                                texture, source, target, new SdlGpuRegion(0, 0, width, height), SdlGpuFilter.LINEAR));
                commands.submit();
                copy[frame] = copied - started;
                total[frame] = System.nanoTime() - started;
                pump();
            }
        }
        row("E. 3840x2160 BGRA: copy into the transfer buffer (33 MB)", copy);
        row("E. 3840x2160 BGRA: copy, upload, scale, present", total);
    }

    /// Changes a horizontal band of the frame, as a repaint would.
    private static void paintBand(ByteBuffer pixels, int stride, int frame) {
        var rows = pixels.capacity() / stride;
        var top = (frame * 13) % Math.max(1, rows - 16);
        var value = (byte) (frame * 3);
        for (var row = top; row < top + 16 && row < rows; row++) {
            for (var x = 0; x < stride; x += 4) {
                var at = row * stride + x;
                pixels.put(at, value);
                pixels.put(at + 1, (byte) 0x40);
                pixels.put(at + 2, (byte) 0x80);
                pixels.put(at + 3, (byte) 0xFF);
            }
        }
    }

    /// Packs `region` of a frame, row after row, into `target`.
    private static void copyRect(ByteBuffer frame, int stride, SdlGpuRegion region, ByteBuffer target) {
        var rowBytes = region.width() * 4;
        for (var row = 0; row < region.height(); row++) {
            var from = (region.y() + row) * stride + region.x() * 4;
            target.put(row * rowBytes, frame, from, rowBytes);
        }
    }

    private void pump() {
        while (video.pollEvent(events)) {
            // Nothing to handle: the window has to be kept responsive.
        }
    }

    private void pumpFor(long millis) {
        var until = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < until) {
            pump();
            Thread.onSpinWait();
        }
    }

    private static void row(String label, long[] nanos) {
        var sorted = nanos.clone();
        Arrays.sort(sorted);
        var median = sorted[sorted.length / 2] / 1e6;
        var p95 = sorted[(int) (sorted.length * 0.95)] / 1e6;
        var mean = Arrays.stream(nanos).average().orElse(0) / 1e6;
        System.out.printf(Locale.ROOT, "| %s | %.3f ms | %.3f ms | %.3f ms mean |%n", label, median, p95, mean);
    }
}
