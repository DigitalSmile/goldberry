package dev.goldberry.example;

import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.golden.ScaleInvariance;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.text.font.Fonts;

/// The gallery, one image per screen: the golden images CI runs over the gallery
/// matrix.
///
/// [ShowcaseDocumentsTest] asserts the *shape* of the documents — that every
/// control is there and every `bind=` reaches the model — and could not tell you
/// whether a screen renders at all. These can: an empty screen, a strip that
/// forgot its rule, or a heading in the wrong colour is a picture that changed.
///
/// `./gradlew :example:test -Dgoldberry.golden.update=true` rewrites them.
///
/// Read more: [Goldens](https://goldberry.dev/docs/contributing/testing.html#goldens).
class GalleryGoldenTest {

    private ShowcaseScene scene;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // The application's own objects, so the gallery is painted against the
        // wiring the window uses rather than a copy of it -- shared with the
        // guide's screen pictures, which must be of the same screens.
        scene = new ShowcaseScene();
    }

    @AfterEach
    void tearDown() {
        // Null-safe: `setUp` can stop at the renderer requirement on a machine
        // with no native library, and a teardown that assumed otherwise would
        // report its own NPE instead of the skip.
        if (scene != null) {
            scene.close();
        }
    }

    /// The whole window on `screen`, at a chosen size.
    private void paint(String name, String screen, Theme theme, int width, int height) {
        paint(name, screen, theme, width, height, false, Sweep.EVERY_SCALE);
    }

    /// Whether the golden is also re-rendered at [ScaleInvariance]'s multipliers
    /// and checked for describing the same picture.
    private enum Sweep {

        /// The default, and what every screen but one gets.
        EVERY_SCALE,

        /// The picture is pinned at 1&times; and the invariance claim is not made
        /// — see [#everyScreen()].
        ONE_SCALE
    }

    /// The same, choosing how the text is drawn — with the one-font renderer these
    /// goldens were taken with, or with a **book**, which is what a screen about
    /// `font-family` needs — and whether the scale sweep applies at all.
    private void paint(String name, String screen, Theme theme, int width, int height, boolean book, Sweep sweep) {
        paint(name, screen, theme, width, height, book, sweep, null);
    }

    /// The same, showing GPU layers through `gpu` when there is one: the GPU
    /// screen on the GPU lane.
    private void paint(
            String name,
            String screen,
            Theme theme,
            int width,
            int height,
            boolean book,
            Sweep sweep,
            @Nullable GpuSurface gpu) {
        var root = scene.root(screen);
        var sheets = scene.stylesheets(theme);

        // **Through the shipped `Offscreen`**, which is the same
        // sequence this method used to spell out for itself: mount, lay out, feed
        // the regions back, advance a frozen clock, and paint the second frame.
        //
        // Every one of those steps was here as a comment explaining why the naive
        // version was wrong — a `message` photographed at zero opacity, a
        // `text-area` wrapped as though it were narrow, a `spinner` caught at a
        // random angle against a wall clock. They are the API's now, so the next
        // application to render a screen headlessly gets them without having read
        // this file.
        //
        // `font(...)` and not `fonts(...)`: these goldens were taken with the
        // one-font renderer, which ignores `font-family`, `font-size` and
        // `font-weight`. Handing over a book would be a typography change wearing
        // an infrastructure change's clothes.

        // The size and the scale are the harness's rather than captured here,
        // because a golden that matches is then re-rendered at 2x, 1.5x and 1.25x
        // and checked for describing the same picture. The
        // whole screen goes through that sweep now, which it always did — the
        // difference is that the render it sweeps is one an application could have
        // written. The one exception is `canvas`, and the note on that test says
        // why it is the exception.
        if (book) {
            // A **font book** rather than the one-font renderer, for the one
            // screen whose subject is `font-family`: the emoji sheet draws every
            // glyph through the face the cascade picks, and a renderer that
            // ignores the property would photograph a wall of `.notdef`.
            // Opened and closed per picture, because a book owns
            // the faces it opened.
            try (var fonts = Fonts.bundled()) {
                assertGolden(
                        name,
                        width,
                        height,
                        sweep,
                        (size, scale) -> Offscreen.of(size)
                                .scale(scale)
                                .stylesheets(sheets)
                                .fonts(fonts)
                                .render(root));
            }
            return;
        }
        assertGolden(
                name,
                width,
                height,
                sweep,
                (size, scale) -> withGpu(Offscreen.of(size).scale(scale), gpu)
                        .stylesheets(sheets)
                        .font(scene.font())
                        .render(root));
    }

    private static Offscreen withGpu(Offscreen offscreen, @Nullable GpuSurface gpu) {
        return gpu == null ? offscreen : offscreen.gpu(gpu);
    }

    /// One of [GoldenImage]'s two entry points, chosen by `sweep` — here rather
    /// than at each of the two call sites above, so the choice is made once.
    private static void assertGolden(String name, int width, int height, Sweep sweep, GoldenImage.Scene scene) {
        if (sweep == Sweep.ONE_SCALE) {
            GoldenImage.assertMatchesAtOneScale(name, width, height, 1.0f, scene);
            return;
        }
        GoldenImage.assertMatches(name, width, height, 1.0f, scene);
    }

    /// Every screen, in the strip's order, each at 1200&times;900: the first
    /// window's worth of it, which is what a reader opening the tab sees.
    ///
    /// Two screens are photographed at one scale and make no invariance claim:
    ///
    /// - **Drawing** holds QR codes and decoded bitmaps. A QR module is a
    ///   hard-edged square in a dense grid and a bitmap drawn at its natural size
    ///   is a raster; re-rendered at a fractional scale and resampled back, a run
    ///   of modules reads inverted, which no neighbourhood search can forgive.
    /// - **GPU** shows `canvas3d`, which is rendered at its physical size.
    ///
    /// **Emoji** is drawn through a font book, because its subject is
    /// `font-family` reaching the emoji face and the one-font renderer ignores the
    /// property.
    ///
    /// Web, Audio and Video are the state every machine sees: the web view's
    /// library and FFmpeg are pinned off by this module's test task, because
    /// whether a machine has them is not something a golden may photograph.
    @TestFactory
    @DisplayName("every screen")
    Stream<DynamicTest> everyScreen() {
        return Gallery.TABS.stream()
                .map(tab -> DynamicTest.dynamicTest(tab.title(), () -> {
                    var oneScale = Set.of("drawing", "gpu").contains(tab.name()) ? Sweep.ONE_SCALE : Sweep.EVERY_SCALE;
                    paint(
                            "gallery-" + tab.name(),
                            tab.name(),
                            Theme.NORD_DARK,
                            1200,
                            900,
                            tab.name().equals("emoji"),
                            oneScale);
                }));
    }

    /// The GPU screen drawn on a real device, read back: the showcase's cubes in
    /// its own shaders, as a headless window's read-back surface draws them. On
    /// the GPU lane, `:example:gpuTest`. At one scale: a 3D view is rendered at
    /// its physical size, which a scale sweep does not describe.
    @Test
    @Tag(GpuTestLauncher.TAG)
    @DisplayName("the GPU screen, drawn on the GPU and read back")
    void gpuDrawn() {
        var device = GpuDeviceRequirement.enforce();
        try (var backend = new HeadlessBackend()) {
            var window = backend.createWindow(WindowSpec.of("gallery", LogicalSize.of(1200, 900)));
            var surface = window.gpuSurface().orElseThrow(() -> new AssertionError("no GPU surface with :gpu here"));
            paint("gallery-gpu-drawn", "gpu", Theme.NORD_DARK, 1200, 900, false, Sweep.ONE_SCALE, surface);
            window.close();
        } finally {
            device.close();
            Sdl.get().quit();
        }
    }

    /// The same sheet in a narrow window, which is the **only** thing that can
    /// show the reflow: fewer columns, and a last column that is a whole tile
    /// rather than a clipped one.
    @Test
    @DisplayName("the Icons screen in a narrow window, with fewer columns")
    void iconsNarrow() {
        paint("gallery-icons-narrow", "icons", Theme.NORD_DARK, 720, 900);
    }

    /// A wall in a narrow window: the window chooses the column count, so 688
    /// points hold one column of cards rather than two cramped ones.
    @Test
    @DisplayName("the Buttons screen in a narrow window, reflowed to one column")
    void buttonsNarrow() {
        paint("gallery-buttons-narrow", "buttons", Theme.NORD_DARK, 720, 900);
    }

    @Test
    @DisplayName("the Forms screen on the light theme")
    void formsLight() {
        paint("gallery-forms-light", "forms", Theme.NORD_LIGHT, 1200, 900);
    }

    @Test
    @DisplayName("the Buttons screen on the light theme")
    void buttonsLight() {
        paint("gallery-buttons-light", "buttons", Theme.NORD_LIGHT, 1200, 900);
    }
}
