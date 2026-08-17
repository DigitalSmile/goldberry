package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The sixth control, and the first whose value is **a number rather than a
/// state** ([ADR-0079]).
///
/// [ToggleTest] proved a gesture. What is new here is that no stylesheet can name
/// where the thumb goes, because the position came out of a model — so the widget
/// places it, and the arithmetic it does on the way (snap, clamp, step) is the
/// part a value assertion has to hold down.
class SliderTest {

    private final List<Double> asked = new ArrayList<>();

    private Slider slider(double min, double max, double value, double step) {
        return new Slider(min, max, value, step, null, asked::add, false, Widgets.Attributes.NONE);
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built sliders are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Slider(0, 100, 40, 5, null, null, false,
                    new Widgets.Attributes("gain", Set.of("vertical"), "gain"));

            var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                    slider id="gain" class="vertical" min=0 max=100 value=40 step=5
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a slider is CSS-selectable by type, id and class")
        void cssSelectable() {
            var sheets = List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    slider   { gap: 1px }
                    #gain    { gap: 2px }
                    .vertical { gap: 3px }
                    """));
            var tree = new ElementTree(new Slider(0, 1, 0.5, 0, null, null, false,
                    new Widgets.Attributes("gain", Set.of("vertical"), "gain")));

            assertEquals(StyleLength.points(2),
                    ComputedStyle.of(new StyleResolver(sheets).resolve(tree.root()),
                            CssLength.Context.DEFAULT).gap());
        }

        @Test
        @DisplayName("slider is a control type and its four parts are not")
        void partsAreNotConstructible() {
            var registered = Controls.inflater().registered();
            assertTrue(Controls.controlTypes().contains("slider"));
            for (var part : List.of("slider-track", "slider-fill", "slider-rest", "slider-thumb")) {
                assertFalse(registered.contains(part), part + " is a part, not a widget (ADR-0065)");
                assertFalse(Controls.controlTypes().contains(part));
            }
        }

        /// A contradiction is refused; a half-typed number is not. `max <= min`
        /// has no reading at all, while a mistyped `min=` is a document being
        /// edited, which reload is deliberately forgiving about (ADR-0051).
        @Test
        @DisplayName("a range that is not a range is refused at construction")
        void badRangeRefused() {
            assertThrows(IllegalArgumentException.class, () -> slider(1, 1, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> slider(10, 0, 5, 0));
            assertThrows(IllegalArgumentException.class, () -> slider(0, 1, 0.5, -1));
        }

        @Test
        @DisplayName("a non-numeric attribute falls back rather than failing the window")
        void nonNumericFallsBack() {
            var slider = (Slider) Controls.inflater().inflateAll(KdlParser.parse("""
                    slider min="oops" max=10 value=3
                    """)).getFirst();

            assertEquals(0, slider.min());
            assertEquals(10, slider.max());
        }
    }

    @Nested
    @DisplayName("the value")
    class Value {

        @Test
        @DisplayName("the fraction is where the thumb goes, and it is the value mapped to 0..1")
        void fractionMapsTheRange() {
            assertEquals(0.0, slider(0, 100, 0, 0).fraction(), 1e-9);
            assertEquals(0.4, slider(0, 100, 40, 0).fraction(), 1e-9);
            assertEquals(1.0, slider(0, 100, 100, 0).fraction(), 1e-9);
            // A range that does not start at zero is the case an off-by-one gets
            // wrong: 5 of 1..9 is the middle, not 5/9.
            assertEquals(0.5, slider(1, 9, 5, 0).fraction(), 1e-9);
        }

        /// A model outside the range is an application bug, and a thumb rendered
        /// off the end of its track is a worse way to report it than one pinned
        /// at the end.
        @Test
        @DisplayName("a value outside the range is clamped rather than trusted")
        void resolvedIsClamped() {
            assertEquals(10, slider(0, 10, 999, 0).resolved(), 1e-9);
            assertEquals(0, slider(0, 10, -5, 0).resolved(), 1e-9);
            assertEquals(1.0, slider(0, 10, 999, 0).fraction(), 1e-9);
        }

        @Test
        @DisplayName("a bound slider shows the property, and takes any Number")
        void boundShowsTheProperty() {
            Property<Number> gain = Property.of(3);
            var slider = new Slider(0, 10, 0, 0, gain, asked::add, false, null);

            assertEquals(3, slider.resolved(), 1e-9, "an Integer model is at least as likely as a Double");
            gain.set(7.5);
            assertEquals(7.5, slider.resolved(), 1e-9);
        }

        @Test
        @DisplayName("a non-numeric or null property falls back to the widget's value")
        void nonNumberFallsBack() {
            assertEquals(4, new Slider(0, 10, 4, 0, Property.of("loud"), null, false, null)
                    .resolved(), 1e-9);
            assertEquals(4, new Slider(0, 10, 4, 0, Property.of(null), null, false, null)
                    .resolved(), 1e-9);
        }

        /// ADR-0063 in one assertion: dragging a bound slider whose handler does
        /// nothing moves neither the property nor the thumb.
        @Test
        @DisplayName("a handler that does nothing leaves the thumb where it was")
        void controlled() {
            var gain = Property.of(2.0);
            var slider = new Slider(0, 10, 0, 0, gain, value -> { }, false, null);

            slider.onKey(press(Key.RIGHT));

            assertEquals(2.0, gain.get(), 1e-9);
            assertEquals(2.0, slider.resolved(), 1e-9);
        }
    }

    @Nested
    @DisplayName("snapping, which the control does so the application does not")
    class Snapping {

        /// A model may hold a value off the grid — nothing snaps it on the way
        /// in, because that would be the control overruling the model. So an
        /// arrow offers the next value the user can **reach**: from 40 on a grid
        /// of 25 that is 50, not `40 + 25` rounded to 75.
        @Test
        @DisplayName("an arrow offers the next reachable value, not the current plus a step")
        void stepGoesToTheGrid() {
            slider(0, 100, 40, 25).onKey(press(Key.RIGHT));
            slider(0, 100, 40, 25).onKey(press(Key.LEFT));
            // Already on the grid: the two readings agree, which is every other
            // time.
            slider(0, 100, 50, 25).onKey(press(Key.RIGHT));

            assertEquals(List.of(50.0, 25.0, 75.0), asked);
        }

        /// A slider from 1 to 10 stepping by 2 offers 1, 3, 5, 7, 9 — the values
        /// reachable *from where the track starts*. Snapping from zero would
        /// offer 2, 4, 6, 8, 10 and make `min` unreachable, which is the more
        /// surprising of the two and hides at the end of the track.
        @Test
        @DisplayName("steps are counted from min, not from zero")
        void stepsCountFromMin() {
            slider(1, 10, 1, 2).onKey(press(Key.RIGHT));
            slider(1, 10, 1, 2).onKey(press(Key.HOME));

            assertEquals(List.of(3.0, 1.0), asked);
        }

        /// 0..10 stepping by 3 has a grid of 0, 3, 6, 9. `End` must still reach
        /// 10: `max` is a value the slider promises, and a user who presses End
        /// and lands on 9 has been told the end of the track is not the end.
        @Test
        @DisplayName("the ends are reachable even when the range is not a whole number of steps")
        void endsAreAlwaysReachable() {
            slider(0, 10, 9, 3).onKey(press(Key.END));
            slider(0, 10, 3, 3).onKey(press(Key.HOME));

            assertEquals(List.of(10.0, 0.0), asked);
        }

        @Test
        @DisplayName("step 0 is continuous and reports what it was given")
        void continuous() {
            slider(0, 1, 0.5, 0).onKey(press(Key.RIGHT));

            assertEquals(0.51, asked.getFirst(), 1e-9, "a hundredth of the range");
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("both arrow pairs step, because the axis is the stylesheet's")
        void bothPairsStep() {
            slider(0, 100, 50, 10).onKey(press(Key.RIGHT));
            slider(0, 100, 50, 10).onKey(press(Key.UP));
            slider(0, 100, 50, 10).onKey(press(Key.LEFT));
            slider(0, 100, 50, 10).onKey(press(Key.DOWN));

            assertEquals(List.of(60.0, 60.0, 40.0, 40.0), asked);
        }

        @Test
        @DisplayName("PageUp and PageDown take ten steps, and Home and End the ends")
        void largeSteps() {
            slider(0, 100, 50, 1).onKey(press(Key.PAGE_UP));
            slider(0, 100, 50, 1).onKey(press(Key.PAGE_DOWN));
            slider(0, 100, 50, 1).onKey(press(Key.HOME));
            slider(0, 100, 50, 1).onKey(press(Key.END));

            assertEquals(List.of(60.0, 40.0, 0.0, 100.0), asked);
        }

        /// The reason ADR-0073 put scope traversal *after* the focused chain, and
        /// the first control that actually relies on it: a slider inside a focus
        /// scope must keep its own arrows.
        @Test
        @DisplayName("an arrow is consumed even when the value did not move")
        void arrowsAreAlwaysConsumed() {
            var event = press(Key.RIGHT);
            slider(0, 10, 10, 1).onKey(event);

            assertTrue(event.isConsumed(),
                    "a slider at its maximum still owns Right; letting it through would move focus");
        }

        /// Deliberately different from every control before this: holding an
        /// arrow to run a value up is how a slider is used, while holding Space
        /// on a checkbox to flutter it is not.
        @Test
        @DisplayName("a repeat steps again, unlike every other control")
        void repeatsStep() {
            slider(0, 100, 50, 10).onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.RIGHT, Modifiers.NONE, true, null));

            assertEquals(List.of(60.0), asked);
        }

        @Test
        @DisplayName("a modified arrow is left alone")
        void modifiedArrowIgnored() {
            slider(0, 100, 50, 10).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.RIGHT,
                    new Modifiers(true, false, false, false), false, null));

            assertTrue(asked.isEmpty());
        }
    }

    @Nested
    @DisplayName("the drag (§3.1: 1:1, no animation)")
    class Drag {

        /// A press jumps the value to where it landed. The fraction comes from
        /// the router's local coordinates, which is the only way a widget can
        /// know how far along itself a pointer is (ADR-0079).
        @Test
        @DisplayName("a press jumps the value to where it landed")
        void pressJumps() {
            slider(0, 100, 0, 0).onPointer(at(PointerEvent.Kind.PRESSED, 60, 200));

            // A tolerance because the fraction comes off `float` coordinates:
            // the router reports pixels, and a pixel is a float everywhere else
            // in the toolkit.
            assertEquals(1, asked.size());
            assertEquals(30.0, asked.getFirst(), 1e-4);
        }

        @Test
        @DisplayName("a move while held follows the pointer exactly")
        void dragFollows() {
            var slider = slider(0, 100, 0, 0);
            slider.onPointer(at(PointerEvent.Kind.PRESSED, 20, 200));
            slider.onPointer(at(PointerEvent.Kind.MOVED, 150, 200));

            assertEquals(2, asked.size());
            assertEquals(10.0, asked.get(0), 1e-4);
            assertEquals(75.0, asked.get(1), 1e-4);
        }

        /// `dragX()` is NaN with no button held, which is the router reporting
        /// "no gesture" through the arithmetic rather than through a flag. A
        /// slider that acted on a bare hover would change its value as the
        /// pointer crossed it.
        @Test
        @DisplayName("a move with no button held does nothing")
        void hoverDoesNotDrag() {
            var event = new PointerEvent(PointerEvent.Kind.MOVED, 150, 10, null, 0, null);
            event.localTo(new PointerEvent.Local(150, 10, 200, 32));

            slider(0, 100, 0, 0).onPointer(event);

            assertTrue(asked.isEmpty(), "hovering a slider must not move it");
        }

        @Test
        @DisplayName("a vertical slider inverts, because a fader's minimum is at the bottom")
        void verticalInverts() {
            var slider = new Slider(0, 100, 0, 0, null, asked::add, false,
                    new Widgets.Attributes(null, Set.of("vertical"), null));
            var event = new PointerEvent(PointerEvent.Kind.PRESSED, 10, 30,
                    PointerEvent.Button.PRIMARY, 1, 10, 30, null);
            // A quarter of the way down a 200-tall fader is three quarters up it.
            event.localTo(new PointerEvent.Local(10, 50, 32, 200));

            slider.onPointer(event);

            assertEquals(1, asked.size());
            assertEquals(75.0, asked.getFirst(), 1e-4);
        }

        @Test
        @DisplayName("a disabled slider refuses the pointer and the keyboard")
        void disabledRefuses() {
            var slider = new Slider(0, 100, 50, 10, null, asked::add, true, null);

            slider.onPointer(at(PointerEvent.Kind.PRESSED, 60, 200));
            slider.onKey(press(Key.RIGHT));

            assertTrue(asked.isEmpty());
            assertFalse(slider.isFocusable(), "a disabled control leaves the Tab order");
        }

        /// A widget poked directly, with no layout behind it, gets
        /// `Local.UNKNOWN` — zero-sized, so `fractionX()` is 0 rather than a
        /// division by zero.
        @Test
        @DisplayName("a press with no layout behind it reads as the start of the track")
        void unlaidOutIsZero() {
            slider(0, 100, 50, 0).onPointer(new PointerEvent(PointerEvent.Kind.PRESSED, 5, 5,
                    PointerEvent.Button.PRIMARY, 1, 5, 5, null));

            assertEquals(List.of(0.0), asked);
        }

        private PointerEvent at(PointerEvent.Kind kind, float x, float width) {
            var event = new PointerEvent(kind, x, 16, PointerEvent.Button.PRIMARY, 1, 0, 16, null);
            event.localTo(new PointerEvent.Local(x, 16, width, 32));
            return event;
        }
    }

    @Nested
    @DisplayName("the parts")
    class Parts {

        @Test
        @DisplayName("the fill and the rest split the track in the value's proportion")
        void fillAndRestSplit() {
            var track = (SliderTrack) slider(0, 100, 25, 0).children().getFirst();
            var parts = track.children();

            assertEquals(0.25, ((SliderFill) parts.get(0)).fraction(), 1e-9);
            assertEquals(0.75, ((SliderRest) parts.get(2)).fraction(), 1e-9);
        }

        /// The thumb is placed by that ratio and by nothing else. This is what
        /// says the position never became a transform — which is the thing a
        /// stylesheet could not have expressed (ADR-0079).
        @Test
        @DisplayName("the thumb carries no transform of its own")
        void thumbHasNoTransform() {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            var tree = new ElementTree(slider(0, 100, 25, 0));
            var thumb = tree.root().children().getFirst().children().get(1);

            var style = ComputedStyle.of(resolver.resolve(thumb), CssLength.Context.DEFAULT);

            assertTrue(style.transform().isNone(),
                    "a slider's thumb is placed by layout; a transform could not express it");
        }

        @Test
        @DisplayName("§3's metrics: track 4, thumb 16, hit target 32")
        void metrics() {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            var tree = new ElementTree(slider(0, 100, 25, 0));

            var control = ComputedStyle.of(resolver.resolve(tree.root()), CssLength.Context.DEFAULT);
            var track = ComputedStyle.of(resolver.resolve(tree.root().children().getFirst()),
                    CssLength.Context.DEFAULT);
            var thumb = ComputedStyle.of(
                    resolver.resolve(tree.root().children().getFirst().children().get(1)),
                    CssLength.Context.DEFAULT);

            assertEquals(StyleLength.points(32), control.height(), "§1.3's hit target");
            assertEquals(StyleLength.points(4), track.height(), "§3's groove");
            assertEquals(StyleLength.points(16), thumb.width());
            assertEquals(StyleLength.points(16), thumb.height());
        }

        /// §3.1: "slider — drag: **1:1, no animation**". A thumb that eased
        /// toward the pointer would lag the finger, so `slider` is deliberately
        /// absent from the shared transition rule.
        @Test
        @DisplayName("the control declares no transition, so a drag cannot lag")
        void noTransitionOnTheControl() {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            var tree = new ElementTree(slider(0, 100, 25, 0));

            var style = ComputedStyle.of(resolver.resolve(tree.root()), CssLength.Context.DEFAULT);

            assertTrue(style.transitions().isEmpty(),
                    "a slider must not animate its own value");
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("a KDL change= receives the snapped value as a string")
        void kdlChangeReceivesAString() {
            var got = new ArrayList<String>();
            var actions = Actions.strict().bind("setGain", (String value) -> got.add(value));

            var slider = (Slider) Controls.inflater(actions, Icons.none(), Bindings.none())
                    .inflateAll(KdlParser.parse("""
                            slider min=0 max=100 value=50 step=10 change="setGain"
                            """)).getFirst();
            slider.onKey(press(Key.RIGHT));

            assertEquals(List.of("60.0"), got);
        }

        @Test
        @DisplayName("bind= resolves against the registry")
        void kdlBind() {
            var gain = Property.of(30.0);
            var bindings = Bindings.strict().bind("audio.gain", gain);

            var slider = (Slider) Controls.inflater(Actions.lenient(), Icons.none(), bindings)
                    .inflateAll(KdlParser.parse("""
                            slider min=0 max=100 bind="audio.gain"
                            """)).getFirst();

            assertEquals(30.0, slider.resolved(), 1e-9);
        }
    }

    private static KeyEvent press(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }
}
