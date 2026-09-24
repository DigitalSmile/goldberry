package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.gpu.GpuDevice;
import io.github.digitalsmile.goldberry.gpu.TextureFormat;
import io.github.digitalsmile.goldberry.gpu.TextureSpec;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTextureFormat;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.window.GpuSurface;

/// `docs/gpu-plan.md` phase 4's exit: each z-order case is a golden that is the
/// same composited and read back, on a real device (ADR-0481).
///
/// Each case is a render tree with [TestLayer]s in it, painted twice through
/// `Offscreen`:
///
/// - **composited**: the frame is painted with holes where the layers are,
///   uploaded, and composited with the layers by the window's own composite
///   pass ([LayerTextures#renderAll], [UiComposite]) into a texture that is read
///   back, which is what a composited window's swapchain would show;
/// - **read back**: the frame is painted with each layer rendered, downloaded
///   and drawn into it, by the compositor's own [SdlReadbackSurface].
///
/// Both are held to one golden, within ADR-0050's tolerance, and to each other
/// at two levels in 256: they differ, if at all, only where translucent UI is
/// blended over a layer, once by Blend2D and once by the GPU. Each case is
/// checked at one scale and not swept, because a layer is a raster rendered at
/// its physical size, which a scale sweep does not describe (ADR-0434); the
/// scrolled list is also checked at 1.5, whose rounding is the hard one.
@Tag(GpuTestLauncher.TAG)
@DisplayName("GPU layers in paint order, composited and read back")
class LayerZOrderTest {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 120;

    private static final int GREY = 0xFF808080;
    private static final int DARK = 0xFF303030;
    private static final int WHITE = 0xFFFFFFFF;

    private static SdlGpuDevice required;
    private static SdlCompositor compositor;
    private static GpuDevice api;
    private static UiComposite composite;

    @BeforeAll
    static void createDevice() {
        // Initialises SDL's video under the lane's driver, which the
        // compositor's own device needs, and skips or fails without one.
        required = GpuDeviceRequirement.enforce();
        compositor = new SdlCompositor();
        api = compositor.api().orElseThrow();
        composite = new UiComposite(compositor.device().orElseThrow());
    }

    @AfterAll
    static void destroyDevice() {
        if (composite != null) {
            composite.close();
        }
        if (compositor != null) {
            compositor.close();
        }
        if (required != null) {
            required.close();
        }
        Sdl.get().quit();
    }

    // ---------------------------------------------------------------------
    // The cases
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("a popup painted after a layer is over it")
    void popupOverALayer() {
        var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
        var popup = at(Box.filled(WHITE).decoration(Decoration.NONE.border(2, DARK)), 80, 50, 60, 50)
                .elevated(true);
        var placed = assertSameBothWays("gpu-layer-popup", 1f, root(popup, at(layer(layer), 20, 20, 100, 70)));
        assertEquals(1, placed.size());
    }

    @Test
    @DisplayName("a layer in a scrolled list is cut to its viewport, and one scrolled out of it is not rendered")
    void layerInAScrolledList() {
        var hidden = new TestLayer(0xFFFF0000, 0xFF00FFFF);
        var shown = new TestLayer(0xFF00FF00, 0xFFFF00FF).moveTo(0.1, 0.4);
        var placed = assertSameBothWays("gpu-layer-scrolled", 1f, scrolledList(hidden, shown));
        assertEquals(1, placed.size(), "only the layer that can be seen");
        // The viewport is 10..110, and the layer 15..125 once scrolled.
        assertEquals(PhysicalRect.of(10, 15, 140, 110), placed.getFirst().target());
        assertEquals(PhysicalRect.of(10, 15, 140, 95), placed.getFirst().scissor());
        assertEquals(0, hidden.renders(), "scrolled out of view, it costs nothing");
    }

    @Test
    @DisplayName("a layer in a scrolled list at 150%, where its top edge falls mid-pixel and is rounded")
    void layerInAScrolledListAtOneAndAHalf() {
        var placed = assertSameBothWays(
                "gpu-layer-scrolled-150",
                1.5f,
                scrolledList(new TestLayer(0xFFFF0000, 0xFF00FFFF), new TestLayer(0xFF00FF00, 0xFFFF00FF)));
        assertEquals(PhysicalRect.of(15, 23, 210, 142), placed.getFirst().scissor());
    }

    @Test
    @DisplayName("two overlapping layers keep paint order, with translucent UI between them")
    void overlappingLayersWithUiBetween() {
        var under = new TestLayer(0xFF0000FF, 0xFFFFFF00);
        var over = new TestLayer(0xFF00FF00, 0xFFFF00FF).moveTo(0.5, 0.1);
        var between = at(Box.filled(0x80FF0000), 60, 40, 60, 50);
        var placed = assertSameBothWays(
                "gpu-layer-overlap",
                1f,
                root(at(layer(under), 10, 10, 90, 70), between, at(layer(over), 80, 60, 70, 50)));
        assertEquals(
                List.of(under, over), placed.stream().map(GpuPlacement::content).toList(), "in paint order");
    }

    @Test
    @DisplayName("a layer under a rounded clip is cut to its bounding box, and rounded by the UI over it")
    void layerUnderARoundedClip() {
        var layer = new TestLayer(WHITE, 0xFF0000FF).moveTo(0.4, 0.3);
        var rounded = Decoration.NONE.radius(16).border(3, DARK);
        var clip = at(Box.filled(0xFF4060A0).decoration(rounded), 20, 15, 120, 90)
                .overflow(Overflow.HIDDEN)
                .children(layer(layer).size(Length.points(120), Length.points(90)));
        // What rounds a GPU layer's corners until there is a mask pass
        // (docs/gpu-plan.md, D4): UI painted over it. A frame of the
        // background's colour whose inner edge is the clip, rounded by 16, and
        // the ring drawn again over that.
        var corners = at(Box.of().decoration(Decoration.NONE.radius(32).border(16, GREY)), 4, -1, 152, 122);
        var ring = at(Box.of().decoration(rounded), 20, 15, 120, 90);
        var placed = assertSameBothWays("gpu-layer-rounded", 1f, root(clip, corners, ring));
        assertEquals(PhysicalRect.of(20, 15, 120, 90), placed.getFirst().scissor(), "the clip's bounding box");
    }

    @Test
    @DisplayName("the popup case at 200%, a layer's physical size twice its logical one")
    void popupOverALayerAtTwo() {
        var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
        var popup = at(Box.filled(WHITE).decoration(Decoration.NONE.border(2, DARK)), 80, 50, 60, 50)
                .elevated(true);
        var placed = assertSameBothWays("gpu-layer-popup-200", 2f, root(popup, at(layer(layer), 20, 20, 100, 70)));
        assertEquals(PhysicalRect.of(40, 40, 200, 140), placed.getFirst().target());
    }

    // ---------------------------------------------------------------------
    // The scenes
    // ---------------------------------------------------------------------

    /// A grey root the size of the picture, holding `children` placed by
    /// [#at]. Painted in order: a child listed later is over one listed
    /// earlier, and an elevated one is over all of them.
    private static Box root(Box... children) {
        return Box.filled(GREY)
                .size(Length.points(WIDTH), Length.points(HEIGHT))
                .children(children);
    }

    /// `box` at `(x, y)`, `width` by `height`, absolutely.
    private static Box at(Box box, float x, float y, float width, float height) {
        return box.size(Length.points(width), Length.points(height))
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.points(y), Length.UNDEFINED, Length.UNDEFINED, Length.points(x)));
    }

    /// A box showing `layer`, or magenta-black stripes where it cannot, which no
    /// golden here expects.
    private static Box layer(TestLayer layer) {
        return Box.of().shrink(0).painting(layer.painter((frame, size) -> {
            for (var y = 0; y < size.height(); y += 4) {
                frame.fillRect(0, y, size.width(), 2, 0xFFFF00FF);
            }
        }));
    }

    /// A 140x100 viewport at (10, 10), scrolled 45 down a column of a 30-tall
    /// `hidden` layer, a 20-tall row, a 110-tall `shown` layer and an 80-tall
    /// row: `hidden` is above the viewport, `shown` runs out of its bottom.
    private static Box scrolledList(TestLayer hidden, TestLayer shown) {
        var content = Box.of()
                .direction(FlexDirection.COLUMN)
                .transform(
                        Transform.of(new Transform.Function.Translate(Transform.Length.ZERO, Transform.Length.px(-45))))
                .children(
                        layer(hidden).size(Length.points(140), Length.points(30)),
                        Box.filled(0xFF6080C0)
                                .size(Length.points(140), Length.points(20))
                                .shrink(0),
                        layer(shown).size(Length.points(140), Length.points(110)),
                        Box.filled(0xFF80A060)
                                .size(Length.points(140), Length.points(80))
                                .shrink(0));
        return root(
                at(Box.filled(DARK), 10, 10, 140, 100).overflow(Overflow.HIDDEN).children(content));
    }

    // ---------------------------------------------------------------------
    // The two ways
    // ---------------------------------------------------------------------

    /// Holds `scene` at `scale` to golden `name` both ways, and the two ways to
    /// each other; what it placed, composited.
    private static List<GpuPlacement> assertSameBothWays(String name, float scale, Box scene) {
        var width = Math.round(WIDTH * scale);
        var height = Math.round(HEIGHT * scale);
        var painter = painting(scene);
        var recorder = new Recorder();
        var composited = composited(painter, recorder, new PhysicalSize(width, height), new DisplayScale(scale));
        var readBack = readBack(painter, new PhysicalSize(width, height), new DisplayScale(scale));

        var differing = 0;
        var worst = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var a = composited.argb(x, y);
                var b = readBack.argb(x, y);
                for (var shift = 0; shift < 32; shift += 8) {
                    var difference = Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF));
                    worst = Math.max(worst, difference);
                    if (difference > 2) {
                        differing++;
                    }
                }
            }
        }
        assertTrue(
                worst <= 2,
                name + ": composited and read back differ by " + worst + " levels, in " + differing + " channels");

        GoldenImage.assertMatchesAtOneScale(
                name, width, height, scale, (size, at) -> composited(painter, new Recorder(), size, at));
        GoldenImage.assertMatchesAtOneScale(name, width, height, scale, (size, at) -> readBack(painter, size, at));
        return recorder.placed;
    }

    private static Painter painting(Box scene) {
        return (frame, size) -> {
            try (var tree = RenderTree.create()) {
                tree.update(frame, scene);
                tree.paint(frame);
            }
        };
    }

    /// What a composited window keeps of a frame: the layers it placed.
    private static final class Recorder implements GpuSurface.Composited {
        List<GpuPlacement> placed = List.of();

        @Override
        public void placed(List<GpuPlacement> layers) {
            placed = layers;
        }
    }

    /// The frame painted with holes, then composited with its layers as a
    /// composited window's present does, into a texture that is read back.
    private static Image composited(Painter scene, Recorder recorder, PhysicalSize size, DisplayScale scale) {
        var frame = Offscreen.of(size).scale(scale).gpu(recorder).paint(scene);
        var whole = PhysicalRect.of(size);
        try (var textures = new LayerTextures();
                var ui = api.createTexture(
                        TextureSpec.sampled(TextureFormat.B8G8R8A8_UNORM, size.width(), size.height()));
                var target = api.createTexture(
                        TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, size.width(), size.height()))) {
            try (var upload = api.beginFrame()) {
                upload.copyPass(copy -> copy.upload(ui, frame.pixels(), List.of(whole)));
                upload.submit();
            }
            var layers = textures.renderAll(api, recorder.placed);
            var commands = compositor.device().orElseThrow().acquireCommandBuffer();
            composite.draw(
                    commands,
                    ApiAccess.texture(target),
                    SdlGpuTextureFormat.B8G8R8A8_UNORM,
                    ApiAccess.texture(ui),
                    layers);
            commands.submit();
            try (var read = api.beginFrame()) {
                var readback = read.readback(target);
                read.submit();
                return direct(readback.awaitPixels());
            }
        }
    }

    /// The frame painted with each layer rendered, read back and drawn into it.
    private static Image readBack(Painter scene, PhysicalSize size, DisplayScale scale) {
        try (var surface = compositor.readback()) {
            return Offscreen.of(size).scale(scale).gpu(surface).paint(scene);
        }
    }

    /// `pixels` in direct memory, which an image drawn or encoded needs.
    private static Image direct(PixelBuffer pixels) {
        var source = pixels.pixels();
        var copy = ByteBuffer.allocateDirect(source.remaining()).order(source.order());
        copy.put(0, source, source.position(), source.remaining());
        return Image.of(new PixelBuffer(pixels.size(), pixels.format(), pixels.stride(), copy));
    }
}
