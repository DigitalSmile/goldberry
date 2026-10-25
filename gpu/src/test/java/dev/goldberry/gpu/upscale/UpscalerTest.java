package dev.goldberry.gpu.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.gpu.upscale.fsr.Fsr1Reference;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;

/// The upscaler's passes on a real device, each held to the Java reference of
/// the same arithmetic ([Fsr1Reference]) pixel for pixel.
///
/// **The tolerance** is the GPU's rounding. Its bilinear weights are
/// fixed-point, a few bits of a texel, and it may fuse a multiply and an add
/// the reference rounds twice. Either moves a value by less than a level of an
/// 8-bit target, which shows as a level now and then: one pass is held to
/// [#TOLERANCE]. EASU then RCAS is held to [#CHAIN_TOLERANCE], because RCAS
/// sharpens what EASU gave it: a pixel a level off going in comes out
/// `1 / (1 - 4 * 0.1875 * sharpness)` levels off, 2.9 at the default, before
/// its own rounding. Lavapipe matches the reference exactly through both FSR
/// passes; an NVIDIA GPU is a level off after EASU and three after RCAS. A
/// wrong tap, weight or constant moves hundreds of pixels by tens of levels.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@Tag(GpuTestLauncher.TAG)
@DisplayName("an upscaler, on the GPU")
class UpscalerTest {

    /// Levels a channel one pass on the GPU may be from the reference.
    private static final int TOLERANCE = 2;

    /// Levels a channel EASU then RCAS at the default sharpness may be from
    /// the reference: one level out of EASU, sharpened 2.9 times, and rounded.
    private static final int CHAIN_TOLERANCE = 4;

    private static CompositeHarness harness;
    private static GpuDevice device;
    private static int baseline;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
        device = harness.device();
        // One upload first, so the device's staging buffer is in the baseline.
        upscale(TestPattern.of(2, 2), 4, 4, UpscaleFilter.LINEAR, 0);
        baseline = harness.openResources();
    }

    @AfterAll
    static void close() {
        if (harness != null) {
            harness.close();
        }
    }

    @AfterEach
    void nothingLeaks() {
        assertEquals(baseline, harness.openResources(), "a test left a resource open");
    }

    /// `input` uploaded, brought to `width` by `height` with `filter` by a new
    /// upscaler, and read back as tightly packed BGRA.
    private static byte[] upscale(
            Fsr1Reference.Rgb input, int width, int height, UpscaleFilter filter, double sharpness) {
        try (var upscaler = new Upscaler(device);
                var source = device.createTexture(
                        TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, input.width(), input.height()));
                var target =
                        device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, width, height));
                var frame = device.beginFrame()) {
            frame.copyPass(copy -> copy.upload(source, ByteBuffer.wrap(input.toBgra())));
            upscaler.upscale(frame, source, target, filter, sharpness);
            try (var readback = frame.readback(target)) {
                frame.submit();
                var pixels = readback.await();
                var bytes = new byte[pixels.remaining()];
                pixels.get(bytes);
                return bytes;
            }
        }
    }

    /// Every channel of `actual` within [#TOLERANCE] of `expected`, and alpha
    /// opaque.
    private static void assertNear(Fsr1Reference.Rgb expected, byte[] actual, String what) {
        assertNear(expected, actual, TOLERANCE, what);
    }

    /// Every channel of `actual` within `tolerance` levels of `expected`, and
    /// alpha opaque.
    private static void assertNear(Fsr1Reference.Rgb expected, byte[] actual, int tolerance, String what) {
        var want = expected.toBgra();
        assertEquals(want.length, actual.length, what + ": the size");
        var worst = 0;
        var at = -1;
        for (var i = 0; i < want.length; i++) {
            var difference = Math.abs((want[i] & 0xFF) - (actual[i] & 0xFF));
            if (difference > worst) {
                worst = difference;
                at = i;
            }
        }
        var pixel = at / 4;
        assertTrue(
                worst <= tolerance,
                what + ": " + worst + " levels apart at (" + pixel % expected.width() + ", " + pixel / expected.width()
                        + ") channel " + at % 4);
    }

    @Nested
    @DisplayName("with FSR 1")
    class Fsr1 {

        @ParameterizedTest(name = "{0}x{1} to {2}x{3}")
        @CsvSource({"48, 36, 96, 72", "48, 36, 64, 48", "50, 30, 77, 46"})
        @DisplayName("upscales with EASU and sharpens with RCAS as the reference does")
        void easuThenRcas(int fromWidth, int fromHeight, int width, int height) {
            var input = TestPattern.of(fromWidth, fromHeight);
            var expected = Fsr1Reference.rcas(
                    Fsr1Reference.easu(input, width, height).quantized(), RenderScale.DEFAULT_SHARPNESS);
            assertNear(
                    expected,
                    upscale(input, width, height, UpscaleFilter.FSR1, RenderScale.DEFAULT_SHARPNESS),
                    CHAIN_TOLERANCE,
                    "EASU then RCAS");
        }

        @Test
        @DisplayName("upscales with EASU alone at no sharpness")
        void easuAlone() {
            var input = TestPattern.of(48, 36);
            assertNear(Fsr1Reference.easu(input, 96, 72), upscale(input, 96, 72, UpscaleFilter.FSR1, 0), "EASU alone");
        }

        @Test
        @DisplayName("sharpens with RCAS alone at the same size, as hard as asked")
        void rcasAlone() {
            var input = Fsr1Reference.linear(TestPattern.of(32, 24), 64, 48).quantized();
            for (var sharpness : new double[] {0.25, 1}) {
                assertNear(
                        Fsr1Reference.rcas(input, sharpness),
                        upscale(input, 64, 48, UpscaleFilter.FSR1, sharpness),
                        "RCAS at " + sharpness);
            }
        }
    }

    @Nested
    @DisplayName("bilinearly")
    class Linear {

        @Test
        @DisplayName("upscales from texel centres, clamped at the edges")
        void upscales() {
            var input = TestPattern.of(40, 30);
            assertNear(
                    Fsr1Reference.linear(input, 100, 75),
                    upscale(input, 100, 75, UpscaleFilter.LINEAR, 0),
                    "bilinear, 2.5x");
        }

        @Test
        @DisplayName("brings a picture drawn at twice the size down to a box filter, whatever the filter asked")
        void downscales() {
            var input = TestPattern.of(96, 72);
            var expected = Fsr1Reference.linear(input, 48, 36);
            assertNear(expected, upscale(input, 48, 36, UpscaleFilter.LINEAR, 0), "linear");
            assertNear(expected, upscale(input, 48, 36, UpscaleFilter.FSR1, 1), "FSR 1 asked");
        }
    }

    @Test
    @DisplayName("refuses what it cannot draw from or into, a foreign frame, and use once closed")
    void refuses() throws Exception {
        try (var upscaler = new Upscaler(device);
                var sampled = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 8, 8));
                var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 16, 16));
                var unsampled = device.createTexture(
                        new TextureSpec(TextureFormat.B8G8R8A8_UNORM, 8, 8, EnumSet.of(TextureUsage.COLOR_TARGET)));
                var frame = device.beginFrame()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> upscaler.upscale(frame, unsampled, target, UpscaleFilter.LINEAR, 0),
                    "a source that cannot be sampled");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> upscaler.upscale(frame, target, sampled, UpscaleFilter.LINEAR, 0),
                    "a target that cannot be rendered into");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> upscaler.upscale(frame, target, target, UpscaleFilter.LINEAR, 0),
                    "itself");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> upscaler.upscale(frame, sampled, target, UpscaleFilter.FSR1, 1.5),
                    "a sharpness past 1");
            assertEquals(device, upscaler.device());
            closeTwice(upscaler);
            assertThrows(
                    IllegalStateException.class,
                    () -> upscaler.upscale(frame, sampled, target, UpscaleFilter.LINEAR, 0),
                    "closed");
            assertTrue(upscaler.toString().endsWith("closed]"), upscaler.toString());
        }
    }

    @Test
    @DisplayName("keeps the texture between EASU and RCAS while the size stays, and releases all of it when closed")
    void keepsWhatItMade() {
        var input = TestPattern.of(16, 12);
        try (var upscaler = new Upscaler(device);
                var source = device.createTexture(TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, 16, 12));
                var target = device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, 32, 24))) {
            try (var frame = device.beginFrame()) {
                frame.copyPass(copy -> copy.upload(source, ByteBuffer.wrap(input.toBgra())));
                upscaler.upscale(frame, source, target, UpscaleFilter.FSR1, 1);
                frame.submit();
            }
            var afterFirst = harness.openResources();
            try (var frame = device.beginFrame()) {
                upscaler.upscale(frame, source, target, UpscaleFilter.FSR1, 1);
                frame.submit();
            }
            assertEquals(afterFirst, harness.openResources(), "nothing new made for the same sizes");
        }
    }

    /// Closed early, and again: closing is idempotent.
    private static void closeTwice(AutoCloseable resource) throws Exception {
        resource.close();
        resource.close();
    }
}
