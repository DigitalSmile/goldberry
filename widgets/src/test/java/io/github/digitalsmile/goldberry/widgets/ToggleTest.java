package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
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

/// The fifth control, and the first with a **gesture**
/// ([ADR-0075](../../../../../../../book/src/adr/0075-a-gestures-origin-is-the-routers.md)).
///
/// [ButtonTest] proves the shape a control has and [CheckboxTest] proves the
/// controlled-value loop. What is new here is the drag: a sequence of events
/// rather than one, decided by an offset the widget cannot have remembered.
class ToggleTest {

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built toggles are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Toggle("Frost", true, null, null, false,
                    new Widgets.Attributes("frost", Set.of("compact"), "frost"));

            var fromKdl = Controls.inflater().inflateAll(KdlParser.parse("""
                    toggle id="frost" class="compact" on=#true "Frost"
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a toggle is CSS-selectable by type, id and class")
        void cssSelectable() {
            var sheets = List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                    toggle    { gap: 1px }
                    #frost    { gap: 2px }
                    .compact  { gap: 3px }
                    """));
            var tree = new ElementTree(new Toggle("Frost", true, null, null, false,
                    new Widgets.Attributes("frost", Set.of("compact"), "frost")));

            var style = ComputedStyle.of(new StyleResolver(sheets).resolve(tree.root()),
                    CssLength.Context.DEFAULT);

            // The id wins on specificity, which is what says all three matched.
            assertEquals(StyleLength.points(2), style.gap());
        }

        /// The parts are the fifth and sixth, and neither is in the catalog —
        /// a `toggle-track` outside a `toggle` is a pill that means nothing
        /// (ADR-0065).
        @Test
        @DisplayName("toggle is a control type and its two parts are not")
        void partsAreNotConstructible() {
            assertTrue(Controls.controlTypes().contains("toggle"));
            assertFalse(Controls.controlTypes().contains("toggle-track"));
            assertFalse(Controls.controlTypes().contains("toggle-thumb"));
        }
    }

    @Nested
    @DisplayName("the drag")
    class Drag {

        /// §3's travel is 16, so the threshold is 8 — the point at which the
        /// thumb has passed the middle. Past it, the value is the *direction*
        /// and not the opposite of what is showing.
        @Test
        @DisplayName("dragging right asks for on, however far past the track it goes")
        void dragRightAsksForOn() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", false, asked::add);

            toggle.onPointer(release(30, 300));

            assertEquals(List.of(true), asked);
        }

        @Test
        @DisplayName("dragging left asks for off")
        void dragLeftAsksForOff() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", true, asked::add);

            toggle.onPointer(release(300, 30));

            assertEquals(List.of(false), asked);
        }

        /// The case a "toggle on release" implementation gets wrong. Dragging
        /// right on a switch already on asks for **on**, not for off: a drag is
        /// a request for a particular state, and the direction is the request.
        @Test
        @DisplayName("dragging towards the state it is already in asks for that state")
        void dragTowardsTheCurrentStateIsNotAFlip() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", true, asked::add);

            toggle.onPointer(release(30, 300));

            assertEquals(List.of(true), asked);
        }

        @Test
        @DisplayName("a movement under the threshold is a click, so the value flips")
        void shortMovementIsAClick() {
            var asked = new ArrayList<Boolean>();

            new Toggle("Frost", false, asked::add).onPointer(release(30, 37));
            new Toggle("Frost", true, asked::add).onPointer(release(30, 37));

            assertEquals(List.of(true, false), asked);
        }

        /// Exactly at the threshold is a drag, not a click. Stated because it is
        /// the boundary and the two branches disagree about it.
        @Test
        @DisplayName("exactly 8px is a drag")
        void theBoundaryIsADrag() {
            var asked = new ArrayList<Boolean>();

            new Toggle("Frost", true, asked::add).onPointer(release(30, 38));

            assertEquals(List.of(true), asked);
        }

        /// A `NaN` offset — no button held — reads as a click through the
        /// arithmetic rather than through a guard, which is why the router
        /// reports `NaN` and not zero.
        @Test
        @DisplayName("an event with no press origin reads as a click, not a drag")
        void noOriginIsAClick() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", false, asked::add);

            toggle.onPointer(new PointerEvent(PointerEvent.Kind.RELEASED, 30, 10,
                    PointerEvent.Button.PRIMARY, 1, null));

            assertEquals(List.of(true), asked);
        }

        /// It reads a release and **not** a click, which is the one place this
        /// control differs from every other. A switch has no cancel gesture:
        /// dragging off it and letting go is a drag, not a cancellation.
        @Test
        @DisplayName("a CLICKED event is ignored, so one gesture is not acted on twice")
        void clickIsIgnored() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", false, asked::add);

            toggle.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 34, 30,
                    PointerEvent.Button.PRIMARY, 1, 30, 30, null));

            assertTrue(asked.isEmpty(), "a toggle acts on the release; the click would be a second time");
        }

        @Test
        @DisplayName("a secondary-button release does nothing")
        void secondaryButtonDoesNothing() {
            var asked = new ArrayList<Boolean>();

            new Toggle("Frost", false, asked::add).onPointer(
                    new PointerEvent(PointerEvent.Kind.RELEASED, 34, 30,
                            PointerEvent.Button.SECONDARY, 1, 30, 30, null));

            assertTrue(asked.isEmpty());
        }

        private static PointerEvent release(float pressX, float releaseX) {
            return new PointerEvent(PointerEvent.Kind.RELEASED, releaseX, 30,
                    PointerEvent.Button.PRIMARY, 1, pressX, 30, null);
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("Space asks for the opposite of what is showing")
        void spaceFlips() {
            var asked = new ArrayList<Boolean>();

            new Toggle("Frost", false, asked::add).onKey(press(Key.SPACE));
            new Toggle("Frost", true, asked::add).onKey(press(Key.SPACE));

            assertEquals(List.of(true, false), asked);
        }

        /// Enter belongs to a dialog's default action (§2.3). A control that
        /// swallowed it would leave a form with no keyboard route to submit once
        /// focus was on one — the same line `checkbox` draws.
        @Test
        @DisplayName("Enter deliberately does nothing")
        void enterDoesNothing() {
            var asked = new ArrayList<Boolean>();

            new Toggle("Frost", false, asked::add).onKey(press(Key.ENTER));

            assertTrue(asked.isEmpty());
        }

        @Test
        @DisplayName("a repeat does nothing, so holding Space does not flutter")
        void repeatDoesNothing() {
            var asked = new ArrayList<Boolean>();
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, true, null);

            new Toggle("Frost", false, asked::add).onKey(event);

            assertTrue(asked.isEmpty());
        }

        private static KeyEvent press(Key key) {
            return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
        }
    }

    @Nested
    @DisplayName("the value (ADR-0063)")
    class Value {

        @Test
        @DisplayName("an unbound toggle shows what it was given")
        void unboundShowsItsOwn() {
            assertTrue(new Toggle("Frost", true).resolved());
            assertFalse(new Toggle("Frost", false).resolved());
        }

        @Test
        @DisplayName("a bound toggle shows the property")
        void boundShowsTheProperty() {
            var frost = Property.of(true);
            var toggle = new Toggle("Frost", frost, value -> { });

            assertTrue(toggle.resolved());
            frost.set(false);
            assertFalse(toggle.resolved());
        }

        /// A property that has not loaded, or holds something else entirely,
        /// reads as the widget's own value rather than as a guess — one case
        /// fewer than `checkbox`, because a switch has no mixed state.
        @Test
        @DisplayName("a non-boolean or null property falls back to the widget's value")
        void nonBooleanFallsBack() {
            assertTrue(new Toggle("Frost", true, Property.of("yes"), value -> { }, false, null).resolved());
            assertTrue(new Toggle("Frost", true, Property.of(null), value -> { }, false, null).resolved());
        }

        /// The whole of ADR-0063 in one assertion: a bound toggle whose handler
        /// does nothing moves neither the property nor the thumb.
        @Test
        @DisplayName("a handler that does nothing leaves the switch where it was")
        void controlled() {
            var frost = Property.of(false);
            var toggle = new Toggle("Frost", frost, value -> { });

            toggle.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));

            assertFalse(frost.get());
            assertFalse(toggle.resolved());
        }

        @Test
        @DisplayName("the widget is handed the read-only half, so it cannot write")
        void readOnly() {
            var frost = Property.of(false);

            assertSame(frost, new Toggle("Frost", frost, value -> { }).binding());
        }

        @Test
        @DisplayName("a disabled toggle refuses the pointer and the keyboard")
        void disabledRefusesEverything() {
            var asked = new ArrayList<Boolean>();
            var toggle = new Toggle("Frost", false, null, asked::add, true, null);

            toggle.onPointer(new PointerEvent(PointerEvent.Kind.RELEASED, 34, 30,
                    PointerEvent.Button.PRIMARY, 1, 30, 30, null));
            toggle.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));

            assertTrue(asked.isEmpty());
            assertFalse(toggle.isFocusable(), "a disabled control leaves the Tab order");
            assertTrue(toggle.isDisabled());
        }

        /// The markup half of the valued action: the value crosses as the string
        /// a document would have written, which is the rule ADR-0073 set for
        /// enums and which keeps one valued shape in the registry.
        @Test
        @DisplayName("a KDL change= receives \"true\" or \"false\"")
        void kdlChangeReceivesAString() {
            var got = new ArrayList<String>();
            var actions = Actions.strict().bind("setFrost", (String value) -> got.add(value));

            var toggle = (Toggle) Controls.inflater(actions, Icons.none(), Bindings.none())
                    .inflateAll(KdlParser.parse("""
                            toggle change="setFrost" "Frost"
                            """)).getFirst();
            toggle.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));

            assertEquals(List.of("true"), got);
        }

        @Test
        @DisplayName("an unwired toggle is inert rather than broken")
        void unwiredIsInert() {
            var toggle = new Toggle("Frost", false);

            toggle.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));

            assertNull(toggle.onChange());
        }
    }

    @Nested
    @DisplayName("the parts")
    class Parts {

        /// §3's four numbers are one arithmetic statement: 2 + 16 + 16 + 2 = 36
        /// across and 2 + 16 + 2 = 20 down. A track that did not add up would
        /// leave the thumb hanging over an edge at one end of its travel.
        @Test
        @DisplayName("the track and the thumb are §3's metrics, and they add up")
        void metricsAddUp() {
            // 1 is the toggle (the spacer is 0), then its track, then the thumb.
            var track = styleOf(1, 0);
            var thumb = styleOf(1, 0, 0);

            assertEquals(StyleLength.points(36), track.width());
            assertEquals(StyleLength.points(20), track.height());
            assertEquals(StyleLength.points(16), thumb.width());
            assertEquals(StyleLength.points(16), thumb.height());

            var padding = track.padding();
            assertEquals(StyleLength.points(2), padding.left());
            assertEquals(StyleLength.points(2), padding.right());
            assertEquals(StyleLength.points(2), padding.top());
            // §3's travel 16 is what is *left over* rather than a number chosen
            // separately: change the track width and the travel is wrong.
            assertEquals(16, 36 - points(padding.left()) - points(padding.right()) - 16, 1e-6,
                    "track 36 - padding 2+2 - thumb 16 must leave exactly §3's travel");
        }

        /// `:checked` is mirrored onto the **track**, which is what lets the
        /// stylesheet write `toggle-track:checked toggle-thumb` and move the
        /// thumb without Java deciding where it goes.
        @Test
        @DisplayName("the track carries :checked and the control does too")
        void checkedIsOnBoth() {
            assertTrue(new Toggle("Frost", true).isChecked());
            assertTrue(((ToggleTrack) new Toggle("Frost", true).children().getFirst()).isChecked());
            assertFalse(((ToggleTrack) new Toggle("Frost", false).children().getFirst()).isChecked());
        }

        @Test
        @DisplayName("disabled reaches both parts, so neither needs a descendant selector")
        void disabledReachesTheParts() {
            var track = (ToggleTrack) new Toggle("Frost", true, null, null, true, null)
                    .children().getFirst();

            assertTrue(track.isDisabled());
            assertTrue(((ToggleThumb) track.children().getFirst()).isDisabled());
        }

        @Test
        @DisplayName("a toggle with no label has a track and nothing else")
        void labelIsOptional() {
            assertEquals(1, new Toggle("", true).children().size());
            assertEquals(2, new Toggle("Frost", true).children().size());
        }

        private static float points(StyleLength length) {
            return ((StyleLength.Points) length).value();
        }

        /// Walks into the element tree by child index: 1 is the track's element
        /// under the toggle, and (1, 0) the thumb under that.
        private static ComputedStyle styleOf(int... path) {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            var tree = new ElementTree(new Widgets.Row(
                    List.of(new Widgets.Spacer(), new Toggle("Frost", true)),
                    Widgets.Attributes.NONE));
            var element = tree.root().children().get(path[0]);
            for (var i = 1; i < path.length; i++) {
                element = element.children().get(path[i]);
            }
            return ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
        }
    }

    @Nested
    @DisplayName("the stylesheet")
    class Styling {

        @Test
        @DisplayName("the control is the density's height, like every other control")
        void heightFollowsTheDensity() {
            assertEquals(StyleLength.points(32), toggleStyle(Density.REGULAR).height());
            assertEquals(StyleLength.points(28), toggleStyle(Density.COMPACT).height());
        }

        /// The track is §3's 36×20 at either density: §3's toggle row gives no
        /// compact value, unlike the rows that carry one in parentheses, so the
        /// pill does not shrink and only the row around it does.
        @Test
        @DisplayName("the track does not shrink with the density")
        void trackIgnoresTheDensity() {
            for (var density : Density.values()) {
                var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK, density));
                var tree = new ElementTree(new Toggle("Frost", true));
                var track = ComputedStyle.of(resolver.resolve(tree.root().children().getFirst()),
                        CssLength.Context.DEFAULT);

                assertEquals(StyleLength.points(36), track.width(), "track width at " + density);
                assertEquals(StyleLength.points(20), track.height(), "track height at " + density);
            }
        }

        /// §2.2's ring, once, for every control — asserted here because a new
        /// control joining the shared rule is a thing that gets forgotten, and a
        /// missing ring is invisible until someone tries the keyboard.
        @Test
        @DisplayName("a keyboard-focused toggle gets the shared focus ring")
        void focusRing() {
            var tree = new ElementTree(new Toggle("Frost", true));
            tree.root().setPseudoClass(Selector.PseudoClass.FOCUS_VISIBLE, true);
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));

            var style = ComputedStyle.of(resolver.resolve(tree.root()), CssLength.Context.DEFAULT);

            assertEquals(2, style.decoration().outlineWidth(), 1e-6);
            assertEquals(2, style.decoration().outlineOffset(), 1e-6);
        }

        @Test
        @DisplayName("a disabled toggle is 45% and never a colour remap")
        void disabledIsOpacity() {
            var tree = new ElementTree(new Toggle("Frost", true, null, null, true, null));
            tree.root().setPseudoClass(Selector.PseudoClass.DISABLED, true);
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));

            assertEquals(0.45,
                    ComputedStyle.of(resolver.resolve(tree.root()), CssLength.Context.DEFAULT).opacity(),
                    1e-6);
        }

        private static ComputedStyle toggleStyle(Density density) {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK, density));
            return ComputedStyle.of(resolver.resolve(new ElementTree(new Toggle("Frost", true)).root()),
                    CssLength.Context.DEFAULT);
        }
    }
}
