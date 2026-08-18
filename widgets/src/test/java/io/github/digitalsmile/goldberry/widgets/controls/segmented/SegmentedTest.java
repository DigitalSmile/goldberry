package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.Bindings;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.Transitions;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Actions;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The eleventh control, and the second composite.
///
/// [io.github.digitalsmile.goldberry.widgets.controls.radio.RadioTest] proved the
/// composite machinery; this proves the thing `docs/core-widgets.md` §3 asks for
/// that the first composite could not show — that a set's **axis** is a property
/// of the widget and not always of its stylesheet ([ADR-0078]) — and the drawing
/// decisions [ADR-0097](../../../../../../../../book/src/adr/0097-a-selection-that-travels-needs-a-geometry.md)
/// had to make when §3's row turned out to describe something §8's subset cannot
/// express.
class SegmentedTest {

    private static List<Widget> inflate(String markup) {
        return Controls.inflater().inflateAll(KdlParser.parse(markup));
    }

    /// The segments, as the control rewrites them — which is where the
    /// exactly-one invariant is actually applied.
    ///
    /// One level down from the bar now: a `segmented` builds a `segmented-track`
    /// and the track holds the segments and the indicator that runs along them
    /// ([ADR-0099]).
    private static List<Option> options(Segmented bar) {
        return track(bar).segments().stream()
                .filter(Option.class::isInstance).map(Option.class::cast).toList();
    }

    private static SegmentedTrack track(Segmented bar) {
        return (SegmentedTrack) bar.children().getFirst();
    }

    @Nested
    @DisplayName("parity (§11)")
    class Parity {

        @Test
        @DisplayName("the Java-built and KDL-built bars are equal values")
        void javaAndKdlAgree() {
            var fromJava = new Segmented("grid",
                    List.of(new Option("list", "List"), new Option("grid", "Grid")),
                    null, null, false,
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
            assertEquals(new Option("list", "List"),
                    inflate("option value=\"list\" \"List\"").getFirst());
        }

        @Test
        @DisplayName("both are CSS-selectable by type, id and class")
        void selectable() {
            assertEquals("segmented", new Segmented("list").cssType());
            assertEquals("option", new Option("list", "List").cssType());
            assertEquals(Set.of("wide"), new Segmented("list").styled("wide").classes());
        }

        @Test
        @DisplayName("the registry lists both, and `segment` is deliberately not a node")
        void registered() {
            var registered = Controls.inflater().registered();

            assertTrue(registered.contains("segmented"));
            assertTrue(registered.contains("option"));
            // §3 writes `option` in this control and in `select`, so that is the
            // node. A `segment` alias would be a second spelling of one thing.
            assertFalse(registered.contains("segment"));
        }

        @Test
        @DisplayName("a segment without a value is refused at inflation")
        void valueRequired() {
            // Defaulting it to the label would make two segments that happen to
            // share a label select together, which reads as a toolkit bug.
            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> inflate("option \"List\""));
            assertTrue(thrown.getMessage().contains("value="), thrown.getMessage());
        }

        @Test
        @DisplayName("a segment with neither a label nor an icon is refused")
        void contentRequired() {
            // §13: an icon-only segment is legal and a *nothing*-only segment is
            // not -- there would be nothing to click on and nothing to read out.
            assertThrows(IllegalArgumentException.class,
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

            assertEquals(List.of(false, true, false),
                    options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("a value no segment carries selects nothing rather than guessing")
        void unmatchedSelectsNothing() {
            var bar = new Segmented("timeline",
                    new Option("list", "List"), new Option("grid", "Grid"));

            assertEquals(List.of(false, false), options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("a null value selects nothing")
        void nullSelectsNothing() {
            var bar = Segmented.of(Property.of(null), null,
                    new Option("list", "List"), new Option("grid", "Grid"));

            assertNull(bar.resolved());
            assertEquals(List.of(false, false), options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("markup cannot mark a segment selected, so it cannot mark two")
        void markupCannotSelect() {
            var bar = (Segmented) inflate("""
                    segmented {
                        option value="list" selected=#true "List"
                        option value="grid" selected=#true "Grid"
                    }
                    """).getFirst();

            assertEquals(List.of(false, false), options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("a child that is not an option is laid out and left alone")
        void otherChildrenSurvive() {
            var bar = new Segmented("grid",
                    List.of(new Text("View"), new Option("grid", "Grid")), null, null, false, null);

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
    @DisplayName("data down, events up (ADR-0063)")
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
            var bar = Segmented.of(view, value -> { },
                    new Option("list", "List"), new Option("grid", "Grid"));
            var grid = options(bar).get(1);
            var element = new ElementTree(bar).root();

            grid.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("list", bar.resolved());
            assertEquals("list", view.get());
        }

        @Test
        @DisplayName("what the user picked travels up, with the value")
        void changeCarriesTheValue() {
            var view = Property.of("list");
            var bar = Segmented.of(view, view::set,
                    new Option("list", "List"), new Option("grid", "Grid"));
            var element = new ElementTree(bar).root();

            options(bar).get(1).onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals("grid", view.get());
            assertEquals(List.of(false, true), options(bar).stream().map(Option::selected).toList(),
                    "and the new value arrives back down through the binding");
        }

        @Test
        @DisplayName("a bound value that is not a String is compared as the author spelled it")
        void nonStringValue() {
            var theme = Property.of(Theme.NORD_DARK);
            var bar = Segmented.of(theme, null,
                    new Option("NORD_LIGHT", "Light"), new Option("NORD_DARK", "Dark"));

            assertEquals(List.of(false, true), options(bar).stream().map(Option::selected).toList());
        }

        @Test
        @DisplayName("markup names a path and a valued action, and both resolve")
        void fromMarkup() {
            var picked = new ArrayList<String>();
            var view = Property.of("list");
            var bindings = Bindings.strict().bind("view.mode", view);
            var actions = Actions.strict().bind("pickView", (String value) -> picked.add(value));

            var bar = (Segmented) Controls.inflater(actions, Icons.none(), bindings)
                    .inflateAll(KdlParser.parse("""
                            segmented bind="view.mode" change="pickView" {
                                option value="list" "List"
                                option value="grid" "Grid"
                            }
                            """)).getFirst();

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
    @DisplayName("one Tab stop, and the arrows are the bar's own axis (§7.2)")
    class Traversal {

        private final List<String> picked = new ArrayList<>();

        private ElementTree tree() {
            return new ElementTree(new Column(
                    new Button("Before", () -> { }),
                    new Segmented("list", picked::add,
                            new Option("list", "List"),
                            new Option("grid", "Grid"),
                            new Option("map", "Map")),
                    new Button("After", () -> { })));
        }

        /// The router reads `:checked` off the element and the renderer is what
        /// mirrors it there, so a traversal test renders first — exactly as a real
        /// frame does.
        private PointerRouter routed(ElementTree tree) {
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                    TestFont.get()).render(tree);
            var router = new PointerRouter();
            router.focusRoot(tree.root());
            return router;
        }

        /// The bar's element, then its track, then the segment — and `+ 1`
        /// because the track's first child is the indicator, which is painted
        /// under the labels and takes no focus.
        private Element segment(ElementTree tree, int index) {
            return tree.root().children().get(1).children().getFirst().children().get(index + 1);
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
        /// are not its to consume and a scroll view above it must still get them
        /// ([ADR-0078]).
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
            assertFalse(new Segmented("list").isFocusable(),
                    "focus lands on a segment, so the ring is on what the user is about to pick");
        }

        @Test
        @DisplayName("Tab enters at the selected segment")
        void entersAtSelection() {
            var tree = new ElementTree(new Column(
                    new Button("Before", () -> { }),
                    new Segmented("map", picked::add,
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
                    new Button("Before", () -> { }),
                    new Segmented("list", picked::add, new Option("list", "List")).disabled(true),
                    new Button("After", () -> { })));
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
            var bar = new Segmented("list", picked::add,
                    new Option("list", "List"), new Option("grid", "Grid"));
            return options(bar).get(index);
        }

        @Test
        @DisplayName("a click anywhere in the segment picks it")
        void clickPicks() {
            var grid = option(1);
            var element = new ElementTree(grid).root();

            grid.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 40, 16, PointerEvent.Button.PRIMARY, 1, element));

            assertEquals(List.of("grid"), picked);
        }

        @Test
        @DisplayName("Space picks and Enter does not")
        void spaceNotEnter() {
            var grid = option(1);
            var element = new ElementTree(grid).root();

            grid.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, element));
            assertTrue(picked.isEmpty(), "Enter belongs to a dialog's default action (§2.3)");

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
            var bar = new Segmented("list", picked::add,
                    new Option("list", "List"), new Option("grid", "Grid").disabled(true));
            var grid = options(bar).get(1);
            var element = new ElementTree(grid).root();

            grid.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));
            grid.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, element));
            grid.onFocusChanged(true, true);

            assertTrue(picked.isEmpty());
            assertFalse(grid.isFocusable());
        }

        @Test
        @DisplayName("a disabled bar does not mark its segments, and does not need to")
        void barDoesNotPushDisabledDown() {
            // ADR-0077: the flag stays on the node that declared it, the fade
            // multiplies down the subtree by itself, and unavailability
            // propagates through the router. Pushing it would fade twice and
            // land at 20%.
            var bar = new Segmented("list", picked::add,
                    new Option("list", "List"), new Option("grid", "Grid")).disabled(true);

            assertTrue(options(bar).stream().noneMatch(Option::disabled));
        }

        @Test
        @DisplayName("an unwired segment does nothing rather than failing")
        void unwiredIsInert() {
            var loose = new Option("grid", "Grid");
            var element = new ElementTree(loose).root();

            loose.onPointer(new PointerEvent(
                    PointerEvent.Kind.CLICKED, 5, 5, PointerEvent.Button.PRIMARY, 1, element));

            assertNull(loose.onSelect(), "a control styled before it is wired is a normal stage");
        }
    }

    @Nested
    @DisplayName("the drawing §3 asks for, and the two halves it could not have")
    class Drawing {

        /// §3: "radius 8 outer, 0 between". §8's subset resolves **one** radius
        /// per box, so the bar carries the 8 and the segment is inset inside it
        /// and takes §1.5's 4 — see ADR-0097. Pinned, because the day a per-corner
        /// radius exists this is the rule that should be revisited rather than
        /// quietly left behind.
        @Test
        @DisplayName("the bar carries the outer radius and the segment an inset one")
        void radii() {
            assertEquals(8.0, styleOf("segmented").decoration().radius());
            assertEquals(4.0, styleOf("option").decoration().radius());
        }

        /// The inset is what makes the two radii legal together: without it a
        /// square-cornered fill would paint over the bar's curve, and nothing in
        /// this toolkit clips.
        @Test
        @DisplayName("the bar's padding is the inset, and it is on §1.3's ramp")
        void inset() {
            var padding = styleOf("segmented").padding();

            assertEquals(StyleLength.points(2), padding.top());
            assertEquals(StyleLength.points(2), padding.left());
            assertEquals(padding.top(), padding.bottom());
            assertEquals(padding.left(), padding.right());
        }

        /// The one layout property the control asserts, because it is the one
        /// that decides what a bar looks like in a column: given spare width the
        /// segments divide it, rather than huddling at the left of a plate that
        /// stretched without them. `radio-group` answers the same question the
        /// other way and says why.
        /// The grid is the **track's**, not the stylesheet's, and this is what
        /// says so: a cell's width is the one metric of this control that no
        /// selector can write, because no selector can count the segments
        /// (ADR-0099).
        @Test
        @DisplayName("the stylesheet gives a segment every metric except its width")
        void widthIsNotTheStylesheets() {
            var cell = styleOf("option");

            assertEquals(StyleLength.UNDEFINED, cell.width(),
                    "a width here would be a number that cannot know how many cells there are");
            assertEquals(StyleLength.points(12), cell.padding().left(), "§3's padding-x, though");
            assertEquals(0.0, styleOf("segmented").flexGrow(),
                    "the bar itself takes no space it was not given");
            assertEquals(1.0, styleOf("segmented-track").flexGrow(),
                    "but the track fills it, because that is what the cells divide");
        }

        @Test
        @DisplayName("a segment's padding-x is §3's 12, at either density")
        void segmentPadding() {
            assertEquals(StyleLength.points(12), styleOf("option").padding().left());
            assertEquals(StyleLength.points(0), styleOf("option").padding().top(),
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
        assertEquals(0, alpha(styleOf("option", Selector.PseudoClass.CHECKED).background()),
                "and a selected one paints nothing either: the fill is the indicator's");

        for (var state : List.of(Selector.PseudoClass.HOVER, Selector.PseudoClass.ACTIVE)) {
            var wash = styleOf("option", state).background();
            assertTrue(alpha(wash) > 0 && alpha(wash) < 255,
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
        assertEquals(4.0, pill.decoration().radius(), "§1.5's small-control radius, inside the bar's 8");
        assertEquals(0.0, pill.opacity(), "and invisible until something is selected");
        assertEquals(1.0, styleOf("segmented-indicator", Selector.PseudoClass.CHECKED).opacity());
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /// The selected segment's foreground is the **fill's** and not the
        /// theme's (ADR-0087), which is what `ContrastTest` measures. This is the
        /// cheaper half of the same claim: that the rule reaches the label at all.
        @Test
        @DisplayName("the selection carries its own foreground")
        void selectionPinsItsForeground() {
            assertNotEquals(styleOf("option").color(),
                    styleOf("option", Selector.PseudoClass.CHECKED).color());
        }

        /// §3.1's row, built: "selection indicator `translate` … between segments,
    /// **base**". The `width` half is absent and cannot arrive — it is not on
    /// §1.7's whitelist — and on a grid it never changes, because every cell is
    /// the same size ([ADR-0099]).
    @Test
    @DisplayName("what moves is the indicator's transform, on the component duration")
    void motion() {
        var pill = styleOf("segmented-indicator").transitions();

        var travel = pill.get(Transitions.Animatable.TRANSFORM);
        assertNotNull(travel, "an indicator that snapped between segments is not §3.1's row");
        assertEquals(160, travel.durationMillis(), 0.001, "--gb-motion-base");
        assertEquals(100, pill.get(Transitions.Animatable.OPACITY).durationMillis(), 0.001,
                "a pill appearing is a state change, not a movement: --gb-motion-fast");

        // The label's colour moves with it, because a selected segment's
        // foreground is picked for the fill it sits on (ADR-0088).
        assertNotNull(styleOf("option").transitions().get(Transitions.Animatable.COLOR));

        // And `width` is not in `Animatable` at all, which is why §3.1's row
        // could only ever be half built as written.
        assertNull(Transitions.Animatable.parse("width"));
    }

        // ------------------------------------------------------------ helpers

        private static ComputedStyle styleOf(String type, Selector.PseudoClass... states) {
            var resolver = new StyleResolver(Controls.stylesheets(Theme.NORD_DARK));
            return ComputedStyle.of(resolver.resolve(new Probe(type, Set.of(states))),
                    CssLength.Context.DEFAULT);
        }

        /// A node that exists only to be styled, exactly as `DensityTest`'s does —
        /// with the states it is in, because the rules under test are ordered
        /// against each other by pseudo-class.
        private record Probe(String type, Set<Selector.PseudoClass> states)
                implements StyleElement {

            @Override
            public String id() {
                return null;
            }

            @Override
            public Set<String> classes() {
                return Set.of();
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
        /// because a transform costs no layout and moves no sibling (ADR-0068).
        /// An indicator that has travelled two segments is still laid out at the
        /// first one, so a test that read the layout alone would say the pill
        /// never moves — and would have passed before any of this was built.
        private List<Drawn> drawn(Widget bar, int width) {
            target = TestFrames.of(width, 60, 1.0f, 0);
            var renderer = new WidgetRenderer(
                    List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
            var tree = new ElementTree(bar);
            var out = new ArrayList<Drawn>();
            try (var render = RenderTree.create()) {
                render.update(target.frame(), renderer.render(tree));
                render.forEachPlacedBox(placed -> {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    var type = placed.box().owner() instanceof Element element ? element.type() : null;
                    out.add(new Drawn(type,
                            matrix.a() * layout.left() + matrix.c() * layout.top() + matrix.e(),
                            matrix.a() * layout.width()));
                });
            }
            return List.copyOf(out);
        }

        /// One box as the screen receives it: its type, its left edge and its
        /// width, both after the matrix.
        private record Drawn(String type, double left, double width) {
        }

        private static List<Drawn> ofType(List<Drawn> boxes, String type) {
            return boxes.stream().filter(box -> type.equals(box.type())).toList();
        }
    }

    @Nested
    @DisplayName("stylesheets and registries agree")
    class Catalog {

        @Test
        @DisplayName("both types are in the catalog's list")
        void inControlTypes() {
            assertTrue(Controls.controlTypes().contains("segmented"));
            assertTrue(Controls.controlTypes().contains("option"),
                    "a segment is a widget a document writes, not a part");
        }

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
            // `radio-group.inline` exists because §3 gives a group no axis. §3
            // gives this one an axis, focusScope() answers to it, and a class
            // that turned the bar vertical would make the two disagree with no
            // way for input to know.
            assertFalse(Controls.baseSource().contains("segmented.vertical"));
            assertFalse(Controls.baseSource().contains("segmented.inline"));
        }
    }

    @Nested
    @DisplayName("chaining")
    class Chaining {

        @Test
        @DisplayName("every wither keeps the type and copies rather than mutates")
        void withers() {
            var bar = new Segmented("list", new Option("list", "List"));

            assertEquals("view", bar.id("view").id());
            assertEquals(Set.of("wide"), bar.styled("wide").classes());
            assertTrue(bar.disabled(true).disabled());
            assertFalse(bar.disabled(true).disabled(false).disabled());
            assertEquals(Attributes.NONE, bar.attributes(), "the original is untouched");

            var option = new Option("list", "List");
            assertEquals("first", option.id("first").id());
            assertTrue(option.disabled(true).disabled());
            assertFalse(option.disabled(), "the original is untouched");
        }
    }
}
