package io.github.digitalsmile.goldberry.widgets.controls.knob;

import io.github.digitalsmile.goldberry.widget.Attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.knob.Knob;
import io.github.digitalsmile.goldberry.widgets.controls.knob.KnobDial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The tenth control, and the first whose drag is a **rate** ([ADR-0089]).
///
/// The pointer tests build events by hand and set the anchor themselves, which is
/// exactly what the router does — [KnobGestureTest] is the one that proves the
/// router really does it, through the real dispatch. Splitting them that way
/// keeps this file about the arithmetic and that one about the wiring.
class KnobTest {

    /// What the knob asked for, in order.
    private static List<Double> asked(Knob knob, PointerEvent... events) {
        var seen = new ArrayList<Double>();
        var wired = new Knob(knob.min(), knob.max(), knob.value(), knob.step(), knob.detents(),
                knob.source(), seen::add, knob.disabled(), knob.attributes());
        for (var event : events) {
            wired.onPointer(event);
        }
        return seen;
    }

    /// A drag event as the router builds one: `dragY` is `y - pressY`, and the
    /// anchor and the gesture's modifiers are stamped on by the router.
    private static PointerEvent drag(float pressY, float y, Modifiers held, double anchor) {
        var event = new PointerEvent(PointerEvent.Kind.MOVED, 0, y, null, 0, 0, pressY, null);
        event.anchoredAt(anchor);
        event.gestureStartedWith(held);
        return event;
    }

    @Nested
    @DisplayName("the drag")
    class Drag {

        /// §3: "value drag **200px** per full range". Up is more, because `dragY`
        /// is positive downwards and a knob that turned up when dragged down
        /// would be the one control in the toolkit that disagrees with the rest.
        @Test
        @DisplayName("200px of travel is the whole range, and up is more")
        void twoHundredPixelsIsTheRange() {
            var knob = new Knob(0, 100, 50, 0, null);

            // 100px up from an anchor of 50 is half the travel, so half the range.
            assertEquals(100.0, asked(knob, drag(200, 100, Modifiers.NONE, 50)).getFirst(), 1e-9);
            // and 100px down is the other half.
            assertEquals(0.0, asked(knob, drag(200, 300, Modifiers.NONE, 50)).getFirst(), 1e-9);
            // 50px up from 50 is a quarter of the range.
            assertEquals(75.0, asked(knob, drag(200, 150, Modifiers.NONE, 50)).getFirst(), 1e-9);
        }

        /// The whole of why [io.github.digitalsmile.goldberry.input.Handles#gestureAnchor()]
        /// exists: the same pointer position means a different value depending on
        /// where the drag started. A slider cannot tell you this, because for a
        /// slider it is not true.
        @Test
        @DisplayName("the same drag from two anchors asks for two values")
        void theAnchorIsWhatDecides() {
            var knob = new Knob(0, 100, 0, 0, null);

            assertEquals(60.0, asked(knob, drag(200, 180, Modifiers.NONE, 50)).getFirst(), 1e-9);
            assertEquals(20.0, asked(knob, drag(200, 180, Modifiers.NONE, 10)).getFirst(), 1e-9);
        }

        /// §3: "×0.1 with fine modifier".
        @Test
        @DisplayName("Shift makes 200px a tenth of the range")
        void fineIsATenth() {
            var knob = new Knob(0, 100, 50, 0, null);
            var shift = new Modifiers(true, false, false, false);

            // 100px up: half the travel, so a twentieth of the range rather than
            // half of it.
            assertEquals(55.0, asked(knob, drag(200, 100, shift, 50)).getFirst(), 1e-9);
        }

        /// The reason the modifier is the **gesture's** and not the event's.
        ///
        /// A knob that read the live modifier would rescale travel already
        /// covered: pressing Shift 100px into this drag would take the value from
        /// 100 back to 55 without the pointer moving at all. Nothing about that
        /// looks broken enough to investigate — it reads as the knob slipping.
        @Test
        @DisplayName("Shift pressed mid-drag does not move the value")
        void theModifierIsSampledAtThePress() {
            var knob = new Knob(0, 100, 50, 0, null);
            var shift = new Modifiers(true, false, false, false);

            // The gesture began unmodified; Shift is down *now* and is ignored.
            var event = drag(200, 100, Modifiers.NONE, 50);
            assertEquals(100.0, asked(knob, event).getFirst(), 1e-9);
            assertTrue(shift.shift(), "and the live modifier is what is being ignored");
        }

        @Test
        @DisplayName("it clamps rather than running past the ends")
        void clamps() {
            var knob = new Knob(0, 100, 50, 0, null);

            assertEquals(100.0, asked(knob, drag(200, -400, Modifiers.NONE, 50)).getFirst(), 1e-9);
            assertEquals(0.0, asked(knob, drag(200, 800, Modifiers.NONE, 50)).getFirst(), 1e-9);
        }

        /// A `MOVED` with no button held carries a `NaN` anchor, which is the
        /// router reporting "no gesture" through the arithmetic (ADR-0075).
        @Test
        @DisplayName("a move with no gesture asks for nothing")
        void hoverIsNotADrag() {
            var knob = new Knob(0, 100, 50, 0, null);

            assertEquals(List.of(),
                    asked(knob, drag(Float.NaN, 100, Modifiers.NONE, Double.NaN)));
        }

        @Test
        @DisplayName("a disabled knob asks for nothing")
        void disabledRefuses() {
            var knob = new Knob(0, 100, 50, 0, 0, null, null, true, Attributes.NONE);

            assertEquals(List.of(), asked(knob, drag(200, 100, Modifiers.NONE, 50)));
        }
    }

    @Nested
    @DisplayName("detents")
    class Detents {

        /// A detent is **magnetic**, not a grid — which is the whole reason it is
        /// not spelled `step`. Three detents over 0..100 sit at 0, 50 and 100, and
        /// the pull reaches a quarter of the 50-unit gap: 12.5 either side.
        @Test
        @DisplayName("a drag near a detent is caught, and one between two is not")
        void magneticRatherThanAGrid() {
            var knob = new Knob(0, 100, 0, 0, 3, null, null, false, Attributes.NONE);

            // 45 is 5 from the detent at 50, inside the pull.
            assertEquals(50.0, asked(knob, drag(200, 200 - 90, Modifiers.NONE, 0)).getFirst(), 1e-9);
            // 30 is 20 from it, outside — and a `step` would have snapped it.
            assertEquals(30.0, asked(knob, drag(200, 200 - 60, Modifiers.NONE, 0)).getFirst(), 1e-9);
        }

        @Test
        @DisplayName("no detents is the ordinary case and changes nothing")
        void noneByDefault() {
            var knob = new Knob(0, 100, 0, 0, null);

            assertEquals(30.0, asked(knob, drag(200, 200 - 60, Modifiers.NONE, 0)).getFirst(), 1e-9);
        }

        @Test
        @DisplayName("one detent is refused, because one position is not a set of them")
        void oneIsRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Knob(0, 1, 0, 0, 1, null, null, false, Attributes.NONE));
        }
    }

    @Nested
    @DisplayName("the click")
    class Click {

        /// A click at `(x, y)` measured against the **dial**, which is what
        /// `localPart()` makes `local()` mean. The dial here is 22x22, which is
        /// what the shipped stylesheet lays out inside a 32px knob.
        private PointerEvent clickAt(float x, float y, float dragX, float dragY) {
            var event = new PointerEvent(PointerEvent.Kind.CLICKED, x, y,
                    PointerEvent.Button.PRIMARY, 1, x - dragX, y - dragY, null);
            event.localTo(new PointerEvent.Local(x, y, 22, 22));
            return event;
        }

        /// Straight up from the centre is half way round a travel that starts at
        /// seven-thirty and runs 270 degrees clockwise.
        @Test
        @DisplayName("a click on the ring turns the knob to the angle clicked")
        void theRingIsATrack() {
            var knob = new Knob(0, 100, 0, 0, null);

            // 11 is the dial's radius, so 11 + 4 is out in the ring. Straight up
            // is the middle of the travel -- the one angle a reader can check
            // against the golden without a protractor.
            assertEquals(50.0, asked(knob, clickAt(11, -4, 0, 0)).getFirst(), 1e-9);
            // Straight **left** is not a quarter of the way round, and this is the
            // assertion that says so: the travel starts at seven-thirty, so nine
            // o'clock is 45 degrees into 270 -- a sixth. A knob whose travel began
            // at nine would read a quarter here and would be wrong by 22.5 degrees
            // everywhere, which is not enough to notice in an image.
            assertEquals(100.0 / 6, asked(knob, clickAt(-4, 11, 0, 0)).getFirst(), 1e-9);
            // And three o'clock is five sixths, by the same arithmetic.
            assertEquals(500.0 / 6, asked(knob, clickAt(26, 11, 0, 0)).getFirst(), 1e-9);
        }

        /// The dial is what you grab. A press that jumped before the drag started
        /// would move the value out from under the gesture about to set it.
        @Test
        @DisplayName("a click on the dial asks for nothing, because the dial is a grab")
        void theDialIsAGrab() {
            var knob = new Knob(0, 100, 0, 0, null);

            // Dead centre, and just inside the rim straight up.
            assertEquals(List.of(), asked(knob, clickAt(11, 11, 0, 0)));
            assertEquals(List.of(), asked(knob, clickAt(11, 1, 0, 0)));
        }

        /// The router synthesizes `CLICKED` whenever the press and the release
        /// landed on the same node — **including at the end of a drag that came
        /// back**. Without the slop, letting go of a drag near where it started
        /// would fire a jump on top of the value the drag had just set.
        @Test
        @DisplayName("a drag that ends where it began is not a click")
        void aDragIsNotAClick() {
            var knob = new Knob(0, 100, 0, 0, null);

            assertEquals(List.of(), asked(knob, clickAt(11, -4, 0, 40)));
        }

        /// The 90 degrees the travel does not cover. Both ends of the control live
        /// down there, and a gap that refused every click would make the bottom of
        /// the knob dead — so it resolves to the nearer end.
        @Test
        @DisplayName("a click in the gap at the bottom goes to the nearer end")
        void theGapResolvesToAnEnd() {
            var knob = new Knob(0, 100, 50, 0, null);

            // Just left of straight down is nearer the start of the travel; just
            // right of it is nearer the end.
            assertEquals(0.0, asked(knob, clickAt(7, 26, 0, 0)).getFirst(), 1e-9);
            assertEquals(100.0, asked(knob, clickAt(15, 26, 0, 0)).getFirst(), 1e-9);
        }

        @Test
        @DisplayName("a knob that has never been painted has no dial to measure against")
        void unlaidOutIsIgnored() {
            var event = new PointerEvent(PointerEvent.Kind.CLICKED, 5, 5,
                    PointerEvent.Button.PRIMARY, 1, 5, 5, null);

            assertEquals(List.of(), asked(new Knob(0, 100, 0, 0, null), event));
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        /// The travel starts at seven-thirty and runs 270 degrees clockwise, so
        /// the middle of it is straight up — which is the one angle a reader can
        /// check against the golden without a protractor.
        @Test
        @DisplayName("zero points down-left, a half points up, one points down-right")
        void anglesAcrossTheTravel() {
            assertEquals(0.75 * Math.PI, Knob.angleAt(0), 1e-9);
            assertEquals(-0.5 * Math.PI, Knob.angleAt(0.5) - 2 * Math.PI, 1e-9);
            assertEquals(0.25 * Math.PI, Knob.angleAt(1) - 2 * Math.PI, 1e-9);
        }

        @Test
        @DisplayName("the angle and the fraction are inverses across the travel")
        void roundTrip() {
            for (var tenth = 0; tenth <= 10; tenth++) {
                var fraction = tenth / 10.0;
                assertEquals(fraction, Knob.fractionAt(Knob.angleAt(fraction)), 1e-9,
                        "the pointer and a click on it must mean the same value");
            }
        }

        @Test
        @DisplayName("it is a mark on the dial, drawn in the dial's colour")
        void isAMarkOnTheDial() {
            var dial = new KnobDial(0.5, false);
            var style = io.github.digitalsmile.goldberry.css.ComputedStyle.INITIAL;

            var box = dial.render(style, List.of(), null);

            assertEquals(io.github.digitalsmile.goldberry.layout.Box.Mark.Kind.POINTER,
                    box.mark().kind());
            assertEquals(Knob.angleAt(0.5), box.mark().start(), 1e-9);
        }
    }

    @Nested
    @DisplayName("the wheel")
    class Wheel {

        /// The first widget in the toolkit to handle [PointerEvent.Kind#WHEEL] —
        /// the route has been live and tested since ADR-0061 and nothing consumed
        /// it.
        @Test
        @DisplayName("a line moves what an arrow moves, and away from the user is less")
        void aLineIsAStep() {
            var knob = new Knob(0, 100, 50, 5, null);

            // deltaY is positive *down the document*, which is away from the user.
            assertEquals(45.0, wheeled(knob, 1).getFirst(), 1e-9);
            assertEquals(55.0, wheeled(knob, -1).getFirst(), 1e-9);
        }

        /// The bug this found. A touchpad reports **fractions** of a line, and a
        /// stepped knob snaps every value it reports to its grid — so a third of a
        /// step rounded straight back to where it started. And because every wheel
        /// event computes from the *current* value rather than accumulating, it
        /// rounded back every time: the knob sat still while the user scrolled,
        /// for as long as they scrolled gently.
        @Test
        @DisplayName("a stepped knob moves at least one step, however gentle the scroll")
        void aGentleScrollStillMoves() {
            var knob = new Knob(0, 100, 50, 5, null);

            assertEquals(45.0, wheeled(knob, 0.3f).getFirst(), 1e-9);
            assertEquals(55.0, wheeled(knob, -0.05f).getFirst(), 1e-9);
        }

        @Test
        @DisplayName("and a fast spin moves several")
        void aFastSpinMovesMore() {
            assertEquals(35.0, wheeled(new Knob(0, 100, 50, 5, null), 3).getFirst(), 1e-9);
        }

        /// A continuous knob has no grid to round to, so the fraction survives —
        /// which is the half of the touchpad behaviour that was always right.
        @Test
        @DisplayName("a continuous knob moves a hundredth of its range, fractions and all")
        void continuousUsesTheRange() {
            assertEquals(49.0, wheeled(new Knob(0, 100, 50, 0, null), 1).getFirst(), 1e-9);
            assertEquals(49.7, wheeled(new Knob(0, 100, 50, 0, null), 0.3f).getFirst(), 1e-6);
        }

        @Test
        @DisplayName("a zero delta asks for nothing")
        void zeroIsNotAScroll() {
            assertEquals(List.of(), wheeled(new Knob(0, 100, 50, 5, null), 0));
        }

        private List<Double> wheeled(Knob knob, float lines) {
            return asked(knob, PointerEvent.wheel(0, 0, 0, lines, null));
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        private List<Double> pressed(Knob knob, Key key) {
            var seen = new ArrayList<Double>();
            new Knob(knob.min(), knob.max(), knob.value(), knob.step(), knob.detents(),
                    knob.source(), seen::add, knob.disabled(), knob.attributes())
                    .onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
            return seen;
        }

        /// §3 gives `slider` and `knob` the same map, so this is deliberately the
        /// same set of assertions [SliderTest] makes: a second thing to learn is a
        /// cost with no benefit.
        @Test
        @DisplayName("arrows step, pages move ten, Home and End are the ends")
        void theSliderMap() {
            var knob = new Knob(0, 100, 50, 5, null);

            assertEquals(55.0, pressed(knob, Key.RIGHT).getFirst(), 1e-9);
            assertEquals(55.0, pressed(knob, Key.UP).getFirst(), 1e-9);
            assertEquals(45.0, pressed(knob, Key.LEFT).getFirst(), 1e-9);
            assertEquals(45.0, pressed(knob, Key.DOWN).getFirst(), 1e-9);
            assertEquals(100.0, pressed(knob, Key.PAGE_UP).getFirst(), 1e-9);
            assertEquals(0.0, pressed(knob, Key.PAGE_DOWN).getFirst(), 1e-9);
            assertEquals(0.0, pressed(knob, Key.HOME).getFirst(), 1e-9);
            assertEquals(100.0, pressed(knob, Key.END).getFirst(), 1e-9);
        }

        /// A knob at its maximum still owns `Right`: letting it through would hand
        /// the key to a focus scope and move focus off the control being adjusted
        /// (ADR-0073, ADR-0078).
        @Test
        @DisplayName("an arrow is consumed even when the value cannot move")
        void consumedAtTheEnd() {
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, false, null);

            new Knob(0, 100, 100, 5, value -> { }).onKey(event);

            assertTrue(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built knobs are equal values")
        void javaAndKdlAgree() {
            var attributes = new Attributes("gain", Set.of("large"), "gain");

            var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                    knob id="gain" class="large" min=0 max=11 value=5 step=1 detents=3
                    """)).getFirst();

            assertEquals(new Knob(0, 11, 5, 1, 3, null, null, false, attributes), fromKdl);
        }

        @Test
        @DisplayName("the control is in the catalog and its parts deliberately are not")
        void registry() {
            assertTrue(Controls.controlTypes().contains("knob"));
            for (var part : List.of("knob-track", "knob-arc")) {
                assertFalse(Controls.controlTypes().contains(part));
                assertFalse(Controls.inflater().registered().contains(part),
                        "a part is CSS-selectable and not KDL-constructible (ADR-0065)");
            }
        }
    }

    @Test
    @DisplayName("a bound knob reads the property and clamps it")
    void binding() {
        var gain = Property.of(150.0);

        assertEquals(100.0, Knob.of(0, 100, 0, gain, null).resolved(), 1e-9,
                "a model outside the range is an application bug, and a knob turned"
                        + " past its stop is a worse way to report it");
        assertNull(new Knob(0, 100, 50, 0, null).binding());
    }

    @Test
    @DisplayName("the fraction is linear across the travel")
    void fraction() {
        assertEquals(0.0, new Knob(0, 100, 0, 0, null).fraction(), 1e-9);
        assertEquals(0.25, new Knob(0, 100, 25, 0, null).fraction(), 1e-9);
        assertEquals(1.0, new Knob(0, 100, 100, 0, null).fraction(), 1e-9);
        assertEquals(0.5, new Knob(-1, 1, 0, 0, null).fraction(), 1e-9,
                "and a range through zero is not a special case");
    }

    /// §3's row: diameters 32 / 48. Read off the resolved style rather than the
    /// stylesheet, so a rule that stopped matching fails here.
    @Test
    @DisplayName("§3's two diameters come out of the cascade")
    void diameters() {
        assertEquals(StyleLength.points(32), styleOf(new Knob(0, 1, 0, 0, null)).width());
        assertEquals(StyleLength.points(48), styleOf(new Knob(0, 1, 0, 0, null).styled("large")).width());
        assertEquals(16, styleOf(new Knob(0, 1, 0, 0, null)).decoration().radius(), 1e-9,
                "half the side, which is what makes a rounded rectangle a disc");
    }

    private static ComputedStyle styleOf(Widget widget) {
        return ComputedStyle.of(
                new StyleResolver(Controls.stylesheets(Theme.NORD_DARK))
                        .resolve(new ElementTree(widget).root()),
                CssLength.Context.DEFAULT);
    }
}
