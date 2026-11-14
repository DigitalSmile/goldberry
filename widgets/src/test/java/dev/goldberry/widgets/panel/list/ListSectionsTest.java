package dev.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.core.scroll.ScrollController;
import dev.goldberry.widgets.text.Text;

/// A list in sections: a header above each section's first row, and one pinned
/// over the viewport's top that the next section's header pushes out.
///
/// Read off a painted frame, for [ListMeasuredTest]'s reason: what a reader
/// sees is where a header was drawn.
class ListSectionsTest {

    private static final int VIEWPORT_HEIGHT = 300;

    /// Twenty sections of ten rows.
    private static final int SECTIONS = 20;

    private static final int PER_SECTION = 10;

    private static final int ROW = 40;

    private static final int HEADER = 24;

    private static final double STILL = 0.5;

    private static final String SCENE = """
            list { width: 200px }
            text.row { height: 40px }
            list-section-header { min-height: 0; height: 24px; padding: 0 }
            """;

    private TestFrames.Target target;
    private RenderTree render;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        if (render != null) {
            render.close();
            render = null;
        }
        if (target != null) {
            target.end();
            target = null;
        }
    }

    private static List<String> items() {
        var items = new ArrayList<String>();
        for (var i = 0; i < SECTIONS * PER_SECTION; i++) {
            items.add("Item " + i);
        }
        return items;
    }

    private static String dayOf(String item) {
        return "Day " + Integer.parseInt(item.substring("Item ".length())) / PER_SECTION;
    }

    /// How the list is told its rows' heights.
    private enum Mode {
        MEASURED,
        PITCH,
        ALL
    }

    private record Timeline(Mode mode, ScrollController controller) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new State<Timeline>() {

                @Override
                public Widget build(BuildContext context) {
                    var list = new ListView<>(
                                    items(), item -> item, item -> new Text(item, Attributes.NONE.classes("row")))
                            .text(item -> item)
                            .sections(ListSectionsTest::dayOf, day -> new Text(day))
                            .id("rows");
                    list = switch (widget().mode()) {
                        case MEASURED -> list.virtualized(RowHeights.estimating(ROW));
                        case PITCH -> list.virtualized(ROW);
                        case ALL -> list;
                    };
                    return new Scroll(List.of(list), ScrollAxis.VERTICAL, Attributes.NONE)
                            .controlledBy(widget().controller());
                }
            };
        }
    }

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();
        private final TestHost host = new TestHost();
        private final ScrollController controller = new ScrollController();

        /// The frame clock, which a programmatic scroll glides on.
        private final Clock.Virtual clock = Clock.virtual();

        Harness(Mode mode) {
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            Theme.NORD_DARK.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                    TestFont.get())
                    .clock(clock);
            tree = new ElementTree(new Timeline(mode, controller), host);
            render = RenderTree.create();
            router.focusRoot(tree.root());
            router.windowBounds(LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            settle();
        }

        void frame() {
            renderer.prepare(tree);
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        void settle() {
            for (var i = 0; i < 8; i++) {
                frame();
            }
        }

        /// Scrolls by `dy` and lets the glide arrive.
        void scrollBy(double dy) {
            controller.scrollBy(0, dy);
            frame();
            clock.advance(5_000);
            settle();
        }

        /// Where each node of `type` whose text is `text` was drawn, top first.
        List<double[]> drawn(String type, @Nullable String classed) {
            var found = new ArrayList<double[]>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && type.equals(element.type())
                        && (classed == null || element.classes().contains(classed))) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    var top = matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f();
                    found.add(new double[] {top, top + layout.height()});
                }
            });
            return found;
        }

        /// Where the row for `name` was drawn, or null.
        double @Nullable [] row(String name) {
            var found = new ArrayList<double[]>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && "list-row".equals(element.type())
                        && ("rows-" + name).equals(element.id())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    var top = matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f();
                    found.add(new double[] {top, top + layout.height()});
                }
            });
            return found.isEmpty() ? null : found.getFirst();
        }

        /// The header elements, in tree order.
        List<ListSectionHeader> headers() {
            var out = new ArrayList<ListSectionHeader>();
            collect(tree.root(), "list-section-header", element -> out.add((ListSectionHeader) element.widget()));
            return out;
        }

        @Nullable ListSectionHeader pinned() {
            return headers().stream().filter(ListSectionHeader::pinned).findFirst().orElse(null);
        }

        double listHeight() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && "list".equals(element.type())) {
                    found.add((double) placed.layout().height());
                }
            });
            return found.getFirst();
        }

        List<String> rows() {
            var out = new ArrayList<String>();
            collect(tree.root(), "list-row", element -> out.add(element.id().substring("rows-".length())));
            return out;
        }

        void press(String row, Key key) {
            var found = new ArrayList<Element>();
            collect(tree.root(), "list-row", element -> {
                if (("rows-" + row).equals(element.id())) {
                    found.add(element);
                }
            });
            var widget = found.getFirst().widget();
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
            if (widget instanceof MeasuredRow measured) {
                measured.onKey(event);
            } else {
                ((ListRow) widget).onKey(event);
            }
            for (var i = 0; i < 3; i++) {
                frame();
                host.tick();
                settle();
            }
        }
    }

    private static void collect(Element from, String type, Consumer<Element> to) {
        if (type.equals(from.type())) {
            to.accept(from);
        }
        for (var child : from.children()) {
            collect(child, type, to);
        }
    }

    @Nested
    @DisplayName("a measured list")
    class Measured {

        @Test
        @DisplayName("draws each section's header above its first row")
        void headerAboveFirstRow() {
            var harness = new Harness(Mode.MEASURED);
            var first = harness.row("Item 0");
            assertTrue(first != null);
            assertEquals(HEADER, first[0], STILL, "the first row starts under its header");
            var eleventh = harness.row("Item 10");
            assertTrue(eleventh != null, () -> "built " + harness.rows());
            assertEquals(2 * HEADER + 10 * ROW, eleventh[0], STILL, "the next section's row under the next header");
            assertEquals(null, harness.pinned(), "nothing is pinned while the first header is in view");
        }

        @Test
        @DisplayName("counts every header into the list's height")
        void height() {
            var harness = new Harness(Mode.MEASURED);
            assertEquals(SECTIONS * HEADER + SECTIONS * PER_SECTION * ROW, harness.listHeight(), STILL);
        }

        @Test
        @DisplayName("pins the top section's header to the viewport's top")
        void pins() {
            var harness = new Harness(Mode.MEASURED);
            harness.scrollBy(HEADER + 3 * ROW);

            var pinned = harness.pinned();
            assertTrue(pinned != null, "a header is pinned once its own has scrolled away");
            assertEquals("Day 0", pinned.section());
            var drawn = harness.drawn("list-section-header", ListSectionHeader.PINNED);
            assertEquals(1, drawn.size());
            assertEquals(0, drawn.getFirst()[0], STILL, "at the viewport's top edge");
        }

        @Test
        @DisplayName("lets the next section's header push the pinned one out")
        void pushes() {
            var harness = new Harness(Mode.MEASURED);
            // The next header's top is HEADER + 10 rows down; stop ten pixels
            // short of it reaching the viewport's top.
            var nextHeader = HEADER + PER_SECTION * ROW;
            harness.scrollBy(nextHeader - 10);

            var drawn = harness.drawn("list-section-header", ListSectionHeader.PINNED);
            assertEquals(1, drawn.size());
            assertEquals(10 - HEADER, drawn.getFirst()[0], STILL, "pushed up by the next header's arrival");
            assertEquals("Day 0", harness.pinned().section());

            harness.scrollBy(20);
            assertEquals("Day 1", harness.pinned().section(), "and the next one takes over");
            assertEquals(
                    0,
                    harness.drawn("list-section-header", ListSectionHeader.PINNED).getFirst()[0],
                    STILL);
        }

        @Test
        @DisplayName("keeps headers out of the rows: End goes to the last row")
        void notRows() {
            var harness = new Harness(Mode.MEASURED);
            assertFalse(harness.rows().stream().anyMatch(name -> name.startsWith("Day")));
            harness.press("Item 0", Key.END);
            assertTrue(harness.rows().contains("Item 199"), () -> "built " + harness.rows());
            assertFalse(
                    Handles.class.isAssignableFrom(ListSectionHeader.class), "a header takes no focus and no input");
        }
    }

    @Test
    @DisplayName("a list virtualized at one height measures once it has sections")
    void pitchMeasures() {
        var harness = new Harness(Mode.PITCH);
        var first = harness.row("Item 0");
        assertTrue(first != null);
        assertEquals(HEADER, first[0], STILL);
        var rows = new ArrayList<Element>();
        collect(harness.tree.root(), "list-row", rows::add);
        assertTrue(rows.getFirst().widget() instanceof MeasuredRow, "its rows say what height they came out as");
    }

    @Test
    @DisplayName("a list that builds every row puts each section in a box its header is pinned inside")
    void builtSections() {
        var harness = new Harness(Mode.ALL);
        var sections = new ArrayList<Element>();
        collect(harness.tree.root(), "list-section", sections::add);
        assertEquals(SECTIONS, sections.size());
        var affixes = new ArrayList<Element>();
        collect(sections.getFirst(), "affix", affixes::add);
        assertEquals(1, affixes.size(), "its header, in an affix");
        assertEquals(SECTIONS * PER_SECTION, harness.rows().size());
        // A row of a list that builds them all is the row token's height.
        assertEquals(SECTIONS * HEADER + SECTIONS * PER_SECTION * 32, harness.listHeight(), STILL);
    }
}
