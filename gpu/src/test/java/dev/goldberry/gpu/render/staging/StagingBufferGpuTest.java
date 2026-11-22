package dev.goldberry.gpu.render.staging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// Staged uploads on a real device, under each layout: what reaches the
/// texture is the image's regions, whatever the padding between them.
///
/// Direct3D 12's layout runs here on whatever the lane has, Vulkan or
/// lavapipe, since Vulkan takes padded rows too; Metal reads rows as packed,
/// and the copy pass then records them one at a time. Either way the pixels
/// are the packed path's.
@Tag(GpuTestLauncher.TAG)
@DisplayName("staged uploads, on a real device")
class StagingBufferGpuTest {

    private static final int WIDTH = 203;
    private static final int HEIGHT = 77;

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
        PACKED(UploadAlignment.PACKED),
        DIRECT3D12(UploadAlignment.DIRECT3D12);

        final UploadAlignment alignment;

        Layout(UploadAlignment alignment) {
            this.alignment = alignment;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("damaged regions reach the texture, and the pixels around them stay")
    void regions(Layout layout) {
        var image = image(WIDTH, HEIGHT, 1);
        var damage = List.of(
                new SdlGpuRegion(3, 5, 41, 7),
                new SdlGpuRegion(0, 0, 1, 1),
                new SdlGpuRegion(150, 60, WIDTH - 150, HEIGHT - 60),
                new SdlGpuRegion(64, 20, 64, 30));
        var expected = new byte[WIDTH * HEIGHT * 4];
        for (var region : damage) {
            copy(image, expected, WIDTH, region);
        }
        try (var texture = texture(1)) {
            var staging = new StagingBuffer(device, layout.alignment);
            upload(texture, staging, image, damage, 0, false);
            assertArrayEquals(expected, readBack(texture, 0, WIDTH, HEIGHT));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("a whole frame, uploaded with fresh texture memory, is the frame")
    void wholeFrame(Layout layout) {
        var image = image(WIDTH, HEIGHT, 2);
        try (var texture = texture(1)) {
            var staging = new StagingBuffer(device, layout.alignment);
            upload(texture, staging, image, List.of(new SdlGpuRegion(0, 0, WIDTH, HEIGHT)), 0, true);
            assertArrayEquals(image, readBack(texture, 0, WIDTH, HEIGHT));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("a mip level other than the first takes padded rows too")
    void mipLevel(Layout layout) {
        var width = WIDTH / 2;
        var height = HEIGHT / 2;
        var image = image(width, height, 3);
        try (var texture = texture(2)) {
            var staging = new StagingBuffer(device, layout.alignment);
            var whole = new SdlGpuRegion(0, 0, width, height);
            upload(texture, staging, image, List.of(whole), 1, false);
            assertArrayEquals(image, readBack(texture, 1, width, height));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Layout.class)
    @DisplayName("lays rows out as its alignment says")
    void layoutIsTheAlignments(Layout layout) {
        var staging = new StagingBuffer(device, layout.alignment);
        var staged = staging.stage(
                ByteBuffer.wrap(image(WIDTH, HEIGHT, 4)),
                WIDTH * 4,
                4,
                List.of(new SdlGpuRegion(0, 0, 41, 2), new SdlGpuRegion(0, 0, 3, 3)));
        assertEquals(
                StagingLayout.of(
                        layout.alignment, 4, List.of(new SdlGpuRegion(0, 0, 41, 2), new SdlGpuRegion(0, 0, 3, 3))),
                staged);
    }

    private static void upload(
            SdlGpuTexture texture,
            StagingBuffer staging,
            byte[] image,
            List<SdlGpuRegion> regions,
            int level,
            boolean cycle) {
        var width = Math.max(1, texture.width() >> level);
        var staged = staging.stage(ByteBuffer.wrap(image), width * 4, 4, regions);
        var commands = device.acquireCommandBuffer();
        try (var pass = commands.beginCopyPass()) {
            for (var placed : staged.regions()) {
                pass.upload(
                        staging.buffer(),
                        placed.offset(),
                        placed.pixelsPerRow(4),
                        texture,
                        level,
                        0,
                        placed.region(),
                        cycle);
            }
        }
        try (var fence = commands.submitWithFence()) {
            fence.await();
        }
    }

    private static SdlGpuTexture texture(int mipLevels) {
        var texture = device.createTexture(
                SdlGpuTextureFormat.B8G8R8A8_UNORM,
                WIDTH,
                HEIGHT,
                1,
                mipLevels,
                SdlGpuSampleCount.ONE,
                EnumSet.of(SdlGpuTextureUsage.SAMPLER));
        // Zeroes everywhere first, so pixels outside the damage are known.
        var staging = new StagingBuffer(device, UploadAlignment.PACKED);
        for (var level = 0; level < mipLevels; level++) {
            var width = Math.max(1, WIDTH >> level);
            var height = Math.max(1, HEIGHT >> level);
            upload(
                    texture,
                    staging,
                    new byte[width * height * 4],
                    List.of(new SdlGpuRegion(0, 0, width, height)),
                    level,
                    false);
        }
        return texture;
    }

    private static byte[] readBack(SdlGpuTexture texture, int level, int width, int height) {
        var size = width * height * 4;
        try (var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, size)) {
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.download(texture, level, 0, new SdlGpuRegion(0, 0, width, height), download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var bytes = new byte[size];
            download.map(false).get(bytes);
            download.unmap();
            return bytes;
        }
    }

    private static byte[] image(int width, int height, long seed) {
        var bytes = new byte[width * height * 4];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static void copy(byte[] from, byte[] to, int width, SdlGpuRegion region) {
        for (var y = region.y(); y < region.y() + region.height(); y++) {
            var at = (y * width + region.x()) * 4;
            System.arraycopy(from, at, to, at, region.width() * 4);
        }
    }
}
