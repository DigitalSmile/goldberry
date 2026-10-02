package dev.goldberry.gpu.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.markup.Named;
import dev.goldberry.widgets.markup.Wiring;

/// `canvas3d` as a widget, with no GPU: its markup, when it is drawn again,
/// and what it shows where there is no GPU. Runs on every leg. Read more:
/// [canvas3d](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@DisplayName("canvas3d, the widget")
class Canvas3dTest {

    /// A renderer that is never asked to draw: these tests have no GPU.
    private static final class Idle implements Canvas3dRenderer {
        @Override
        public void init(GpuDevice device) {
            throw new AssertionError("no GPU here");
        }

        @Override
        public void render(GpuFrame frame, Canvas3dTarget target) {
            throw new AssertionError("no GPU here");
        }

        @Override
        public void dispose() {}
    }

    @Nested
    @DisplayName("from markup")
    class FromMarkup {

        @Test
        @DisplayName("takes a named renderer, continuous=, depth= and revision=")
        void attributes() {
            var renderer = new Idle();
            var wiring = Wiring.none().with(Named.strict().bind("cube", renderer));
            var canvas = assertInstanceOf(
                    Canvas3d.class,
                    Canvas3d.inflate(
                            KdlParser.parse(
                                            "canvas3d renderer=\"cube\" continuous=#true depth=\"d16\" revision=3 id=\"v\"")
                                    .getFirst(),
                            List.of(),
                            wiring));
            assertSame(renderer, canvas.renderer());
            assertTrue(canvas.continuous());
            assertEquals(Canvas3d.Depth.D16, canvas.depth());
            assertEquals(3, canvas.revision());
            assertEquals("v", canvas.attributes().id());

            var plain = assertInstanceOf(
                    Canvas3d.class,
                    Canvas3d.inflate(
                            KdlParser.parse("canvas3d renderer=\"cube\"").getFirst(), List.of(), wiring));
            assertEquals(new Canvas3d(renderer), plain, "on demand, no depth, revision 0");
        }

        @Test
        @DisplayName("refuses children and a depth it does not know")
        void refuses() {
            var wiring = Wiring.none().with(Named.strict().bind("cube", new Idle()));
            var node =
                    KdlParser.parse("canvas3d renderer=\"cube\" depth=\"d24\"").getFirst();
            assertThrows(IllegalArgumentException.class, () -> Canvas3d.inflate(node, List.of(), wiring));
            var withChild = KdlParser.parse("canvas3d renderer=\"cube\"").getFirst();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Canvas3d.inflate(withChild, List.of(new Column(List.of(), Attributes.NONE)), wiring));
        }
    }

    @Test
    @DisplayName("names its depths")
    void depths() {
        assertEquals(Canvas3d.Depth.NONE, Canvas3d.Depth.named(null));
        assertEquals(Canvas3d.Depth.D32, Canvas3d.Depth.named(" D32 "));
        assertNull(Canvas3d.Depth.NONE.format());
        assertEquals(TextureFormat.D16_UNORM, Canvas3d.Depth.D16.format());
    }

    @Nested
    @DisplayName("when it is drawn again")
    class Redrawn {

        private final Canvas3d.Canvas3dState state = new Canvas3d.Canvas3dState();

        @Test
        @DisplayName("an on-demand canvas's painter is equal until its revision changes, so its box is not damaged")
        void onDemand() {
            try (var layer = new Canvas3dLayer(new Idle(), null)) {
                var first = painter(layer, false, 0, 1000);
                assertEquals(first, painter(layer, false, 0, 1016), "a later frame, the same revision");
                assertNotEquals(first, painter(layer, false, 1, 1033), "a new revision");
                assertEquals(33_000_000L, painter(layer, false, 1, 1050).nanos(), "the time the revision came");
            }
        }

        @Test
        @DisplayName("a continuous canvas's painter differs every frame, with the frame's time since its first")
        void continuous() {
            try (var layer = new Canvas3dLayer(new Idle(), null)) {
                var first = painter(layer, true, 0, 1000);
                var next = painter(layer, true, 0, 1016);
                assertNotEquals(first, next);
                assertEquals(0, first.nanos());
                assertEquals(16_000_000L, next.nanos());
            }
        }

        private Canvas3dSurface.Canvas3dPainter painter(
                Canvas3dLayer layer, boolean continuous, long revision, double now) {
            var nanos = state.nanos(now, continuous, revision);
            var stamp = continuous ? Double.doubleToLongBits(now) : revision;
            return new Canvas3dSurface.Canvas3dPainter(layer, stamp, nanos, continuous, revision, 0, state);
        }
    }

    @Test
    @DisplayName("says why it is not drawn: the GPU off, none here, or a GPU that failed")
    void reasons() {
        assertNull(Canvas3d.Canvas3dState.reason(true, true, null));
        assertEquals(Canvas3d.Canvas3dState.GPU_OFF, Canvas3d.Canvas3dState.reason(false, false, " OFF"));
        assertEquals(Canvas3d.Canvas3dState.NO_GPU, Canvas3d.Canvas3dState.reason(false, false, null));
        assertEquals(Canvas3d.Canvas3dState.FAILED, Canvas3d.Canvas3dState.reason(false, true, "auto"));
    }

    @Nested
    @DisplayName("with no GPU")
    class Unavailable {

        private Font font;

        @BeforeEach
        void setUp() {
            NativeLibraryRequirement.enforce();
            font = Font.bundled(BundledFont.UI, 13);
        }

        @AfterEach
        void tearDown() {
            if (font != null) {
                font.close();
            }
        }

        @Test
        @DisplayName("fills its box with --gb-canvas3d-unavailable, and never asks its renderer for anything")
        void fills() {
            var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
            sheets.add(Stylesheet.parse(
                    CascadeLayer.APPLICATION,
                    "#scene { padding: 20px; flex-grow: 1; background: #2e3440; }"
                            + " canvas3d { width: 160px; height: 120px; }"));
            var scene = new Column(List.of(new Canvas3d(new Idle())), Attributes.NONE.id("scene"));
            GoldenImage.assertMatches(
                    "canvas3d-unavailable",
                    200,
                    160,
                    1f,
                    (size, scale) -> Offscreen.of(size)
                            .scale(scale)
                            .stylesheets(sheets)
                            .font(font)
                            .render(scene));
        }
    }
}
