package io.github.digitalsmile.goldberry.gpu.video;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.gpu.TextureFormat;
import io.github.digitalsmile.goldberry.gpu.TextureSpec;
import io.github.digitalsmile.goldberry.gpu.composite.CompositeHarness;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// D8's second half (`docs/gpu-plan.md`, phase 6; ADR-0484): what a
/// [VideoLayer] costs the UI thread to show a new 4K picture every frame, as
/// `video-view` shows one, through the public API's staged upload.
///
/// Each frame shows a new [VideoImage] -- a new picture to the layer, over one
/// of two buffers, as the frame queue's slots are reused -- renders the layer
/// into a 4K target, and submits. The rows are the CPU time of the render (the
/// planes copied into staging memory and the passes recorded) and of the
/// submit, then the interval with the GPU paced by a wait on every frame's
/// readback of one pixel, which is the most the GPU could be behind. Run by
/// `:gpu:videoLayerProbe`, on the first thread, and read by a person.
final class VideoLayerProbe {

    private static final int FRAMES = 180;
    private static final int WIDTH = 3840;
    private static final int HEIGHT = 2160;

    private VideoLayerProbe() {}

    static void main() {
        try (var harness = CompositeHarness.open()) {
            var device = harness.device();
            System.out.printf(Locale.ROOT, "GPU %s, a %dx%d picture each frame%n%n", device.driver(), WIDTH, HEIGHT);
            System.out.println("| Measurement | median | p95 | mean |");
            System.out.println("|---|---|---|---|");
            for (var layout : new PlaneLayout[] {PlaneLayout.NV12, PlaneLayout.I420, PlaneLayout.P010}) {
                run(harness, layout);
            }
        }
    }

    private static void run(CompositeHarness harness, PlaneLayout layout) {
        var device = harness.device();
        var buffers = new ArrayList<List<ByteBuffer>>();
        var strides = new ArrayList<Integer>();
        var bytes = 0L;
        for (var plane = 0; plane < layout.planes(); plane++) {
            var format = layout.textureFormats().get(plane);
            strides.add(layout.planeWidth(plane, WIDTH) * format.bytesPerPixel());
            bytes += (long) strides.getLast() * layout.planeHeight(plane, HEIGHT);
        }
        for (var copy = 0; copy < 2; copy++) {
            var planes = new ArrayList<ByteBuffer>();
            for (var plane = 0; plane < layout.planes(); plane++) {
                var buffer = ByteBuffer.allocateDirect(strides.get(plane) * layout.planeHeight(plane, HEIGHT));
                for (var i = 0; i < buffer.capacity(); i += 97) {
                    buffer.put(i, (byte) (i * 31 + copy));
                }
                planes.add(buffer);
            }
            buffers.add(planes);
        }
        var render = new long[FRAMES];
        var submit = new long[FRAMES];
        var interval = new long[FRAMES];
        try (var layer = new VideoLayer();
                var target =
                        device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH, HEIGHT))) {
            var last = System.nanoTime();
            for (var i = 0; i < FRAMES; i++) {
                layer.show(new VideoImage.Planes(
                        layout, WIDTH, HEIGHT, buffers.get(i % 2), strides, ColorMatrix.BT709, false));
                try (var frame = device.beginFrame()) {
                    var started = System.nanoTime();
                    layer.render(frame, target);
                    var rendered = System.nanoTime();
                    var readback = frame.readback(target, PhysicalRect.of(0, 0, 1, 1));
                    frame.submit();
                    var submitted = System.nanoTime();
                    readback.awaitPixels();
                    render[i] = rendered - started;
                    submit[i] = submitted - rendered;
                }
                var now = System.nanoTime();
                interval[i] = now - last;
                last = now;
            }
        }
        var label = "4K " + layout + " (" + bytes / 1_000_000 + " MB)";
        row(label + ": render (copy to staging, record)", render);
        row(label + ": submit", submit);
        row(label + ": frame, waiting for the GPU", interval);
        System.out.printf(
                Locale.ROOT,
                "| %s: upload rate while rendering | %.1f GB/s | | |%n",
                label,
                bytes / (median(render) / 1e9) / 1e9);
    }

    private static double median(long[] nanos) {
        var sorted = nanos.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
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
