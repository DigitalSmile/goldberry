package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuRegion;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTexture;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureUsage;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTransferUsage;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.PresentTimings;
import io.github.digitalsmile.goldberry.render.composite.Claim;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;

/// The composited window's GPU half (`docs/gpu-plan.md`, phase 3; ADR-0479), on
/// a real device.
///
/// The parity that matters: a frame composited through the GPU shows the same
/// pixels the window surface would. The surface shows a premultiplied frame's
/// colour bytes and ignores its alpha; the composite draws it over opaque black
/// with premultiplied "over", which keeps the colour bytes and makes the alpha
/// opaque. [Parity#everyByteValue] holds it to that for random frames, so every
/// byte value and every alpha is covered.
@Tag(GpuTestLauncher.TAG)
@DisplayName("the compositor, on a real device")
class CompositorTest {

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

    @Nested
    @DisplayName("the composite pass")
    class Parity {

        @Test
        @DisplayName("keeps every colour byte of a premultiplied frame, and makes it opaque")
        void everyByteValue() {
            var width = 64;
            var height = 32;
            var frame = premultiplied(width, height, 7);
            try (var composite = new UiComposite(device);
                    var ui = upload(frame, width, height);
                    var target = device.createTexture(
                            SdlGpuTextureFormat.B8G8R8A8_UNORM,
                            width,
                            height,
                            EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER))) {
                var commands = device.acquireCommandBuffer();
                composite.draw(commands, target, target.format(), ui);
                commands.submit();
                var back = readBack(device, target);
                for (var i = 0; i < width * height; i++) {
                    for (var channel = 0; channel < 3; channel++) {
                        assertEquals(
                                frame[i * 4 + channel], back[i * 4 + channel], "pixel " + i + " channel " + channel);
                    }
                    assertEquals((byte) 255, back[i * 4 + 3], "pixel " + i + " is opaque");
                }
            }
        }

        /// At the sizes real windows are: nearest sampling 1:1 has to pick each
        /// pixel's own texel at the far edge of a 3K frame as well as at the
        /// origin, where a texture coordinate's rounding would show first. Odd
        /// sizes too, since a window's pixels need not be even.
        @Test
        @DisplayName("keeps every byte at full window sizes, even and odd, to the far corner")
        void windowSizes() {
            for (var size : new int[][] {{1920, 1080}, {3024, 1842}, {2561, 1599}}) {
                var width = size[0];
                var height = size[1];
                var frame = premultiplied(width, height, width);
                try (var composite = new UiComposite(device);
                        var ui = upload(frame, width, height);
                        var target = device.createTexture(
                                SdlGpuTextureFormat.B8G8R8A8_UNORM,
                                width,
                                height,
                                EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER))) {
                    var commands = device.acquireCommandBuffer();
                    composite.draw(commands, target, target.format(), ui);
                    commands.submit();
                    var back = readBack(device, target);
                    var wrong = 0;
                    for (var i = 0; i < width * height; i++) {
                        for (var channel = 0; channel < 3; channel++) {
                            if (frame[i * 4 + channel] != back[i * 4 + channel]) {
                                wrong++;
                            }
                        }
                    }
                    assertEquals(0, wrong, width + "x" + height + ": colour bytes that changed");
                }
            }
        }

        @Test
        @DisplayName("draws a smaller UI at the top left and leaves the rest black, as a resize mid-frame does")
        void smallerUi() {
            var frame = premultiplied(8, 8, 8);
            try (var composite = new UiComposite(device);
                    var ui = upload(frame, 8, 8);
                    var target = device.createTexture(
                            SdlGpuTextureFormat.B8G8R8A8_UNORM,
                            16,
                            16,
                            EnumSet.of(SdlGpuTextureUsage.COLOR_TARGET, SdlGpuTextureUsage.SAMPLER))) {
                var commands = device.acquireCommandBuffer();
                composite.draw(commands, target, target.format(), ui);
                commands.submit();
                var back = readBack(device, target);
                assertEquals(frame[0], back[0]);
                assertEquals(frame[(7 * 8 + 7) * 4 + 1], back[(7 * 16 + 7) * 4 + 1]);
                for (var channel = 0; channel < 3; channel++) {
                    assertEquals(0, back[(12 * 16 + 12) * 4 + channel], "outside the UI is black");
                }
            }
        }
    }

    @Nested
    @DisplayName("a claimed window")
    class ClaimedWindow {

        private SdlWindowHandle window;
        private SdlCompositor compositor;

        @BeforeEach
        void createWindow() {
            window = SdlVideo.get().createWindow("goldberry compositor test", 64, 48, EnumSet.of(SdlWindowFlag.HIDDEN));
            compositor = new SdlCompositor();
        }

        @AfterEach
        void destroyWindow() {
            compositor.close();
            SdlVideo.get().destroyWindow(window);
        }

        @Test
        @DisplayName("uploads a first frame whole, then only the damage, and keeps the rest of the texture")
        void damageOnly() {
            var claimed = claimed(compositor, window);
            var first = premultiplied(32, 16, 1);
            var second = premultiplied(32, 16, 2);
            claimed.present(frame(first, 32, 16), List.of(new DamageRect(0, 0, 4, 4)));
            assertEquals(32 * 16 * 4, claimed.lastPresent().uploadBytes(), "the first frame goes up whole");
            var damage = List.of(new DamageRect(3, 2, 5, 4), new DamageRect(20, 10, 12, 6));
            claimed.present(frame(second, 32, 16), damage);
            assertEquals((5 * 4 + 12 * 6) * 4, claimed.lastPresent().uploadBytes(), "then only the damage");
            var back = readBack(compositor.device().orElseThrow(), claimed.uiTexture());
            for (var y = 0; y < 16; y++) {
                for (var x = 0; x < 32; x++) {
                    var damaged = inside(damage, x, y);
                    var expected = damaged ? second : first;
                    for (var channel = 0; channel < 4; channel++) {
                        var at = (y * 32 + x) * 4 + channel;
                        assertEquals(expected[at], back[at], "at " + x + "," + y);
                    }
                }
            }
            claimed.close();
        }

        @Test
        @DisplayName("remakes its texture when the frame changes size, and uploads that frame whole")
        void resize() {
            var claimed = claimed(compositor, window);
            claimed.present(frame(premultiplied(32, 16, 3), 32, 16), List.of(new DamageRect(0, 0, 32, 16)));
            claimed.present(frame(premultiplied(40, 20, 4), 40, 20), List.of(new DamageRect(0, 0, 1, 1)));
            assertEquals(40 * 20 * 4, claimed.lastPresent().uploadBytes());
            assertEquals(40, claimed.uiTexture().width());
            claimed.close();
        }

        @Test
        @DisplayName("presents nothing for no damage, and refuses to present once given back")
        void noDamageAndClosed() {
            var claimed = claimed(compositor, window);
            var frame = frame(premultiplied(8, 8, 5), 8, 8);
            claimed.present(frame, List.of(new DamageRect(0, 0, 8, 8)));
            claimed.present(frame, List.of());
            assertEquals(PresentTimings.NONE, claimed.lastPresent());
            claimed.close();
            claimed.close();
            assertThrows(
                    IllegalStateException.class, () -> claimed.present(frame, List.of(new DamageRect(0, 0, 1, 1))));
        }

        @Test
        @DisplayName("gives the window back, so its surface can be painted again, and can claim it once more")
        void givesTheWindowBack() {
            var claimed = claimed(compositor, window);
            claimed.close();
            var surface = SdlVideo.get().acquireSurface(window);
            assertTrue(surface.width() >= 64 && surface.height() >= 48, "a surface of the window's size");
            SdlVideo.get().invalidateSurface(window);
            claimed(compositor, window).close();
        }

        @Test
        @DisplayName("makes one device for every window, and none until the first claim")
        void oneDevice() {
            assertTrue(compositor.device().isEmpty(), "no device before a claim");
            var claimed = claimed(compositor, window);
            var made = compositor.device().orElseThrow();
            var other = SdlVideo.get().createWindow("second", 32, 32, EnumSet.of(SdlWindowFlag.HIDDEN));
            try {
                var second = claimed(compositor, other);
                assertEquals(made, compositor.device().orElseThrow());
                second.close();
            } finally {
                SdlVideo.get().destroyWindow(other);
            }
            claimed.close();
            assertTrue(compositor.unavailable().isEmpty());
        }
    }

    @Test
    @DisplayName("remembers a device it could not make, and claims nothing after")
    void noDevice() {
        var window = SdlVideo.get().createWindow("no device", 32, 32, EnumSet.of(SdlWindowFlag.HIDDEN));
        System.setProperty(DeviceOptions.DRIVER_PROPERTY, "no-such-driver");
        var compositor = new SdlCompositor();
        try {
            var refused = assertInstanceOf(Claim.Refused.class, compositor.claim(window));
            assertTrue(refused.reason().contains("no GPU device"), refused.reason());
            assertTrue(compositor.unavailable().orElseThrow().contains("no GPU device"));
            System.clearProperty(DeviceOptions.DRIVER_PROPERTY);
            assertInstanceOf(Claim.Refused.class, compositor.claim(window), "not asked again");
            assertFalse(compositor.device().isPresent());
        } finally {
            System.clearProperty(DeviceOptions.DRIVER_PROPERTY);
            compositor.close();
            SdlVideo.get().destroyWindow(window);
        }
    }

    // --- helpers ---------------------------------------------------------------

    /// `window`, claimed by `compositor`, or the test fails with the reason.
    private static SdlCompositedWindow claimed(SdlCompositor compositor, SdlWindowHandle window) {
        return switch (compositor.claim(window)) {
            case Claim.Claimed(var claimed) -> (SdlCompositedWindow) claimed;
            case Claim.Refused(var reason) -> throw new AssertionError("refused: " + reason);
        };
    }

    /// Random premultiplied BGRA: every channel at most its pixel's alpha.
    static byte[] premultiplied(int width, int height, long seed) {
        var random = new Random(seed);
        var bytes = new byte[width * height * 4];
        for (var i = 0; i < width * height; i++) {
            var alpha = random.nextInt(256);
            for (var channel = 0; channel < 3; channel++) {
                bytes[i * 4 + channel] = (byte) random.nextInt(alpha + 1);
            }
            bytes[i * 4 + 3] = (byte) alpha;
        }
        return bytes;
    }

    private static PixelBuffer frame(byte[] bytes, int width, int height) {
        return new PixelBuffer(
                new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED, width * 4, ByteBuffer.wrap(bytes));
    }

    private static boolean inside(List<DamageRect> damage, int x, int y) {
        return damage.stream()
                .anyMatch(rect ->
                        x >= rect.x() && x < rect.x() + rect.width() && y >= rect.y() && y < rect.y() + rect.height());
    }

    private static SdlGpuTexture upload(byte[] bytes, int width, int height) {
        var texture = device.createTexture(
                SdlGpuTextureFormat.B8G8R8A8_UNORM, width, height, EnumSet.of(SdlGpuTextureUsage.SAMPLER));
        try (var buffer = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, bytes.length)) {
            buffer.map(false).put(bytes);
            buffer.unmap();
            var commands = device.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.upload(buffer, 0, texture, SdlGpuRegion.of(texture), false);
            }
            commands.submit();
        }
        return texture;
    }

    private static byte[] readBack(SdlGpuDevice on, SdlGpuTexture texture) {
        var region = SdlGpuRegion.of(texture);
        var size = (int) texture.byteSize(region);
        try (var download = on.createTransferBuffer(SdlGpuTransferUsage.DOWNLOAD, size)) {
            var commands = on.acquireCommandBuffer();
            try (var pass = commands.beginCopyPass()) {
                pass.download(texture, region, download, 0);
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
}
