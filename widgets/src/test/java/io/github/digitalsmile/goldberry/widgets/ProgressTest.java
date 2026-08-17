package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The seventh control, and the first that is not a control: it reports and takes
/// nothing back ([ADR-0081]).
///
/// What is new here is **motion that is not a transition**. Everything that has
/// moved so far moved between two styles the cascade resolved; a sweep has no two
/// styles, so it is drawn from the frame clock — and the thing worth holding down
/// is that this needs no state, which is what these tests are mostly about.
class ProgressTest {

    private static final Widgets.Attributes ID =
            new Widgets.Attributes("p", Set.of(), "p");

    /// The box tree a renderer produces for `widget` at `now` on a virtual clock.
    private static io.github.digitalsmile.goldberry.layout.Box paint(Widget widget, double now) {
        var clock = Clock.virtual();
        var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                .clock(clock);
        clock.advance(now);
        return renderer.render(new ElementTree(widget));
    }

    private static io.github.digitalsmile.goldberry.layout.Box fillOf(Widget widget, double now) {
        return paint(widget, now).children().getFirst();
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built bars are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Progress(40, 100, false, null, ID);

            var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                    progress id="p" value=40 max=100
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("an indeterminate bar is one too")
        void indeterminateParity() {
            var fromJava = new Progress(0, 1, true, null, ID);

            var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                    progress id="p" indeterminate=#true
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("progress is a control type and progress-fill is a part")
        void fillIsAPart() {
            assertTrue(Controls.controlTypes().contains("progress"));
            assertFalse(Controls.inflater().registered().contains("progress-fill"));
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

            assertEquals(StyleLength.percent(40), fill.width());
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
            var renderer = new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                    .clock(clock);

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

            var one = fillOf(new Widgets.Row(List.of(first), ID), 400)
                    .children().getFirst().transform();
            var other = fillOf(new Widgets.Row(List.of(second), ID), 400)
                    .children().getFirst().transform();

            assertEquals(one, other);
        }

        /// **Inside the track, there and back.** The off-one-end-and-in-at-the-
        /// other drawing needs `overflow: hidden` to hide both the overhang and
        /// the wrap, and nothing here clips a box — so a bar that ran past its
        /// track would be drawn over its neighbours, once a loop, forever.
        @Test
        @DisplayName("the bar reverses at the ends and never leaves the track")
        void sweepStaysInsideTheTrack() {
            // 0.3 of the track wide, so it has the other 0.7 to cross -- which
            // in units of the bar itself, which is what a percentage translate
            // means, is 233%.
            assertEquals(StyleLength.percent(30), fillOf(Progress.sweeping(), 0).width());
            assertEquals(0, translateOf(fillOf(Progress.sweeping(), 0)), 1e-6);
            assertEquals(700.0 / 3, translateOf(fillOf(Progress.sweeping(), 600)), 1e-6);
            assertEquals(0, translateOf(fillOf(Progress.sweeping(), 1200)), 1e-6);
            // Half way out and half way back are the same place, which is what
            // "it reverses" means and what a wrapping sweep would not do.
            assertEquals(350.0 / 3, translateOf(fillOf(Progress.sweeping(), 300)), 1e-6);
            assertEquals(350.0 / 3, translateOf(fillOf(Progress.sweeping(), 900)), 1e-6);
        }

        @Test
        @DisplayName("the loop has no jump in it, because it turns rather than wraps")
        void sweepDoesNotJump() {
            // Two frames either side of the loop boundary. A bar that ran off
            // one end and came back in at the other would cross the **whole
            // travel** between these two, once every 1.2 seconds, in a control
            // nothing clips.
            var acrossTheWrap = Math.abs(travel(1199.99) - travel(1200.01));
            assertEquals(0, acrossTheWrap, 1e-4, "the loop turns rather than wrapping");

            // And it is genuinely moving elsewhere, or the assertion above would
            // pass for a bar that never went anywhere.
            assertTrue(Math.abs(travel(300) - travel(316)) > 0.01);
        }

        private static double travel(double now) {
            return ProgressFill.travelAt(ProgressFill.phaseAt(now));
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
            var fill = renderer.render(new ElementTree(Progress.sweeping())).children().getFirst();

            assertTrue(fill.transform().isNone());
        }

        private static double translateOf(io.github.digitalsmile.goldberry.layout.Box box) {
            if (box.transform().functions().getFirst()
                    instanceof Transform.Function.Translate(var x, var ignored)) {
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

            assertEquals(StyleLength.points(4), bar.height());
            assertEquals(2, bar.decoration().radius(), 1e-9, "half of 4 is a pill");
        }

        /// One widget, two drawings, and the selector that tells them apart is one
        /// an author already knows from the checkbox's mixed state.
        @Test
        @DisplayName("`progress:indeterminate` reaches a sweeping bar and not a valued one")
        void indeterminateIsSelectable() {
            var sheets = List.of(
                    Controls.baseStylesheet(),
                    Theme.NORD_DARK.load(),
                    Stylesheet.parse(CascadeLayer.APPLICATION, """
                            progress:indeterminate { gap: 7px }
                            """));

            assertEquals(StyleLength.points(7), gapOf(Progress.sweeping(), sheets));
            assertNotEquals(StyleLength.points(7), gapOf(new Progress(0.4), sheets));
        }

        private static StyleLength gapOf(Widget widget, List<Stylesheet> sheets) {
            // Rendered rather than resolved directly: `:indeterminate` is mirrored
            // onto the element by the renderer, which is the step being asserted.
            var tree = new ElementTree(widget);
            new WidgetRenderer(sheets, TestFont.get()).render(tree);
            return ComputedStyle.of(new StyleResolver(sheets).resolve(tree.root()),
                    CssLength.Context.DEFAULT).gap();
        }
    }
}
