package dev.goldberry.gpu.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.golden.Tolerance;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.gpu.upscale.RenderScale;
import dev.goldberry.gpu.upscale.UpscaleFilter;
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

/// The cube at a fixed angle in a `canvas3d`, a widget tree rendered both ways
/// a window shows it, on a real device.
///
/// The angle is the renderer's at the frame's time, and `Offscreen`'s clock is
/// virtual, so the picture is the same on every run. At one scale: a 3D view is
/// rendered at its physical size, which a scale sweep does not describe.
/// Read more: [canvas3d](https://goldberry.dev/docs/components/gpu.html#canvas3d).
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
        // whose aliased silhouette may give a pixel to the other side; a GPU
        // golden has its own tolerance for that.
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

    /// What an upscaled cube may differ by on another driver than lavapipe,
    /// which blessed it. [Tolerance#GPU]'s reasons hold, and grow. RCAS
    /// sharpens each rounding difference of the lit faces by up to three times,
    /// so a channel may be four levels apart anywhere: an NVIDIA GPU is three
    /// apart on the FSR goldens and one on the bilinear one. And a silhouette
    /// pixel that flips at the size drawn is upscaled into about three of the
    /// box's, so the share that may stray further is three times the GPU's.
    /// A cube in the wrong place or a face of the wrong colour moves thousands.
    private static final Tolerance UPSCALED = new Tolerance(4, 1, 0.003);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "canvas3d-cube-fsr1-50, 0.5, fsr1, 80x60",
        "canvas3d-cube-fsr1-75, 0.75, fsr1, 120x90",
        "canvas3d-cube-linear-50, 0.5, linear, 80x60"
    })
    @DisplayName("draws the cube at a render scale and upscales it into the box, the same both ways")
    void renderScale(String golden, double factor, String filter, String drawn) {
        var scale = new RenderScale(factor, UpscaleFilter.named(filter), RenderScale.DEFAULT_SHARPNESS);
        var size = new PhysicalSize(WIDTH, HEIGHT);
        var cube = new TestCube();
        try (var scene = new Mounted(cube(cube).renderScale(scale))) {
            var composited = harness.composited(surface -> scene.picture(size, 1f, surface));
            assertEquals(
                    PhysicalRect.of(20, 20, 160, 120),
                    composited.placed().getFirst().target(),
                    "placed in the whole box, whatever it was drawn at");
            assertEquals(List.of("init", "resize " + drawn, "render " + drawn), cube.calls);
            var readBack = harness.readBack(surface -> scene.picture(size, 1f, surface));
            CompositeHarness.assertSamePicture(golden, composited.image(), readBack);
        }
        GoldenImage.assertMatchesAtOneScale(golden, WIDTH, HEIGHT, 1f, UPSCALED, (at, at1) -> {
            try (var scene = new Mounted(cube(new TestCube()).renderScale(scale))) {
                return harness.readBack(surface -> scene.picture(at, at1.factor(), surface));
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
