package io.github.digitalsmile.goldberry.gpu.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuBlend;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuFilter;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuLoad;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureUsage;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTransferUsage;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag;

/// Phase 0's other half (`docs/gpu-plan.md`, D1 and D8): a composited frame with
/// a 4K video layer under the UI, stage by stage, on a real window.
///
/// Each frame uploads a new 3840×2160 picture's planes, converts it with the
/// Y'CbCr shader into a letterboxed quad, uploads a caret's worth of UI damage,
/// and draws the UI over it premultiplied: the composited window with one GPU
/// layer, as phase 3 and phase 6 will build it. Run by `:gpu:gpuVideoProbe`, on
/// the first thread, and read by a person.
final class GpuVideoProbe {

    private static final int FRAMES = 240;
    private static final int VIDEO_WIDTH = 3840;
    private static final int VIDEO_HEIGHT = 2160;

    private GpuVideoProbe() {}

    static void main(String[] args) {
        var sdl = Sdl.get();
        sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
        var video = SdlVideo.get();
        var window = video.createWindow(
                "goldberry: GPU video probe", 1280, 800, EnumSet.of(SdlWindowFlag.HIGH_PIXEL_DENSITY));
        video.showWindow(window);
        try (var device = SdlGpuDevice.create(SdlGpuDevice.Options.defaults())) {
            var pixels = video.windowSizeInPixels(window);
            pumpFor(video, 500);
            System.out.printf(
                    Locale.ROOT,
                    "GPU %s, window %dx%d px, display %.1f Hz, video %dx%d%n%n",
                    device.driver(),
                    pixels.width(),
                    pixels.height(),
                    video.refreshRate(window),
                    VIDEO_WIDTH,
                    VIDEO_HEIGHT);
            System.out.println("| Measurement | median | p95 | mean |");
            System.out.println("|---|---|---|---|");
            for (var layout : new YuvLayout[] {YuvLayout.NV12, YuvLayout.P010}) {
                run(device, video, window, pixels.width(), pixels.height(), layout);
            }
        } finally {
            video.destroyWindow(window);
            sdl.quit();
        }
    }

    private static void run(
            SdlGpuDevice device, SdlVideo video, SdlWindowHandle window, int width, int height, YuvLayout layout) {
        var bytesPerSample = layout.bitDepth() > 8 ? 2 : 1;
        var lumaBytes = VIDEO_WIDTH * VIDEO_HEIGHT * bytesPerSample;
        var chromaBytes = lumaBytes / 2;
        var picture = ByteBuffer.allocateDirect(lumaBytes + chromaBytes).order(ByteOrder.nativeOrder());
        var caret = new SdlGpuRegion(40, 40, 200, 40);
        var caretBytes = caret.width() * caret.height() * 4;

        var copy = new long[FRAMES];
        var record = new long[FRAMES];
        var acquire = new long[FRAMES];
        var submit = new long[FRAMES];
        var interval = new long[FRAMES];

        var planes = new ArrayList<SdlGpuTexture>();
        var sampled = Set.of(SdlGpuTextureUsage.SAMPLER);
        try (var claimed = device.claimWindow(window);
                var quad = ShaderLibrary.create(device, BuiltInShader.QUAD_VERTEX);
                var yuv = ShaderLibrary.create(device, layout.shader());
                var textureShader = ShaderLibrary.create(device, BuiltInShader.TEXTURE_FRAGMENT);
                var sampler = device.createSampler(SdlGpuFilter.LINEAR);
                var nearest = device.createSampler(SdlGpuFilter.NEAREST);
                var ui = device.createTexture(
                        SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
                var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, lumaBytes + chromaBytes);
                var uiUpload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, caretBytes)) {
            var format = claimed.textureFormat().orElseThrow();
            var videoPipeline = device.createGraphicsPipeline(quad, yuv, format, SdlGpuBlend.REPLACE);
            var uiPipeline = device.createGraphicsPipeline(quad, textureShader, format, SdlGpuBlend.PREMULTIPLIED_OVER);
            for (var plane = 0; plane < layout.planeFormats().size(); plane++) {
                planes.add(device.createTexture(
                        layout.planeFormats().get(plane),
                        layout.planeWidth(plane, VIDEO_WIDTH),
                        layout.planeHeight(plane, VIDEO_HEIGHT),
                        sampled));
            }
            var conversion = new YuvConversion(layout, YuvMatrix.BT709, false).uniforms(VIDEO_WIDTH / 2);
            // Letterboxed: 16:9 into the window's width.
            var videoHeight = width * VIDEO_HEIGHT / VIDEO_WIDTH;
            var videoQuad = Quad.of(0, (height - videoHeight) / 2, width, videoHeight, width, height)
                    .uniforms();
            var uiQuad = Quad.of(0, 0, width, height, width, height).uniforms();
            var events = new SdlEventBuffer();
            var last = System.nanoTime();
            for (var frame = 0; frame < FRAMES; frame++) {
                picture.put(frame % picture.capacity(), (byte) frame);
                var started = System.nanoTime();
                var mapped = upload.map(true);
                mapped.put(picture.duplicate().clear());
                upload.unmap();
                var caretMapped = uiUpload.map(true);
                caretMapped.put(0, (byte) frame);
                uiUpload.unmap();
                var copied = System.nanoTime();

                var commands = device.acquireCommandBuffer();
                try (var pass = commands.beginCopyPass()) {
                    pass.upload(upload, 0, planes.get(0), SdlGpuRegion.of(planes.get(0)), true);
                    var offset = lumaBytes;
                    for (var plane = 1; plane < planes.size(); plane++) {
                        var texture = planes.get(plane);
                        pass.upload(upload, offset, texture, SdlGpuRegion.of(texture), true);
                        offset += (int) texture.byteSize(SdlGpuRegion.of(texture));
                    }
                    pass.upload(uiUpload, 0, ui, caret, false);
                }
                var recorded = System.nanoTime();
                var swapchain = commands.acquireSwapchainTexture(claimed);
                var acquired = System.nanoTime();
                swapchain.ifPresent(target -> {
                    try (var pass = commands.beginRenderPass(target, SdlGpuLoad.clear(0, 0, 0, 1))) {
                        pass.bindPipeline(videoPipeline);
                        pass.bindFragmentSamplers(sampler, planes.toArray(SdlGpuTexture[]::new));
                        pass.pushVertexUniforms(0, videoQuad);
                        pass.pushFragmentUniforms(0, conversion);
                        pass.draw(Quad.VERTICES);
                        pass.bindPipeline(uiPipeline);
                        pass.bindFragmentSamplers(nearest, ui);
                        pass.pushVertexUniforms(0, uiQuad);
                        pass.draw(Quad.VERTICES);
                    }
                });
                commands.submit();
                var submitted = System.nanoTime();
                copy[frame] = copied - started;
                record[frame] = recorded - copied;
                acquire[frame] = acquired - recorded;
                submit[frame] = submitted - acquired;
                interval[frame] = submitted - last;
                last = submitted;
                while (video.pollEvent(events)) {
                    // Keep the window responsive.
                }
            }
            videoPipeline.close();
            uiPipeline.close();
        } finally {
            planes.forEach(SdlGpuTexture::close);
        }
        var label = "4K " + layout + " layer + UI caret";
        row(label + ": copy the picture and the damage (" + (lumaBytes + chromaBytes) / 1_000_000 + " MB)", copy);
        row(label + ": record the uploads", record);
        row(label + ": wait for the swapchain texture", acquire);
        row(label + ": convert, composite, submit", submit);
        row(label + ": frame interval", interval);
    }

    private static void pumpFor(SdlVideo video, long millis) {
        var events = new SdlEventBuffer();
        var until = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < until) {
            while (video.pollEvent(events)) {
                // Let the window appear.
            }
            Thread.onSpinWait();
        }
    }

    private static void row(String label, long[] nanos) {
        var sorted = nanos.clone();
        Arrays.sort(sorted);
        System.out.printf(
                Locale.ROOT,
                "| %s | %.3f ms | %.3f ms | %.3f ms |%n",
                label,
                sorted[sorted.length / 2] / 1e6,
                sorted[(int) (sorted.length * 0.95)] / 1e6,
                Arrays.stream(nanos).average().orElse(0) / 1e6);
    }
}
