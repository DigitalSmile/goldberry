package io.github.digitalsmile.goldberry.widgets.core.canvas;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.Painter;
import io.github.digitalsmile.goldberry.paint.StyledPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// `docs/gaps.md` G11: a painter that is told what the cascade resolved.
///
/// The claim being pinned is that the *stylesheet* reaches the drawing — change
/// `font-size` or `color` and the painter is handed a different answer without
/// naming either — and that the older two-parameter form still means exactly
/// what it did (ADR-0288).
class StyledCanvasTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("hands the painter the font the cascade resolved, not one it named")
    void theFontComesFromTheStylesheet() {
        var sizes = new ArrayList<Double>();

        paint(
                "#plot { width: 100px; height: 40px; font-size: 27px }",
                (frame, size, style) -> sizes.add(style.font().size()));

        assertEquals(List.of(27.0), sizes, "a canvas should inherit the font the cascade settled, like a label does");
    }

    @Test
    @DisplayName("a different declaration is a different style, with nothing named in Java")
    void followsTheStylesheet() {
        var sizes = new ArrayList<Double>();
        StyledPainter painter = (frame, size, style) -> sizes.add(style.font().size());

        paint("#plot { width: 100px; height: 40px; font-size: 11px }", painter);
        paint("#plot { width: 100px; height: 40px; font-size: 23px }", painter);

        assertEquals(List.of(11.0, 23.0), sizes);
    }

    @Test
    @DisplayName("hands the painter the resolved colour, so canvas ink follows the theme")
    void theInkComesFromTheStylesheet() {
        var inks = new ArrayList<Integer>();

        paint("#plot { width: 100px; height: 40px; color: #b48ead }", (frame, size, style) -> inks.add(style.ink()));

        assertEquals(List.of(0xFFB48EAD), inks);
    }

    @Test
    @DisplayName("hands over this frame's time, which a two-parameter painter has no way to ask for")
    void carriesTheFrameClock() {
        var times = new ArrayList<Double>();

        paint("#plot { width: 100px; height: 40px }", (frame, size, style) -> {
            times.add(style.nowMillis());
            assertFalse(style.reducedMotion(), "nothing asked for less movement");
        });

        assertEquals(1, times.size());
    }

    @Test
    @DisplayName("binds the style when the box is built, not when it is painted")
    void boundAtRenderTime() {
        // The box carries a plain Painter: a StyledPainter reaching the paint
        // pass unbound would be painted with CanvasStyle.none(), which is the
        // bug this binding exists to prevent.
        StyledPainter painter = (frame, size, style) -> {};

        var tree = new ElementTree(new Canvas(painter, id("plot")));
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet()), TestFont.get());
        var box = renderer.render(tree);

        assertNotNull(box.painting());
        assertNotSame(painter, box.painting(), "a styled painter must be bound before it reaches the box");
    }

    @Test
    @DisplayName("a plain painter is carried through untouched, and is still the same object")
    void aPlainPainterIsNotWrapped() {
        Painter painter = (frame, size) -> {};

        var tree = new ElementTree(new Canvas(painter, id("plot")));
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet()), TestFont.get());
        var box = renderer.render(tree);

        assertSame(painter, box.painting(), "a painter that asks the cascade nothing should pay nothing");
    }

    @Test
    @DisplayName("bound() is an ordinary Painter, which is what a Box can carry")
    void boundIsAPainter() {
        var seen = new ArrayList<CanvasStyle>();
        StyledPainter painter = (frame, size, style) -> seen.add(style);
        var style = CanvasStyle.none().ink(0xFF112233);

        Painter bound = painter.bound(style);
        var target = TestFrames.of(10, 10, 1.0f);
        try {
            bound.paint(target.frame(), target.frame().size());
        } finally {
            target.end();
        }

        assertEquals(List.of(style), seen);
    }

    @Test
    @DisplayName("unbound, it paints with none() rather than failing mid-frame")
    void unboundFallsBackToNone() {
        var seen = new ArrayList<CanvasStyle>();
        StyledPainter painter = (frame, size, style) -> seen.add(style);

        var target = TestFrames.of(10, 10, 1.0f);
        try {
            // Called through Painter, which is what Offscreen.paint and a direct
            // Box.painting(...) do.
            ((Painter) painter).paint(target.frame(), target.frame().size());
        } finally {
            target.end();
        }

        assertEquals(List.of(CanvasStyle.none()), seen);
    }

    @Test
    @DisplayName("null is not ambiguous between the two painter constructors")
    void nullIsNotAmbiguous() {
        // The compiler is the assertion: StyledPainter is a subtype of Painter,
        // so `Canvas(StyledPainter)` is the more specific overload and `null`
        // picks it rather than failing to resolve.
        assertNull(new Canvas(null).painter());
        assertNull(new Canvas(null, id("plot")).painter());
        assertTrue(new Canvas(null, id("plot")).attributes().id().equals("plot"));
    }

    private void paint(String css, StyledPainter painter) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
        var tree = new ElementTree(new Canvas(painter, id("plot")));
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());
        var target = TestFrames.of(160, 60, 1.0f);
        try {
            BoxPainter.paint(target.frame(), renderer.render(tree));
        } finally {
            target.end();
        }
    }
}
