package io.github.digitalsmile.goldberry.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssColor;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.css.Transitions;
import io.github.digitalsmile.goldberry.css.Transitions.Animatable;
import io.github.digitalsmile.goldberry.css.Transitions.Timing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The frame clock, the curves, and the overlay — `docs/design-system.md` §1.7.
class MotionTest {

    private static final Timing FAST = new Timing(100, Easing.LINEAR, 0);

    /// A style that transitions its background over 100 ms, linearly, so the
    /// arithmetic in a test is arithmetic and not a bezier solve.
    private static ComputedStyle style(int background) {
        return ComputedStyle.INITIAL
                .background(background)
                .transitions(Transitions.NONE.with(Animatable.BACKGROUND_COLOR, FAST));
    }

    @Nested
    @DisplayName("the clock")
    class TheClock {

        @Test
        @DisplayName("a virtual clock moves only when told to")
        void virtualClock() {
            var clock = Clock.virtual();

            assertEquals(0, clock.nowMillis());
            clock.advance(80);
            assertEquals(80, clock.nowMillis());
            assertEquals(80, clock.nowMillis(), "reading it does not move it");
        }

        @Test
        @DisplayName("a clock does not run backwards")
        void monotonic() {
            // A test that could rewind could assert on a state no real frame is
            // ever in.
            var clock = Clock.virtual().advance(100);

            assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
            assertThrows(IllegalArgumentException.class, () -> clock.set(50));
        }

        @Test
        @DisplayName("the system clock is monotonic, not the wall clock")
        void systemClock() {
            // nanoTime rather than currentTimeMillis: an animation must not jump
            // because NTP stepped the clock or the user changed time zone.
            var clock = Clock.system();
            var first = clock.nowMillis();
            var second = clock.nowMillis();

            assertTrue(second >= first, second + " went backwards from " + first);
        }
    }

    @Nested
    @DisplayName("easing")
    class Curves {

        @Test
        @DisplayName("every curve starts at 0 and ends at 1")
        void endpoints() {
            for (var easing : Easing.values()) {
                assertEquals(0, easing.at(0), 1e-9, easing.toString());
                assertEquals(1, easing.at(1), 1e-9, easing.toString());
                assertEquals(0, easing.at(-5), 1e-9, "clamped below");
                assertEquals(1, easing.at(5), 1e-9, "clamped above");
            }
        }

        @Test
        @DisplayName("every curve is monotonic, so a value never goes backwards")
        void monotonic() {
            // "No bounce or overshoot in system components" (§1.7). A curve that
            // exceeded 1 or dipped below 0 would be an overshoot nobody asked
            // for -- and the solver's Newton step is exactly what would produce
            // one if it were not bounded.
            for (var easing : Easing.values()) {
                var previous = -1.0;
                for (var i = 0; i <= 200; i++) {
                    var value = easing.at(i / 200.0);
                    assertTrue(value >= previous - 1e-9,
                            easing + " went backwards at " + i / 200.0);
                    assertTrue(value >= -1e-9 && value <= 1 + 1e-9,
                            easing + " left 0..1 at " + i / 200.0 + ": " + value);
                    previous = value;
                }
            }
        }

        @Test
        @DisplayName("ease-enter decelerates and ease-exit accelerates")
        void shapes() {
            // The whole reason there are two: an enter is most of the way there
            // by halfway, an exit has barely started. §1.7 pairs `ease-exit`
            // with a shorter duration, and together they make a dismissal feel
            // decisive rather than reluctant.
            assertTrue(Easing.EASE_ENTER.at(0.5) > 0.5,
                    "a decelerating curve is ahead at the midpoint");
            assertTrue(Easing.EASE_EXIT.at(0.5) < 0.5,
                    "an accelerating curve is behind at the midpoint");
            assertEquals(0.5, Easing.LINEAR.at(0.5), 1e-9);
        }

        @Test
        @DisplayName("the three keywords parse and nothing else does")
        void parsing() {
            assertEquals(Easing.EASE_ENTER, Easing.parse("ease-enter"));
            assertEquals(Easing.LINEAR, Easing.parse("linear"));

            // CSS has `ease-in-out`; this system does not. A stylesheet naming
            // it is a dropped declaration rather than a curve nobody chose.
            assertEquals(null, Easing.parse("ease-in-out"));
            assertEquals(null, Easing.parse("cubic-bezier"));
        }
    }

    @Nested
    @DisplayName("colour interpolation")
    class Interpolation {

        @Test
        @DisplayName("the midpoint keeps roughly twice the chroma sRGB would")
        void noGreyDeadZone() {
            // The whole argument for OKLCH (§1.7), measured rather than asserted.
            // Nord's danger red and success green:
            //
            //   sRGB midpoint  #b18f7b   channel spread 54
            //   OKLCH midpoint #bf9152   channel spread 109
            //
            // Channel spread stands in for chroma, which is exactly what an
            // unweighted average in a gamma-encoded space destroys — the sRGB
            // answer is a muddy tan that reads as neither end.
            var oklch = CssColor.mix(0xFFBF616A, 0xFFA3BE8C, 0.5);

            assertTrue(spread(oklch) > spread(naiveMidpoint(0xFFBF616A, 0xFFA3BE8C)) * 1.5,
                    () -> "the midpoint went muddy: #" + Integer.toHexString(oklch));
        }

        private int spread(int argb) {
            var r = (argb >>> 16) & 0xFF;
            var g = (argb >>> 8) & 0xFF;
            var b = argb & 0xFF;
            return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
        }

        /// What averaging the encoded channels gives — the thing OKLCH is here
        /// to avoid.
        private int naiveMidpoint(int from, int to) {
            return (((((from >>> 16) & 0xFF) + ((to >>> 16) & 0xFF)) / 2) << 16)
                    | (((((from >>> 8) & 0xFF) + ((to >>> 8) & 0xFF)) / 2) << 8)
                    | ((((from & 0xFF) + (to & 0xFF)) / 2));
        }

        @Test
        @DisplayName("the ends are exact")
        void endpoints() {
            assertEquals(0xFFBF616A, CssColor.mix(0xFFBF616A, 0xFFA3BE8C, 0));
            assertEquals(0xFFA3BE8C, CssColor.mix(0xFFBF616A, 0xFFA3BE8C, 1));
            assertEquals(0xFFBF616A, CssColor.mix(0xFFBF616A, 0xFFA3BE8C, -1), "clamped");
        }

        @Test
        @DisplayName("alpha moves linearly and independently")
        void alpha() {
            var mixed = CssColor.mix(0x00FFFFFF, 0xFFFFFFFF, 0.5);
            assertEquals(0x80, (mixed >>> 24) & 0xFF, 1);
        }

        @Test
        @DisplayName("a fade to a grey does not sweep through hues neither end has")
        void powerlessHue() {
            // A grey has no hue -- the angle is noise. Interpolating towards it
            // around an arbitrary arc would send a fading blue through green.
            for (var i = 1; i < 10; i++) {
                var mixed = CssColor.mix(0xFF5E81AC, 0xFF808080, i / 10.0);
                var r = (mixed >>> 16) & 0xFF;
                var b = mixed & 0xFF;
                assertTrue(b >= r, () -> "a fading blue became red: " + Integer.toHexString(mixed));
            }
        }
    }

    @Nested
    @DisplayName("the overlay")
    class Overlay {

        @Test
        @DisplayName("the first frame starts nothing")
        void firstFrameIsNotAChange() {
            // A control appearing is not a control changing. Otherwise a window
            // would fade every control in from black when it opened.
            var animations = new Animations();
            animations.observe(style(0xFF000000), 0);

            assertFalse(animations.isAnimating());
        }

        @Test
        @DisplayName("a change starts a transition, and the value is between the two")
        void interpolates() {
            var animations = new Animations();
            animations.observe(style(0xFF000000), 0);
            animations.observe(style(0xFFFFFFFF), 0);

            assertTrue(animations.isAnimating());

            var midway = animations.apply(style(0xFFFFFFFF), 50).background();
            assertNotEquals(0xFF000000, midway);
            assertNotEquals(0xFFFFFFFF, midway);
        }

        @Test
        @DisplayName("the target is reached exactly, and then it stops")
        void arrives() {
            var animations = new Animations();
            animations.observe(style(0xFF000000), 0);
            animations.observe(style(0xFFFFFFFF), 0);

            assertEquals(0xFFFFFFFF, animations.apply(style(0xFFFFFFFF), 100).background());

            // And the last frame is painted at the target *before* it is
            // forgotten, which is why settle runs after apply.
            assertFalse(animations.settle(100));
            assertFalse(animations.isAnimating());
        }

        @Test
        @DisplayName("retargeting mid-flight starts from the current value")
        void retargets() {
            // §1.7: "values never jump". A pointer leaving a button 50ms into a
            // 100ms fade must return from where the colour actually is.
            var animations = new Animations();
            animations.observe(style(0xFF000000), 0);
            animations.observe(style(0xFFFFFFFF), 0);
            var midway = animations.apply(style(0xFFFFFFFF), 50).background();

            // Reverse at 50ms.
            animations.observe(style(0xFF000000), 50);
            var justAfter = animations.apply(style(0xFF000000), 51).background();

            var midwayLuma = (midway >>> 16) & 0xFF;
            var afterLuma = (justAfter >>> 16) & 0xFF;
            assertTrue(Math.abs(midwayLuma - afterLuma) < 20,
                    "the value jumped from " + midwayLuma + " to " + afterLuma);
            assertTrue(afterLuma < midwayLuma, "and it is heading back down");
        }

        @Test
        @DisplayName("the animated value is never written back into the target")
        void neverWrittenBack() {
            // The sentence the whole design hangs off. If the overlay were
            // written back, the next cascade would diff the halfway colour
            // against the target and start again from it -- a control that
            // approached its hover colour and never arrived.
            var animations = new Animations();
            var target = style(0xFFFFFFFF);
            animations.observe(style(0xFF000000), 0);
            animations.observe(target, 0);

            var painted = animations.apply(target, 50);

            assertEquals(0xFFFFFFFF, target.background(), "apply must not mutate its argument");
            assertNotEquals(target.background(), painted.background(),
                    "the overlay is a separate value, and it is what gets drawn");

            // And the next frame diffs against the target, not the overlay: it
            // is already heading there, so it does not restart from halfway.
            animations.observe(target, 50);
            assertEquals(0xFFFFFFFF, animations.apply(target, 100).background(),
                    "it still arrives on time");
        }

        @Test
        @DisplayName("a property that stops being declared stops moving")
        void undeclaredStops() {
            // A rule that removed its own transition means the author wants the
            // value now, and continuing would animate against a stylesheet that
            // no longer asks for it.
            var animations = new Animations();
            animations.observe(style(0xFF000000), 0);
            animations.observe(style(0xFFFFFFFF), 0);
            assertTrue(animations.isAnimating());

            animations.observe(ComputedStyle.INITIAL.background(0xFF00FF00), 10);
            assertFalse(animations.isAnimating());
        }

        @Test
        @DisplayName("a zero duration snaps and asks for no frame")
        void instant() {
            // What `prefers-reduced-motion` collapses everything to (§1.7 rule
            // 6), and what `button:active` declares so a press applies in 0ms.
            var instant = ComputedStyle.INITIAL
                    .transitions(Transitions.NONE.with(Animatable.BACKGROUND_COLOR, Timing.INSTANT));
            var animations = new Animations();

            animations.observe(instant.background(0xFF000000), 0);
            animations.observe(instant.background(0xFFFFFFFF), 0);

            assertFalse(animations.isAnimating());
            assertEquals(0xFFFFFFFF, animations.apply(instant.background(0xFFFFFFFF), 0).background());
        }

        @Test
        @DisplayName("a delay holds the value before it moves")
        void delay() {
            var delayed = ComputedStyle.INITIAL.transitions(Transitions.NONE.with(
                    Animatable.BACKGROUND_COLOR, new Timing(100, Easing.LINEAR, 50)));
            var animations = new Animations();

            animations.observe(delayed.background(0xFF000000), 0);
            animations.observe(delayed.background(0xFFFFFFFF), 0);

            assertEquals(0xFF000000, animations.apply(delayed.background(0xFFFFFFFF), 40).background(),
                    "still waiting");
            assertTrue(animations.settle(140), "and the delay counts towards the span");
            assertFalse(animations.settle(151));
        }

        @Test
        @DisplayName("opacity moves linearly, not through a colour space")
        void opacityIsANumber() {
            var fading = ComputedStyle.INITIAL
                    .transitions(Transitions.NONE.with(Animatable.OPACITY, FAST));
            var animations = new Animations();

            animations.observe(fading.opacity(1), 0);
            animations.observe(fading.opacity(0.45), 0);

            assertEquals(0.725, animations.apply(fading.opacity(0.45), 50).opacity(), 1e-6);
        }

        @Test
        @DisplayName("nothing running means the style is handed straight back")
        void noAllocation() {
            var animations = new Animations();
            var target = style(0xFF000000);

            assertSame(target, animations.apply(target, 0),
                    "a static tree must not allocate a style per node per frame");
        }
    }

    @Nested
    @DisplayName("animating a transform")
    class TransformOverlay {

        /// A style that transitions its transform over 100 ms, linearly.
        private static ComputedStyle moving(Transform transform) {
            return ComputedStyle.INITIAL
                    .transform(transform)
                    .transitions(Transitions.NONE.with(Animatable.TRANSFORM, FAST));
        }

        private static Transform scale(double factor) {
            return Transform.of(new Transform.Function.Scale(factor, factor));
        }

        @Test
        @DisplayName("a scale moves through the numbers, not through the matrix")
        void scales() {
            // The checkbox tick's specified `scale 0.6 -> 1`. Halfway is 0.8,
            // which is the value an author would predict -- and the reason
            // interpolation happens on the function list rather than on the six
            // matrix entries it multiplies out to.
            var animations = new Animations();
            animations.observe(moving(scale(0.6)), 0);
            animations.observe(moving(scale(1.0)), 0);

            var halfway = animations.apply(moving(scale(1.0)), 50);
            assertEquals(
                    java.util.List.of(new Transform.Function.Scale(0.8, 0.8)),
                    halfway.transform().functions());
        }

        @Test
        @DisplayName("the target style is never mutated, exactly as for a colour")
        void doesNotWriteBack() {
            // The sentence the whole overlay design hangs off. A transform
            // written back would be diffed against the target on the next frame
            // and would start again from where it had got to -- a control that
            // approaches its scale and never arrives.
            var animations = new Animations();
            animations.observe(moving(scale(0.6)), 0);
            animations.observe(moving(scale(1.0)), 0);

            var target = moving(scale(1.0));
            animations.apply(target, 50);
            assertEquals(scale(1.0), target.transform());
        }

        @Test
        @DisplayName("retargeting starts from where the transform actually is")
        void retargets() {
            // §1.7's "values never jump". A control whose hover scale is reversed
            // halfway must shrink from 1.2, not from the 1.4 it never reached.
            var animations = new Animations();
            animations.observe(moving(scale(1.0)), 0);
            animations.observe(moving(scale(1.4)), 0);
            // Halfway: 1.2. Now reverse.
            animations.observe(moving(scale(1.0)), 50);

            var immediately = animations.apply(moving(scale(1.0)), 50);
            var scaled = (Transform.Function.Scale) immediately.transform().functions().getFirst();
            assertEquals(1.2, scaled.x(), 1e-9, "it resumes from where it was");
        }

        @Test
        @DisplayName("a transform transition ends and stops asking for frames")
        void settles() {
            var animations = new Animations();
            animations.observe(moving(scale(0.6)), 0);
            animations.observe(moving(scale(1.0)), 0);

            assertTrue(animations.isAnimating());
            assertTrue(animations.settle(50), "still moving at halfway");
            assertFalse(animations.settle(100), "done at the end");
            assertEquals(0, animations.runningCount());
        }

        @Test
        @DisplayName("`transition: transform` parses, where `transition: width` still does not")
        void parses() {
            assertEquals(Animatable.TRANSFORM, Animatable.parse("transform"));
            assertNull(Animatable.parse("width"),
                    "layout properties never transition (§1.7)");
        }
    }

    @Nested
    @DisplayName("reduced motion (§1.7 rule 6)")
    class Reduced {

        @Test
        @DisplayName("every transition collapses to instant, and none is dropped")
        void collapses() {
            // Kept at zero rather than removed, so the machinery still runs and
            // still ends: a reduced-motion user reaches the same states by the
            // same route rather than taking a different path through the toolkit.
            var reduced = Transitions.NONE
                    .with(Animatable.BACKGROUND_COLOR, FAST)
                    .with(Animatable.OPACITY, FAST)
                    .reduced();

            assertEquals(2, reduced.byProperty().size());
            assertTrue(reduced.get(Animatable.BACKGROUND_COLOR).isInstant());
            assertTrue(reduced.get(Animatable.OPACITY).isInstant());
        }
    }
}
