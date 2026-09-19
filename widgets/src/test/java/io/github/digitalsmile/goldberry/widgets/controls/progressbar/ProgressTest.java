package io.github.digitalsmile.goldberry.widgets.controls.progressbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// The seventh control, and the first that is not a control: it reports and takes
/// nothing back ([ADR-0081]).
///
/// What is new here is **motion that is not a transition**. Everything that has
/// moved so far moved between two styles the cascade resolved; a sweep has no two
/// styles, so it is drawn from the frame clock — and the thing worth holding down
/// is that this needs no state, which is what these tests are mostly about.
class ProgressTest {

    private static final Attributes ID = new Attributes("p", Set.of(), "p");

    /// The box tree a renderer produces for `widget` at `now` on a virtual clock.
    private static Box paint(Widget widget, double now) {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).clock(clock);
        clock.advance(now);
        return renderer.render(new ElementTree(widget));
    }

    private static Box fillOf(Widget widget, double now) {
        return paint(widget, now).children().getFirst();
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built bars are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Progress(40, 100, false, null, ID);

            var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                    progress id="p" value=40 max=100
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("an indeterminate bar is one too")
        void indeterminateParity() {
            var fromJava = new Progress(0, 1, true, null, ID);

            var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                    progress id="p" indeterminate=#true
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a maximum of zero has no reading, and is refused")
        void badMaximumRefused() {
            assertThrows(IllegalArgumentException.class, () -> new Progress(1, 0, false, null, ID));
            assertThrows(IllegalArgumentException.class, () -> new Progress(1, -5, false, null, ID));
        }
    }

    @Nested
    @DisplayName("the value")
    class Value {

        @Test
        @DisplayName("the fraction is the value over the maximum, clamped")
        void resolvedIsAFraction() {
            assertEquals(0.4, new Progress(40, 100, false, null, ID).resolved(), 1e-9);
            assertEquals(0.4, new Progress(0.4).resolved(), 1e-9);
            assertEquals(1.0, new Progress(999, 100, false, null, ID).resolved(), 1e-9);
            assertEquals(0.0, new Progress(-5, 100, false, null, ID).resolved(), 1e-9);
        }

        @Test
        @DisplayName("a bound bar follows the property, and takes any Number")
        void boundFollowsTheProperty() {
            Property<Number> done = Property.of(3);
            var bar = new Progress(0, 10, false, done, ID);

            assertEquals(0.3, bar.resolved(), 1e-9);
            done.set(7.5);
            assertEquals(0.75, bar.resolved(), 1e-9);
        }

        /// The fill's **width** is the value, and it is a plain percentage —
        /// which is the thing a slider cannot do, because a slider's fill shares
        /// its track with a 16px thumb (ADR-0079).
        @Test
        @DisplayName("the value reaches the box as a width, not as a flex ratio")
        void valueIsAWidth() {
            var fill = fillOf(new Progress(0.4), 0);

            assertEquals(Length.percent(40), fill.width());
            assertEquals(0, fill.flexGrow(), 1e-9, "a progress fill grows into nothing");
        }
    }

    @Nested
    @DisplayName("the sweep, which is a function of the clock and nothing else")
    class Sweep {

        @Test
        @DisplayName("an indeterminate bar keeps asking for frames and a determinate one does not")
        void onlySweepingAnimates() {
            assertTrue(Progress.sweeping().isAnimating());
            assertFalse(new Progress(0.4).isAnimating());
        }

        /// §1.7's idle frame loop, from the renderer's side: a window showing a
        /// spinner must keep painting, and one showing a finished bar must be
        /// allowed to stop.
        @Test
        @DisplayName("the renderer reports a sweeping bar as animating")
        void rendererStaysAwake() {
            var clock = Clock.virtual();
            var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get()).clock(clock);

            renderer.render(new ElementTree(Progress.sweeping()));
            assertTrue(renderer.isAnimating());

            renderer.render(new ElementTree(new Progress(0.4)));
            assertFalse(renderer.isAnimating(), "a bar with a value is a still picture");
        }

        @Test
        @DisplayName("the phase is the clock modulo the period, so it repeats exactly")
        void phaseRepeats() {
            assertEquals(0.0, ProgressFill.phaseAt(0), 1e-9);
            assertEquals(0.5, ProgressFill.phaseAt(600), 1e-9);
            assertEquals(0.0, ProgressFill.phaseAt(1200), 1e-9);
            // The clock's origin is arbitrary and may be enormous -- it is
            // `System.nanoTime` in a real window -- so the modulus has to come
            // first or the phase would lose precision within an hour of uptime.
            assertEquals(0.5, ProgressFill.phaseAt(1200 * 10_000 + 600), 1e-9);
        }

        /// The claim ADR-0081 rests on: **two bars are in step because neither
        /// remembers when it started.** A controller started at mount would put
        /// two bars that appeared a frame apart permanently out of phase, and
        /// nothing would look broken enough to investigate.
        @Test
        @DisplayName("two bars built at different times sweep together")
        void twoBarsAgree() {
            var first = Progress.sweeping();
            var second = Progress.sweeping();

            var one = fillOf(new Row(List.of(first), ID), 400)
                    .children()
                    .getFirst()
                    .transform();
            var other = fillOf(new Row(List.of(second), ID), 400)
                    .children()
                    .getFirst()
                    .transform();

            assertEquals(one, other);
        }

        /// **Off one edge and in at the other** ([ADR-0418]). The bar begins one
        /// whole bar-width before the track and ends one whole track-width after
        /// its own start, so it is outside the clip at both ends of the loop —
        /// which is what makes the wrap between them invisible.
        @Test
        @DisplayName("the bar starts entirely off the leading edge and ends entirely off the far one")
        void sweepRunsOffBothEdges() {
            // 0.3 of the track wide, so its leading edge crosses 1.3 of the
            // track -- which in units of the bar itself, which is what a
            // percentage translate means, is 433%.
            assertEquals(Length.percent(30), fillOf(Progress.sweeping(), 0).width());

            // The two ends of the travel, as the arithmetic states them. At the
            // top of the loop the bar's left edge is one whole bar to the left of
            // the track (-100% of itself); at the bottom it has crossed 1.3
            // tracks and its left edge sits on the track's right-hand edge
            // (1 / 0.3 = 333% of itself). Nothing is visible at either, which is
            // the same picture -- and is precisely why the wrap cannot be seen.
            assertEquals(-100, ProgressFill.offsetAt(0), 1e-9);
            assertEquals(1000.0 / 3, ProgressFill.offsetAt(1), 1e-9);

            // The second of those is a **limit** and not a frame: `phaseAt(1200)`
            // is zero, because 1200 ms is the top of the next loop rather than
            // the bottom of this one. A test that asserted it at 1200 would be
            // asserting the modulus is broken.
            assertEquals(-100, translateOf(fillOf(Progress.sweeping(), 0)), 1e-6);
            assertEquals(-100, translateOf(fillOf(Progress.sweeping(), 1200)), 1e-6);
            // And half way is half way, because it is linear the whole way.
            assertEquals((-100 + 1000.0 / 3) / 2, translateOf(fillOf(Progress.sweeping(), 600)), 1e-6, "linear (§3.1)");
        }

        /// The half of the drawing that was unavailable until ADR-0114, and the
        /// reason this bar may now leave its track at all: `progress` cuts it off.
        /// Without the clip, a bar a third of the way out is painted over whatever
        /// is beside the control.
        @Test
        @DisplayName("the track clips, which is what lets the bar leave it")
        void theTrackClips() {
            assertEquals(Overflow.HIDDEN, paint(Progress.sweeping(), 0).overflow());
            // On a determinate bar too. It has never needed it -- a fill that is
            // a width cannot leave its track -- but one rule for one widget is
            // how the two stop agreeing.
            assertEquals(Overflow.HIDDEN, paint(new Progress(0.4), 0).overflow());
        }

        @Test
        @DisplayName("it moves in one direction only, and never reverses")
        void sweepDoesNotReverse() {
            // The old drawing went out and came back, so two phases either side
            // of the midpoint were the *same place*. They must not be now: a bar
            // that reverses says the work has a far end to turn at.
            assertNotEquals(offset(300), offset(900));
            assertTrue(offset(300) < offset(600) && offset(600) < offset(900), "monotonic across the loop");
        }

        /// The cost of the new drawing, stated rather than hidden: there **is** a
        /// discontinuity at the loop boundary, of the whole travel, once every
        /// 1.2 seconds. It is invisible only because the bar is outside the clip
        /// on both sides of it — so this test asserts the jump exists *and* that
        /// it happens where nothing is drawn.
        @Test
        @DisplayName("the wrap is a real jump, and it happens off-screen at both ends")
        void theWrapIsHiddenRatherThanAbsent() {
            var before = offset(1199.99);
            var after = offset(1200.01);

            // Nearly the whole 433% of travel, between two frames a hundredth of
            // a millisecond apart.
            assertTrue(before - after > 430, "the whole travel, in one frame: " + before + " to " + after);
            // And both sides of it are outside the track, to within the hundredth
            // of a millisecond the samples are off the instant by: a bar 0.3 of
            // the track wide is clear of the leading edge at -100% of itself and
            // clear of the far edge at 333%.
            assertEquals(-100, after, 0.01, "after the wrap it has not entered yet");
            assertEquals(1000.0 / 3, before, 0.01, "before it, it had already left");
        }

        private static double offset(double now) {
            return ProgressFill.offsetAt(ProgressFill.phaseAt(now));
        }

        @Test
        @DisplayName("it moves by a transform, so a sweeping frame runs no layout")
        void sweepIsATransform() {
            var early = fillOf(Progress.sweeping(), 100);
            var later = fillOf(Progress.sweeping(), 500);

            assertEquals(early.width(), later.width(), "the width never changes");
            assertNotEquals(early.transform(), later.transform(), "the transform does");
        }

        /// §3.1: "reduced-motion: opacity pulse". The sweep is what goes away —
        /// and it goes away rather than slowing down, because a slower sweep is
        /// still movement.
        @Test
        @DisplayName("reduced motion stops the sweep dead")
        void reducedMotionDoesNotSweep() {
            var clock = Clock.virtual();
            var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                    .clock(clock)
                    .reducedMotion(true);

            clock.advance(500);
            var fill = renderer.render(new ElementTree(Progress.sweeping()))
                    .children()
                    .getFirst();

            assertTrue(fill.transform().isNone());
        }

        private static double translateOf(Box box) {
            if (box.transform().functions().getFirst() instanceof Transform.Function.Translate(var x, var _)) {
                assertTrue(x.percentage(), "the travel is a proportion of the bar itself");
                return x.value();
            }
            throw new AssertionError("a sweeping bar carries a translate");
        }
    }

    @Nested
    @DisplayName("style")
    class Style {

        @Test
        @DisplayName("§3's metrics: track 4, radius full")
        void metrics() {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            var tree = new ElementTree(new Progress(0.4));

            var bar = ComputedStyle.of(resolver.resolve(tree.root()), CssLength.Context.DEFAULT);

            assertEquals(Length.points(4), bar.height());
            assertEquals(Corners.all(2), bar.decoration().corners(), "half of 4 is a pill");
        }

        /// One widget, two drawings, and the selector that tells them apart is one
        /// an author already knows from the checkbox's mixed state.
        @Test
        @DisplayName("`progress:indeterminate` reaches a sweeping bar and not a valued one")
        void indeterminateIsSelectable() {
            var sheets = List.of(
                    Controls.baseStylesheet(), Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            progress:indeterminate { gap: 7px }
                            """));

            assertEquals(Length.points(7), gapOf(Progress.sweeping(), sheets));
            assertNotEquals(Length.points(7), gapOf(new Progress(0.4), sheets));
        }

        private static Length gapOf(Widget widget, List<Stylesheet> sheets) {
            // Rendered rather than resolved directly: `:indeterminate` is mirrored
            // onto the element by the renderer, which is the step being asserted.
            var tree = new ElementTree(widget);
            new WidgetRenderer(sheets, TestFont.get()).render(tree);
            return ComputedStyle.of(new StyleResolver(sheets).resolve(tree.root()), CssLength.Context.DEFAULT)
                    .gap();
        }
    }
}
