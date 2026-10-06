package dev.goldberry.gpu.offscreen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.golden.Tolerance;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.gpu.view.TestCube;
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;

/// The public way to a picture with a `canvas3d` in it: an application's test
/// opens an [OffscreenGpu], hands its surface to `Offscreen`, and gets the
/// scene with the canvas rendered, the same picture the harness's read-back
/// surface gives and the window shows.
@Tag(GpuTestLauncher.TAG)
@DisplayName("an offscreen GPU")
class OffscreenGpuTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 160;

    private static SdlGpuDevice required;
    private static Font font;

    /// The device requirement first: without the library or a device the test
    /// skips, or fails where the lane required one, before a font is asked for.
    @BeforeAll
    static void open() {
        required = GpuDeviceRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterAll
    static void close() {
        if (font != null) {
            font.close();
        }
        if (required != null) {
            required.close();
        }
    }

    /// The scene's background, which is what shows where the canvas is when
    /// nothing renders it.
    private static final int BACKGROUND = 0xFF2E3440;

    /// The stylesheets of the scene: the canvas 160 by 120 inside 20 of padding.
    private static List<Stylesheet> sheets() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(Stylesheet.parse(
                CascadeLayer.APPLICATION,
                "#scene { padding: 20px; flex-grow: 1; background: #2e3440; }"
                        + " canvas3d { width: 160px; height: 120px; }"));
        return sheets;
    }

    private static Column scene(Canvas3d canvas) {
        return new Column(List.of(canvas), Attributes.NONE.id("scene"));
    }

    /// The scene of `Canvas3dGoldenTest`, so the two pictures share a golden.
    private static final class Mounted implements AutoCloseable {
        final ElementTree tree;
        final WidgetRenderer renderer;

        Mounted(Canvas3d canvas) {
            tree = new ElementTree(scene(canvas));
            renderer = new WidgetRenderer(sheets(), font);
        }

        Image picture(PhysicalSize size, float scale, GpuSurface surface) {
            return Offscreen.of(size)
                    .scale(scale)
                    .gpu(surface)
                    .paint((frame, logical) -> BoxPainter.paint(frame, renderer.render(tree)));
        }

        @Override
        public void close() {
            tree.unmount();
        }
    }

    @Test
    @DisplayName("renders a canvas3d through Offscreen to the picture the harness and a window give")
    void rendersTheCanvas() {
        var cube = new TestCube();
        try (var gpu = OffscreenGpu.open()) {
            assertFalse(gpu.isClosed());
            assertSame(gpu.surface(), gpu.surface(), "one surface");
            assertFalse(gpu.device().isClosed());
            assertTrue(Sdl.get().wasInit().contains(SdlSubsystem.VIDEO), "video is up while it is open");
            GoldenImage.assertMatchesAtOneScale("canvas3d-cube", WIDTH, HEIGHT, 1f, Tolerance.GPU, (at, scale) -> {
                try (var scene = new Mounted(new Canvas3d(cube).depth(Canvas3d.Depth.D16))) {
                    return scene.picture(at, scale.factor(), gpu.surface());
                }
            });
            assertEquals("init", cube.calls.getFirst(), "the renderer was initialised on the offscreen device");
            assertEquals("dispose", cube.calls.getLast(), "and disposed with the tree");
        }
    }

    @Test
    @DisplayName("renders the canvas in a session's frames and a strip's, as in a still render")
    void sessionsAndStrips() {
        try (var gpu = OffscreenGpu.open()) {
            var builder = Offscreen.of(WIDTH, HEIGHT).stylesheets(sheets()).gpu(gpu.surface());
            var still = builder.render(scene(new Canvas3d(new TestCube()).depth(Canvas3d.Depth.D16)));
            var centre = still.argb(WIDTH / 2, HEIGHT / 2);
            assertNotEquals(BACKGROUND, centre, "the still render has the cube");
            try (var session = builder.session(scene(new Canvas3d(new TestCube()).depth(Canvas3d.Depth.D16)))) {
                assertClose(centre, session.frame().argb(WIDTH / 2, HEIGHT / 2), "a session's first frame");
                session.advance(Duration.ofMillis(16));
                assertClose(centre, session.frame().argb(WIDTH / 2, HEIGHT / 2), "and one after the clock moved");
            }
            try (var strip = builder.strip(scene(new Canvas3d(new TestCube()).depth(Canvas3d.Depth.D16)))) {
                assertClose(centre, strip.frame().argb(WIDTH / 2, HEIGHT / 2), "a strip's frame");
            }
        }
    }

    /// Each channel of `actual` within two levels of `expected`'s: the same
    /// draw on the same device, read back twice.
    private static void assertClose(int expected, int actual, String what) {
        for (var shift = 0; shift < 32; shift += 8) {
            assertEquals(
                    (expected >>> shift) & 0xFF,
                    (actual >>> shift) & 0xFF,
                    2,
                    what + ": " + Integer.toHexString(actual));
        }
    }

    @Test
    @DisplayName("closes once, and refuses its surface and device after")
    void closes() {
        var gpu = OffscreenGpu.open();
        var device = gpu.device();
        gpu.close();
        gpu.close();
        assertTrue(gpu.isClosed());
        assertTrue(device.isClosed(), "the device went with it");
        assertThrows(IllegalStateException.class, gpu::surface);
        assertThrows(IllegalStateException.class, gpu::device);
    }
}
