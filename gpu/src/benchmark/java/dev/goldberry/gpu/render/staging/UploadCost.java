package dev.goldberry.gpu.render.staging;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;

import dev.goldberry.gpu.render.mapped.CopyShape;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuFence;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// What a frame's upload costs the UI thread, per path, and how long until the
/// GPU has copied it: D6's first number.
///
/// - **Staged** is today's path: the damaged regions copied out of a heap
///   frame into the device's staging buffer, then recorded. Its stage is what
///   a composited present counts as `upload`: the copy and the recording, not
///   the submit, which the composite shares.
/// - **In place** is the prototype of phase 2: the frame lives in an upload
///   transfer buffer, persistently mapped, and the regions are recorded
///   straight out of it with the frame's stride as their pitch, in a command
///   buffer of their own submitted with a fence (D4 (a)). Its stage counts that
///   submit, since it is a cost the path adds. **Bands** widens each region to
///   whole rows starting at a multiple of 512 bytes, the one shape Direct3D 12
///   copies from a frame-sized buffer as it is.
final class UploadCost {

    /// The four ways measured.
    enum Path {
        /// Staged, rows packed: every driver before phase 1.
        STAGED_PACKED("staged, packed"),
        /// Staged, rows padded to 256 and regions at 512: Direct3D 12's since
        /// phase 1.
        STAGED_ALIGNED("staged, 256/512"),
        /// Recorded out of the frame, region by region.
        IN_PLACE("in place"),
        /// Recorded out of the frame, as whole-row bands.
        IN_PLACE_BANDS("in place, bands");

        private final String label;

        Path(String label) {
            this.label = label;
        }

        boolean inPlace() {
            return this == IN_PLACE || this == IN_PLACE_BANDS;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /// One row: the stage on the UI thread, and from its start until the GPU's
    /// fence signalled.
    record Row(Path path, int width, int height, Damage damage, long bytes, Samples stage, Samples copied) {}

    private final SdlGpuDevice device;
    private final int warmup;
    private final int runs;

    UploadCost(SdlGpuDevice device, int warmup, int runs) {
        this.device = device;
        this.warmup = warmup;
        this.runs = runs;
    }

    /// Measures `path` for `damage` of a `width` by `height` frame.
    Row measure(Path path, int width, int height, Damage damage) {
        var stride = UploadAlignment.DIRECT3D12.pitch((long) width * 4);
        var regions = damage.regions(width, height);
        var recorded = path == Path.IN_PLACE_BANDS
                ? CopyShape.BANDS.copies(regions, width, stride, UploadAlignment.DIRECT3D12)
                : regions;
        var whole = damage == Damage.WHOLE;
        var bytes = 0L;
        for (var region : recorded) {
            bytes += (long) region.width() * region.height() * 4;
        }
        var stage = new long[runs];
        var copied = new long[runs];
        try (var texture = device.createTexture(
                        SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
                var raster = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, stride * height)) {
            var frame = ByteBuffer.allocateDirect(stride * height);
            fill(frame, 1);
            var mapped = raster.map(false);
            fill(mapped, 1);
            raster.unmap();
            var staging = new StagingBuffer(
                    device, path == Path.STAGED_ALIGNED ? UploadAlignment.DIRECT3D12 : UploadAlignment.PACKED);
            for (var i = 0; i < warmup + runs; i++) {
                if (path.inPlace()) {
                    // What painting does to the raster before its upload:
                    // mapped, written where it changed, and given back.
                    var pixels = raster.map(false);
                    touch(pixels, stride, recorded, i);
                    raster.unmap();
                } else {
                    touch(frame, stride, regions, i);
                }
                var started = System.nanoTime();
                long staged;
                if (path.inPlace()) {
                    var commands = device.acquireCommandBuffer();
                    try (var pass = commands.beginCopyPass()) {
                        for (var region : recorded) {
                            pass.upload(
                                    raster, region.y() * stride + region.x() * 4, stride / 4, texture, region, whole);
                        }
                    }
                    var fence = commands.submitWithFence();
                    staged = System.nanoTime();
                    await(fence);
                } else {
                    var commands = device.acquireCommandBuffer();
                    var layout = staging.stage(frame, stride, 4, regions);
                    try (var pass = commands.beginCopyPass()) {
                        for (var placed : layout.regions()) {
                            pass.upload(
                                    staging.buffer(),
                                    placed.offset(),
                                    placed.pixelsPerRow(4),
                                    texture,
                                    placed.region(),
                                    whole);
                        }
                    }
                    staged = System.nanoTime();
                    await(commands.submitWithFence());
                }
                var done = System.nanoTime();
                if (i >= warmup) {
                    stage[i - warmup] = staged - started;
                    copied[i - warmup] = done - started;
                }
            }
        }
        return new Row(path, width, height, damage, bytes, new Samples(stage), new Samples(copied));
    }

    private static void await(SdlGpuFence fence) {
        try (fence) {
            fence.await();
        }
    }

    /// Writes one byte per row of each region, as a paint that changed them
    /// would, so no driver can tell the upload is the same as the last.
    private static void touch(ByteBuffer pixels, int stride, List<SdlGpuRegion> regions, int frame) {
        for (var region : regions) {
            for (var y = region.y(); y < region.y() + region.height(); y++) {
                pixels.put(y * stride + region.x() * 4, (byte) frame);
            }
        }
    }

    private static void fill(ByteBuffer pixels, int seed) {
        for (var i = 0; i < pixels.capacity(); i += 61) {
            pixels.put(i, (byte) (i * 31 + seed));
        }
    }
}
