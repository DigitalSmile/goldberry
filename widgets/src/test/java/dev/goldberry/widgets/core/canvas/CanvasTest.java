package dev.goldberry.widgets.core.canvas;

import static dev.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.paint.Painter;
import dev.goldberry.paint.StyledPainter;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.markup.Named;

/// The `canvas` primitive, as a widget.
///
/// The painting contract itself is pinned in `:core` by `CanvasPaintTest` —
/// origin, clip, and the `save`/`restore` that lets an application's code touch
/// the frame at all. What is checked here is the half that is a *widget*: that it
/// is a styled box like any other, that markup can write one, and that a painter
/// and a stylesheet compose rather than fight.
///
/// Read more: [Canvas](https://goldberry.dev/docs/components/drawing.html#canvas).
class CanvasTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("carries its painter into the box it renders")
    void thePainterReachesTheBox() {
        Painter painter = (frame, size) -> {};

        var tree = new ElementTree(new Canvas(painter));
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet()), TestFont.get());
        var box = renderer.render(tree);

        assertNotNull(box.painting(), "a canvas with no painter on its box draws nothing");
        assertEquals(painter, box.painting());
    }

    @Test
    @DisplayName("markup writes one, and with nothing named it draws nothing")
    void inflatesFromKdl() {
        var widget =
                Widgets.inflater().inflate(KdlParser.parse("canvas id=\"plot\"").getFirst());

        var canvas = assertInstanceOfCanvas(widget);
        assertEquals("plot", canvas.id());
        assertNull(canvas.painter());
        assertNull(canvas.input());
    }

    @Test
    @DisplayName("markup names a painter and an input the application bound")
    void markupNamesAPainter() {
        Painter painter = (frame, size) -> {};
        var input = new Input() {};
        var named = Named.strict().bind("app.plot", painter).bind("app.plot-input", input);

        var canvas = assertInstanceOfCanvas(Widgets.inflater(named, Icons.none())
                .inflate(KdlParser.parse("canvas painter=\"app.plot\" input=\"app.plot-input\"")
                        .getFirst()));

        assertSame(painter, canvas.painter());
        assertSame(input, canvas.input());
    }

    @Test
    @DisplayName("a styled painter is a painter, so markup names one the same way")
    void markupNamesAStyledPainter() {
        StyledPainter painter = (frame, size, style) -> {};
        var named = Named.strict().bind("app.plot", painter);

        var canvas = assertInstanceOfCanvas(Widgets.inflater(named, Icons.none())
                .inflate(KdlParser.parse("canvas painter=\"app.plot\"").getFirst()));

        assertSame(painter, canvas.painter());
    }

    @Test
    @DisplayName("a name bound to something that is not a painter is refused")
    void aNameThatIsNotAPainter() {
        var named = Named.strict().bind("app.plot", "not a painter");

        assertThrows(
                IllegalArgumentException.class,
                () -> Widgets.inflater(named, Icons.none())
                        .inflate(KdlParser.parse("canvas painter=\"app.plot\"").getFirst()));
    }

    private static Canvas assertInstanceOfCanvas(Widget widget) {
        assertTrue(widget instanceof Canvas, "expected a canvas, got " + widget.getClass());
        return (Canvas) widget;
    }

    @Test
    @DisplayName("is told the size the stylesheet gave it, not one it chose")
    void sizeComesFromTheStylesheet() {
        var seen = new ArrayList<String>();
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "#plot { width: 120px; height: 40px; padding: 4px }");

        paint(
                "canvas-size",
                160,
                60,
                sheet,
                new Canvas((frame, size) -> seen.add(size.width() + "x" + size.height()), id("plot")),
                false);

        // 120x40 less the 4px padding on each side: a canvas draws inside the
        // padding like every other content.
        assertEquals(List.of("112.0x32.0"), seen);
    }

    @Test
    @DisplayName("a canvas under a theme, painted")
    void golden() {
        // Two bars in the first two series slots of the derived palette, on a
        // themed surface with a border and a radius from CSS.
        // The picture says the two halves compose: the frame, the edge and the
        // radius are the stylesheet's, and everything inside is the painter's.
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame  { padding: 12px; background: var(--gb-bg) }
                #plot   { width: 176px; height: 76px; padding: 8px;
                          background: var(--gb-surface);
                          border: 1px solid var(--gb-border);
                          border-radius: 6px }
                """);

        paint(
                "canvas-dark",
                200,
                100,
                sheet,
                new Column(List.of(new Canvas(CanvasTest::bars, id("plot"))), id("frame")),
                true);
    }

    /// Two bars and a baseline, in logical pixels from the canvas's own origin.
    private static void bars(dev.goldberry.paint.Frame frame, dev.goldberry.render.model.LogicalSize size) {

        var baseline = size.height() - 1;
        frame.fillRect(0, baseline, size.width(), 1, 0xFF4C566A);
        // Slot 1 and slot 4 of the series palette: green and blue, which is the
        // widest-separated adjacent pair a two-series chart can take.
        frame.fillRect(8, baseline - 40, 40, 40, 0xFF73A340);
        frame.fillRect(64, baseline - 56, 40, 56, 0xFF5094E5);
    }

    private void paint(String name, int width, int height, Stylesheet sheet, Widget content, boolean golden) {

        var tree = new ElementTree(content);
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());
        if (golden) {
            GoldenImage.assertMatches(
                    name, width, height, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
            return;
        }
        // The same path without the image, for a test whose claim is about what
        // the painter was told rather than about what it drew.
        var target = dev.goldberry.paint.TestFrames.of(width, height, 1.0f);
        try {
            BoxPainter.paint(target.frame(), renderer.render(tree));
        } finally {
            target.end();
        }
    }
}
