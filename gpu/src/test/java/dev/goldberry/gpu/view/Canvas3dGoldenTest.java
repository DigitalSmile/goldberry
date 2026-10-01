package dev.goldberry.gpu.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.image.Image;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;

/// `docs/gpu-plan.md` phase 5's golden: the cube at a fixed angle in a
/// `canvas3d`, a widget tree rendered both ways a window shows it, on a real
/// device (ADR-0482).
///
/// The angle is the renderer's at the frame's time, and `Offscreen`'s clock is
/// virtual, so the picture is the same on every run. At one scale: a 3D view is
/// rendered at its physical size, which a scale sweep does not describe
/// (ADR-0434).
@Tag(GpuTestLauncher.TAG)
@DisplayName("a canvas3d, composited and read back")
class Canvas3dGoldenTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 160;

    private static CompositeHarness harness;
    private static Font font;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterAll
    static void close() {
        if (font != null) {
            font.close();
        }
        if (harness != null) {
            harness.close();
        }
    }

    /// A scene of one canvas at 160x120 in 20 pixels of an opaque background, as
    /// a window's is -- a composited window shows what is transparent as black,
    /// and a read-back picture leaves it transparent -- mounted for as
    /// long as the test needs it, as a window's tree is. `Offscreen.render`
    /// would unmount it -- disposing the renderer -- before a composited picture
    /// had drawn its layer, which a window never does.
    private static final class Mounted implements AutoCloseable {
        final ElementTree tree;
        final WidgetRenderer renderer;

        Mounted(Canvas3d canvas) {
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.parse(
                    CascadeLayer.APPLICATION,
                    "#scene { padding: 20px; flex-grow: 1; background: #2e3440; }"
                            + " canvas3d { width: 160px; height: 120px; }"));
            tree = new ElementTree(new Column(List.of(canvas), Attributes.NONE.id("scene")));
            renderer = new WidgetRenderer(sheets, font);
        }

        /// The scene painted over `surface` at `size` and `scale`.
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

    private static Canvas3d cube(TestCube renderer) {
        return new Canvas3d(renderer).depth(Canvas3d.Depth.D16);
    }

    @Test
    @DisplayName("draws the lit cube with depth, the same both ways, and places it where the stylesheet put it")
    void cube() {
        var size = new PhysicalSize(WIDTH, HEIGHT);
        var cube = new TestCube();
        try (var scene = new Mounted(cube(cube))) {
            var composited = harness.composited(surface -> scene.picture(size, 1f, surface));
            assertEquals(1, composited.placed().size());
            assertEquals(
                    PhysicalRect.of(20, 20, 160, 120),
                    composited.placed().getFirst().target());
            assertEquals(List.of("init", "resize 160x120", "render 160x120"), cube.calls);

            var readBack = harness.readBack(surface -> scene.picture(size, 1f, surface));
            CompositeHarness.assertSamePicture("canvas3d-cube", composited.image(), readBack);
        }
        assertEquals("dispose", cube.calls.getLast(), "disposed with the tree");

        // Rasterized and lit by the GPU: blessed on Metal and compared on whatever
        // driver runs this, whose shading may round a level apart everywhere and
        // whose aliased silhouette may give a pixel to the other side (ADR-0503).
        GoldenImage.assertMatchesAtOneScale("canvas3d-cube", WIDTH, HEIGHT, 1f, Tolerance.GPU, (at, scale) -> {
            try (var scene = new Mounted(cube(new TestCube()))) {
                return harness.readBack(surface -> scene.picture(at, scale.factor(), surface));
            }
        });
        GoldenImage.assertMatchesAtOneScale("canvas3d-cube", WIDTH, HEIGHT, 1f, Tolerance.GPU, (at, scale) -> {
            try (var scene = new Mounted(cube(new TestCube()))) {
                return harness.composited(surface -> scene.picture(at, scale.factor(), surface))
                        .image();
            }
        });
    }

    @Test
    @DisplayName("renders at the canvas's physical size at 200%, and not again while nothing asks it to")
    void hiDpiAndOnDemand() {
        var cube = new TestCube();
        var size = new PhysicalSize(WIDTH * 2, HEIGHT * 2);
        // One read-back surface across both pictures, as a window keeps its own.
        try (var scene = new Mounted(cube(cube));
                var surface = harness.readBackSurface()) {
            scene.picture(size, 2f, surface);
            scene.picture(size, 2f, surface);
        }
        assertEquals(List.of("init", "resize 320x240", "render 320x240", "dispose"), cube.calls);
    }
}
