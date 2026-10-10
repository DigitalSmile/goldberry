package dev.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
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
import dev.goldberry.widgets.core.scroll.ScrollAnchor;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.core.scroll.ScrollController;
import dev.goldberry.widgets.text.Text;

/// A list virtualized over [RowHeights] — rows that are not all one height,
/// measured as they are built and counted at an estimate until then.
///
/// Everything is read off a painted frame for [ListVirtualTest]'s reason, and the
/// statements about rows not moving are read off **where a row was drawn**, for
/// `ScrollTimelineTest`'s: a reader looks at a line, and "it did not jump" means
/// that number did not change.
class ListMeasuredTest {

    private static final int VIEWPORT_HEIGHT = 300;

    /// What one notch of the wheel moves: three lines of `--gb-scroll-line`'s
    /// twenty pixels.
    private static final double NOTCH = 60;

    /// Five thousand, the timeline the entry measured.
    private static final int COUNT = 5_000;

    /// How near two painted positions count as the same — Yoga's floats, and
    /// every row a whole number of pixels, so this is noise and not tolerance.
    private static final double STILL = 0.5;

    /// Five heights, all whole pixels and all above the row token's 32, so a
    /// row is as tall as its class says.
    private static final int[] HEIGHTS = {40, 60, 80, 100, 120};

    private static final String SCENE = """
            list { width: 200px }
            text.h40 { height: 40px }
            text.h60 { height: 60px }
            text.h80 { height: 80px }
            text.h100 { height: 100px }
            text.h120 { height: 120px }
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

    /// The model: messages by name, each with a height, and the two ways a
    /// timeline changes.
    private static final class Feed {

        private final List<String> rows = new ArrayList<>();
        private final Map<String, Integer> heights = new HashMap<>();
        private Runnable listener = () -> {};
        private int older;

        /// How many times the factory was asked for a row, and for which.
        private int built;

        private final Set<String> everBuilt = new HashSet<>();

        Feed(int count) {
            for (var i = 0; i < count; i++) {
                add(rows.size(), "Row " + i, i);
            }
        }

        private void add(int at, String name, int seed) {
            rows.add(at, name);
            // A spread that is not periodic in any small window, so no run of
            // rows happens to average out to the estimate.
            heights.put(name, HEIGHTS[Math.floorMod(seed * 7 + seed / 3, HEIGHTS.length)]);
        }

        void attach(Runnable value) {
            listener = value;
        }

        void append() {
            add(rows.size(), "New " + rows.size(), rows.size());
            listener.run();
        }

        void prepend(int count) {
            for (var i = 0; i < count; i++) {
                var seed = older++;
                add(0, "Older " + seed, seed);
            }
            listener.run();
        }

        /// A row's content changes height — a picture arriving in a message.
        void grow(String row, int height) {
            heights.put(row, height);
            listener.run();
        }

        Widget row(String name) {
            built++;
            everBuilt.add(name);
            return new Text(name, Attributes.NONE.classes("h" + heights.get(name)));
        }

        int height(String name) {
            return heights.get(name);
        }
    }

    /// A `scroll` over a measured list of a [Feed].
    private record Timeline(Feed feed, ScrollAnchor anchor, double estimate, ScrollController controller)
            implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new TimelineState();
        }

        static final class TimelineState extends State<Timeline> {

            @Override
            protected void initState() {
                widget().feed().attach(() -> setState(() -> {}));
            }

            @Override
            public Widget build(BuildContext context) {
                var feed = widget().feed();
                var list = new ListView<>(List.copyOf(feed.rows), name -> name, feed::row)
                        .text(name -> name)
                        .virtualized(RowHeights.estimating(widget().estimate()))
                        .id("rows");
                return new Scroll(List.of(list), ScrollAxis.VERTICAL, Attributes.NONE)
                        .anchor(widget().anchor())
                        .controlledBy(widget().controller());
            }
        }
    }

    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();
        private final TestHost host = new TestHost();
        private final ScrollController controller = new ScrollController();
        private final Feed feed;

        Harness(Feed feed, ScrollAnchor anchor, double estimate) {
            this.feed = feed;
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            Theme.NORD_DARK.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                    TestFont.get());
            tree = new ElementTree(new Timeline(feed, anchor, estimate, controller), host);
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

        /// Enough frames for the window, the measurements and the corrections
        /// they ask for to have landed — each is a frame behind the one before.
        void settle() {
            for (var i = 0; i < 8; i++) {
                frame();
            }
        }

        void wheel(float lines) {
            router.pointerWheel(100, VIEWPORT_HEIGHT / 2f, 0, lines, Modifiers.NONE);
            settle();
        }

        /// The rows built, by item name.
        List<String> rows() {
            var out = new ArrayList<String>();
            collect(tree.root(), "list-row", element -> out.add(element.id().substring("rows-".length())));
            return out;
        }

        /// Where the row for `name` was drawn, top and bottom, in the window's
        /// coordinates; null when it is not built.
        double @Nullable [] drawn(String name) {
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

        double top(String name) {
            var at = drawn(name);
            assertTrue(at != null, () -> name + " is not built; the window holds " + rows());
            return at[0];
        }

        /// The list's own height, which is the scroll extent.
        double listHeight() {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element && "list".equals(element.type())) {
                    found.add((double) placed.layout().height());
                }
            });
            assertEquals(1, found.size());
            return found.getFirst();
        }

        /// The first row drawn wholly inside the viewport — the reader's line.
        String firstWhole() {
            String best = null;
            var bestTop = Double.MAX_VALUE;
            for (var name : rows()) {
                var at = drawn(name);
                if (at != null && at[0] >= -STILL && at[0] < bestTop) {
                    best = name;
                    bestTop = at[0];
                }
            }
            assertTrue(best != null, "no row is drawn inside the viewport");
            return best;
        }

        MeasuredRow row(String name) {
            var found = new ArrayList<Element>();
            collect(tree.root(), "list-row", element -> {
                if (("rows-" + name).equals(element.id())) {
                    found.add(element);
                }
            });
            assertEquals(1, found.size(), () -> "no row " + name + " among " + rows());
            return (MeasuredRow) found.getFirst().widget();
        }

        void press(String row, Key key) {
            row(row).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
            reachLands();
        }

        void type(String row, String text) {
            row(row).onText(new TextEvent(text, null));
            reachLands();
        }

        /// A reach is a rebuild and then a focus on the loop's timer — and here
        /// the frames between, which is where the rows are measured.
        private void reachLands() {
            frame();
            host.tick();
            settle();
            host.tick();
            settle();
        }
    }

    private static void collect(Element from, String type, java.util.function.Consumer<Element> to) {
        if (type.equals(from.type())) {
            to.accept(from);
        }
        for (var child : from.children()) {
            collect(child, type, to);
        }
    }

    @Nested
    @DisplayName("the window")
    class Window {

        @Test
        @DisplayName("five thousand rows of five heights build a screenful, not five thousand")
        void onlyAScreenful() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);

            var built = harness.rows().size();
            assertTrue(built > 0 && built < 30, () -> "expected a screenful of rows, built " + built);
            assertTrue(feed.built < 200, () -> "the factory was asked for " + feed.built + " rows to open the list");
        }

        @Test
        @DisplayName("and a frame while scrolling builds what came into view, not the model")
        void scrollingBuildsTheWindow() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);

            for (var turn = 0; turn < 10; turn++) {
                feed.built = 0;
                harness.router.pointerWheel(100, VIEWPORT_HEIGHT / 2f, 0, 30, Modifiers.NONE);
                harness.frame();
                harness.frame();
                harness.frame();
                var asked = feed.built;
                // A whole rebuild of the window per frame is the most a frame
                // may cost: three frames, each at most a window.
                assertTrue(asked < 3 * 30, () -> "three frames asked the factory for " + asked + " rows");
                var built = harness.rows().size();
                assertTrue(built < 30, () -> "the window grew to " + built + " rows");
            }
            assertTrue(!harness.rows().contains("Row 0"), "the window did not move");
        }

        @Test
        @DisplayName("the extent is the measured rows plus the estimate for the rest")
        void extentIsMeasuredPlusEstimated() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);
            harness.wheel(200);

            var measured = 0.0;
            for (var name : feed.everBuilt) {
                measured += feed.height(name);
            }
            var expected = measured + (COUNT - feed.everBuilt.size()) * 64.0;

            assertEquals(expected, harness.listHeight(), STILL);
        }

        @Test
        @DisplayName("it settles — a frame that changes nothing builds nothing")
        void itTerminates() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);
            harness.wheel(120);
            var settled = harness.rows();

            feed.built = 0;
            harness.settle();

            assertEquals(settled, harness.rows());
            assertEquals(0, feed.built, "a still list kept rebuilding its rows");
        }

        @Test
        @DisplayName("the rows say they are measured, which is what the stylesheet sizes them by")
        void rowsAreMeasured() {
            var harness = new Harness(new Feed(10), ScrollAnchor.START, 64);

            assertTrue(harness.row("Row 0").classes().contains(MeasuredRow.CLASS));
            var drawn = harness.drawn("Row 1");
            assertTrue(drawn != null);
            assertEquals(
                    harness.feed.height("Row 1"), drawn[1] - drawn[0], STILL, "a row was not its content's height");
        }
    }

    @Nested
    @DisplayName("an estimate that is wrong")
    class WrongEstimate {

        /// Rows of 40 to 120 counted at 20: every row built above the reader's
        /// line is between twenty and a hundred pixels taller than it was
        /// counted, and none of that may reach the screen.
        @Test
        @DisplayName("does not move the row the reader is on as the rows above it are measured")
        void scrollingUpKeepsTheLine() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 20);
            // Far down, onto rows nothing has measured: a notch is three lines
            // of twenty, so this is sixty thousand pixels into a hundred
            // thousand counted.
            harness.wheel(1000);
            var line = harness.firstWhole();
            var before = harness.top(line);

            // Up by a little, so the rows above come into the window and are
            // measured: the line moves by what the wheel moved and by nothing
            // the estimate got wrong.
            harness.wheel(-3);
            var corrected = harness.top(line);

            assertEquals(before + NOTCH * 3, corrected, STILL, "the reader's line moved by more than the wheel did");
        }

        @Test
        @DisplayName("and a row above the line that grows moves the viewport, not the line")
        void aRowThatGrowsAboveTheLine() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);
            harness.wheel(150);
            var line = harness.firstWhole();
            var before = harness.top(line);
            var rows = harness.rows();
            var above = rows.get(rows.indexOf(line) - 1);

            feed.grow(above, feed.height(above) == 120 ? 40 : 120);
            harness.settle();

            assertEquals(before, harness.top(line), STILL, "a row above the line changing height moved the line");
        }

        @Test
        @DisplayName("and below the line it moves nothing at all")
        void aRowThatGrowsBelowTheLine() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);
            harness.wheel(150);
            var line = harness.firstWhole();
            var before = harness.top(line);
            var offset = harness.controller.position().offsetY();
            var rows = harness.rows();
            var below = rows.get(rows.indexOf(line) + 1);

            feed.grow(below, feed.height(below) == 120 ? 40 : 120);
            harness.settle();

            assertEquals(before, harness.top(line), STILL);
            assertEquals(offset, harness.controller.position().offsetY(), STILL, "the viewport moved for a row below");
        }
    }

    @Nested
    @DisplayName("reaching a row")
    class Reaching {

        @Test
        @DisplayName("a typeahead to a row thousands down lands it at the top, and it stays there")
        void typeaheadLandsTheRow() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 20);

            harness.type("Row 0", "Row 2500");

            assertEquals(List.of("rows-Row 2500"), harness.host.focusRequests());
            assertEquals(0, harness.top("Row 2500"), STILL, "the row reached was not put at the top edge");
            var built = harness.rows().size();
            assertTrue(built < 30, () -> "reaching a row built " + built + " rows");
            assertTrue(feed.built < 400, () -> "the factory was asked for " + feed.built + " rows");
        }

        @Test
        @DisplayName("End reaches the last row and the viewport ends on it")
        void endReachesTheLastRow() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);

            harness.press("Row 0", Key.END);

            assertEquals(List.of("rows-Row 4999"), harness.host.focusRequests());
            var drawn = harness.drawn("Row 4999");
            assertTrue(drawn != null, "the last row is not built");
            assertEquals(VIEWPORT_HEIGHT, drawn[1], STILL, "the last row does not end at the viewport's bottom");
        }

        @Test
        @DisplayName("Home comes back to the first row")
        void homeReachesTheFirstRow() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.START, 64);
            harness.wheel(600);
            harness.host.forgetFocusRequests();

            harness.press(harness.rows().get(5), Key.HOME);

            assertEquals(List.of("rows-Row 0"), harness.host.focusRequests());
            assertEquals(0, harness.top("Row 0"), STILL);
        }
    }

    @Nested
    @DisplayName("a timeline")
    class Timelines {

        @Test
        @DisplayName("opens at its end")
        void opensAtTheEnd() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.END, 64);

            var drawn = harness.drawn("Row 4999");
            assertTrue(drawn != null, () -> "the last row is not built; the window holds " + harness.rows());
            assertEquals(VIEWPORT_HEIGHT, drawn[1], STILL);
            assertTrue(harness.rows().size() < 30);
        }

        @Test
        @DisplayName("stays at the end when a message is appended")
        void appendStaysPinned() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.END, 64);

            feed.append();
            harness.settle();

            var drawn = harness.drawn("New 5000");
            assertTrue(drawn != null, "the new message is not built");
            assertEquals(VIEWPORT_HEIGHT, drawn[1], STILL, "a message arriving at the end left the viewport behind");
        }

        @Test
        @DisplayName("keeps the reader's line when history is prepended above it")
        void prependKeepsTheLine() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.END, 64);
            harness.wheel(-40);
            var line = harness.firstWhole();
            var before = harness.top(line);

            feed.prepend(200);
            harness.settle();

            assertEquals(before, harness.top(line), STILL, "a page of history moved the line being read");
        }

        @Test
        @DisplayName("and keeps it when the history above turns out a different height than counted")
        void prependedRowsMeasuredKeepTheLine() {
            var feed = new Feed(COUNT);
            var harness = new Harness(feed, ScrollAnchor.END, 20);
            harness.wheel(-40);
            var line = harness.firstWhole();
            var before = harness.top(line);

            // Up past the line a little, so rows above it are built and measured
            // against an estimate a fifth of their height, then back.
            harness.wheel(-3);
            harness.wheel(3);

            assertEquals(before, harness.top(line), STILL);
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refusals {

        @Test
        @DisplayName("an estimate that is not a positive number")
        void estimate() {
            assertThrows(IllegalArgumentException.class, () -> RowHeights.estimating(0));
            assertThrows(IllegalArgumentException.class, () -> RowHeights.estimating(-4));
            assertThrows(IllegalArgumentException.class, () -> RowHeights.estimating(Double.NaN));
        }

        @Test
        @DisplayName("and the fixed forms put the measuring away again")
        void fixedFormsClearIt() {
            var measured = ListView.of(List.of("a")).virtualized(RowHeights.estimating(40));
            assertEquals(new RowHeights(40), measured.rowHeights());
            assertEquals(null, measured.virtualized(32).rowHeights());
            assertEquals(null, measured.virtualized().rowHeights());
        }
    }
}
