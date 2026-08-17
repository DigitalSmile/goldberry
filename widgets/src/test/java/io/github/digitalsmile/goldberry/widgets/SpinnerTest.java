package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The eighth control and the smallest: a ring, turning, with nothing else to it
/// ([ADR-0081]).
class SpinnerTest {

    private static Box paint(Widget widget, double now, boolean reduced) {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                .clock(clock)
                .reducedMotion(reduced);
        clock.advance(now);
        return renderer.render(new ElementTree(widget));
    }

    private static double turnsAt(double now) {
        if (paint(new Spinner(), now, false).transform().functions().getFirst()
                instanceof Transform.Function.Rotate(var radians)) {
            return radians / (2 * Math.PI);
        }
        throw new AssertionError("a spinner carries a rotation");
    }

    @Test
    @DisplayName("the Java-built and KDL-built spinners are equal values")
    void javaAndKdlAgree() {
        var attributes = new Widgets.Attributes("busy", Set.of(), "busy");

        var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                spinner id="busy"
                """)).getFirst();

        assertEquals(new Spinner(attributes), fromKdl);
    }

    /// §3.1: "rotation 900ms `linear` loop". Linear, so the angle is the phase —
    /// a spinner that eased would speed up and slow down once a turn, which reads
    /// as a stutter rather than as motion.
    @Test
    @DisplayName("a turn takes 900ms and the angle is linear in the clock")
    void rotationIsLinearOverThePeriod() {
        assertEquals(0.0, turnsAt(0), 1e-9);
        assertEquals(0.25, turnsAt(225), 1e-9);
        assertEquals(0.5, turnsAt(450), 1e-9);
        assertEquals(0.0, turnsAt(900), 1e-9, "and it comes back to where it started");
        // The clock's origin is arbitrary and large in a real window.
        assertEquals(0.5, turnsAt(900 * 10_000 + 450), 1e-9);
    }

    /// The whole of ADR-0081 in one assertion: two spinners are in step because
    /// neither of them remembers anything. A controller started at mount would
    /// put two that appeared a frame apart permanently out of phase.
    @Test
    @DisplayName("two spinners in one window are in step")
    void spinnersAgree() {
        var row = paint(new Widgets.Row(
                new Spinner(new Widgets.Attributes("a", Set.of(), "a")),
                new Spinner(new Widgets.Attributes("b", Set.of(), "b"))), 300, false);

        assertEquals(row.children().get(0).transform(), row.children().get(1).transform());
    }

    @Test
    @DisplayName("it keeps the frame loop awake, because a still spinner is a picture")
    void alwaysAnimating() {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                .clock(clock);

        renderer.render(new ElementTree(new Spinner()));

        assertTrue(new Spinner().isAnimating());
        assertTrue(renderer.isAnimating());
    }

    /// §3.1: "reduced-motion: opacity pulse". The rotation stops entirely rather
    /// than slowing — a slow rotation is still a rotation.
    @Test
    @DisplayName("reduced motion stops it turning")
    void reducedMotionDoesNotTurn() {
        assertTrue(paint(new Spinner(), 300, true).transform().isNone());
        assertFalse(paint(new Spinner(), 300, false).transform().isNone());
    }

    /// A `Box.Mark`, like a tick and a dot — not an
    /// [io.github.digitalsmile.goldberry.icon.Icon], which owns native memory a
    /// widget must not hold, and not a new native symbol, because the arc is
    /// cubics through the one already exported (ADR-0064).
    @Test
    @DisplayName("the ring is a mark the painter draws, and takes the node's colour")
    void ringIsAMark() {
        var style = ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(Theme.NORD_DARK))
                        .resolve(new ElementTree(new Spinner()).root()),
                CssLength.Context.DEFAULT);
        var box = paint(new Spinner(), 0, false);

        assertEquals(Box.Mark.Kind.ARC, box.mark().kind());
        assertEquals(style.color(), box.mark().argb(),
                "`color` inherits, so a spinner in a primary button is that label's colour");
        assertEquals(StyleLength.points(16), style.width(), "§3's small-indicator 16");
        assertEquals(StyleLength.points(16), style.height());
    }
}
