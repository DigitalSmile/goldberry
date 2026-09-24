package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;

/// `SDL_GPU` through the wrappers, on a real device: Metal on macOS, Vulkan
/// under lavapipe on the GPU lane (`docs/gpu-plan.md`, phase 1).
///
/// Phase 1's exit is here: a texture cleared to a known colour and downloaded
/// reads back byte for byte, and pixels uploaded come back unchanged.
///
/// Tagged `gpu`, so the ordinary `test` task leaves it to `:natives:gpuTest`,
/// which runs it on the JVM's first thread as macOS needs ([GpuTestLauncher]).
@Tag(GpuTestLauncher.TAG)
@DisplayName("SdlGpuDevice, on a real device")
class SdlGpuDeviceTest {

    private static final Set<SdlGpuTextureUsage> TARGET =
            EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER);

    private static SdlGpuDevice device;

    @BeforeAll
    static void createDevice() {
        device = GpuDeviceRequirement.enforce();
    }

    @AfterAll
    static void destroyDevice() {
        if (device != null) {
            device.close();
        }
        Sdl.get().quit();
    }

    @AfterEach
    void nothingLeaks() {
        assertEquals(0, device.openResources(), "a test left a resource open");
    }

    @Test
    @DisplayName("names its driver, one SDL compiled in, and takes a format that was asked for")
    void describesItself() {
        assertTrue(SdlGpuDevice.compiledDrivers().contains(device.driver()), device.driver());
        assertFalse(device.shaderFormats().isEmpty());
        assertTrue(
                device.shaderFormats().stream()
                        .anyMatch(SdlGpuDevice.Options.defaults().shaderFormats()::contains),
                device.shaderFormats()::toString);
    }

    @Test
    @DisplayName("makes every format the toolkit uses, as a sampled texture")
    void supportsTheFormatsTheToolkitUses() {
        for (var format : SdlGpuTextureFormat.values()) {
            assertTrue(device.supports(format, EnumSet.of(SdlGpuTextureUsage.SAMPLER)), format::toString);
        }
    }

    @Test
    @DisplayName("clears a texture to a colour that downloads byte for byte")
    void clearsAndDownloads() {
        // 51/255 is 0.2 in the float, and 51 again in the byte: no rounding for
        // two drivers to disagree about.
        try (var texture = device.createTexture(SdlGpuTextureFormat.B8G8R8A8_UNORM, 16, 8, TARGET);
                var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, 16 * 8 * 4)) {
            var commands = device.acquireCommandBuffer();
            commands.clear(texture, 1f, 0f, 51 / 255f, 1f);
            try (var pass = commands.beginCopyPass()) {
                pass.download(texture, SdlGpuRegion.of(texture), download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var pixels = download.map(false);
            for (var i = 0; i < 16 * 8; i++) {
                // B, G, R, A in memory.
                assertEquals(51, Byte.toUnsignedInt(pixels.get(i * 4)), "blue of pixel " + i);
                assertEquals(0, Byte.toUnsignedInt(pixels.get(i * 4 + 1)), "green of pixel " + i);
                assertEquals(255, Byte.toUnsignedInt(pixels.get(i * 4 + 2)), "red of pixel " + i);
                assertEquals(255, Byte.toUnsignedInt(pixels.get(i * 4 + 3)), "alpha of pixel " + i);
            }
            download.unmap();
        }
    }

    @Test
    @DisplayName("uploads into a region and downloads the same bytes back")
    void uploadsAndDownloadsARegion() {
        var region = new SdlGpuRegion(8, 4, 20, 10);
        var bytes = new byte[20 * 10 * 4];
        new Random(4).nextBytes(bytes);
        try (var texture = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 32, 16, TARGET);
                var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, bytes.length);
                var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, bytes.length)) {
            upload.map(false).put(bytes);
            upload.unmap();
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.upload(upload, 0, texture, region, false);
                pass.download(texture, region, download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var back = new byte[bytes.length];
            download.map(false).get(back);
            download.unmap();
            assertArrayEquals(bytes, back);
        }
    }

    @Test
    @DisplayName("round-trips 16-bit planes, as a 10-bit video's luma will travel")
    void roundTripsSixteenBitPlanes() {
        var region = new SdlGpuRegion(0, 0, 8, 8);
        var bytes = new byte[8 * 8 * 2];
        new Random(10).nextBytes(bytes);
        try (var texture = device.createTexture(
                        SdlGpuTextureFormat.R16_UNORM, 8, 8, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
                var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, bytes.length);
                var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, bytes.length)) {
            upload.map(false).put(bytes);
            upload.unmap();
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.upload(upload, 0, texture, region, false);
                pass.download(texture, region, download, 0);
            }
            try (var fence = commands.submitWithFence()) {
                fence.await();
            }
            var back = new byte[bytes.length];
            download.map(false).get(back);
            download.unmap();
            assertArrayEquals(bytes, back);
        }
    }

    @Nested
    @DisplayName("checks SDL's rules in Java, before SDL is called")
    class Rules {

        @Test
        @DisplayName("a command buffer is used once")
        void usedOnce() {
            var commands = device.acquireCommandBuffer();
            commands.submit();
            assertTrue(commands.isFinished());
            assertThrows(IllegalStateException.class, commands::submit);
            assertThrows(IllegalStateException.class, commands::beginCopyPass);
            assertThrows(IllegalStateException.class, commands::cancel);
        }

        @Test
        @DisplayName("nothing else is recorded while a copy pass is open, and cancelling waits for it")
        void onePassAtATime() {
            try (var texture = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET)) {
                var commands = device.acquireCommandBuffer();
                var pass = commands.beginCopyPass();
                assertThrows(IllegalStateException.class, () -> commands.clear(texture, 0, 0, 0, 1));
                assertThrows(IllegalStateException.class, commands::beginCopyPass);
                assertThrows(IllegalStateException.class, commands::submit);
                pass.close();
                assertThrows(
                        IllegalStateException.class, () -> pass.download(texture, SdlGpuRegion.of(texture), null, 0));
                commands.cancel();
            }
        }

        @Test
        @DisplayName("a mapped buffer, a region outside the texture, and bytes that do not fit are refused")
        void refusesBadCopies() {
            try (var texture = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET);
                    var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, 64);
                    var download = device.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, 64)) {
                var commands = device.acquireCommandBuffer();
                try (var pass = commands.beginCopyPass()) {
                    var whole = SdlGpuRegion.of(texture);
                    upload.map(false);
                    assertThrows(IllegalStateException.class, () -> pass.upload(upload, 0, texture, whole, false));
                    upload.unmap();
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> pass.upload(upload, 0, texture, new SdlGpuRegion(2, 2, 4, 4), false));
                    assertThrows(IllegalArgumentException.class, () -> pass.upload(upload, 4, texture, whole, false));
                    assertThrows(IllegalArgumentException.class, () -> pass.upload(download, 0, texture, whole, false));
                    assertThrows(IllegalArgumentException.class, () -> pass.download(texture, whole, upload, 0));
                }
                commands.cancel();
            }
        }

        @Test
        @DisplayName("clearing a texture that is not a colour target is refused")
        void clearNeedsAColourTarget() {
            try (var texture =
                    device.createTexture(SdlGpuTextureFormat.R8_UNORM, 4, 4, EnumSet.of(SdlGpuTextureUsage.SAMPLER))) {
                var commands = device.acquireCommandBuffer();
                assertThrows(IllegalArgumentException.class, () -> commands.clear(texture, 0, 0, 0, 1));
                commands.cancel();
            }
        }

        @Test
        @DisplayName("mapped memory stops working at unmap, rather than reading freed memory")
        void mappedMemoryIsScoped() {
            try (var upload = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, 16)) {
                var mapped = upload.map(false);
                assertThrows(IllegalStateException.class, () -> upload.map(false));
                mapped.put(0, (byte) 1);
                upload.unmap();
                assertFalse(upload.isMapped());
                assertThrows(IllegalStateException.class, () -> mapped.put(0, (byte) 2));
            }
        }

        @Test
        @DisplayName("a closed resource fails in Java, naming itself")
        void closedResourcesFailInJava() {
            var texture = device.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET);
            texture.close();
            texture.close();
            assertTrue(texture.isClosed());
            var commands = device.acquireCommandBuffer();
            var thrown = assertThrows(IllegalStateException.class, () -> commands.clear(texture, 0, 0, 0, 1));
            assertTrue(thrown.getMessage().contains("SdlGpuTexture"), thrown.getMessage());
            commands.cancel();
        }

        @Test
        @DisplayName("sizes and usages that cannot make a texture are refused before SDL is asked")
        void refusesEmptyTextures() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> device.createTexture(SdlGpuTextureFormat.R8_UNORM, 0, 4, TARGET));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> device.createTexture(SdlGpuTextureFormat.R8_UNORM, 4, 4, Set.of()));
            assertThrows(
                    IllegalArgumentException.class, () -> device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, 0));
        }
    }

    @Nested
    @DisplayName("a second device")
    class SecondDevice {

        @Test
        @DisplayName("is the named driver when one is named, which is the name property working")
        void takesTheNamedDriver() {
            try (var named = SdlGpuDevice.create(SdlGpuDevice.Options.defaults().withDriver(device.driver()))) {
                assertEquals(device.driver(), named.driver());
            }
        }

        @Test
        @DisplayName("releases what is still open when it closes")
        void closingReleasesTheRest() {
            var second = SdlGpuDevice.create(SdlGpuDevice.Options.defaults());
            var texture = second.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET);
            var buffer = second.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, 64);
            buffer.map(false);
            assertEquals(2, second.openResources());
            second.close();
            assertTrue(second.isClosed());
            assertTrue(texture.isClosed());
            assertTrue(buffer.isClosed());
            assertFalse(buffer.isMapped());
            assertThrows(IllegalStateException.class, second::acquireCommandBuffer);
        }

        @Test
        @DisplayName("resources of one device are refused by another's commands")
        void resourcesStayWithTheirDevice() {
            try (var second = SdlGpuDevice.create(SdlGpuDevice.Options.defaults());
                    var texture = second.createTexture(SdlGpuTextureFormat.R8G8B8A8_UNORM, 4, 4, TARGET)) {
                var commands = device.acquireCommandBuffer();
                assertThrows(IllegalArgumentException.class, () -> commands.clear(texture, 0, 0, 0, 1));
                commands.cancel();
            }
        }

        @Test
        @DisplayName("is refused a driver that takes none of the formats asked for")
        void refusesFormatsNoDriverTakes() {
            var other = device.shaderFormats().contains(SdlGpuShaderFormat.MSL)
                    ? EnumSet.of(SdlGpuShaderFormat.DXIL)
                    : EnumSet.of(SdlGpuShaderFormat.METALLIB);
            var options = new SdlGpuDevice.Options(other, false, true, Optional.empty());
            assertThrows(
                    io.github.digitalsmile.goldberry.natives.sdl.SdlException.class,
                    () -> SdlGpuDevice.create(options));
        }
    }
}
