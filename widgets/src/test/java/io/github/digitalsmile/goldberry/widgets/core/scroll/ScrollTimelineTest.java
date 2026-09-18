package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `scroll anchor="end"` and `preserve-on-prepend` — the viewport a chat, a log
/// and a console all wanted and none of them could write (`docs/gaps.md` G48,
/// ADR-0392).
///
/// Beside [ScrollTest] rather than inside it, because everything here needs a
/// viewport whose **content changes while the window is up**: a timeline is a
/// list a message arrives in, and a fixed root widget cannot be one.
///
/// Everything is read off the paint for [ScrollTest]'s reason. Three of the four
/// statements below are about where a row is drawn, and an offset that is right
/// in the state and wrong on screen is the bug worth looking for.
class ScrollTimelineTest {

    /// A viewport 100 tall, which twenty rows overflow several times over.
    private static final int VIEWPORT_HEIGHT = 100;

    /// How near two painted positions count as the same. A tenth of a pixel:
    /// these are floats out of Yoga and the arithmetic is exact, so this is
    /// noise and not tolerance.
    private static final double STILL = 0.1;

    /// Rows of a whole number of pixels, and five of them to a viewport.
    ///
    /// Not decoration. Yoga snaps every node's position to the pixel grid, so
    /// when the run that was inserted is not a whole number of pixels tall,
    /// different rows below it move by numbers a pixel apart. The **anchor** is
    /// preserved exactly whatever the heights are — the offset moves by the
    /// distance that node moved, which is the snapped one — and a row either
    /// side of it can land a rounded pixel from where it was. That is a fact
    /// about the grid rather than about this widget, so whole rows take it out
    /// of the assertions and leave the arithmetic being tested.
    private static final String ROWS = "text { height: 20px; }";

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

    /// The rows a [Timeline] shows, and the two ways a timeline changes.
    ///
    /// The keys are the whole point of it. Preserving an offset means
    /// recognising a row that was on screen a frame ago, and a list matched by
    /// position has no such row — so the feed hands every line an identity that
    /// does not depend on where in the list it currently is.
    private static final class Feed {

        private final List<String> rows = new ArrayList<>();
        private Runnable listener = () -> {};
        private int older;

        Feed(int count) {
            for (var i = 0; i < count; i++) {
                rows.add("row " + i);
            }
        }

        void attach(Runnable value) {
            listener = value;
        }

        /// A message arrives.
        void append() {
            rows.add("new " + rows.size());
            listener.run();
        }

        /// A page of history is fetched, which puts `count` rows **above**
        /// everything the reader can see.
        void prepend(int count) {
            for (var i = 0; i < count; i++) {
                rows.addFirst("older " + older++);
            }
            listener.run();
        }
    }

    /// A `scroll` over a [Feed] — the shape of every chat timeline.
    private record Timeline(
            Feed feed, ScrollAnchor anchor, @Nullable Boolean preserve) implements Widget.Stateful {

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
                var rows = new ArrayList<Widget>();
                for (var row : widget().feed().rows) {
                    rows.add(new Text(row, Attributes.NONE.key(row)));
                }
                var scroll = new Scroll(rows, ScrollAxis.VERTICAL, Attributes.NONE).anchor(widget().anchor());
                return widget().preserve() == null ? scroll : scroll.preserveOnPrepend(widget().preserve());
            }
        }
    }

    /// A live timeline: rendered, laid out, and with a router holding the
    /// regions both the extents and the anchor are read out of.
    private final class Harness {

        private final ElementTree tree;
        private final WidgetRenderer renderer;
        private final PointerRouter router = new PointerRouter();
        private final Feed feed;

        Harness(Feed feed, ScrollAnchor anchor, @Nullable Boolean preserve) {
            this.feed = feed;
            target = TestFrames.of(200, VIEWPORT_HEIGHT, 1.0f, 0);
            renderer = new WidgetRenderer(
                    List.of(
                            Controls.baseStylesheet(),
                            Theme.NORD_DARK.load(),
                            Stylesheet.parse(CascadeLayer.APPLICATION, ROWS)),
                    TestFont.get());
            tree = new ElementTree(new Timeline(feed, anchor, preserve));
            render = RenderTree.create();
            router.focusRoot(tree.root());
            frame();
        }

        /// One frame: rebuild whatever went dirty, render, lay out, and hand the
        /// router the rectangles. Exactly what a window does.
        void frame() {
            tree.flush();
            render.update(target.frame(), renderer.render(tree));
            router.updateRegions(HitTest.capture(render));
        }

        /// Two frames.
        ///
        /// **Everything here is one frame late and that is the design**, not a
        /// convenience: the heights of rows that have just been inserted do not
        /// exist until a frame has been laid out, so the correction is applied
        /// on the frame after the insertion. It is the bargain `Measured` has
        /// carried since a thumb first needed a size ([ADR-0117]), and at frame
        /// rate it is not a jump anybody sees.
        void settle() {
            frame();
            frame();
        }

        Element viewport() {
            return find(tree.root(), "scroll");
        }

        ScrollViewport widget() {
            return (ScrollViewport) viewport().widget();
        }

        /// How far down this viewport is, as the last build described it.
        double offsetY() {
            return widget().offsetY();
        }

        /// How far down it could go in all.
        double overflowY() {
            var scroll = widget();
            return scroll.viewport().overflowY(scroll.content());
        }

        /// Where `row` was **drawn**, in the window's coordinates.
        ///
        /// The one number every statement below is really about: a reader looks
        /// at a line, and "it did not jump" means this did not change.
        double rowTop(String row) {
            var found = new ArrayList<Double>();
            render.forEachPlacedBox(placed -> {
                if (placed.box().owner() instanceof Element element
                        && element.widget() instanceof Text text
                        && row.equals(text.content())) {
                    var matrix = placed.transform();
                    var layout = placed.layout();
                    found.add(matrix.b() * layout.left() + matrix.d() * layout.top() + matrix.f());
                }
            });
            assertEquals(1, found.size(), "expected exactly one painted row named " + row);
            return found.getFirst();
        }

        /// Turns the wheel over the middle of the viewport by `notches`, then
        /// paints.
        void wheel(float notches) {
            router.pointerWheel(100, 50, 0, notches, Modifiers.NONE);
            frame();
        }

        void press(Key key) {
            router.focus(viewport(), true);
            router.keyPressed(key, Modifiers.NONE, false);
            frame();
        }
    }

    private static Element find(Element from, String type) {
        if (type.equals(from.type())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("a conversation opens on its newest message")
    class OpensAtTheEnd {

        @Test
        @DisplayName("anchor=END lands at the end of the content it turns out to have")
        void opensAtTheEnd() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);

            harness.settle();

            assertTrue(harness.overflowY() > 0, "twenty rows did not overflow a hundred pixels");
            assertEquals(harness.overflowY(), harness.offsetY(), STILL, "it did not open at the end");
        }

        @Test
        @DisplayName("and anchor=START, which is the default, still opens at zero")
        void startIsUnchanged() {
            var harness = new Harness(new Feed(20), ScrollAnchor.START, null);

            harness.settle();

            assertEquals(0, harness.offsetY(), STILL);
        }

        @Test
        @DisplayName("content that does not overflow opens at zero whatever the anchor says")
        void nothingToScroll() {
            var harness = new Harness(new Feed(1), ScrollAnchor.END, null);

            harness.settle();

            assertEquals(0, harness.overflowY(), STILL);
            assertEquals(0, harness.offsetY(), STILL);
        }
    }

    @Nested
    @DisplayName("it stays at the end while it is there")
    class StaysAtTheEnd {

        @Test
        @DisplayName("a message arriving while you are at the bottom keeps you at the bottom")
        void followsTheEnd() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();
            var wasAtEnd = harness.overflowY();

            harness.feed.append();
            harness.settle();

            assertTrue(harness.overflowY() > wasAtEnd, "the message did not make the content taller");
            assertEquals(harness.overflowY(), harness.offsetY(), STILL, "it stopped following the end");
        }

        /// **The one that regresses.** Every implementation that follows the end
        /// by watching the content's height follows it for a reader who has
        /// scrolled away too, and drags them back down mid-sentence.
        @Test
        @DisplayName("but one arriving while you are reading history does not move you at all")
        void readingHistoryIsNotMoved() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();
            harness.wheel(-2);
            harness.frame();
            var offset = harness.offsetY();
            var line = harness.rowTop("row 4");

            harness.feed.append();
            harness.settle();

            assertEquals(offset, harness.offsetY(), STILL, "it dragged the reader back down");
            assertEquals(line, harness.rowTop("row 4"), STILL, "the line the reader was on moved");
        }

        @Test
        @DisplayName("and pressing End puts you back in its way")
        void endRejoins() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();
            harness.wheel(-2);
            harness.frame();
            assertFalse(harness.offsetY() >= harness.overflowY() - ScrollStick.TOLERANCE, "the wheel moved nothing");

            harness.press(Key.END);
            harness.settle();
            harness.feed.append();
            harness.settle();

            assertEquals(harness.overflowY(), harness.offsetY(), STILL, "End did not put it back on the end");
        }

        @Test
        @DisplayName("a single line up is enough to stop following, because a line up is a decision")
        void onePixelUpLetsGo() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();

            // A third of a notch is one line, which is twenty pixels and forty
            // times `ScrollStick.TOLERANCE`. The tolerance is there for the
            // arithmetic of a drag landing on the last pixel, not to give a
            // deliberate scroll a grace period.
            harness.wheel(-1f / ScrollViewport.LINES_PER_NOTCH);
            harness.frame();
            var offset = harness.offsetY();

            harness.feed.append();
            harness.settle();

            assertEquals(offset, harness.offsetY(), STILL);
        }
    }

    @Nested
    @DisplayName("rows added above keep the reader's line")
    class PreserveOnPrepend {

        @Test
        @DisplayName("paging history in moves the offset, not the reader")
        void prependKeepsTheLine() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();
            harness.wheel(-2);
            harness.frame();
            var line = harness.rowTop("row 6");
            var offset = harness.offsetY();

            harness.feed.prepend(5);
            harness.settle();

            assertTrue(harness.offsetY() > offset + STILL, "the offset did not follow the inserted rows");
            assertEquals(line, harness.rowTop("row 6"), STILL, "the reader's line jumped");
        }

        /// The naive implementation — "the content got taller, so shift the
        /// offset" — passes the test above and fails this one, which is why both
        /// are here.
        @Test
        @DisplayName("and a row added below moves nothing, which a height alone cannot tell you")
        void appendShiftsNothing() {
            var harness = new Harness(new Feed(20), ScrollAnchor.START, true);
            harness.wheel(2);
            harness.settle();
            var line = harness.rowTop("row 8");
            var offset = harness.offsetY();

            harness.feed.append();
            harness.settle();

            assertEquals(offset, harness.offsetY(), STILL, "it shifted for content added at the bottom");
            assertEquals(line, harness.rowTop("row 8"), STILL);
        }

        @Test
        @DisplayName("a viewport that was told not to preserve lets the reader be pushed down")
        void refusedPreserveIsObeyed() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, false);
            harness.settle();
            harness.wheel(-2);
            harness.frame();
            var offset = harness.offsetY();

            harness.feed.prepend(5);
            harness.settle();

            assertEquals(offset, harness.offsetY(), STILL, "preserve-on-prepend=#false was not obeyed");
        }

        @Test
        @DisplayName("prepending while at the end still leaves you at the end, counted once")
        void prependAtTheEndIsNotCountedTwice() {
            var harness = new Harness(new Feed(20), ScrollAnchor.END, null);
            harness.settle();

            harness.feed.prepend(5);
            harness.settle();

            assertEquals(harness.overflowY(), harness.offsetY(), STILL, "the insertion was counted twice");
        }
    }

    @Nested
    @DisplayName("the attribute is three states, not two")
    class TriState {

        private static Scroll scroll() {
            return new Scroll(new Text("a"));
        }

        @Test
        @DisplayName("unset means whatever the anchor says, in whichever order they were written")
        void unsetFollowsTheAnchor() {
            assertFalse(scroll().preservesOnPrepend(), "START preserves by default");
            assertTrue(scroll().anchor(ScrollAnchor.END).preservesOnPrepend(), "END does not");
        }

        @Test
        @DisplayName("and set means what it says, in whichever order they were written")
        void setWinsOverTheAnchor() {
            assertFalse(
                    scroll().anchor(ScrollAnchor.END).preserveOnPrepend(false).preservesOnPrepend(),
                    "the anchor overrode an explicit false");
            assertFalse(
                    scroll().preserveOnPrepend(false).anchor(ScrollAnchor.END).preservesOnPrepend(),
                    "writing the two the other way round meant something else");
            assertTrue(scroll().preserveOnPrepend(true).preservesOnPrepend());
        }
    }

    @Nested
    @DisplayName("the markup")
    class Markup {

        private static Scroll inflate(String source) {
            var node = KdlParser.parse(source).getFirst();
            return (Scroll) Scroll.inflate(node, List.of(), null);
        }

        @Test
        @DisplayName("anchor= reaches the widget, and a misspelling does not throw")
        void anchorParses() {
            assertEquals(ScrollAnchor.END, ScrollAnchor.parse("end"));
            assertEquals(ScrollAnchor.END, ScrollAnchor.parse("bottom"));
            assertEquals(ScrollAnchor.START, ScrollAnchor.parse("sideways"));
            assertEquals(ScrollAnchor.START, ScrollAnchor.parse(null));
            assertEquals(ScrollAnchor.END, inflate("scroll anchor=\"end\"").anchor());
        }

        @Test
        @DisplayName("an absent preserve-on-prepend stays absent all the way to the widget")
        void absentStaysAbsent() {
            var scroll = inflate("scroll anchor=\"end\"");

            assertNotNull(scroll);
            assertTrue(scroll.preservesOnPrepend(), "anchor=end did not bring the default with it");
            assertEquals(null, scroll.preserveOnPrepend(), "the absent attribute was resolved too early");
        }

        @Test
        @DisplayName("and a document may switch it off under an end anchor")
        void switchedOff() {
            var scroll = inflate("scroll anchor=\"end\" preserve-on-prepend=#false");

            assertFalse(scroll.preservesOnPrepend());
        }

        @Test
        @DisplayName("or on under a start one")
        void switchedOn() {
            var scroll = inflate("scroll preserve-on-prepend=#true");

            assertEquals(ScrollAnchor.START, scroll.anchor());
            assertTrue(scroll.preservesOnPrepend());
        }
    }
}
