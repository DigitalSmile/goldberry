package io.github.digitalsmile.goldberry.widgets.controls.button;

import io.github.digitalsmile.goldberry.widget.Attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// The first control, checked the three ways §11 says a widget has to exist.
class ButtonTest {

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built buttons are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Button("Save", null, null, false,
                    new Attributes("save", Set.of("primary"), "save"));

            var nodes = KdlParser.parse("""
                    button id="save" class="primary" "Save"
                    """);
            var fromKdl = Widgets.inflater().inflateAll(nodes).getFirst();

            // The invariant, and the reason it is a test rather than a promise:
            // two constructors for one widget drift the first time either grows
            // a field.
            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("a button is CSS-selectable by type, id and class")
        void selectable() {
            var button = new Button("Save", null, null, false,
                    new Attributes("save", Set.of("primary"), null));

            assertEquals("button", button.cssType());
            assertEquals("save", button.id());
            assertEquals(Set.of("primary"), button.classes());
        }

        @Test
        @DisplayName("the registry lists it, and refuses what it does not know")
        void registered() {
            assertTrue(Widgets.inflater().registered().contains("button"));
            assertTrue(Widgets.inflater().registered().contains("column"),
                    "the primitives come along, so markup can mix the two");
            assertTrue(Controls.controlTypes().contains("button"));

            // A part is not a control: `check-indicator` is CSS-selectable and
            // deliberately not KDL-constructible, so it is in neither list.
            assertTrue(!Controls.controlTypes().contains("check-indicator"));
            assertTrue(!Widgets.inflater().registered().contains("check-indicator"));
        }

        @Test
        @DisplayName("`styled` produces the same classes markup would")
        void styledMatchesMarkup() {
            var fromJava = new Button("Delete", null).styled("danger");
            var fromKdl = Widgets.inflater()
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
            var element = new ElementTree(new Button("Save", null, null, false,
                    new Attributes(null, Set.of(classes), null))).root();
            return ComputedStyle.of(new StyleResolver(sheets).resolve(element),
                    CssLength.Context.DEFAULT);
        }

        @Test
        @DisplayName("the base layer gives the design system's metrics")
        void metrics() {
            var style = style(null);

            // docs/design-system.md §3: height 32, padding-x 12, gap 6, radius 8.
            assertEquals(StyleLength.points(32), style.height());
            assertEquals(new Insets(StyleLength.points(0), StyleLength.points(12),
                    StyleLength.points(0), StyleLength.points(12)), style.padding());
            assertEquals(StyleLength.points(6), style.gap());
            assertEquals(8, style.decoration().radius(), 1e-9);

            // And no border, because §3's button row does not have one. The
            // machinery exists -- the checkbox's glyph uses it -- and using it
            // here anyway would be improvising a metric the system has not
            // agreed to (Principle 3).
            assertTrue(!style.decoration().hasBorder());
        }

        @Test
        @DisplayName("the label is `body-strong`, which is §3's typography column")
        void bodyStrong() {
            var typography = style(null).typography();

            // §1.4: body-strong is Inter 600 at 13/18. The last of the four
            // things `controls.css` used to say it could not express.
            assertEquals("Inter", typography.family());
            assertEquals(13, typography.size(), 1e-9);
            assertEquals(18, typography.resolvedLineHeight(), 1e-9);
            assertEquals(io.github.digitalsmile.goldberry.assets.BundledFont.Weight.SEMI_BOLD,
                    typography.weight());

            // And it is a real second face rather than a synthetic smear: the
            // weight picks a different file, which is the whole of ADR-0066.
            assertEquals(io.github.digitalsmile.goldberry.assets.BundledFont.UI_STRONG,
                    typography.face());
        }

        @Test
        @DisplayName("the focus ring is the design system's, and only for the keyboard")
        void focusRing() {
            var sheets = List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
            var element = new ElementTree(new Button("Save")).root();
            var resolver = new StyleResolver(sheets);

            // §2.2: ":focus is not :focus-visible" -- a button clicked with a
            // mouse is focused and gets no ring. The router keeps the two apart
            // (ADR-0054) exactly so this rule can.
            element.setPseudoClass(Selector.PseudoClass.FOCUS, true);
            var clicked = ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);
            assertTrue(!clicked.decoration().hasOutline(), "a mouse focus draws no ring");

            element.setPseudoClass(Selector.PseudoClass.FOCUS_VISIBLE, true);
            var tabbed = ComputedStyle.of(resolver.resolve(element), CssLength.Context.DEFAULT);

            // 2px --gb-focus at a 2px offset, following the control's radius.
            assertEquals(2, tabbed.decoration().outlineWidth(), 1e-9);
            assertEquals(2, tabbed.decoration().outlineOffset(), 1e-9);
            assertEquals(0xFF88C0D0, tabbed.decoration().outlineColor(), "nord8, --gb-focus");
            assertEquals(8, tabbed.decoration().radius(), 1e-9, "the ring follows the radius");
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
            var box = new Button("Save").render(style, List.of(), TestFont.context());

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
            var actions = ActionRegistry.strict().bind("save", () -> fired.add("saved"));

            var button = (Button) Widgets.inflater(actions)
                    .inflateAll(KdlParser.parse("button press=\"save\" \"Save\"")).getFirst();
            button.onPress().run();

            assertEquals(List.of("saved"), fired);
        }

        @Test
        @DisplayName("a strict registry refuses a name nobody bound")
        void strictRefuses() {
            // `press="svae"` is a typo, and a button that silently does nothing
            // produces a bug report with no error anywhere in it.
            var actions = ActionRegistry.strict().bind("save", () -> { });

            assertThrows(IllegalArgumentException.class, () -> Widgets.inflater(actions)
                    .inflateAll(KdlParser.parse("button press=\"svae\" \"Save\"")));
        }

        @Test
        @DisplayName("a lenient registry inflates anyway, for a document being edited")
        void lenientAllows() {
            var button = (Button) Widgets.inflater(ActionRegistry.lenient())
                    .inflateAll(KdlParser.parse("button press=\"nothing-yet\" \"Save\"")).getFirst();

            assertEquals(null, button.onPress());
        }

        @Test
        @DisplayName("binding the same name twice is refused, rebinding is not")
        void doubleBind() {
            var actions = ActionRegistry.strict().bind("save", () -> { });

            assertThrows(IllegalStateException.class, () -> actions.bind("save", () -> { }));
            actions.rebind("save", () -> { });
        }
    }

    @Nested
    @DisplayName("icon and disabled")
    class IconAndDisabled {

        private Icon icon;

        @BeforeEach
        void buildIcon() {
            TestFont.get();   // skips the whole nest when there is no library
            icon = Icon.bundled("plus", 16);
        }

        @AfterEach
        void closeIcon() {
            if (icon != null) {
                icon.close();
            }
        }

        @Test
        @DisplayName("an icon is a box beside the label, not a decoration over it")
        void iconIsABox() {
            // The answer to what ADR-0043 left open. An icon is built at a size
            // and that size is its intrinsic one, so it needs no measure
            // function and no callback into C.
            var box = new Button("Save").withIcon(icon)
                    .render(ComputedStyle.INITIAL, List.of(), TestFont.context());

            assertEquals(2, box.children().size());
            assertEquals(icon, box.children().getFirst().icon().icon());
            assertEquals(StyleLength.points(16), box.children().getFirst().width());
            assertEquals("Save", box.children().get(1).text().paragraph().text());
        }

        @Test
        @DisplayName("an icon-only button is legal; an empty one is not")
        void iconOnly() {
            var box = new Button("", icon, null, false, Attributes.NONE)
                    .render(ComputedStyle.INITIAL, List.of(), TestFont.context());
            assertEquals(1, box.children().size());

            // Nothing to click on and nothing to read out (§13).
            assertThrows(IllegalArgumentException.class,
                    () -> new Button("", null, null, false, Attributes.NONE));
        }

        @Test
        @DisplayName("markup names an icon and the registry resolves it")
        void iconFromMarkup() {
            var icons = Icons.strict().bind("plus", icon);

            var button = (Button) Widgets.inflater(ActionRegistry.none(), icons)
                    .inflateAll(KdlParser.parse("button icon=\"plus\" \"New\"")).getFirst();

            assertEquals(icon, button.icon());
        }

        @Test
        @DisplayName("a strict icon registry refuses a name nobody registered")
        void unknownIcon() {
            // Markup cannot build an icon, because nothing would ever close it.
            assertThrows(IllegalArgumentException.class,
                    () -> Widgets.inflater(ActionRegistry.none(), Icons.strict())
                            .inflateAll(KdlParser.parse("button icon=\"plus\" \"New\"")));
        }
    }

    @Nested
    @DisplayName("disabled")
    class Disabled {

        private final List<String> fired = new ArrayList<>();

        @Test
        @DisplayName("a disabled button refuses every route to its action")
        void refusesActivation() {
            var button = new Button("Save", () -> fired.add("pressed")).disabled(true);
            var element = new ElementTree(button).root();

            button.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 10, 10, PointerEvent.Button.PRIMARY, 1, element));
            button.onKey(new KeyEvent(
                    KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));

            assertTrue(fired.isEmpty(), () -> "fired was " + fired);
        }

        @Test
        @DisplayName("Tab skips it, rather than stranding a keyboard user on it")
        void notFocusable() {
            assertTrue(!new Button("Save").disabled(true).isFocusable());
        }

        @Test
        @DisplayName("`disabled=#true` in markup is the same value as in Java")
        void fromMarkup() {
            var fromKdl = (Button) Widgets.inflater()
                    .inflateAll(KdlParser.parse("button disabled=#true \"Save\"")).getFirst();

            assertEquals(new Button("Save").disabled(true), fromKdl);
        }

        @Test
        @DisplayName("the renderer mirrors it onto the element, so the cascade sees it")
        void mirroredToTheElement() {
            // The one pseudo-class a widget owns rather than the router. Without
            // this the stylesheet and the widget would disagree about the same
            // button.
            var tree = new ElementTree(new Button("Save").disabled(true));
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                    TestFont.get()).render(tree);

            assertTrue(tree.root().hasState(Selector.PseudoClass.DISABLED));
        }

        @Test
        @DisplayName("disabled fades the control and keeps its variant's colour")
        void fadesRatherThanRemaps() {
            var sheets = List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load());
            var element = new ElementTree(new Button("Save").styled("danger").disabled(true)).root();
            element.setPseudoClass(Selector.PseudoClass.DISABLED, true);

            var style = ComputedStyle.of(new StyleResolver(sheets).resolve(element),
                    CssLength.Context.DEFAULT);
            var enabled = ComputedStyle.of(
                    new StyleResolver(sheets).resolve(
                            new ElementTree(new Button("Save").styled("danger")).root()),
                    CssLength.Context.DEFAULT);

            // docs/design-system.md §2.1: "disabled is 45% opacity on the whole
            // control, never color-remapped". The variant survives, which is the
            // point -- a disabled danger button still reads as dangerous, and a
            // remap to one grey surface would have made every disabled button
            // look alike whatever it does.
            //
            // Compared against the *enabled* button rather than against a pinned
            // hex: what this test claims is that the two are the same colour, and
            // a literal made it also claim which colour, so a legitimate change to
            // the danger ramp failed it for a reason it was not about (ADR-0088).
            assertEquals(0.45, style.opacity(), 1e-9);
            assertEquals(enabled.background(), style.background(),
                    "still the danger colour, faded rather than remapped");
            assertEquals(io.github.digitalsmile.goldberry.backend.Cursor.NOT_ALLOWED, style.cursor());
        }

        @Test
        @DisplayName("the fade reaches the label and the border, not just the surface")
        void fadeReachesTheSubtree() {
            // The reason opacity is worth having as a property rather than eight
            // muted tokens: one number fades the fill, the text and the ring
            // together, and they cannot drift apart.
            var faded = Box.filled(0xFF804020)
                    .decoration(Decoration.NONE.border(1, 0xFF102030))
                    .opacity(0.45)
                    .fade(0.45);

            assertEquals(0x73804020, faded.background());
            assertEquals(0x73102030, faded.decoration().borderColor());
        }
    }
}
