package dev.goldberry.gpu.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.gpu.render.YuvConversion;
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.window.GpuSurface;

/// [VideoLayer] on a real device: every layout converted as the reference says,
/// BGRA drawn as it is, a part of a picture stretched over the box, and a
/// picture uploaded once however often it is drawn (`docs/gpu-plan.md`,
/// phase 6; ADR-0484).
///
/// Pictures are drawn 1:1, so the linear sampler reads texel centres and the
/// result is the conversion's alone.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a video layer on the GPU")
class VideoLayerTest {

    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;

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

    /// `layer` placed over the whole of a `width × height` picture, read back.
    private static Image drawn(VideoLayer layer, int width, int height, GpuSurface surface) {
        return Offscreen.of(width, height)
                .gpu(surface)
                .paint((frame, size) -> assertTrue(frame.gpuLayer(layer, 0, 0, size.width(), size.height())));
    }

    private static Image readBack(VideoLayer layer, int width, int height) {
        return harness.readBack(surface -> drawn(layer, width, height, surface));
    }

    /// A picture of one colour, `codes` at the layout's depth, stored as the
    /// layout stores it, rows padded to show strides are honoured.
    private static VideoImage.Planes uniform(PlaneLayout layout, ColorMatrix matrix, boolean fullRange, int[] codes) {
        var wide = layout.yuv().bitDepth() > 8;
        var shift = layout == PlaneLayout.P010 ? 6 : 0;
        var planes = new ArrayList<ByteBuffer>();
        var strides = new ArrayList<Integer>();
        for (var plane = 0; plane < layout.planes(); plane++) {
            var format = layout.textureFormats().get(plane);
            var width = layout.planeWidth(plane, WIDTH);
            var rows = layout.planeHeight(plane, HEIGHT);
            var stride = width * format.bytesPerPixel() + 8;
            var buffer = ByteBuffer.allocateDirect(stride * rows).order(ByteOrder.LITTLE_ENDIAN);
            var components = format.bytesPerPixel() / (wide ? 2 : 1);
            for (var row = 0; row < rows; row++) {
                for (var x = 0; x < width; x++) {
                    for (var component = 0; component < components; component++) {
                        // Luma, or Cb then Cr interleaved, or the plane's own chroma.
                        var code = plane == 0 ? codes[0] : components == 2 ? codes[1 + component] : codes[plane];
                        var at = row * stride + (x * components + component) * (wide ? 2 : 1);
                        if (wide) {
                            buffer.putShort(at, (short) (code << shift));
                        } else {
                            buffer.put(at, (byte) code);
                        }
                    }
                }
            }
            planes.add(buffer);
            strides.add(stride);
        }
        return new VideoImage.Planes(layout, WIDTH, HEIGHT, planes, strides, matrix, fullRange);
    }

    @Test
    @DisplayName("converts every layout, matrix and range as the reference does, within one step")
    void convertsEveryLayout() {
        var codes8 = new int[][] {{81, 90, 240}, {145, 54, 34}, {126, 128, 128}, {200, 20, 250}};
        try (var layer = new VideoLayer()) {
            for (var layout : PlaneLayout.values()) {
                for (var matrix : ColorMatrix.values()) {
                    for (var fullRange : new boolean[] {false, true}) {
                        var conversion = new YuvConversion(layout.yuv(), matrix.yuv(), fullRange);
                        for (var colour : codes8) {
                            var shift = layout.yuv().bitDepth() - 8;
                            var codes = new int[] {colour[0] << shift, colour[1] << shift, colour[2] << shift};
                            layer.show(uniform(layout, matrix, fullRange, codes));
                            var picture = readBack(layer, WIDTH, HEIGHT);
                            var expected = conversion.toRgbBytes(codes[0], codes[1], codes[2]);
                            for (var y = 0; y < HEIGHT; y++) {
                                for (var x = 0; x < WIDTH; x++) {
                                    var argb = picture.argb(x, y);
                                    var where = conversion + " at (" + x + ", " + y + ")";
                                    assertEquals(0xFF, argb >>> 24, where);
                                    assertTrue(Math.abs(((argb >> 16) & 0xFF) - expected[0]) <= 1, where + " red");
                                    assertTrue(Math.abs(((argb >> 8) & 0xFF) - expected[1]) <= 1, where + " green");
                                    assertTrue(Math.abs((argb & 0xFF) - expected[2]) <= 1, where + " blue");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /// A BGRA picture whose left half is `left` and right half `right`.
    private static VideoImage.Bgra halves(int left, int right) {
        var stride = WIDTH * 4 + 16;
        var pixels = ByteBuffer.allocateDirect(stride * HEIGHT).order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                pixels.putInt(y * stride + x * 4, x < WIDTH / 2 ? left : right);
            }
        }
        return new VideoImage.Bgra(WIDTH, HEIGHT, stride, pixels);
    }

    @Test
    @DisplayName("draws BGRA as it is, and a part of it over the whole box")
    void bgraAndAPart() {
        var red = 0xFFC03020;
        var blue = 0xFF2040D0;
        try (var layer = new VideoLayer()) {
            layer.show(halves(red, blue));
            var whole = readBack(layer, WIDTH, HEIGHT);
            assertEquals(red, whole.argb(0, 0));
            assertEquals(red, whole.argb(WIDTH / 2 - 1, HEIGHT - 1));
            assertEquals(blue, whole.argb(WIDTH / 2, 0));
            assertEquals(blue, whole.argb(WIDTH - 1, HEIGHT - 1));

            // The right half, drawn over a box its own size: blue, every pixel.
            layer.show(layer.image(), PhysicalRect.of(WIDTH / 2, 0, WIDTH / 2, HEIGHT));
            var part = readBack(layer, WIDTH / 2, HEIGHT);
            for (var y = 0; y < HEIGHT; y++) {
                for (var x = 0; x < WIDTH / 2; x++) {
                    assertEquals(blue, part.argb(x, y), "(" + x + ", " + y + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("uploads a picture once, however often it is drawn, and a new picture at once")
    void uploadsOnce() {
        var red = 0xFFC03020;
        var green = 0xFF30B040;
        try (var layer = new VideoLayer();
                var surface = harness.readBackSurface()) {
            var picture = halves(red, red);
            layer.show(picture);
            assertEquals(red, drawn(layer, WIDTH, HEIGHT, surface).argb(3, 3));
            assertFalse(layer.needsRender(), "drawn, and nothing new");

            // The same picture with other bytes, drawn at another size so the
            // layer is rendered again: the texture still holds the upload.
            picture.pixels().putInt(3 * (WIDTH * 4 + 16) + 3 * 4, green);
            assertEquals(red, drawn(layer, WIDTH * 2, HEIGHT * 2, surface).argb(6, 6));

            var next = halves(green, green);
            layer.show(next);
            assertTrue(layer.needsRender());
            assertEquals(green, drawn(layer, WIDTH, HEIGHT, surface).argb(3, 3));
        }
    }

    @Test
    @DisplayName("shows the same picture composited as read back")
    void compositedAsReadBack() {
        try (var layer = new VideoLayer()) {
            layer.show(uniform(PlaneLayout.I420, ColorMatrix.BT601, false, new int[] {81, 90, 240}));
            var composited = harness.composited(surface -> drawn(layer, WIDTH, HEIGHT, surface));
            assertEquals(1, composited.placed().size());
            CompositeHarness.assertSamePicture("video-layer", composited.image(), readBack(layer, WIDTH, HEIGHT));
        }
    }

    @Test
    @DisplayName("before a picture it is black; on another device it starts again; closed, it refuses")
    void lifecycle() {
        var layer = new VideoLayer();
        assertEquals(0xFF000000, readBack(layer, 4, 4).argb(1, 1));

        layer.show(halves(0xFF808080, 0xFF808080));
        readBack(layer, WIDTH, HEIGHT);
        var other = harness.otherDevice();
        try (var target = other.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH, HEIGHT));
                var frame = other.beginFrame()) {
            layer.render(frame, target);
            var readback = frame.readback(target);
            frame.submit();
            var pixels = readback.awaitPixels().pixels().order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0xFF808080, pixels.getInt(0), "uploaded again on the new device");
        }
        harness.closeOthers();

        layer.close();
        try (var target = harness.device()
                        .createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH, HEIGHT));
                var frame = harness.device().beginFrame()) {
            assertThrows(IllegalStateException.class, () -> layer.render(frame, target));
        }
    }
}
