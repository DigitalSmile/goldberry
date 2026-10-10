package dev.goldberry.widgets.controls.segmented;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.Property;
import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.bind.registry.BindingRegistry;
import dev.goldberry.css.Border;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.StyleElement;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.cascade.Transitions;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.input.FocusScope;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.kdl.KdlSyntaxException;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// The eleventh control, and the second composite.
///
/// [dev.goldberry.widgets.controls.radio.RadioTest] proved the
/// composite machinery; this proves the thing the catalogue asks of a segmented
/// control that the first composite could not show — that a set's **axis** is a
/// property of the widget and not always of its stylesheet — and the drawing
/// decisions a joined bar forces, because the catalogue's row describes something
/// the CSS subset cannot express on its own.
///
/// Read more: [Segmented](https://goldberry.dev/docs/components/choices.html#segmented).
class SegmentedTest {

    private static List<Widget> inflate(String markup) {
        return Widgets.inflater().inflateAll(KdlParser.parse(markup));
    }

    /// The segments, as the control rewrites them — which is where the
    /// exactly-one invariant is actually applied.
    ///
    /// One level down from the bar now: a `segmented` builds a `segmented-track`
    /// and the track holds the segments and the indicator that runs along them.
    private static List<Option> options(Segmented bar) {
        return track(bar).segments().stream()
                .filter(Option.class::isInstance)
                .map(Option.class::cast)
                .toList();
    }

    private static SegmentedTrack track(Segmented bar) {
        return (SegmentedTrack) bar.children().getFirst();
    }

    /// The alpha of a packed `0xAARRGGBB`, which is how "does this paint
    /// anything" is asked of a colour.
    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    @Nested
    @DisplayName("markup and Java build the same tree")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built bars are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Segmented(
                    "grid",
                    List.of(new Option("list", "List"), new Option("grid", "Grid")),
                    null,
                    null,
                    false,
                    new Attributes("view", Set.of("compact"), "view"));

            var fromKdl = inflate("""
                    segmented id="view" class="compact" value="grid" {
                        option value="list" "List"
                        option value="grid" "Grid"
                    }
                    """).getFirst();

            assertEquals(fromJava, fromKdl);
        }

        @Test
        @DisplayName("and so are the segments on their own")
        void optionsAgree() {
            assertEquals(
                    new Option("list", "List"),
                    inflate("option value=\"list\" \"List\"").getFirst());
        }

        /// That `segmented` and `option` are registered is swept over the whole
        /// catalog by `WidgetParityTest`; that `segment` is **not** is a claim
        /// about a name nothing registers, so nothing else can hold it.
        @Test
        @DisplayName("`segment` is deliberately not a second spelling of `option`")
        void noSegmentAlias() {
            // This control and `select` both take `option` children, so that is
            // the node. A `segment` alias would be a second spelling of one thing.
            assertFalse(Widgets.inflater().registered().contains("segment"));
        }

        @Test
        @DisplayName("a segment without a value is refused at inflation")
        void valueRequired() {
            // Defaulting it to the label would make two segments that happen to
            // share a label select together, which reads as a toolkit bug.
            var thrown = assertThrows(IllegalArgumentException.class, () -> inflate("option \"List\""));
            assertTrue(thrown.getMessage().contains("value="), thrown.getMessage());
        }

        @Test
        @DisplayName("a segment with neither a label nor an icon is refused")
        void contentRequired() {
            // An icon-only segment is legal and a *nothing*-only segment is not:
            // there would be nothing to click on and nothing to read out, and a
            // control nobody can read out is a failure rather than a blank.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Option("list", "", null, false, null, false, Attributes.NONE));
        }
    }

    @Nested
    @DisplayName("exactly one is on, and the bar is what holds it")
    class Invariant {

        @Test
        @DisplayName("the bar marks the matching segment and only that one")
        void oneSelected() {
            var bar = (Segmented) inflate("""
                    segmented value="grid" {
                        option value="list" "List"
                        option value="grid" "Grid"
                        option value="map" "Map"
                    }
                    """).getFirst();

            assertEquals(
                    List.of(false, true, false),
                    options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("a value no segment carries selects nothing rather than guessing")
        void unmatchedSelectsNothing() {
            var bar = new Segmented("timeline", new Option("list", "List"), new Option("grid", "Grid"));

            assertEquals(
                    List.of(false, false),
                    options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("a null value selects nothing")
        void nullSelectsNothing() {
            var bar = Segmented.of(Property.of(null), null, new Option("list", "List"), new Option("grid", "Grid"));

            assertNull(bar.resolved());
            assertEquals(
                    List.of(false, false),
                    options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("markup cannot mark a segment selected, so it cannot mark two")
        void markupCannotSelect() {
            // Nothing reads `selected`, so the inflater refuses it rather than
            // dropping it.
            var refused = assertThrows(KdlSyntaxException.class, () -> inflate("""
                    segmented {
                        option value="list" selected=#true "List"
                        option value="grid" selected=#true "Grid"
                    }
                    """));

            assertTrue(refused.getMessage().startsWith("option at 2:5 ignores selected=#true"), refused.getMessage());
        }

        @Test
        @DisplayName("a child that is not an option is laid out and left alone")
        void otherChildrenSurvive() {
            var bar = new Segmented(
                    "grid", List.of(new Text("View"), new Option("grid", "Grid")), null, null, false, null);

            assertEquals(2, track(bar).segments().size());
            assertEquals(new Text("View"), track(bar).segments().getFirst());
        }

        @Test
        @DisplayName("the bar copies the segments it is handed")
        void childrenAreCopied() {
            var mutable = new ArrayList<Widget>();
            mutable.add(new Option("list", "List"));

            var bar = new Segmented(null, mutable, null, null, false, Attributes.NONE);
            mutable.add(new Option("grid", "Grid"));

            assertEquals(1, track(bar).segments().size());
        }
    }

    @Nested
    @DisplayName("data flows down and events flow up")
    class Binding {

        @Test
        @DisplayName("the selection comes from the bound property")
        void controlled() {
            var view = Property.of("list");
            var bar = Segmented.of(view, null, new Option("list", "List"), new Option("grid", "Grid"));

            assertEquals("list", bar.resolved());
            view.set("grid");
            assertEquals("grid", bar.resolved(), "the application moved it, so it moved");
        }

        @Test
        @DisplayName("a click does not move a bar whose handler does nothing")
        void controlledMeansControlled() {
            var view = Property.of("list");
            var bar = Segmented.of(view, value -> {}, new Option("list", "List"), new Option("grid", "Grid"));
            var grid = options(bar).get(1);
            var element = new ElementTree(bar).root();

            grid.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("list", bar.resolved());
            assertEquals("list", view.get());
        }

        @Test
        @DisplayName("what the user picked travels up, with the value")
        void changeCarriesTheValue() {
            var view = Property.of("list");
            var bar = Segmented.of(view, view::set, new Option("list", "List"), new Option("grid", "Grid"));
            var element = new ElementTree(bar).root();

            options(bar)
                    .get(1)
                    .onPointer(
                            new PointerEvent(PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("grid", view.get());
            assertEquals(
                    List.of(false, true),
                    options(bar).stream().map(Option::selected).toList(),
                    "and the new value arrives back down through the binding");
        }

        @Test
        @DisplayName("a bound value that is not a String is compared as the author spelled it")
        void nonStringValue() {
            var theme = Property.of(Theme.NORD_DARK);
            var bar = Segmented.of(theme, null, new Option("NORD_LIGHT", "Light"), new Option("NORD_DARK", "Dark"));

            assertEquals(
                    List.of(false, true),
                    options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("markup names a path and a valued action, and both resolve")
        void fromMarkup() {
            var picked = new ArrayList<String>();
            var view = Property.of("list");
            var bindings = BindingRegistry.strict().bind("view.mode", view);
            var actions = ActionRegistry.strict().bind("pickView", (String value) -> picked.add(value));

            var bar = (Segmented) Widgets.inflater(actions, Icons.none(), bindings)
                    .inflateAll(KdlParser.parse("""
                            segmented bind="view.mode" change="pickView" {
                                option value="list" "List"
                                option value="grid" "Grid"
                            }
                            """))
                    .getFirst();

            assertEquals("list", bar.resolved());
            options(bar).get(1).onSelect().run();
            assertEquals(List.of("grid"), picked, "the handler is told which one");
        }

        @Test
        @DisplayName("`binding()` is what the element subscribes to")
        void subscribes() {
            var view = Property.of("list");
            new ElementTree(Segmented.of(view, null, new Option("list", "List")));

            assertEquals(1, view.listenerCount());
        }
    }

    @Nested
    @DisplayName("the bar is one Tab stop, and the arrows rove along its own axis")
    class Traversal {

        private final List<String> picked = new ArrayList<>();

        private ElementTree tree() {
            return new ElementTree(new Column(
                    new Button("Before", () -> {}),
                    new Segmented(
                            "list",
                            picked::add,
                            new Option("list", "List"),
                            new Option("grid", "Grid"),
                            new Option("map", "Map")),
                    new Button("After", () -> {})));
        }

        /// The router reads `:checked` off the element and the renderer is what
        /// mirrors it there, so a traversal test renders first — exactly as a real
        /// frame does.
        private PointerRouter routed(ElementTree tree) {
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
            var router = new PointerRouter();
            router.focusRoot(tree.root());
            return router;
        }

        /// The bar's element, then its track, then the segment — and `+ 1`
        /// because the track's first child is the indicator, which is painted
        /// under the labels and takes no focus.
        /// The `index`-th segment, found **by type** rather than by counting
        /// parts: the track also holds the pill and one hairline per boundary,
        /// and a fixed offset would say something different every time the
        /// anatomy changed.
        private Element segment(ElementTree tree, int index) {
            return tree.root().children().get(1).children().getFirst().children().stream()
                    .filter(child -> "option".equals(child.type()))
                    .toList()
                    .get(index);
        }

        @Test
        @DisplayName("the bar is one Tab stop, not three")
        void oneStop() {
            var tree = tree();
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("option", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type(), "one press crossed all three segments");
        }

        @Test
        @DisplayName("Right moves along the bar and asks for that segment")
        void rightRoves() {
            var tree = tree();
            var router = routed(tree);
            router.focus(segment(tree, 0), true);
            picked.clear();

            router.keyPressed(Key.RIGHT, Modifiers.NONE, false);

            assertSame(segment(tree, 1), router.focused(), "the ring moved");
            assertEquals(List.of("grid"), picked, "and the bar asked for `grid`");
        }

        /// **The one line that separates this control from `radio-group` in
        /// Java.** A group answers to both arrow pairs because its direction is
        /// its stylesheet's; a bar has a direction of its own, so `Up` and `Down`
        /// are not its to consume and a scroll view above it must still get them.
        @Test
        @DisplayName("Down does nothing, because a bar's axis is horizontal")
        void verticalArrowsAreNotTheBars() {
            var tree = tree();
            var router = routed(tree);
            router.focus(segment(tree, 0), true);
            picked.clear();

            router.keyPressed(Key.DOWN, Modifiers.NONE, false);

            assertSame(segment(tree, 0), router.focused(), "focus stayed where it was");
            assertTrue(picked.isEmpty(), "and nothing was asked for");
        }

        @Test
        @DisplayName("the axis is the widget's answer and not an accident of the drawing")
        void scopeIsHorizontal() {
            assertEquals(FocusScope.HORIZONTAL, new Segmented("list").focusScope());
            assertFalse(
                    new Segmented("list").isFocusable(),
                    "focus lands on a segment, so the ring is on what the user is about to pick");
        }

        @Test
        @DisplayName("Tab enters at the selected segment")
        void entersAtSelection() {
            var tree = new ElementTree(new Column(
                    new Button("Before", () -> {}),
                    new Segmented(
                            "map",
                            picked::add,
                            new Option("list", "List"),
                            new Option("grid", "Grid"),
                            new Option("map", "Map"))));
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            assertSame(segment(tree, 2), router.focused());
        }

        @Test
        @DisplayName("a disabled bar has no Tab stop at all")
        void disabledBarSkipped() {
            var tree = new ElementTree(new Column(
                    new Button("Before", () -> {}),
                    new Segmented("list", picked::add, new Option("list", "List")).disabled(true),
                    new Button("After", () -> {})));
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            assertEquals("After", ((Button) router.focused().widget()).label());
        }
    }

    @Nested
    @DisplayName("picking a segment")
    class Activation {

        private final List<String> picked = new ArrayList<>();

        private Option option(int index) {
            var bar = new Segmented("list", picked::add, new Option("list", "List"), new Option("grid", "Grid"));
            return options(bar).get(index);
        }

        @Test
        @DisplayName("a click anywhere in the segment picks it")
        void clickPicks() {
            var grid = option(1);
            var element = new ElementTree(grid).root();

            grid.onPointer(
                    new PointerEvent(PointerEvent.Kind.CLICKED, 40, 16, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(List.of("grid"), picked);
        }

        @Test
        @DisplayName("Space picks and Enter does not")
        void spaceNotEnter() {
            var grid = option(1);
            var element = new ElementTree(grid).root();

            grid.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, element));
            assertTrue(picked.isEmpty(), "Enter belongs to a dialog's default action");

            grid.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            assertEquals(List.of("grid"), picked);
        }

        @Test
        @DisplayName("keyboard focus picks it and mouse focus does not")
        void focusPicksOnlyFromTheKeyboard() {
            var grid = option(1);

            grid.onFocusChanged(true, false);
            assertTrue(picked.isEmpty());

            grid.onFocusChanged(true, true);
            assertEquals(List.of("grid"), picked);
        }

        @Test
        @DisplayName("a disabled segment refuses every route and leaves the Tab order")
        void disabledRefuses() {
            var bar = new Segmented(
                    "list", picked::add, new Option("list", "List"), new Option("grid", "Grid").disabled(true));
            var grid = options(bar).get(1);
            var element = new ElementTree(grid).root();

            grid.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));
            grid.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            grid.onFocusChanged(true, true);

            assertTrue(picked.isEmpty());
            assertFalse(grid.isFocusable());
        }

        @Test
        @DisplayName("a disabled bar does not mark its segments, and does not need to")
        void barDoesNotPushDisabledDown() {
            // The flag stays on the node that declared it, the fade
            // multiplies down the subtree by itself, and unavailability
            // propagates through the router. Pushing it would fade twice and
            // land at 20%.
            var bar = new Segmented("list", picked::add, new Option("list", "List"), new Option("grid", "Grid"))
                    .disabled(true);

            assertTrue(options(bar).stream().noneMatch(Option::disabled));
        }

        /// The catalogue requires `name=` on an icon-only segment, and until every
        /// widget gained that attribute it did not exist anywhere. `Option` already refuses a segment
        /// with neither a label nor an icon, which was the half that could be
        /// enforced; this is the other half.
        @Test
        @DisplayName("an icon-only segment is named by `name=`, because an icon alone has no accessible name")
        void iconOnlyIsNamed() {
            var icon = dev.goldberry.icon.Icon.bundled("list", 16);
            try {
                var unnamed = new Option("list", "", icon, false, null, false, Attributes.NONE);
                var named = new Option("list", "", icon, false, null, false, Attributes.NONE.name("List view"));

                assertNull(unnamed.accessibleName());
                assertEquals("List view", named.accessibleName());
                assertEquals(
                        "Grid",
                        new Option("grid", "Grid", icon, false, null, false, Attributes.NONE.name("Grid view"))
                                .accessibleName(),
                        "a label wins, because it is what is on screen");
            } finally {
                icon.close();
            }
        }

        @Test
        @DisplayName("an unwired segment does nothing rather than failing")
        void unwiredIsInert() {
            var loose = new Option("grid", "Grid");
            var element = new ElementTree(loose).root();

            loose.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertNull(loose.onSelect(), "a control styled before it is wired is a normal stage");
        }
    }

    @Nested
    @DisplayName("the drawing, joined")
    class Drawing {

        /// The catalogue says radius 8 outer, 0 between, and it is drawn now that
        /// the bar is joined — the bar carries the 8, the segments and the pill
        /// carry the 7 that is the 8 less the bar's own border, and only the
        /// corners at the **ends** of the row keep it. The first drawing deferred
        /// this for two reasons: a corner was one number, and nothing clipped. A
        /// corner is four numbers now, and the second stopped mattering once a
        /// fill could round its own outer corners instead of being cut to shape.
        @Test
        @DisplayName("the bar's radius is 8, and what is inside it is that less the border")
        void radii() {
            assertEquals(Corners.all(8), styleOf("segmented").decoration().corners());
            assertEquals(
                    Corners.all(7),
                    styleOf("option").decoration().corners(),
                    "the stylesheet carries the radius; which corners keep it is the track's");
            assertEquals(
                    Corners.all(7), styleOf("segmented-indicator").decoration().corners());
        }

        /// The joined drawing's own arithmetic: the bar's padding is its border,
        /// so the track is exactly the box inside the edge. It was 2 while the
        /// segments were inset pills, and that 2 was a spacing step; this 1 is not a
        /// spacing step at all but the width of the line it clears.
        @Test
        @DisplayName("the bar's padding is its border's width, so the track is its inner box")
        void insetIsTheBorder() {
            var bar = styleOf("segmented");

            assertEquals(Length.points(1), bar.padding().top());
            assertEquals(
                    Border.all(1, bar.decoration().border().top().argb()),
                    bar.decoration().border());
            assertEquals(bar.padding().top(), bar.padding().left());
            assertEquals(bar.padding().top(), bar.padding().bottom());
            assertEquals(bar.padding().left(), bar.padding().right());
        }

        /// The catalogue's 1px divider in `--gb-border`, which came back with the
        /// drawing it belonged to — the first drawing dropped it because a divider
        /// separates segments that meet, and they meet again.
        @Test
        @DisplayName("a divider is a hairline in the border's own colour")
        void dividerIsAHairline() {
            var divider = styleOf("segmented-divider");

            assertEquals(Length.points(1), divider.width());
            assertEquals(
                    styleOf("segmented").decoration().border().top().argb(),
                    divider.background(),
                    "the line between two segments is the line around them");
            assertNotNull(
                    divider.transitions().get(Transitions.Animatable.OPACITY),
                    "a hairline that blinked would beat the pill that is still travelling");
        }

        /// The widget says **which** lines are beside the pill and the stylesheet
        /// says what that means — the half of the old `restyle` a stylesheet could
        /// have written once it was told.
        @Test
        @DisplayName("the hairlines beside the pill are hidden by the stylesheet, told which by the widget")
        void besideTheSelectionIsTheStylesheets() {
            var left = new SegmentedDivider(1, 4, 1);
            var right = new SegmentedDivider(2, 4, 1);
            var far = new SegmentedDivider(3, 4, 1);

            assertEquals(Set.of("beside-selection"), left.classes());
            assertEquals(left.classes(), right.classes());
            assertEquals(Set.of(), far.classes(), "a line away from the pill is not beside it");

            var hidden = styleOf("segmented-divider", left.classes());
            assertEquals(0.0, hidden.opacity(), 1e-9);
            assertEquals(1.0, styleOf("segmented-divider", far.classes()).opacity(), 1e-9);
            assertEquals(
                    1.0,
                    left.restyle(styleOf("segmented-divider")).opacity(),
                    1e-9,
                    "restyle places the line and no longer decides whether it shows");
        }

        /// The one layout property the control asserts, because it is the one
        /// that decides what a bar looks like in a column: given spare width the
        /// segments divide it, rather than huddling at the left of a plate that
        /// stretched without them. `radio-group` answers the same question the
        /// other way and says why.
        /// The grid is the **track's**, not the stylesheet's, and this is what
        /// says so: a cell's width is the one metric of this control that no
        /// selector can write, because no selector can count the segments.
        @Test
        @DisplayName("the stylesheet gives a segment every metric except its width")
        void widthIsNotTheStylesheets() {
            var cell = styleOf("option");

            assertEquals(
                    Length.UNDEFINED,
                    cell.width(),
                    "a width here would be a number that cannot know how many cells there are");
            assertEquals(Length.points(12), cell.padding().left(), "the segment's 12px padding-x, though");
            assertEquals(0.0, styleOf("segmented").flexGrow(), "the bar itself takes no space it was not given");
            assertEquals(
                    1.0,
                    styleOf("segmented-track").flexGrow(),
                    "but the track fills it, because that is what the cells divide");
        }

        @Test
        @DisplayName("a segment's padding-x is 12, at either density")
        void segmentPadding() {
            assertEquals(Length.points(12), styleOf("option").padding().left());
            assertEquals(
                    Length.points(0),
                    styleOf("option").padding().top(),
                    "the height comes from the bar, so a segment must not add to it");
        }

        /// The fill is one box that travels, so a hover cannot be an opaque fill any
        /// more: a segment is painted *after* the indicator, and an opaque hover on
        /// the selected one would cover the pill that just arrived there — worse,
        /// clicking a new segment would paint the destination fill instantly and beat
        /// the animation to it. Both states are a translucent wash instead.
        @Test
        @DisplayName("a segment's own fill is a wash, so it never covers the pill")
        void hoverIsAWash() {
            assertEquals(0, alpha(styleOf("option").background()), "a segment at rest paints nothing");
            assertEquals(
                    0,
                    alpha(styleOf("option", Selector.PseudoClass.CHECKED).background()),
                    "and a selected one paints nothing either: the fill is the indicator's");

            for (var state : List.of(Selector.PseudoClass.HOVER, Selector.PseudoClass.ACTIVE)) {
                var wash = styleOf("option", state).background();
                assertTrue(
                        alpha(wash) > 0 && alpha(wash) < 255,
                        "a segment's " + state + " must be translucent, or it would hide the pill: "
                                + Integer.toHexString(wash));
            }
        }

        /// The pill itself: the one box in the control that carries a colour, and the
        /// one the design system's `--gb-segmented-selected-bg` names.
        @Test
        @DisplayName("the indicator carries the selection's fill")
        void indicatorIsTheFill() {
            var pill = styleOf("segmented-indicator");

            assertEquals(255, alpha(pill.background()), "the pill is opaque");
            assertEquals(
                    Corners.all(7),
                    pill.decoration().corners(),
                    "the bar's 8 less its border, before the track squares what is not an end");
            assertEquals(0.0, pill.opacity(), "and invisible until something is selected");
            assertEquals(
                    1.0,
                    styleOf("segmented-indicator", Selector.PseudoClass.CHECKED).opacity());
        }

        /// The selected segment's foreground is the **fill's** and not the
        /// theme's, which is what `ContrastTest` measures. This is the
        /// cheaper half of the same claim: that the rule reaches the label at all.
        @Test
        @DisplayName("the selection carries its own foreground")
        void selectionPinsItsForeground() {
            assertNotEquals(
                    styleOf("option").color(),
                    styleOf("option", Selector.PseudoClass.CHECKED).color());
        }

        /// The design system's row for the indicator, built: it `translate`s
        /// between segments on **base**. The `width` half is absent and cannot
        /// arrive — it is not a property the toolkit can transition — and on a
        /// grid it never changes, because every cell is the same size.
        @Test
        @DisplayName("what moves is the indicator's transform, on the component duration")
        void motion() {
            var pill = styleOf("segmented-indicator").transitions();

            var travel = pill.get(Transitions.Animatable.TRANSFORM);
            assertNotNull(travel, "the indicator must travel between segments, not snap");
            assertEquals(160, travel.durationMillis(), 0.001, "--gb-motion-base");
            assertEquals(
                    100,
                    pill.get(Transitions.Animatable.OPACITY).durationMillis(),
                    0.001,
                    "a pill appearing is a state change, not a movement: --gb-motion-fast");

            // The label's colour moves with it, because a selected segment's
            // foreground is picked for the fill it sits on.
            assertNotNull(styleOf("option").transitions().get(Transitions.Animatable.COLOR));

            // And `width` is not in `Animatable` at all, which is why the design system's row
            // could only ever be half built as written.
            assertNull(Transitions.Animatable.parse("width"));
        }

        /// **A label longer than 1/n of the bar is cut**, which is what this
        /// control's own comment said it could not do.
        ///
        /// The cells are equal by construction — `SegmentedTrack` sizes them,
        /// because no selector can count segments — so a label wider
        /// than its cell is the ordinary case rather than an edge one. It used to
        /// overflow, and not because nothing clips: a box with text is a measured
        /// leaf, so narrowing it re-measured the paragraph and **wrapped** it:
        /// `text-overflow` cannot cut a line that `white-space` has let wrap.
        /// `white-space: nowrap` is what stops the re-measure.
        @Test
        @DisplayName("a segment's label is one line, cut where it does not fit")
        void labelsAreCutRatherThanWrapped() {
            var option = styleOf("option");

            assertFalse(option.whiteSpace().wraps(), "a wrapped label in an equal-width cell is two lines in one");
            assertTrue(option.textFlow().ellipsises(), "and the cut says it happened");
        }

        // ------------------------------------------------------------ helpers

        private static ComputedStyle styleOf(String type, Selector.PseudoClass... states) {
            return styleOf(type, Set.of(), states);
        }

        private static ComputedStyle styleOf(String type, Set<String> classes, Selector.PseudoClass... states) {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            return ComputedStyle.of(
                    resolver.resolve(new Probe(type, classes, Set.of(states))), CssLength.Context.DEFAULT);
        }

        /// A node that exists only to be styled, exactly as `DensityTest`'s does —
        /// with the classes and the states it is in, because the rules under test
        /// are ordered against each other by both.
        private record Probe(String type, Set<String> classes, Set<Selector.PseudoClass> states)
                implements StyleElement {

            @Override
            public String id() {
                return null;
            }

            @Override
            public StyleElement parent() {
                return null;
            }

            @Override
            public boolean hasState(Selector.PseudoClass state) {
                return states.contains(state);
            }
        }
    }

    @Nested
    @DisplayName("the grid the indicator travels on")
    class Geometry {

        private TestFrames.Target target;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
        }

        @AfterEach
        void tearDown() {
            if (target != null) {
                target.end();
            }
        }

        /// Where every box in a laid-out bar is actually **drawn** — the
        /// rectangle Yoga produced, with the matrix the painter will apply to it.
        ///
        /// Not [HitTest]'s regions, and the difference is the whole subject here:
        /// those carry the *untransformed* rectangle and the matrix beside it,
        /// because a transform costs no layout and moves no sibling.
        /// An indicator that has travelled two segments is still laid out at the
        /// first one, so a test that read the layout alone would say the pill
        /// never moves — and would have passed before any of this was built.
        private List<Drawn> drawn(Widget bar, int width) {
            target = TestFrames.of(width, 60, 1.0f, 0);
            var renderer =
                    new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            var tree = new ElementTree(bar);
            var out = new ArrayList<Drawn>();
            try (var render = RenderTree.create()) {
                render.update(target.frame(), renderer.render(tree));
                render.forEachPlacedBox(placed -> {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    var type = placed.box().owner() instanceof Element element ? element.type() : null;
                    out.add(new Drawn(
                            type,
                            matrix.a() * layout.left() + matrix.c() * layout.top() + matrix.e(),
                            matrix.a() * layout.width(),
                            placed.box()));
                });
            }
            return List.copyOf(out);
        }

        /// One box as the screen receives it: its type, its left edge and its
        /// width — both after the matrix — and the box itself, for the questions
        /// that are about paint rather than place.
        private record Drawn(String type, double left, double width, Box box) {}

        /// Four segments, so that "between" is a cell with a neighbour on both
        /// sides and not merely the other end.
        private static Segmented bar(String selected) {
            return new Segmented(
                    selected,
                    List.of(
                            new Option("list", "List"), new Option("grid", "Grid"),
                            new Option("map", "Map"), new Option("sat", "Satellite")),
                    null,
                    null,
                    false,
                    Attributes.NONE);
        }

        /// A radius of 8 outside and 0 between, on the cells: the bar's own 8 less
        /// its 1px border at the two ends of the row, and nothing in between.
        @Test
        @DisplayName("a segment is round only at the ends of the row")
        void cellsAreRoundOnlyAtTheEnds() {
            var cells = ofType(drawn(bar("grid"), 400), "option");

            assertEquals(4, cells.size());
            assertEquals(
                    new Corners(7, 0, 0, 7), cells.getFirst().box().decoration().corners());
            assertEquals(Corners.SQUARE, cells.get(1).box().decoration().corners());
            assertEquals(Corners.SQUARE, cells.get(2).box().decoration().corners());
            assertEquals(
                    new Corners(0, 7, 7, 0), cells.getLast().box().decoration().corners());
        }

        /// And the same rule on the fill that moves, which is what keeps the
        /// drawing right at both ends of a travel.
        @Test
        @DisplayName("the pill takes the corners of the cell it is on")
        void pillTakesItsCellsCorners() {
            assertEquals(
                    new Corners(7, 0, 0, 7),
                    ofType(drawn(bar("list"), 400), "segmented-indicator")
                            .getFirst()
                            .box()
                            .decoration()
                            .corners());
            assertEquals(
                    Corners.SQUARE,
                    ofType(drawn(bar("grid"), 400), "segmented-indicator")
                            .getFirst()
                            .box()
                            .decoration()
                            .corners());
            assertEquals(
                    new Corners(0, 7, 7, 0),
                    ofType(drawn(bar("sat"), 400), "segmented-indicator")
                            .getFirst()
                            .box()
                            .decoration()
                            .corners());
        }

        /// One hairline per boundary, each exactly where two cells meet — which
        /// is the claim that says a percentage of the track and the grid the
        /// cells are laid on are the same measurement.
        @Test
        @DisplayName("a hairline sits where two cells meet, one per boundary")
        void hairlinesSitOnTheBoundaries() {
            var boxes = drawn(bar("grid"), 400);
            var cells = ofType(boxes, "option");
            var lines = ofType(boxes, "segmented-divider");

            assertEquals(cells.size() - 1, lines.size(), "one gap fewer than there are cells");
            for (var gap = 0; gap < lines.size(); gap++) {
                assertEquals(
                        cells.get(gap + 1).left(),
                        lines.get(gap).left(),
                        0.5,
                        "hairline " + gap + " is not on the seam it divides");
                assertEquals(1, lines.get(gap).width(), 1e-9, "a divider is a 1px hairline");
            }
        }

        /// The two beside the selection are faded out: the pill covers the seam on
        /// its left and abuts the one on its right, and a line there would draw a
        /// boundary the selection already is.
        @Test
        @DisplayName("the hairlines beside the selection are invisible, and the rest are not")
        void hairlinesBesideTheSelectionAreHidden() {
            // Read as the **fill that reaches the screen** rather than as the
            // node's `opacity`: the render tree multiplies opacity down the
            // subtree into the colours themselves, so a box that has
            // been faded out carries a transparent background and an opacity of 1.
            var lines = ofType(drawn(bar("grid"), 400), "segmented-divider");

            assertEquals(0, alpha(lines.get(0).box().background()), "left of the pill");
            assertEquals(0, alpha(lines.get(1).box().background()), "right of the pill");
            assertEquals(255, alpha(lines.get(2).box().background()), "and the far one still shows");
        }

        /// A bar whose value matches no segment is a real state — a model that has
        /// not loaded — and it is the only one in which every hairline shows.
        @Test
        @DisplayName("nothing selected leaves every hairline drawn")
        void nothingSelectedShowsThemAll() {
            var lines = ofType(drawn(bar(null), 400), "segmented-divider");

            assertEquals(3, lines.size());
            assertTrue(
                    lines.stream().allMatch(line -> alpha(line.box().background()) == 255),
                    "a bar with no selection has nothing to hide a hairline for");
        }

        private static List<Drawn> ofType(List<Drawn> boxes, String type) {
            return boxes.stream().filter(box -> type.equals(box.type())).toList();
        }
    }

    @Nested
    @DisplayName("stylesheets and registries agree")
    class Catalog {

        @Test
        @DisplayName("every type the catalog claims has a rule that styles it")
        void styled() {
            // `segmented` and `option` both paint nothing without one, which is
            // the failure `Controls` exists to make impossible.
            var css = Controls.baseSource();
            assertTrue(css.contains("\nsegmented {"), "the bar has no rule");
            assertTrue(css.contains("\noption {"), "the segment has no rule");
        }

        @Test
        @DisplayName("a stylesheet cannot flip the bar into a column")
        void noAxisClass() {
            // `radio-group.inline` exists because a radio group has no axis of its
            // own. A bar has one, focusScope() answers to it, and a class
            // that turned the bar vertical would make the two disagree with no
            // way for input to know.
            assertFalse(Controls.baseSource().contains("segmented.vertical"));
            assertFalse(Controls.baseSource().contains("segmented.inline"));
        }
    }
}
