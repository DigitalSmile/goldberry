package dev.goldberry.gpu.render.mapped;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.gpu.render.staging.StagingBuffer;
import dev.goldberry.gpu.render.staging.UploadAlignment;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A raster painted in place, on a real device: what reaches the texture is
/// what was painted, frame after frame, and the same as the staged path gives.
///
/// Every layout runs on whatever the lane has. Vulkan copies all three as they
/// are; Metal records padded rows one at a time, and the pixels are the same.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a raster painted in place, on a real device")
class MappedRasterGpuTest {

    private static final PhysicalSize SIZE = new PhysicalSize(203, 77);
    private static final List<SdlGpuRegion> DAMAGE = List.of(
            new SdlGpuRegion(3, 5, 41, 7),
            new SdlGpuRegion(0, 0, 1, 1),
            new SdlGpuRegion(150, 60, 53, 17),
            new SdlGpuRegion(64, 20, 64, 30));

    private static SdlGpuDevice device;

    @BeforeAll
    static void createDevice() {
        device = GpuDeviceRequirement.enforce();
    }

    @AfterAll
    static void destroyDevice() {
        if (device == null) {
            return;
        }
        device.close();
        Sdl.get().quit();
    }

    enum Layout {
        RECTANGLES(new InPlaceLayout(UploadAlignment.PACKED, CopyShape.RECTANGLES)),
        PACKED_BANDS(new InPlaceLayout(UploadAlignment.PACKED, CopyShape.BANDS)),
        DIRECT3D12_BANDS(new InPlaceLayout(UploadAlignment.DIRECT3D12, CopyShape.BANDS));

        final InPlaceLayout layout;

        Layout(InPlaceLayout layout) {
            this.layout = layout;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("the texture holds what was painted, region by region, frame after frame")
    void holdsWhatWasPainted(Layout layout) {
        var random = new Random(7);
        try (var raster = MappedRaster.create(device, SIZE, layout.layout);
                var texture = texture()) {
            var first = raster.lend();
            scribble(first, List.of(new SdlGpuRegion(0, 0, SIZE.width(), SIZE.height())), random);
            raster.upload(texture, List.of(new SdlGpuRegion(0, 0, SIZE.width(), SIZE.height())));
            for (var frame = 0; frame < 3; frame++) {
                var pixels = raster.lend();
                scribble(pixels, DAMAGE, random);
                raster.upload(texture, DAMAGE);
                var expected = packed(raster.lend());
                assertArrayEquals(expected, readBack(texture), "frame " + frame);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("gives the same pixels as the staged path, for a frame Blend2D painted with its workers")
    void sameAsStaged(Layout layout) {
        try (var raster = MappedRaster.create(device, SIZE, layout.layout);
                var inPlace = texture();
                var staged = texture()) {
            var heap = PixelBuffer.allocate(SIZE, PixelFormat.BGRA32_PREMULTIPLIED);
            paint(heap);
            paint(raster.lend());
            raster.upload(inPlace, List.of(new SdlGpuRegion(0, 0, SIZE.width(), SIZE.height())));
            var staging = new StagingBuffer(device);
            var commands = device.acquireCommandBuffer();
            var placed = staging.stage(
                    heap.pixels(), heap.stride(), 4, List.of(new SdlGpuRegion(0, 0, SIZE.width(), SIZE.height())));
            try (var pass = commands.beginCopyPass()) {
                for (var region : placed.regions()) {
                    pass.upload(
                            staging.buffer(), region.offset(), region.pixelsPerRow(4), staged, region.region(), true);
                }
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            raster.lend();
            assertArrayEquals(readBack(staged), readBack(inPlace));
        }
    }

    @Test
    @DisplayName("is laid out for this device's driver as phase 0 measured")
    void layoutForThisDriver() {
        var layout = InPlaceLayout.of(device.driver(), device.uploadsPaddedRows());
        try (var raster = MappedRaster.create(device, SIZE, layout)) {
            assertTrue(raster.lend().stride() >= SIZE.width() * 4);
        }
    }

    @Test
    @DisplayName("the check at creation measures in auto, and leaves on and off to the run")
    void check() {
        var measured = InPlaceProbe.decide(device, InPlaceProbe.Mode.AUTO, InPlaceGate.DEFAULT);
        assertTrue(measured.measured().isPresent(), measured::describe);
        assertTrue(measured.measured().get().ratio() > 0, measured::describe);
        var on = InPlaceProbe.decide(device, InPlaceProbe.Mode.ON, InPlaceGate.DEFAULT);
        assertTrue(on.on() && on.measured().isEmpty());
        var off = InPlaceProbe.decide(device, InPlaceProbe.Mode.OFF, InPlaceGate.DEFAULT);
        assertFalse(off.on() || off.measured().isPresent());
    }

    private static SdlGpuTexture texture() {
        return device.createTexture(
                SdlGpuTextureFormat.B8G8R8A8_UNORM,
                SIZE.width(),
                SIZE.height(),
                EnumSet.of(SdlGpuTextureUsage.SAMPLER));
    }

    private static void paint(PixelBuffer buffer) {
        var frame = Frame.over(buffer, new DisplayScale(1.0f));
        try {
            frame.fill(0xFF2E3440);
            for (var i = 0; i < 12; i++) {
                frame.fillRect(i * 13, i * 5, 60, 30, 0x8088C0D0 + i);
            }
        } finally {
            frame.end();
        }
    }

    private static void scribble(PixelBuffer buffer, List<SdlGpuRegion> regions, Random random) {
        var pixels = buffer.pixels();
        for (var region : regions) {
            for (var y = region.y(); y < region.y() + region.height(); y++) {
                for (var x = region.x(); x < region.x() + region.width(); x++) {
                    pixels.putInt(y * buffer.stride() + x * 4, random.nextInt());
                }
            }
        }
    }

    /// The raster's pixels with its rows packed, as a texture reads back.
    private static byte[] packed(PixelBuffer buffer) {
        var row = buffer.size().width() * 4;
        var bytes = new byte[row * buffer.size().height()];
        for (var y = 0; y < buffer.size().height(); y++) {
            buffer.pixels().get(y * buffer.stride(), bytes, y * row, row);
        }
        return bytes;
    }

    private static byte[] readBack(SdlGpuTexture texture) {
        var region = SdlGpuRegion.of(texture);
        var size = (int) texture.byteSize(region);
        try (var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, size)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.download(texture, region, download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var bytes = new byte[size];
            ByteBuffer mapped = download.map(false);
            mapped.get(bytes);
            download.unmap();
            return bytes;
        }
    }
}
