package io.github.digitalsmile.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.CanvasStyle;
import io.github.digitalsmile.goldberry.paint.StyledPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// `docs/gaps.md` G41: a canvas painted from the clock asks for its next frame
/// ([ADR-0348]).
///
/// §1.7's loop is idle when nothing moves, and until this a `canvas` could not
/// say that it did. The assertions are all about [WidgetRenderer#isAnimating()],
/// because that is the one question the launcher asks before it schedules
/// another frame.
class CanvasAnimatingTest {

    private static final StyledPainter NOTHING = (frame, size, style) -> {};

    private static Attributes id(String value) {
        return new Attributes(value, Set.of(), value);
    }

    private static WidgetRenderer renderer(Clock clock, boolean reduced) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "#floor { width: 80px; height: 40px }");
        return new WidgetRenderer(List.of(Controls.baseStylesheet(), sheet), TestFont.get())
                .clock(clock)
                .reducedMotion(reduced);
    }

    @Test
    @DisplayName("a canvas that says nothing is a still picture, and the loop goes idle")
    void stillByDefault() {
        var renderer = renderer(Clock.virtual(), false);

        renderer.render(new ElementTree(new Canvas(NOTHING, id("floor"))));

        assertFalse(renderer.isAnimating());
        assertNull(new Canvas(NOTHING).animating());
    }

    @Test
    @DisplayName("a canvas that asks keeps the loop awake")
    void askingKeepsTheLoopAwake() {
        var renderer = renderer(Clock.virtual(), false);

        renderer.render(new ElementTree(new Canvas(NOTHING, id("floor")).animating(style -> true)));

        assertTrue(renderer.isAnimating());
    }

    @Test
    @DisplayName("a settle stops by itself when the clock passes its end")
    void stopsByItself() {
        var clock = Clock.virtual();
        var renderer = renderer(clock, false);
        var tree = new ElementTree(new Canvas(NOTHING, id("floor")).animating(style -> style.nowMillis() < 850));

        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "at 0 ms the last tile has not landed");

        clock.advance(849);
        renderer.render(tree);
        assertTrue(renderer.isAnimating());

        clock.advance(1);
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "the frame that lands the last tile is the frame that goes quiet");
    }

    @Test
    @DisplayName("the predicate sees the frame time and the motion setting the painter was given")
    void seesWhatThePainterSaw() {
        var clock = Clock.virtual();
        clock.advance(120);
        var painted = new ArrayList<CanvasStyle>();
        var asked = new ArrayList<CanvasStyle>();
        StyledPainter painter = (frame, size, style) -> painted.add(style);
        Predicate<CanvasStyle> question = style -> asked.add(style) && false;

        var renderer = renderer(clock, true);
        var box = renderer.render(new ElementTree(new Canvas(painter, id("floor")).animating(question)));
        box.painting().paint(null, null);

        assertEquals(1, asked.size(), "asked once per frame");
        assertEquals(painted, asked, "the painter and the question are handed one snapshot's worth of values");
        assertEquals(120.0, asked.getFirst().nowMillis());
        assertTrue(asked.getFirst().reducedMotion());
    }

    @Test
    @DisplayName("the withers carry the question along")
    void withersKeepIt() {
        Predicate<CanvasStyle> question = style -> true;
        var canvas = new Canvas(NOTHING).animating(question);

        assertSame(question, canvas.input(new Input() {}).animating());
        assertSame(question, canvas.withAttributes(id("floor")).animating());
    }
}
