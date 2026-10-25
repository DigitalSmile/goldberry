package dev.goldberry.gpu.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

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
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.window.GpuSurface;

/// [VideoLayer] on a real device: every layout converted as the reference says,
/// BGRA drawn as it is, a part of a picture stretched over the box, a picture
/// uploaded once however often it is drawn, and one drawn into a layer and a
/// level of a texture of the caller's. This is how `video-view`
/// shows its pictures when the GPU is present.
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
        return uniform(layout, matrix, fullRange, codes, ColorVolume.SDR);
    }

    private static VideoImage.Planes uniform(
            PlaneLayout layout, ColorMatrix matrix, boolean fullRange, int[] codes, ColorVolume colour) {
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
        return new VideoImage.Planes(layout, WIDTH, HEIGHT, planes, strides, matrix, fullRange, colour);
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

    @Test
    @DisplayName("maps HDR and wide gamut to SDR BT.709 as the reference does, within one step")
    void mapsHdr() {
        var volumes = new ColorVolume[] {
            new ColorVolume(ColorVolume.Primaries.BT2020, ColorVolume.Transfer.PQ, 1000),
            new ColorVolume(ColorVolume.Primaries.BT2020, ColorVolume.Transfer.PQ, 4000),
            new ColorVolume(ColorVolume.Primaries.BT709, ColorVolume.Transfer.PQ, 150),
            new ColorVolume(ColorVolume.Primaries.BT2020, ColorVolume.Transfer.HLG, 1000),
            new ColorVolume(ColorVolume.Primaries.BT2020, ColorVolume.Transfer.SDR, 203),
            new ColorVolume(ColorVolume.Primaries.DISPLAY_P3, ColorVolume.Transfer.SDR, 203)
        };
        var codes10 = new int[][] {{940, 512, 512}, {502, 512, 512}, {820, 312, 632}, {388, 460, 700}, {64, 512, 512}};
        try (var layer = new VideoLayer()) {
            for (var layout : new PlaneLayout[] {PlaneLayout.P010, PlaneLayout.I010, PlaneLayout.NV12}) {
                for (var volume : volumes) {
                    var conversion = new YuvConversion(layout.yuv(), ColorMatrix.BT2020.yuv(), false);
                    var mapping = volume.toneMapping();
                    for (var colour : codes10) {
                        var shift = 10 - layout.yuv().bitDepth();
                        var codes = new int[] {colour[0] >> shift, colour[1] >> shift, colour[2] >> shift};
                        layer.show(uniform(layout, ColorMatrix.BT2020, false, codes, volume));
                        var picture = readBack(layer, WIDTH, HEIGHT);
                        var mapped = mapping.apply(conversion.toRgb(codes[0], codes[1], codes[2]));
                        var argb = picture.argb(WIDTH / 2, HEIGHT / 2);
                        var where = layout + " " + volume + " " + Arrays.toString(codes);
                        assertEquals((int) Math.round(mapped[0] * 255), (argb >> 16) & 0xFF, 1, where + " red");
                        assertEquals((int) Math.round(mapped[1] * 255), (argb >> 8) & 0xFF, 1, where + " green");
                        assertEquals((int) Math.round(mapped[2] * 255), argb & 0xFF, 1, where + " blue");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("staged planes, written on another thread, draw as planes do, and their memory comes back")
    void stagedPlanes() throws InterruptedException {
        var codes = new int[] {145, 54, 34};
        try (var layer = new VideoLayer()) {
            var staging = layer.staging();
            assertTrue(staging.obtain(PlaneLayout.NV12, WIDTH, HEIGHT).isEmpty(), "nothing made before a render");
            layer.show(uniform(PlaneLayout.NV12, ColorMatrix.BT709, false, codes));
            readBack(layer, WIDTH, HEIGHT);
            var planes = staging.obtain(PlaneLayout.NV12, WIDTH, HEIGHT).orElseThrow();
            assertEquals(256, planes.stride(0), "rows padded to 256 bytes");
            var writer = Thread.ofPlatform().start(() -> {
                var luma = planes.plane(0);
                var chroma = planes.plane(1);
                for (var y = 0; y < HEIGHT; y++) {
                    for (var x = 0; x < WIDTH; x++) {
                        luma.put(y * planes.stride(0) + x, (byte) 200);
                    }
                }
                for (var y = 0; y < HEIGHT / 2; y++) {
                    for (var x = 0; x < WIDTH / 2; x++) {
                        chroma.put(y * planes.stride(1) + 2 * x, (byte) 90);
                        chroma.put(y * planes.stride(1) + 2 * x + 1, (byte) 160);
                    }
                }
            });
            writer.join();
            layer.show(new VideoImage.Staged(planes, ColorMatrix.BT709, false, ColorVolume.SDR));
            var picture = readBack(layer, WIDTH, HEIGHT);
            var expected =
                    new YuvConversion(PlaneLayout.NV12.yuv(), ColorMatrix.BT709.yuv(), false).toRgbBytes(200, 90, 160);
            var argb = picture.argb(WIDTH / 2, HEIGHT / 2);
            assertEquals(expected[0], (argb >> 16) & 0xFF, 1);
            assertEquals(expected[1], (argb >> 8) & 0xFF, 1);
            assertEquals(expected[2], argb & 0xFF, 1);
            assertEquals(200, planes.plane(0).get(0) & 0xFF, "still readable after the upload");
            assertTrue(planes.isAlive());

            planes.release();
            assertFalse(planes.isAlive());
            readBack(layer, WIDTH, HEIGHT);
            var again = staging.obtain(PlaneLayout.NV12, WIDTH, HEIGHT);
            assertTrue(again.isPresent(), "released memory is handed out again after a render");
            again.get().release();
        }
    }

    @Test
    @DisplayName("staged planes of a closed layer are no longer alive, and nothing more is handed out")
    void stagingCloses() {
        var layer = new VideoLayer();
        var staging = layer.staging();
        staging.obtain(PlaneLayout.I420, WIDTH, HEIGHT);
        layer.show(uniform(PlaneLayout.I420, ColorMatrix.BT601, false, new int[] {81, 90, 240}));
        readBack(layer, WIDTH, HEIGHT);
        var planes = staging.obtain(PlaneLayout.I420, WIDTH, HEIGHT).orElseThrow();
        layer.close();
        assertFalse(planes.isAlive());
        assertThrows(IllegalStateException.class, () -> planes.plane(0));
        assertTrue(staging.obtain(PlaneLayout.I420, WIDTH, HEIGHT).isEmpty());
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
    @DisplayName("draws into one layer of an array and one level of a mip chain, leaving the rest as it was")
    void intoATextureView() {
        var red = 0xFFC03020;
        var blue = 0xFF2040D0;
        var device = harness.device();
        try (var layer = new VideoLayer();
                var array = device.createTexture(TextureSpec.array(
                        TextureFormat.B8G8R8A8_UNORM,
                        WIDTH,
                        HEIGHT,
                        4,
                        EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER)));
                var chain = device.createTexture(
                        TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, WIDTH * 2, HEIGHT * 2)
                                .withMipLevels(2));
                var frame = device.beginFrame()) {
            for (var index = 0; index < 4; index++) {
                frame.renderPass(array.layer(index), Load.clear(0, 1, 0, 1), _ -> {});
            }
            frame.renderPass(chain.level(0), Load.clear(0, 1, 0, 1), _ -> {});
            layer.show(halves(red, blue));
            layer.render(frame, array.layer(2));
            layer.render(frame, chain.level(1));
            var layers = new ArrayList<Readback>();
            for (var index = 0; index < 4; index++) {
                layers.add(frame.readback(array.layer(index)));
            }
            var level0 = frame.readback(chain.level(0));
            var level1 = frame.readback(chain.level(1));
            frame.submit();
            for (var index = 0; index < 4; index++) {
                var pixels = layers.get(index).awaitPixels().pixels().order(ByteOrder.LITTLE_ENDIAN);
                if (index == 2) {
                    assertEquals(red, pixels.getInt(0), "layer 2, left");
                    assertEquals(blue, pixels.getInt((WIDTH - 1) * 4), "layer 2, right");
                } else {
                    for (var at = 0; at < WIDTH * HEIGHT * 4; at += 4) {
                        assertEquals(0xFF00FF00, pixels.getInt(at), "layer " + index + " keeps its clear colour");
                    }
                }
            }
            var top = level0.awaitPixels().pixels().order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0xFF00FF00, top.getInt(0), "level 0 keeps its clear colour");
            var drawn = level1.awaitPixels();
            assertEquals(WIDTH, drawn.size().width(), "level 1 is half the texture's width");
            var half = drawn.pixels().order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(red, half.getInt(0), "level 1, left");
            assertEquals(blue, half.getInt((WIDTH - 1) * 4), "level 1, right");
        }
    }

    @Test
    @DisplayName("refuses a target of another format than BGRA")
    void refusesAnotherFormat() {
        var device = harness.device();
        try (var layer = new VideoLayer();
                var rgba = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 4, 4));
                var frame = device.beginFrame()) {
            layer.show(halves(0xFF808080, 0xFF808080));
            assertThrows(IllegalArgumentException.class, () -> layer.render(frame, rgba));
            assertThrows(IllegalArgumentException.class, () -> layer.render(frame, rgba.layer(0)));
            frame.submit();
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
