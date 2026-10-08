package dev.goldberry.media.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.Readback;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.gpu.render.YuvConversion;
import dev.goldberry.gpu.render.YuvLayout;
import dev.goldberry.gpu.render.YuvMatrix;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.render.model.PhysicalRect;

/// [PictureRenderer] on a real device: an NV12 picture converted into one layer
/// of an array texture, the others left as they were, and a part of a picture
/// stretched over a whole texture.
///
/// Pictures are drawn 1:1, so the linear sampler reads texel centres, and a
/// colour is the conversion's alone, within a step of rounding.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a picture renderer on the GPU")
class PictureRendererOnGpuTest {

    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;
    private static final int CLEAR = 0xFF00FF00;

    /// Two colours, as luma, Cb and Cr codes: they differ in luma alone, so a
    /// picture of both halves has one chroma throughout.
    private static final int[] LEFT = {81, 90, 240};
    private static final int[] RIGHT = {170, 90, 240};

    private static final YuvConversion CONVERSION = new YuvConversion(YuvLayout.NV12, YuvMatrix.BT709, false);

    private static CompositeHarness harness;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
    }

    @AfterAll
    static void close() {
        if (harness != null) {
            harness.close();
        }
    }

    /// An NV12 picture whose left half is `LEFT` and right half `RIGHT`, rows
    /// padded to show the strides are honoured.
    private static VideoPlanes halves() {
        var stride = WIDTH + 8;
        var luma = ByteBuffer.allocateDirect(stride * HEIGHT);
        var chroma = ByteBuffer.allocateDirect(stride * HEIGHT / 2);
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                luma.put(y * stride + x, (byte) (x < WIDTH / 2 ? LEFT[0] : RIGHT[0]));
            }
        }
        for (var y = 0; y < HEIGHT / 2; y++) {
            for (var x = 0; x < WIDTH / 2; x++) {
                chroma.put(y * stride + x * 2, (byte) LEFT[1]);
                chroma.put(y * stride + x * 2 + 1, (byte) LEFT[2]);
            }
        }
        return new VideoPlanes(
                PixelFormat.NV12,
                WIDTH,
                HEIGHT,
                List.of(luma, chroma),
                List.of(stride, stride),
                VideoFrame.ColorMatrix.BT709,
                false,
                0);
    }

    /// Asserts the pixel at `x, y` of `pixels`, a `width`-wide read-back, is
    /// `codes` converted, within one step a channel.
    private static void assertConverted(int[] codes, ByteBuffer pixels, int width, int x, int y, String where) {
        var expected = CONVERSION.toRgbBytes(codes[0], codes[1], codes[2]);
        var argb = pixels.getInt((y * width + x) * 4);
        var at = where + " at (" + x + ", " + y + "): " + Integer.toHexString(argb);
        assertEquals(0xFF, argb >>> 24, at);
        assertTrue(Math.abs(((argb >> 16) & 0xFF) - expected[0]) <= 1, at + " red");
        assertTrue(Math.abs(((argb >> 8) & 0xFF) - expected[1]) <= 1, at + " green");
        assertTrue(Math.abs((argb & 0xFF) - expected[2]) <= 1, at + " blue");
    }

    @Test
    @DisplayName("converts a picture of planes into one layer of an array, and leaves the other layers alone")
    void intoOneLayer() {
        var device = harness.device();
        try (var renderer = new PictureRenderer();
                var array = device.createTexture(TextureSpec.array(
                        TextureFormat.B8G8R8A8_UNORM,
                        WIDTH,
                        HEIGHT,
                        4,
                        EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
                var frame = device.beginFrame()) {
            for (var layer = 0; layer < 4; layer++) {
                frame.renderPass(array.layer(layer), Load.clear(0, 1, 0, 1), _ -> {});
            }
            var picture = halves();
            renderer.render(frame, picture, array.layer(2));
            var layers = new ArrayList<Readback>();
            for (var layer = 0; layer < 4; layer++) {
                layers.add(frame.readback(array.layer(layer)));
            }
            frame.submit();
            for (var layer = 0; layer < 4; layer++) {
                var pixels = layers.get(layer).awaitPixels().pixels().order(ByteOrder.LITTLE_ENDIAN);
                for (var y = 0; y < HEIGHT; y++) {
                    for (var x = 0; x < WIDTH; x++) {
                        if (layer == 2) {
                            assertConverted(x < WIDTH / 2 ? LEFT : RIGHT, pixels, WIDTH, x, y, "layer 2");
                        } else {
                            var at = (y * WIDTH + x) * 4;
                            assertEquals(CLEAR, pixels.getInt(at), "layer " + layer + " keeps its clear colour");
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("stretches a part of a picture over the whole of a texture, and refuses a part outside it")
    void sourceRectangle() {
        var device = harness.device();
        try (var renderer = new PictureRenderer();
                var target = device.createTexture(
                        TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH / 2, HEIGHT));
                var frame = device.beginFrame()) {
            var picture = halves();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> renderer.render(frame, picture, PhysicalRect.of(WIDTH / 2, 0, WIDTH, HEIGHT), target));
            renderer.render(frame, picture, PhysicalRect.of(WIDTH / 2, 0, WIDTH / 2, HEIGHT), target);
            var readback = frame.readback(target);
            frame.submit();
            var pixels = readback.awaitPixels().pixels().order(ByteOrder.LITTLE_ENDIAN);
            for (var y = 0; y < HEIGHT; y++) {
                for (var x = 0; x < WIDTH / 2; x++) {
                    assertConverted(RIGHT, pixels, WIDTH / 2, x, y, "the right half");
                }
            }
        }
    }

    @Test
    @DisplayName("refuses a target of another format, and refuses everything once closed")
    void refusals() {
        var device = harness.device();
        var renderer = new PictureRenderer();
        var picture = halves();
        try (var rgba = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, WIDTH, HEIGHT));
                var bgra =
                        device.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH, HEIGHT))) {
            try (var frame = device.beginFrame()) {
                assertThrows(IllegalArgumentException.class, () -> renderer.render(frame, picture, rgba));
                renderer.render(frame, picture, bgra);
                frame.submit();
            }
            renderer.close();
            try (var frame = device.beginFrame()) {
                assertThrows(IllegalStateException.class, () -> renderer.render(frame, picture, bgra));
                frame.submit();
            }
        }
    }
}
