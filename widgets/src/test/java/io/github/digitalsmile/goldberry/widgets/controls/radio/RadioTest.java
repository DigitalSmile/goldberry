package io.github.digitalsmile.goldberry.widgets.controls.radio;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.BindingRegistry;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.radio.Radio;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioDot;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioGroup;
import io.github.digitalsmile.goldberry.widgets.controls.radio.RadioIndicator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// The third and fourth controls, and the first **composite**.
///
/// [CheckboxTest] proved a control whose value comes from outside it. This proves
/// the thing a single control cannot have: an invariant over a *set*, one Tab
/// stop for several focusable nodes, and a selection that is also the roving
/// position ([ADR-0073]).
class RadioTest {

    private static List<Widget> inflate(String markup) {
        return Widgets.inflater().inflateAll(KdlParser.parse(markup));
    }

    /// The group's options, as the group rewrites them — which is where the
    /// exactly-one invariant is actually applied.
    private static List<Radio> options(RadioGroup group) {
        return group.children().stream().filter(Radio.class::isInstance).map(Radio.class::cast).toList();
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built radios are equal values")
        void radioJavaAndKdlAgree() {
            var fromJava = new Radio("dark", "Dark", false, null, false,
                    new Attributes("theme-dark", Set.of("compact"), "theme-dark"));

            var fromKdl = inflate("""
                    radio id="theme-dark" class="compact" value="dark" "Dark"
                    """).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("the Java-built and KDL-built groups are equal values")
        void groupJavaAndKdlAgree() {
            var fromJava = new RadioGroup("dark",
                    List.of(new Radio("light", "Light"), new Radio("dark", "Dark")),
                    null, null, false,
                    new Attributes("theme", Set.of("inline"), "theme"));

            var fromKdl = inflate("""
                    radio-group id="theme" class="inline" value="dark" {
                        radio value="light" "Light"
                        radio value="dark" "Dark"
                    }
                    """).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("both are CSS-selectable by type, id and class")
        void selectable() {
            assertEquals("radio", new Radio("dark", "Dark").cssType());
            assertEquals("radio-group", new RadioGroup("dark").cssType());
            assertEquals(Set.of("inline"), new RadioGroup("dark").styled("inline").classes());
        }

        @Test
        @DisplayName("the registry lists both, and the parts are deliberately absent")
        void registered() {
            var registered = Widgets.inflater().registered();
            assertTrue(registered.contains("radio"));
            assertTrue(registered.contains("radio-group"));
            assertFalse(registered.contains("radio-indicator"),
                    "a part is CSS-selectable and not KDL-constructible (ADR-0065)");
            assertFalse(registered.contains("radio-dot"), "and so is the dot inside it");
            assertFalse(registered.contains("check-mark"), "and the checkbox's mark");
            assertFalse(registered.contains("toggle-track"), "and the switch's pill");
            assertFalse(registered.contains("toggle-thumb"), "and the disc inside it");
            assertFalse(registered.contains("slider-track"), "and the slider's groove");
            assertFalse(registered.contains("slider-thumb"), "and the disc that runs along it");
            assertFalse(registered.contains("progress-fill"), "and the coloured part of a bar");
            assertFalse(registered.contains("segmented-track"), "and the grid a pill runs along");
            assertFalse(registered.contains("segmented-indicator"), "and the pill itself");
            // Pinned rather than counted: a control reaching the catalog is a
            // deliberate act, and this failing is what makes it one.
            assertEquals(
                    List.of("button", "checkbox", "toggle", "slider", "radio-group", "radio",
                            "segmented", "option", "select", "progress", "spinner", "badge", "knob"),
                    Controls.controlTypes());
        }

        @Test
        @DisplayName("a radio without a value is refused at inflation")
        void valueRequired() {
            // Defaulting it to the label would make two options that happen to
            // share a label select together, which reads as a toolkit bug.
            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> inflate("radio \"Dark\""));
            assertTrue(thrown.getMessage().contains("value="), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("exactly one is on, and the group is what holds it")
    class Invariant {

        @Test
        @DisplayName("the group marks the matching option and only that one")
        void oneSelected() {
            var group = (RadioGroup) inflate("""
                    radio-group value="dark" {
                        radio value="light" "Light"
                        radio value="dark" "Dark"
                        radio value="system" "System"
                    }
                    """).getFirst();

            assertEquals(List.of(false, true, false),
                    options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("a value no option carries selects nothing rather than guessing")
        void unmatchedSelectsNothing() {
            // A model that has not loaded, or one holding a value from a newer
            // version of the document. Guessing the first would report a choice
            // the user never made.
            var group = new RadioGroup("solarized",
                    new Radio("light", "Light"), new Radio("dark", "Dark"));

            assertEquals(List.of(false, false), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("a null value selects nothing")
        void nullSelectsNothing() {
            var group = RadioGroup.of(Property.of(null), null,
                    new Radio("light", "Light"), new Radio("dark", "Dark"));

            assertNull(group.resolved());
            assertEquals(List.of(false, false), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("markup cannot mark an option selected, so it cannot mark two")
        void markupCannotSelect() {
            // `selected` is not an attribute at all: the invariant is the group's
            // and a document that could set it per option could break it.
            var group = (RadioGroup) inflate("""
                    radio-group {
                        radio value="light" selected=#true "Light"
                        radio value="dark" selected=#true "Dark"
                    }
                    """).getFirst();

            assertEquals(List.of(false, false), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("a child that is not a radio is laid out and left alone")
        void otherChildrenSurvive() {
            var group = new RadioGroup("dark", List.of(
                    new Text("Theme"), new Radio("dark", "Dark")), null, null, false, null);

            assertEquals(2, group.children().size());
            assertEquals(new Text("Theme"), group.children().getFirst(),
                    "a group that dropped what it did not recognise would lose a heading"
                            + " with no error");
        }
    }

    @Nested
    @DisplayName("data down, events up (ADR-0063)")
    class Binding {

        @Test
        @DisplayName("the selection comes from the bound property")
        void controlled() {
            var theme = Property.of("light");
            var group = RadioGroup.of(theme, null,
                    new Radio("light", "Light"), new Radio("dark", "Dark"));

            assertEquals("light", group.resolved());
            theme.set("dark");
            assertEquals("dark", group.resolved(), "the application moved it, so it moved");
        }

        @Test
        @DisplayName("a click does not move a group whose handler does nothing")
        void controlledMeansControlled() {
            var theme = Property.of("light");
            var group = RadioGroup.of(theme, value -> { },
                    new Radio("light", "Light"), new Radio("dark", "Dark"));
            var dark = options(group).get(1);
            var element = new ElementTree(group).root();

            dark.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("light", group.resolved());
            assertEquals("light", theme.get());
        }

        @Test
        @DisplayName("what the user picked travels up, with the value")
        void changeCarriesTheValue() {
            var theme = Property.of("light");
            var group = RadioGroup.of(theme, theme::set,
                    new Radio("light", "Light"), new Radio("dark", "Dark"));
            var element = new ElementTree(group).root();

            options(group).get(1).onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("dark", theme.get());
            assertEquals("dark", group.resolved(),
                    "and the new value arrives back down through the binding");
            assertEquals(List.of(false, true), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("a bound value that is not a String is compared as the author spelled it")
        void nonStringValue() {
            // An enum or an int in the model, strings in the document. The
            // coercion never guesses what an object means, only how it is
            // written down.
            var theme = Property.of(Theme.NORD_DARK);
            var group = RadioGroup.of(theme, null,
                    new Radio("NORD_LIGHT", "Light"), new Radio("NORD_DARK", "Dark"));

            assertEquals(List.of(false, true), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("markup names a path and a valued action, and both resolve")
        void fromMarkup() {
            var picked = new ArrayList<String>();
            var theme = Property.of("light");
            var bindings = BindingRegistry.strict().bind("prefs.theme", theme);
            var actions = ActionRegistry.strict().bind("pickTheme", (String value) -> picked.add(value));

            var group = (RadioGroup) Widgets.inflater(actions, Icons.none(), bindings)
                    .inflateAll(KdlParser.parse("""
                            radio-group bind="prefs.theme" change="pickTheme" {
                                radio value="light" "Light"
                                radio value="dark" "Dark"
                            }
                            """)).getFirst();

            assertEquals("light", group.resolved());
            options(group).get(1).onSelect().run();
            assertEquals(List.of("dark"), picked, "the handler is told which one");
        }

        @Test
        @DisplayName("a plain Runnable resolves against `change` too")
        void runnableAdapts() {
            // For a handler that reads the model itself. Making the author pick
            // the bind() overload to match the widget would be a distinction only
            // the registry cares about.
            var fired = new ArrayList<String>();
            var actions = ActionRegistry.strict().bind("refresh", () -> fired.add("ran"));

            var group = (RadioGroup) Widgets.inflater(actions)
                    .inflateAll(KdlParser.parse("""
                            radio-group change="refresh" { radio value="a" "A" }
                            """)).getFirst();

            options(group).getFirst().onSelect().run();
            assertEquals(List.of("ran"), fired);
        }

        @Test
        @DisplayName("a valued action refuses to answer an attribute that takes none")
        void valuedIsNotARunnable() {
            var actions = ActionRegistry.strict().bind("pickTheme", (String value) -> { });

            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> Widgets.inflater(actions)
                            .inflateAll(KdlParser.parse("button press=\"pickTheme\" \"Pick\"")));
            assertTrue(thrown.getMessage().contains("expects a value"), thrown.getMessage());
        }

        @Test
        @DisplayName("a strict registry refuses a name nobody bound")
        void strictRefuses() {
            assertThrows(IllegalArgumentException.class,
                    () -> Widgets.inflater(ActionRegistry.strict())
                            .inflateAll(KdlParser.parse("""
                                    radio-group change="pikcTheme" { radio value="a" "A" }
                                    """)));
        }

        @Test
        @DisplayName("`binding()` is what the element subscribes to")
        void subscribes() {
            var theme = Property.of("light");
            new ElementTree(RadioGroup.of(theme, null, new Radio("light", "Light")));

            assertEquals(1, theme.listenerCount());
        }
    }

    @Nested
    @DisplayName("one Tab stop, arrows inside (§7.2)")
    class Traversal {

        private final List<String> picked = new ArrayList<>();

        private ElementTree tree(String selected) {
            return new ElementTree(new Column(
                    new Button("Before", () -> { }),
                    new RadioGroup(selected, picked::add,
                            new Radio("light", "Light"),
                            new Radio("dark", "Dark"),
                            new Radio("system", "System")),
                    new Button("After", () -> { })));
        }

        /// The router reads `:checked` off the element, and the renderer is what
        /// mirrors it there — so a traversal test has to render first, exactly as
        /// a real frame does.
        private PointerRouter routed(ElementTree tree) {
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                    TestFont.get()).render(tree);
            var router = new PointerRouter();
            router.focusRoot(tree.root());
            return router;
        }

        private Element option(ElementTree tree, int index) {
            return tree.root().children().get(1).children().get(index);
        }

        @Test
        @DisplayName("the group is one Tab stop, not three")
        void oneStop() {
            var tree = tree("dark");
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("radio", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type(), "one press crossed all three options");
        }

        @Test
        @DisplayName("Tab enters at the selected option")
        void entersAtSelection() {
            var tree = tree("system");
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            assertSame(option(tree, 2), router.focused());
        }

        @Test
        @DisplayName("an arrow key asks for the next option, and does not select it")
        void selectionFollowsFocusThroughTheApplication() {
            // The whole of ADR-0063 applied to a composite: the arrow moves the
            // ring, the change goes up with the value, and the tick moves only
            // when the application sets the property.
            var tree = tree("light");
            var router = routed(tree);
            router.focus(option(tree, 0), true);
            picked.clear();

            router.keyPressed(Key.DOWN, Modifiers.NONE, false);

            assertSame(option(tree, 1), router.focused(), "the ring moved");
            assertEquals(List.of("dark"), picked, "and the group asked for `dark`");
            assertFalse(tree.root().children().get(1).children().get(1)
                            .hasState(Selector.PseudoClass.CHECKED),
                    "but nothing selected it here: this group's handler only records");
        }

        @Test
        @DisplayName("a disabled group has no Tab stop at all")
        void disabledGroupSkipped() {
            var tree = new ElementTree(new Column(
                    new Button("Before", () -> { }),
                    new RadioGroup("light", picked::add, new Radio("light", "Light")).disabled(true),
                    new Button("After", () -> { })));
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            assertEquals("After", ((Button) router.focused().widget()).label());
        }
    }

    @Nested
    @DisplayName("picking an option")
    class Activation {

        private final List<String> picked = new ArrayList<>();

        private Radio option(int index) {
            var group = new RadioGroup("light", picked::add,
                    new Radio("light", "Light"), new Radio("dark", "Dark"));
            return options(group).get(index);
        }

        @Test
        @DisplayName("a click anywhere in the row picks it, label included")
        void clickPicks() {
            var dark = option(1);
            var element = new ElementTree(dark).root();

            dark.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 80, 16, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(List.of("dark"), picked);
        }

        @Test
        @DisplayName("Space picks and Enter does not")
        void spaceNotEnter() {
            var dark = option(1);
            var element = new ElementTree(dark).root();

            dark.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, element));
            assertTrue(picked.isEmpty(), "Enter belongs to a dialog's default action (§2.3)");

            dark.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            assertEquals(List.of("dark"), picked);
        }

        @Test
        @DisplayName("keyboard focus picks it and mouse focus does not")
        void focusPicksOnlyFromTheKeyboard() {
            // If a pointer focus selected, the press that moves focus and the
            // click that follows would each fire the change.
            var dark = option(1);

            dark.onFocusChanged(true, false);
            assertTrue(picked.isEmpty());

            dark.onFocusChanged(true, true);
            assertEquals(List.of("dark"), picked);
        }

        @Test
        @DisplayName("re-picking the option already on is a no-op, not a toggle")
        void rePickingIsHarmless() {
            // Which is what makes Tab returning into a group safe: the entry
            // raises a change for the value already held, and `Property.set`
            // swallows it.
            var theme = Property.of("dark");
            var group = RadioGroup.of(theme, theme::set,
                    new Radio("light", "Light"), new Radio("dark", "Dark"));

            options(group).get(1).onFocusChanged(true, true);

            assertEquals("dark", theme.get());
            assertEquals(List.of(false, true), options(group).stream().map(Radio::selected).toList());
        }

        @Test
        @DisplayName("a disabled option refuses every route and leaves the Tab order")
        void disabledRefuses() {
            var group = new RadioGroup("light", picked::add,
                    new Radio("light", "Light"), new Radio("dark", "Dark").disabled(true));
            var dark = options(group).get(1);
            var element = new ElementTree(dark).root();

            dark.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));
            dark.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            dark.onFocusChanged(true, true);

            assertTrue(picked.isEmpty());
            assertFalse(dark.isFocusable());
        }

        @Test
        @DisplayName("a disabled group does not mark its options, and does not need to")
        void groupDoesNotPushDisabledDown() {
            var group = new RadioGroup("light", picked::add,
                    new Radio("light", "Light"), new Radio("dark", "Dark")).disabled(true);

            // The flag stays on the node that declared it. Pushing it down would
            // make every option match `:disabled` as well, and 45% applied twice
            // lands at 20% -- which is why this used to need an `opacity: 1` undo
            // rule in controls.css. Unavailability propagates through the router
            // instead, and the fade propagates by itself because opacity
            // multiplies down a subtree (ADR-0077).
            assertTrue(options(group).stream().noneMatch(Radio::disabled),
                    "a group's disabled is the group's, not each option's");
        }

        @Test
        @DisplayName("an option that disabled itself keeps it, inside an available group")
        void anOptionMayDisableItself() {
            var group = new RadioGroup("light", picked::add,
                    new Radio("light", "Light"),
                    new Radio("dark", "Dark", false, null, true, null));

            assertFalse(options(group).getFirst().disabled());
            assertTrue(options(group).get(1).disabled(),
                    "a document may disable one option in a group that is otherwise available");
        }

        @Test
        @DisplayName("an unwired option does nothing rather than failing")
        void unwiredIsInert() {
            var loose = new Radio("dark", "Dark");
            var element = new ElementTree(loose).root();

            loose.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertNull(loose.onSelect(), "a control styled before it is wired is a normal stage");
        }
    }

    @Nested
    @DisplayName("the glyph is a part (ADR-0065)")
    class Part {

        @Test
        @DisplayName("a radio builds an indicator and its label")
        void buildsTheGlyph() {
            var children = new Radio("dark", "Dark", true, null, false, null).children();

            assertEquals(2, children.size());
            assertEquals(new RadioIndicator(true, false), children.getFirst());
            assertEquals(new Text("Dark"), children.get(1));
        }

        @Test
        @DisplayName("an icon-less, label-less option is just the glyph")
        void noLabel() {
            assertEquals(1, new Radio("dark", "").children().size());
        }

        @Test
        @DisplayName("the indicator carries `:checked` and the disabled flag")
        void indicatorState() {
            assertTrue(new RadioIndicator(true, false).isChecked());
            assertFalse(new RadioIndicator(false, false).isChecked());
            assertTrue(new RadioIndicator(false, true).isDisabled());
            assertEquals("radio-indicator", new RadioIndicator(false, false).cssType());
        }

        @Test
        @DisplayName("the renderer mirrors the selection onto the element, so CSS can see it")
        void mirroredToTheElement() {
            for (var selected : List.of(true, false)) {
                var tree = new ElementTree(new RadioGroup(selected ? "dark" : "light",
                        new Radio("dark", "Dark")));
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                        TestFont.get()).render(tree);

                var radio = tree.root().children().getFirst();
                assertEquals(selected, radio.hasState(Selector.PseudoClass.CHECKED));
                assertEquals(selected,
                        radio.children().getFirst().hasState(Selector.PseudoClass.CHECKED),
                        "and onto the glyph, which is what `radio-indicator:checked` styles");
            }
        }

        @Test
        @DisplayName("the dot is a node of its own, so it can scale without the ring")
        void dotIsANode() {
            // §3.1's "check/dot: scale 0.6->1 + opacity". A Box.Mark is drawn
            // onto the box that carries it, so while the dot lived on the
            // indicator, scaling it grew the 16px ring too.
            var indicator = new RadioIndicator(true, false);

            assertEquals(1, indicator.children().size());
            assertEquals("radio-dot", ((RadioDot) indicator.children().getFirst()).cssType());
            assertNull(indicator.render(ComputedStyle.INITIAL, List.of(), TestFont.context()).mark(),
                    "the ring carries no mark itself");
        }

        @Test
        @DisplayName("the dot is built unselected too, or it could never transition in")
        void dotAlwaysBuilt() {
            // A node that appears with the value has no previous style to move
            // from, and a newly built element deliberately starts no transition
            // (ADR-0067) -- so it would snap. The stylesheet fades it instead.
            for (var selected : List.of(true, false)) {
                var dot = (RadioDot) new RadioIndicator(selected, false).children().getFirst();
                assertEquals(Box.Mark.Kind.DOT,
                        dot.render(ComputedStyle.INITIAL, List.of(), TestFont.context())
                                .mark().kind());
            }
        }

        @Test
        @DisplayName("the scale and the fade are the stylesheet's, not the widget's")
        void animationIsCss() {
            var style = new StyleResolver(
                    List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()));
            var tree = new ElementTree(new RadioGroup("dark", new Radio("dark", "Dark")));
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                    TestFont.get()).render(tree);

            var dot = tree.root().children().getFirst().children().getFirst().children().getFirst();
            assertEquals("radio-dot", dot.type());
            var resolved = ComputedStyle.of(style.resolve(dot), CssLength.Context.DEFAULT, null);
            assertFalse(resolved.transitions().isEmpty(),
                    "§3.1 gives the dot a transition, and it is declared in CSS so that"
                            + " reduced motion and a theme can both reach it");
        }

        @Test
        @DisplayName("a radio has no mixed state")
        void noMixed() {
            // A group's "nothing selected yet" is no option matching, not an
            // option in a third state -- which is why `:indeterminate` appears
            // nowhere in the radio rules.
            assertFalse(new RadioIndicator(true, false).isIndeterminate());
            assertFalse(new Radio("dark", "Dark", true, null, false, null).isIndeterminate());
        }
    }

    @Nested
    @DisplayName("reconciliation")
    class Keys {

        @Test
        @DisplayName("an option's key is its value, so reordering keeps its element")
        void keyedByValue() {
            assertEquals("dark", new Radio("dark", "Dark").key());
            assertEquals("theme", new Radio("dark", "Dark", false, null, false,
                    new Attributes("theme", Set.of(), "theme")).key(),
                    "an explicit id still wins");
        }

        @Test
        @DisplayName("the group is a focus scope and is not itself focusable")
        void groupShape() {
            var group = new RadioGroup("dark", new Radio("dark", "Dark"));

            // BOTH rather than an axis, and it is the one composite in the
            // catalog for which that is right: a group's direction is its
            // stylesheet's, and `.inline` flips it, so input cannot know which
            // pair the user is looking at (ADR-0078).
            assertEquals(FocusScope.BOTH, group.focusScope());
            assertFalse(group.isFocusable(),
                    "the ring belongs on the option the user is about to pick");
            assertNotNull(group.children());
        }
    }
}
