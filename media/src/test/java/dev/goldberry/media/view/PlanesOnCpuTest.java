package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.image.Image;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.PushedPictures;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.image.Fit;

/// Pictures of planes drawn where no GPU shows them: converted to BGRA once a
/// picture, as a call's tile or a camera is drawn in a window presented on the
/// CPU and in every offscreen picture.
@DisplayName("planes on the CPU")
class PlanesOnCpuTest {

    /// Eight bars, as a test card has them: white, yellow, cyan, green,
    /// magenta, red, blue, black.
    private static final int[] BARS = {
        0xFFFFFFFF, 0xFFFFFF00, 0xFF00FFFF, 0xFF00FF00, 0xFFFF00FF, 0xFFFF0000, 0xFF0000FF, 0xFF000000,
    };

    @BeforeEach
    void natives() {
        RendererRequirement.enforce();
        MediaNativesRequirement.enforce();
    }

    /// BT.709 limited-range Y'CbCr of `argb`, as `{y, cb, cr}`.
    private static int[] yuv(int argb) {
        var r = ((argb >> 16) & 0xFF) / 255.0;
        var g = ((argb >> 8) & 0xFF) / 255.0;
        var b = (argb & 0xFF) / 255.0;
        var y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        var cb = (b - y) / 1.8556;
        var cr = (r - y) / 1.5748;
        return new int[] {
            (int) Math.round(16 + 219 * y), (int) Math.round(128 + 224 * cb), (int) Math.round(128 + 224 * cr)
        };
    }

    /// The bars, `width`×`height`, in `format`: NV12 or I420.
    static VideoPlanes bars(PixelFormat format, int width, int height) {
        var planes = new ArrayList<ByteBuffer>();
        var strides = new ArrayList<Integer>();
        for (var plane = 0; plane < format.planes(); plane++) {
            var stride = format.planeRowBytes(plane, width);
            planes.add(ByteBuffer.allocateDirect(stride * format.planeRows(plane, height)));
            strides.add(stride);
        }
        for (var row = 0; row < height; row++) {
            for (var column = 0; column < width; column++) {
                var colour = yuv(BARS[column * BARS.length / width]);
                planes.getFirst().put(row * strides.getFirst() + column, (byte) colour[0]);
                if (row % 2 == 0 && column % 2 == 0) {
                    var chromaRow = row / 2;
                    var chromaColumn = column / 2;
                    if (format == PixelFormat.NV12) {
                        var at = chromaRow * strides.get(1) + chromaColumn * 2;
                        planes.get(1).put(at, (byte) colour[1]);
                        planes.get(1).put(at + 1, (byte) colour[2]);
                    } else {
                        planes.get(1).put(chromaRow * strides.get(1) + chromaColumn, (byte) colour[1]);
                        planes.get(2).put(chromaRow * strides.get(2) + chromaColumn, (byte) colour[2]);
                    }
                }
            }
        }
        return new VideoPlanes(format, width, height, planes, strides, VideoFrame.ColorMatrix.BT709, false, 0);
    }

    private static void assertNear(int expected, Image image, int x, int y) {
        var actual = image.argb(x, y);
        for (var shift = 0; shift <= 16; shift += 8) {
            var channel = shift;
            assertTrue(
                    Math.abs(((expected >> channel) & 0xFF) - ((actual >> channel) & 0xFF)) <= 6,
                    () -> "at (" + x + ", " + y + "): expected " + Integer.toHexString(expected) + ", found "
                            + Integer.toHexString(actual));
        }
    }

    @Test
    @DisplayName("converts NV12 and I420 to the colours they were made from")
    void colours() {
        try (var cpu = new PlanesOnCpu()) {
            for (var format : List.of(PixelFormat.NV12, PixelFormat.I420)) {
                var image = cpu.image(bars(format, 64, 16));
                assertNotNull(image, format.toString());
                for (var bar = 0; bar < BARS.length; bar++) {
                    assertNear(BARS[bar], image, bar * 8 + 4, 8);
                }
            }
        }
    }

    @Test
    @DisplayName("converts a picture once, however many frames draw it, and nothing once closed")
    void once() {
        var cpu = new PlanesOnCpu();
        var picture = bars(PixelFormat.NV12, 32, 8);
        var first = cpu.image(picture);
        assertSame(first, cpu.image(picture));
        cpu.close();
        assertNull(cpu.image(bars(PixelFormat.NV12, 32, 8)));
    }

    @Test
    @DisplayName("lets a picture-view draw pushed planes where the frame has no GPU")
    void drawn() {
        var nv12 = new PushedPictures();
        nv12.push(bars(PixelFormat.NV12, 320, 180));
        var i420 = new PushedPictures();
        i420.push(bars(PixelFormat.I420, 320, 180));
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "#scene { padding: 12px; gap: 12px; background: var(--gb-surface); }"
                        + " video-view { width: 160px; height: 90px; }"));
        var scene = new Row(
                List.of(new PictureView(nv12, Fit.CONTAIN), new PictureView(i420, Fit.COVER)),
                Attributes.NONE.id("scene"));
        GoldenImage.assertMatches(
                "picture-view-planes",
                356,
                114,
                1f,
                (size, scale) ->
                        Offscreen.of(size).scale(scale).stylesheets(sheets).render(scene));
    }
}
