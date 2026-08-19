package io.github.digitalsmile.goldberry.widgets.controls.checkbox;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.BindingRegistry;
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
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.CheckIndicator;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.CheckMark;
import io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// The second control, and the first whose *value* comes from outside it.
///
/// [ButtonTest] proves the shape a control has; this proves the half ADR-0063
/// describes and nothing had yet exercised — data down through `bind`, events up
/// through `change`, and a control that cannot write to its own model.
class CheckboxTest {

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built checkboxes are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Checkbox("Frost", Checkbox.Value.CHECKED, null, null, false,
                    new Attributes("frost", Set.of("compact"), "frost"));

            var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                    checkbox id="frost" class="compact" checked=#true "Frost"
                    """)).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a checkbox is CSS-selectable by type, id and class")
        void selectable() {
            var checkbox = new Checkbox("Frost", Checkbox.Value.UNCHECKED)
                    .styled("compact");

            assertEquals("checkbox", checkbox.cssType());
            assertEquals(Set.of("compact"), checkbox.classes());
        }

        @Test
        @DisplayName("the registry lists it beside button")
        void registered() {
            assertTrue(Widgets.inflater().registered().contains("checkbox"));
            assertTrue(Controls.controlTypes().contains("checkbox"));
        }

        @Test
        @DisplayName("`indeterminate=#true` wins over `checked=#true`")
        void indeterminateWins() {
            // A document that says both has said something contradictory, and
            // mixed is the state that cannot be reached any other way -- so
            // resolving it to "checked" would discard the more specific claim.
            var mixed = (Checkbox) Widgets.inflater().inflateAll(KdlParser.parse("""
                    checkbox checked=#true indeterminate=#true "Some"
                    """)).getFirst();

            assertEquals(Checkbox.Value.MIXED, mixed.resolved());
        }
    }

    @Nested
    @DisplayName("three states")
    class States {

        @Test
        @DisplayName("mixed is neither checked nor unchecked, and matches its own pseudo-class")
        void mixedIsItsOwnState() {
            var mixed = new Checkbox("Some", Checkbox.Value.MIXED);

            assertFalse(mixed.isChecked(), "`checkbox:checked` must mean the tick and nothing else");
            assertTrue(mixed.isIndeterminate());
        }

        @Test
        @DisplayName("toggling never produces mixed")
        void toggleSkipsMixed() {
            // Mixed is a state the application can describe and the user cannot
            // reach: clicking a partial selection asks for "all of them".
            assertEquals(Checkbox.Value.CHECKED, Checkbox.Value.MIXED.toggled());
            assertEquals(Checkbox.Value.CHECKED, Checkbox.Value.UNCHECKED.toggled());
            assertEquals(Checkbox.Value.UNCHECKED, Checkbox.Value.CHECKED.toggled());
        }

        @Test
        @DisplayName("the renderer mirrors all three onto the element")
        void mirroredToTheElement() {
            for (var value : Checkbox.Value.values()) {
                var tree = new ElementTree(new Checkbox("Frost", value));
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                        TestFont.get()).render(tree);

                assertEquals(value == Checkbox.Value.CHECKED,
                        tree.root().hasState(Selector.PseudoClass.CHECKED), value.toString());
                assertEquals(value == Checkbox.Value.MIXED,
                        tree.root().hasState(Selector.PseudoClass.INDETERMINATE), value.toString());
            }
        }
    }

    @Nested
    @DisplayName("data down, events up (ADR-0063)")
    class Binding {

        @Test
        @DisplayName("the value comes from the bound property, not from the click")
        void controlled() {
            var frost = Property.of(false);
            var checkbox = Checkbox.of("Frost", frost, () -> { });

            assertEquals(Checkbox.Value.UNCHECKED, checkbox.resolved());
            frost.set(true);
            assertEquals(Checkbox.Value.CHECKED, checkbox.resolved(),
                    "the application moved it, so it moved");
        }

        @Test
        @DisplayName("a click does not move a control whose handler does nothing")
        void controlledMeansControlled() {
            // The whole of ADR-0063 in one assertion. A checkbox is controlled in
            // the React sense: the tick moves when the application sets the
            // property, not when the pointer lands. A control that will not move
            // means the state did not change -- which is a bug in the
            // application, exactly where it should be.
            var frost = Property.of(false);
            var checkbox = Checkbox.of("Frost", frost, () -> { });
            var element = new ElementTree(checkbox).root();

            checkbox.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(Checkbox.Value.UNCHECKED, checkbox.resolved());
            assertFalse(frost.get());
        }

        @Test
        @DisplayName("what the user did travels up as an action")
        void changeFires() {
            var frost = Property.of(false);
            var checkbox = Checkbox.of("Frost", frost, () -> frost.set(!frost.get()));
            var element = new ElementTree(checkbox).root();

            checkbox.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertTrue(frost.get());
            assertEquals(Checkbox.Value.CHECKED, checkbox.resolved(),
                    "and the new value arrives back down through the binding");
        }

        @Test
        @DisplayName("a bound property may hold a Boolean or a Value")
        void bothValueTypes() {
            // An application modelling a binary preference should not have to
            // import a tri-state enum to bind one.
            assertEquals(Checkbox.Value.CHECKED,
                    Checkbox.of("a", Property.of(true), null).resolved());
            assertEquals(Checkbox.Value.MIXED,
                    Checkbox.of("a", Property.of(Checkbox.Value.MIXED), null).resolved());
        }

        @Test
        @DisplayName("a property holding nothing yet falls back to the markup's value")
        void nullFallsBack() {
            var loading = Property.of(null);
            var checkbox = new Checkbox("Frost", Checkbox.Value.MIXED, loading, null, false, null);

            // Guessing that null means "off" would show a definite answer for a
            // value that has not loaded.
            assertEquals(Checkbox.Value.MIXED, checkbox.resolved());
        }

        @Test
        @DisplayName("markup names a path and an action, and the registries resolve them")
        void fromMarkup() {
            var fired = new ArrayList<String>();
            var frost = Property.of(true);
            var bindings = BindingRegistry.strict().bind("prefs.frost", frost);
            var actions = ActionRegistry.strict().bind("toggleFrost", () -> fired.add("toggled"));

            var checkbox = (Checkbox) Widgets.inflater(actions, Icons.none(), bindings)
                    .inflateAll(KdlParser.parse("""
                            checkbox bind="prefs.frost" change="toggleFrost" "Frosted sidebar"
                            """)).getFirst();

            assertEquals(Checkbox.Value.CHECKED, checkbox.resolved());
            checkbox.onChange().run();
            assertEquals(List.of("toggled"), fired);
        }

        @Test
        @DisplayName("`binding()` is what the element subscribes to, so a change rebuilds")
        void subscribes() {
            var frost = Property.of(false);
            var checkbox = Checkbox.of("Frost", frost, null);
            new ElementTree(checkbox);

            assertEquals(1, frost.listenerCount(),
                    "the element followed the binding, exactly as `text bind=` does");
        }

        @Test
        @DisplayName("a strict registry refuses a path nobody bound")
        void strictRefuses() {
            assertThrows(IllegalArgumentException.class,
                    () -> Widgets.inflater(ActionRegistry.none(), Icons.none(), BindingRegistry.strict())
                            .inflateAll(KdlParser.parse("checkbox bind=\"prefs.frost\" \"Frost\"")));
        }
    }

    @Nested
    @DisplayName("activation")
    class Activation {

        private final List<String> fired = new ArrayList<>();

        private Checkbox checkbox() {
            return new Checkbox("Frost", Checkbox.Value.UNCHECKED, () -> fired.add("changed"));
        }

        @Test
        @DisplayName("a click anywhere in the control toggles, label included")
        void clickToggles() {
            var checkbox = checkbox();
            var element = new ElementTree(checkbox).root();

            checkbox.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 80, 16, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(List.of("changed"), fired);
        }

        @Test
        @DisplayName("Space toggles and Enter does not")
        void spaceNotEnter() {
            var checkbox = checkbox();
            var element = new ElementTree(checkbox).root();

            checkbox.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, element));
            assertTrue(fired.isEmpty(),
                    "Enter belongs to a dialog's default action (§2.3); a checkbox that"
                            + " swallowed it would leave a form unsubmittable from the keyboard");

            checkbox.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            assertEquals(List.of("changed"), fired);
        }

        @Test
        @DisplayName("a held Space is one toggle, not many")
        void noRepeat() {
            var checkbox = checkbox();
            var element = new ElementTree(checkbox).root();

            checkbox.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, true, element));

            assertTrue(fired.isEmpty());
        }

        @Test
        @DisplayName("a disabled checkbox refuses every route")
        void disabledRefuses() {
            var checkbox = checkbox().disabled(true);
            var element = new ElementTree(checkbox).root();

            checkbox.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));
            checkbox.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));

            assertTrue(fired.isEmpty());
            assertFalse(checkbox.isFocusable(), "and Tab skips it");
        }
    }

    @Nested
    @DisplayName("appearance")
    class Appearance {

        private ComputedStyle style(String type, Selector.PseudoClass... states) {
            var sheets = List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
            var tree = new ElementTree(new Checkbox("Frost", Checkbox.Value.UNCHECKED));
            var element = type.equals("checkbox") ? tree.root() : tree.root().children().getFirst();
            for (var state : states) {
                element.setPseudoClass(state, true);
            }
            return ComputedStyle.of(new StyleResolver(sheets).resolve(element),
                    CssLength.Context.DEFAULT);
        }

        @Test
        @DisplayName("the control is the hit target and the glyph is 16")
        void metrics() {
            // docs/design-system.md §3: glyph 16, hit >= 32, label gap 8. §1.3:
            // "hit targets >= 32x32 even when the visual is smaller (checkbox
            // glyph 16px, hit area 32)" -- which is exactly this pair of numbers.
            var control = style("checkbox");
            assertEquals(StyleLength.points(32), control.height());
            assertEquals(StyleLength.points(8), control.gap());

            var glyph = style("check-indicator");
            assertEquals(StyleLength.points(16), glyph.width());
            assertEquals(StyleLength.points(16), glyph.height());
            assertEquals(4, glyph.decoration().radius(), 1e-9, "§1.5's small-control corner");
            assertTrue(glyph.decoration().hasBorder(),
                    "an unchecked box has to be visible on a surface it would otherwise match");
        }

        @Test
        @DisplayName("checked fills with the accent and the mark is drawn in `color`")
        void checkedFill() {
            var resting = style("check-indicator");
            var checked = style("check-indicator", Selector.PseudoClass.CHECKED);

            assertEquals(0xFF88C0D0, checked.background(), "nord8, --gb-accent on the dark theme");
            assertEquals(0xFF2E3440, checked.color(), "nord0: a light fill needs a dark tick (§1.2)");
            assertEquals(0x00000000, resting.color(), "and an unchecked box draws no mark");
        }

        @Test
        @DisplayName("the label inherits the control's colour, on both themes")
        void labelInheritsColour() {
            // The bug: the label is a `text` child element that no rule names, so
            // before `color` inherited it resolved to ComputedStyle.INITIAL's
            // black -- invisible on the dark theme. `button` never showed it,
            // because it copies `style.color()` onto its child boxes by hand and
            // bypasses the cascade entirely.
            for (var entry : List.of(
                    java.util.Map.entry(Theme.NORD_DARK, 0xFFECEFF4),
                    java.util.Map.entry(Theme.NORD_LIGHT, 0xFF2E3440))) {

                var sheets = List.of(Controls.baseStylesheet(), entry.getKey().load());
                var tree = new ElementTree(new Checkbox("Frost", Checkbox.Value.UNCHECKED));
                var label = tree.root().children().get(1);

                var control = ComputedStyle.of(
                        new StyleResolver(sheets).resolve(tree.root()),
                        CssLength.Context.DEFAULT);
                var style = ComputedStyle.of(
                        new StyleResolver(sheets).resolve(label),
                        CssLength.Context.DEFAULT, control);

                assertEquals(entry.getValue(), style.color(), entry.getKey() + " label");
            }
        }

        @Test
        @DisplayName("the glyph is a part: CSS-selectable, not KDL-constructible")
        void partIsNotAWidget() {
            // A `check-indicator` outside a `checkbox` is a square that means
            // nothing, so registering the node would let a document create
            // exactly that. Restyling it is what an author wants, and a type
            // selector is the whole of that.
            assertFalse(Widgets.inflater().registered().contains("check-indicator"));
            assertTrue(Controls.baseSource().contains("check-indicator"));
        }
    }

    @Nested
    @DisplayName("the boxes it renders")
    class Rendered {

        @Test
        @DisplayName("the glyph and the label are child elements, so both are styleable")
        void twoChildren() {
            var checkbox = new Checkbox("Frost", Checkbox.Value.CHECKED);

            assertEquals(2, checkbox.children().size());
            assertEquals("check-indicator",
                    ((CheckIndicator) checkbox.children().getFirst()).cssType());
            assertEquals("Frost", ((Text) checkbox.children().get(1)).content());
        }

        @Test
        @DisplayName("a checkbox with no label is one glyph")
        void labelless() {
            // The table-cell case: a checkbox with nothing to say.
            assertEquals(1, new Checkbox("", Checkbox.Value.UNCHECKED).children().size());
        }

        @Test
        @DisplayName("the mark is the state's, and it is drawn in every state")
        void marks() {
            // Unchecked draws a tick too, at zero opacity. A node that came into
            // existence checked would have no previous style to transition from
            // and would snap, so §3.1's "scale 0.6->1 + opacity" needs the mark
            // present throughout and hidden by the stylesheet (ADR-0073).
            assertEquals(Box.Mark.Kind.CHECK, mark(Checkbox.Value.UNCHECKED).kind());
            assertEquals(Box.Mark.Kind.CHECK, mark(Checkbox.Value.CHECKED).kind());
            assertEquals(Box.Mark.Kind.DASH, mark(Checkbox.Value.MIXED).kind());
        }

        @Test
        @DisplayName("the mark is a node of its own, so it can scale without the glyph")
        void markIsANode() {
            var indicator = new CheckIndicator(Checkbox.Value.CHECKED, false, 2);

            assertEquals(1, indicator.children().size());
            assertEquals("check-mark",
                    ((CheckMark) indicator.children().getFirst()).cssType());
            assertNull(indicator.render(ComputedStyle.INITIAL, List.of(), TestFont.context()).mark(),
                    "the glyph carries no mark itself: scaling this box would scale the"
                            + " 16px square with it, which is not the animation §3.1 asks for");
        }

        private Box.Mark mark(Checkbox.Value value) {
            return ((CheckMark) new CheckIndicator(value, false, 2).children().getFirst())
                    .render(ComputedStyle.INITIAL, List.of(), TestFont.context())
                    .mark();
        }

        @Test
        @DisplayName("a box may not carry a mark and content at once")
        void markIsExclusive() {
            // The mark fills the box, so anything else on it would be drawn
            // underneath -- refused rather than rendered wrong.
            var mark = new Box.Mark(Box.Mark.Kind.CHECK, 0xFFFFFFFF, 2);
            assertThrows(IllegalArgumentException.class,
                    () -> Box.icon(io.github.digitalsmile.goldberry.icon.Icon.bundled("plus", 16),
                            0xFFFFFFFF).mark(mark));
        }
    }

    @Nested
    @DisplayName("theming")
    class Theming {

        @Test
        @DisplayName("both themes answer, and the base rule names no colour")
        void bothThemes() {
            for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
                var sheets = List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, ""));
                var tree = new ElementTree(new Checkbox("Frost", Checkbox.Value.CHECKED));
                var glyph = tree.root().children().getFirst();
                glyph.setPseudoClass(Selector.PseudoClass.CHECKED, true);

                var style = ComputedStyle.of(new StyleResolver(sheets).resolve(glyph),
                        CssLength.Context.DEFAULT);

                assertTrue((style.background() >>> 24) != 0,
                        theme + " left the checked glyph transparent");
                assertTrue((style.color() >>> 24) != 0, theme + " left the tick invisible");
            }
        }
    }
}
