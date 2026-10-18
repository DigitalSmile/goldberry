package dev.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Painter;
import dev.goldberry.paint.StyledPainter;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// A canvas under `isolation: isolate` keeps the raster its painter drew, and a
/// painter is not called again for a canvas nothing changed.
///
/// Counted at the painter, which is the cost the cache exists to save: the
/// pixels would be the same either way.
class CanvasCacheTest {

    private static final int FRAMES = 5;

    private static final String ISOLATED = "#c { isolation: isolate }";

    private final Clock.Virtual clock = Clock.virtual();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Paints the same tree `FRAMES` times, advancing the clock between frames,
    /// and answers how many times the painter ran.
    private int paints(Canvas canvas, String css, AtomicInteger calls) {
        var target = TestFrames.of(200, 200, 1f, 0);
        var renderer = new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                Theme.NORD_DARK.load(),
                                Stylesheet.parse(
                                        CascadeLayer.APPLICATION, "#c { width: 100px; height: 100px }\n" + css)),
                        TestFont.get())
                .clock(clock);
        var tree = new ElementTree(new Column(List.of(canvas, new Text("beside")), Attributes.NONE));
        try (var render = RenderTree.create()) {
            for (var i = 0; i < FRAMES; i++) {
                render.update(target.frame(), renderer.render(tree));
                render.paint(target.frame());
                clock.advance(16);
            }
        } finally {
            target.end();
        }
        return calls.get();
    }

    @Test
    @DisplayName("a canvas that is not isolated is drawn on every paint")
    void notIsolated() {
        var calls = new AtomicInteger();
        Painter painter = (frame, size) -> calls.incrementAndGet();

        assertEquals(FRAMES, paints(new Canvas(painter, Attributes.NONE.id("c")), "", calls));
    }

    @Test
    @DisplayName("an isolated canvas is drawn once and copied after")
    void isolatedPlain() {
        var calls = new AtomicInteger();
        Painter painter = (frame, size) -> calls.incrementAndGet();

        assertEquals(1, paints(new Canvas(painter, Attributes.NONE.id("c")), ISOLATED, calls));
    }

    @Test
    @DisplayName("and so is one told what the cascade resolved, though the clock moved")
    void isolatedStyled() {
        var calls = new AtomicInteger();
        StyledPainter painter = (frame, size, style) -> calls.incrementAndGet();

        assertEquals(1, paints(new Canvas(painter, Attributes.NONE.id("c")), ISOLATED, calls));
    }

    @Test
    @DisplayName("an animating canvas is drawn on every frame, isolated or not")
    void animating() {
        var calls = new AtomicInteger();
        StyledPainter painter = (frame, size, style) -> calls.incrementAndGet();
        var canvas = new Canvas(painter, Attributes.NONE.id("c")).animating(style -> true);

        assertEquals(FRAMES, paints(canvas, ISOLATED, calls));
    }

    @Test
    @DisplayName("a bound painter is equal to the same painter under the same style, the time aside when still")
    void equality() {
        StyledPainter painter = (frame, size, style) -> {};
        var at0 = new CanvasStyle(CanvasStyle.none().font(), 0xFF112233, 0, false);
        var at16 = new CanvasStyle(at0.font(), at0.ink(), 16, false);
        var otherInk = new CanvasStyle(at0.font(), 0xFF445566, 0, false);

        assertEquals(new BoundPainter(painter, at0, true), new BoundPainter(painter, at16, true));
        assertEquals(new BoundPainter(painter, at0, true).hashCode(), new BoundPainter(painter, at16, true).hashCode());
        assertNotEquals(new BoundPainter(painter, at0, false), new BoundPainter(painter, at16, false));
        assertNotEquals(new BoundPainter(painter, at0, true), new BoundPainter(painter, otherInk, true));
        assertNotEquals(new BoundPainter(painter, at0, true), new BoundPainter((frame, size, style) -> {}, at0, true));
    }
}
