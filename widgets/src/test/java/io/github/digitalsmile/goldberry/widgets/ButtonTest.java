package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The first control, checked the three ways §11 says a widget has to exist.
class ButtonTest {

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built buttons are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Button("Save", null,
                    new Widgets.Attributes("save", Set.of("primary"), "save"));

            var nodes = KdlParser.parse("""
                    button id="save" class="primary" "Save"
                    """);
            var fromKdl = Controls.inflater().inflateAll(nodes).getFirst();

            // The invariant, and the reason it is a test rather than a promise:
            // two constructors for one widget drift the first time either grows
            // a field.
            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a button is CSS-selectable by type, id and class")
        void selectable() {
            var button = new Button("Save", null,
                    new Widgets.Attributes("save", Set.of("primary"), null));

            assertEquals("button", button.cssType());
            assertEquals("save", button.id());
            assertEquals(Set.of("primary"), button.classes());
        }

        @Test
        @DisplayName("the registry lists it, and refuses what it does not know")
        void registered() {
            assertTrue(Controls.inflater().registered().contains("button"));
            assertTrue(Controls.inflater().registered().contains("column"),
                    "the primitives come along, so markup can mix the two");
            assertEquals(List.of("button"), Controls.controlTypes());
        }

        @Test
        @DisplayName("`styled` produces the same classes markup would")
        void styledMatchesMarkup() {
            var fromJava = new Button("Delete", null).styled("danger");
            var fromKdl = Controls.inflater()
                    .inflateAll(KdlParser.parse("button class=\"danger\" \"Delete\"")).getFirst();

            assertEquals(((Button) fromKdl).classes(), fromJava.classes());
        }
    }

    @Nested
    @DisplayName("activation")
    class Activation {

        private final List<String> fired = new ArrayList<>();

        private Button button() {
            return new Button("Save", () -> fired.add("pressed"));
        }

        private Element mount(Button button) {
            return new ElementTree(button).root();
        }

        @Test
        @DisplayName("a click activates it")
        void click() {
            var button = button();
            var element = mount(button);

            button.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 10, 10, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(List.of("pressed"), fired);
        }

        @Test
        @DisplayName("a release is not an activation")
        void releaseDoesNothing() {
            // The router only synthesizes CLICKED when the press and the release
            // landed on the same node, so a button that acted on RELEASED would
            // fire for a drag the user cancelled.
            var button = button();
            var element = mount(button);

            button.onPointer(new PointerEvent(
                    PointerEvent.Kind.RELEASED, 10, 10, PointerEvent.Button.PRIMARY, 1, element));

            assertTrue(fired.isEmpty(), () -> "fired was " + fired);
        }

        @Test
        @DisplayName("Space and Enter activate it")
        void keyboard() {
            var button = button();
            var element = mount(button);

            button.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            button.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, element));

            assertEquals(List.of("pressed", "pressed"), fired);
        }

        @Test
        @DisplayName("a held key does not repeat the activation")
        void repeatIgnored() {
            // Holding Space is one activation. A control that wants the opposite
            // -- a spinner's arrows -- will say so.
            var button = button();
            var element = mount(button);

            button.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, true, element));

            assertTrue(fired.isEmpty(), () -> "fired was " + fired);
        }

        @Test
        @DisplayName("a modified key is somebody else's shortcut")
        void modifiedKeyIgnored() {
            var button = button();
            var element = mount(button);

            button.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER,
                    new Modifiers(false, true, false, false), false, element));

            assertTrue(fired.isEmpty(), () -> "Ctrl+Enter is an accelerator, not this button");
        }

        @Test
        @DisplayName("activating consumes the event, so an ancestor does not act too")
        void consumes() {
            var button = button();
            var element = mount(button);
            var event = new PointerEvent(
                    PointerEvent.Kind.CLICKED, 10, 10, PointerEvent.Button.PRIMARY, 1, element);

            button.onPointer(event);

            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("a button with no action is harmless")
        void noAction() {
            var button = new Button("Cancel");
            var element = mount(button);

            button.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 10, 10, PointerEvent.Button.PRIMARY, 1, element));
        }

        @Test
        @DisplayName("it is focusable, which is what puts it in the Tab order")
        void focusable() {
            assertTrue(((Handles) new Button("Save")).isFocusable());
        }
    }

    @Nested
    @DisplayName("appearance")
    class Appearance {

        /// The whole cascade a real window has: the controls' base layer, a
        /// theme, and whatever the application adds.
        private ComputedStyle style(String appCss, String... classes) {
            var sheets = new ArrayList<Stylesheet>();
            sheets.add(Controls.baseStylesheet());
            sheets.add(Theme.NORD_DARK.load());
            if (appCss != null) {
                sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, appCss));
            }
            var element = new ElementTree(new Button("Save", null,
                    new Widgets.Attributes(null, Set.of(classes), null))).root();
            return ComputedStyle.of(new StyleResolver(sheets).resolve(element),
                    CssLength.Context.DEFAULT);
        }

        @Test
        @DisplayName("the base layer gives the design system's metrics")
        void metrics() {
            var style = style(null);

            // docs/design-system.md §3: height 32, padding-x 12, gap 6.
            assertEquals(StyleLength.points(32), style.height());
            assertEquals(new Insets(StyleLength.points(0), StyleLength.points(12),
                    StyleLength.points(0), StyleLength.points(12)), style.padding());
            assertEquals(StyleLength.points(6), style.gap());
        }

        @Test
        @DisplayName("the colours come from the theme, and the base rule names none")
        void themed() {
            var dark = style(null);

            // --gb-button-bg is nord2 in the dark theme. The base rule says
            // `var(--gb-button-bg)` and never learns which theme answered (§10).
            assertEquals(0xFF434C5E, dark.background());
            assertTrue(!Controls.baseSource().contains("#"),
                    "the base stylesheet must name no colour of its own");
        }

        @Test
        @DisplayName("a variant is a class, so markup and Java pick it the same way")
        void variant() {
            assertNotEquals(style(null).background(), style(null, "primary").background());
            assertEquals(0xFF88C0D0, style(null, "primary").background(),
                    "primary is the accent in the dark theme");
        }

        @Test
        @DisplayName("an application overrides a component token without touching a rule")
        void tokenOverride() {
            // What design-system.md §3 means by "app stylesheets may override
            // component tokens, never structure".
            var style = style(":root { --gb-button-bg: #ff0000 }");

            assertEquals(0xFFFF0000, style.background());
            assertEquals(StyleLength.points(32), style.height(), "and the metrics are untouched");
        }

        @Test
        @DisplayName("the pointer becomes a hand over it")
        void cursor() {
            assertEquals(io.github.digitalsmile.goldberry.backend.Cursor.POINTER, style(null).cursor());
        }

        @Test
        @DisplayName(":hover and :active change the background")
        void states() {
            var sheets = List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
            var element = new ElementTree(new Button("Save")).root();
            var resolver = new StyleResolver(sheets);

            var resting = ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
            element.setPseudoClass(Selector.PseudoClass.HOVER, true);
            var hovered = ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
            element.setPseudoClass(Selector.PseudoClass.ACTIVE, true);
            var pressed = ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);

            assertNotEquals(resting.background(), hovered.background());
            assertNotEquals(hovered.background(), pressed.background());
        }
    }

    @Nested
    @DisplayName("the box it renders")
    class Rendered {

        @Test
        @DisplayName("the label is a child box, not text on the button itself")
        void labelIsAChild() {
            // A box with text is a measured leaf and Yoga never lays a measured
            // node's children out, so a button holding its own text could never
            // also hold an icon.
            var style = ComputedStyle.INITIAL;
            var box = new Button("Save").render(style, List.of(), () -> TestFont.get());

            assertEquals(1, box.children().size());
            assertEquals(null, box.text());
            assertEquals("Save", box.children().getFirst().text().paragraph().text());
        }
    }

    @Nested
    @DisplayName("actions (§9)")
    class ActionBinding {

        @Test
        @DisplayName("markup names an action and the registry resolves it")
        void resolves() {
            var fired = new ArrayList<String>();
            var actions = Actions.strict().bind("save", () -> fired.add("saved"));

            var button = (Button) Controls.inflater(actions)
                    .inflateAll(KdlParser.parse("button press=\"save\" \"Save\"")).getFirst();
            button.onPress().run();

            assertEquals(List.of("saved"), fired);
        }

        @Test
        @DisplayName("a strict registry refuses a name nobody bound")
        void strictRefuses() {
            // `press="svae"` is a typo, and a button that silently does nothing
            // produces a bug report with no error anywhere in it.
            var actions = Actions.strict().bind("save", () -> { });

            assertThrows(IllegalArgumentException.class, () -> Controls.inflater(actions)
                    .inflateAll(KdlParser.parse("button press=\"svae\" \"Save\"")));
        }

        @Test
        @DisplayName("a lenient registry inflates anyway, for a document being edited")
        void lenientAllows() {
            var button = (Button) Controls.inflater(Actions.lenient())
                    .inflateAll(KdlParser.parse("button press=\"nothing-yet\" \"Save\"")).getFirst();

            assertEquals(null, button.onPress());
        }

        @Test
        @DisplayName("binding the same name twice is refused, rebinding is not")
        void doubleBind() {
            var actions = Actions.strict().bind("save", () -> { });

            assertThrows(IllegalStateException.class, () -> actions.bind("save", () -> { }));
            actions.rebind("save", () -> { });
        }
    }
}
