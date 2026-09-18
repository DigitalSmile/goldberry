package io.github.digitalsmile.goldberry.widgets.panel.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// `split-pane` — §5's two children and a draggable divider ([ADR-0165]).
///
/// The interesting claims are all about the **anchor**. A divider does not read
/// the pointer's position the way a slider does — the pointer is somewhere inside
/// a six-point bar, and mapping that to a fraction would jump the divider under
/// the finger on every press — so the gesture is a translation from where it
/// started. The tests below drive it exactly as the router would: a press that
/// takes the anchor, then moves carrying `pressX` so `dragX` means something.
class SplitPaneTest {

    /// The pane's own length, as a frame would report it. 300 points, so the
    /// arithmetic in the assertions is legible: half is 147 once the six-point
    /// divider is out of it.
    private static final float LENGTH = 300;

    /// What the two children actually share — the length less the divider
    /// standing between them.
    ///
    /// **The position is a fraction of this**, which is the thing the clamp used
    /// to disagree with: it held the divider off `firstMin / LENGTH` while the
    /// first pane was sized against `LENGTH - DIVIDER`, so a pane at its
    /// minimum came out a little under it. Every fraction below is written
    /// against this number so the two cannot drift apart again.
    private static final float CONTENT = LENGTH - SplitPaneView.DIVIDER;

    /// The lowest fraction the default minimum allows at [#LENGTH] — where a
    /// drag to the near edge stops.
    private static final double LOWEST = SplitPane.DEFAULT_MINIMUM / CONTENT;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static SplitPane split(double position) {
        return new SplitPane(
                SplitAxis.HORIZONTAL,
                position,
                null,
                SplitPane.DEFAULT_MINIMUM,
                SplitPane.DEFAULT_MINIMUM,
                false,
                List.of(new Text("first"), new Text("second")),
                Attributes.NONE);
    }

    /// A tree whose pane has been measured, which is what every drag needs: the
    /// state converts pixels to a fraction and has nothing to convert with until
    /// a frame has laid the pane out.
    private static ElementTree measured(SplitPane pane, boolean vertical) {
        var tree = new ElementTree(pane);
        view(tree).measured(vertical ? new Extent(120, LENGTH) : new Extent(LENGTH, 120), null);
        tree.flush();
        return tree;
    }

    private static ElementTree measured(SplitPane pane) {
        return measured(pane, false);
    }

    private static SplitPaneView view(ElementTree tree) {
        return Described.first(tree, SplitPaneView.class);
    }

    private static SplitDivider divider(ElementTree tree) {
        return Described.first(tree, SplitDivider.class);
    }

    /// A press, then a move that has travelled `by` points — the two events the
    /// router sends, with `pressX` set so `dragX` is not `NaN`.
    private static void drag(ElementTree tree, float by, boolean vertical) {
        var bar = divider(tree);
        var anchor = bar.gestureAnchor();
        bar.onPointer(new PointerEvent(PointerEvent.Kind.PRESSED, 0, 0, PointerEvent.Button.PRIMARY, 1, null));
        var move = new PointerEvent(
                PointerEvent.Kind.MOVED,
                vertical ? 0 : by,
                vertical ? by : 0,
                PointerEvent.Button.PRIMARY,
                0,
                0,
                0,
                null);
        move.anchoredAt(anchor);
        bar.onPointer(move);
        tree.flush();
    }

    private static void key(ElementTree tree, Key key) {
        divider(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
        tree.flush();
    }

    @Nested
    @DisplayName("being measured")
    class Measuring {

        /// **The defect this pins.** The state took the measurement and did not ask
        /// for a rebuild, so the view kept the `-1` it had been built with and the
        /// first pane stayed on its first-frame proportional guess. A `position` of
        /// 0.5 put the divider wherever the two children's content happened to land,
        /// for ever — and dragging it was the only thing that ever corrected it,
        /// which is why every test here that *did* drag passed.
        @Test
        @DisplayName("moves the divider to where the position says, with nobody touching it")
        void aMeasurementIsEnough() {
            var tree = new ElementTree(split(0.5));
            assertEquals(-1, view(tree).firstLength(), "nothing has been laid out yet, so there is no size to give");

            view(tree).measured(new Extent(LENGTH, 120), null);
            assertTrue(tree.needsBuild(), "a pane that has learnt its own length has something new to describe");
            tree.flush();

            assertEquals(
                    (LENGTH - SplitPaneView.DIVIDER) / 2,
                    view(tree).firstLength(),
                    0.01,
                    "half of what is left once the divider is out of it");
        }

        @Test
        @DisplayName("asks for nothing when the measurement has not changed")
        void anUnchangedMeasurementIsQuiet() {
            var tree = measured(split(0.5));

            view(tree).measured(new Extent(LENGTH, 120), null);

            assertFalse(
                    tree.needsBuild(),
                    "a still window must rebuild nothing, or the idle frame loop wakes every frame (§1.7)");
        }

        @Test
        @DisplayName("follows a resize")
        void aNewLengthMovesIt() {
            var tree = measured(split(0.5));

            view(tree).measured(new Extent(LENGTH * 2, 120), null);
            tree.flush();

            assertEquals((LENGTH * 2 - SplitPaneView.DIVIDER) / 2, view(tree).firstLength(), 0.01);
        }

        @Test
        @DisplayName("measures the other axis when it is stacked")
        void verticalMeasuresHeight() {
            var pane = new SplitPane(
                    SplitAxis.VERTICAL,
                    0.25,
                    null,
                    SplitPane.DEFAULT_MINIMUM,
                    SplitPane.DEFAULT_MINIMUM,
                    false,
                    List.of(new Text("first"), new Text("second")),
                    Attributes.NONE);
            var tree = new ElementTree(pane);

            view(tree).measured(new Extent(120, LENGTH), null);
            tree.flush();

            assertEquals((LENGTH - SplitPaneView.DIVIDER) / 4, view(tree).firstLength(), 0.01);
        }
    }

    @Nested
    @DisplayName("what it describes")
    class Description {

        @Test
        @DisplayName("two children become two sides and a divider")
        void parts() {
            var tree = new ElementTree(split(0.5));

            assertEquals(
                    2, Described.of(tree, SplitPaneView.SplitPaneSide.class).size());
            assertEquals(1, Described.of(tree, SplitDivider.class).size());
        }

        /// A three-way split is two split panes, one inside the other. Saying so
        /// at construction is better than laying out a third child nobody sized.
        @Test
        @DisplayName("anything but two children is refused")
        void twoChildren() {
            for (var count : List.of(0, 1, 3)) {
                var kids = new java.util.ArrayList<io.github.digitalsmile.goldberry.widget.Widget>();
                for (var i = 0; i < count; i++) {
                    kids.add(new Text("pane " + i));
                }
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SplitPane(SplitAxis.HORIZONTAL, 0.5, null, 0, 0, false, kids, Attributes.NONE),
                        count + " children should be refused");
            }
        }

        /// The axis names the arrangement, not the divider — see [SplitAxis].
        @Test
        @DisplayName("the axis is a class as well as a layout")
        void axisIsAClass() {
            assertTrue(view(new ElementTree(split(0.5))).classes().contains("horizontal"));
            assertTrue(view(new ElementTree(new SplitPane(SplitAxis.VERTICAL, 0.5, null, new Text("a"), new Text("b"))))
                    .classes()
                    .contains("vertical"));
        }

        /// The node a stylesheet sees is the view, not the stateful widget.
        @Test
        @DisplayName("the id the document wrote lands on the split-pane node")
        void attributesLandOnTheView() {
            var tree = new ElementTree(split(0.5).withAttributes(Attributes.NONE.id("s")));

            assertEquals("s", view(tree).id());
        }

        /// The divider is the tab stop and the only one: a split whose panes were
        /// focusable would put a stop between every pair of them.
        @Test
        @DisplayName("the divider is the only focusable part")
        void oneTabStop() {
            var tree = new ElementTree(split(0.5));

            var focusable = Described.in(tree).stream()
                    .filter(w ->
                            w instanceof io.github.digitalsmile.goldberry.input.handler.Handles h && h.isFocusable())
                    .toList();
            assertEquals(1, focusable.size(), "expected only the divider, got " + focusable);
            assertInstanceOf(SplitDivider.class, focusable.getFirst());
        }

        /// Before a frame has laid the pane out there is no pixel length, and the
        /// view says so rather than sizing the first pane to nothing.
        @Test
        @DisplayName("an unmeasured pane reports no pixel length")
        void unmeasured() {
            assertTrue(view(new ElementTree(split(0.5))).firstLength() < 0);
        }

        /// The thickness is written in two places because the first pane's size
        /// is computed against it, and a stylesheet that disagreed would put the
        /// second pane's edge out by the difference — silently.
        @Test
        @DisplayName("the divider's thickness matches what the stylesheet sets")
        void thicknessAgrees() {
            var css = io.github.digitalsmile.goldberry.widgets.Controls.baseStylesheet()
                    .toString();
            assertEquals(6f, SplitPaneView.DIVIDER, "if this changes, `split-divider` in controls.css changes with it");
            assertTrue(css.contains("split-divider"), "the rule exists to be kept in step");
        }
    }

    @Nested
    @DisplayName("dragging the divider")
    class Dragging {

        /// **The claim.** The divider moves by exactly the pointer's travel, from
        /// where it was — not to where the pointer is.
        @Test
        @DisplayName("a drag moves the divider by the pointer's travel")
        void translation() {
            var tree = measured(split(0.5));
            var before = view(tree).position();

            drag(tree, 30, false);

            // 30 points of a 300-point pane is a tenth.
            assertEquals(before + 0.1, view(tree).position(), 1e-6);
        }

        /// Nothing moves on the press itself, which is the whole reason the
        /// anchor exists: a divider grabbed near its edge must not jump so its
        /// centre lands under the finger.
        @Test
        @DisplayName("a press alone moves nothing")
        void pressAloneDoesNothing() {
            var tree = measured(split(0.5));
            var before = view(tree).position();

            divider(tree)
                    .onPointer(new PointerEvent(PointerEvent.Kind.PRESSED, 0, 0, PointerEvent.Button.PRIMARY, 1, null));
            tree.flush();

            assertEquals(before, view(tree).position());
        }

        /// A move with no button held carries `NaN` for its travel, and a divider
        /// that acted on it would follow the pointer around the window.
        @Test
        @DisplayName("a move with no button held is ignored")
        void hoverDoesNothing() {
            var tree = measured(split(0.5));
            var before = view(tree).position();

            divider(tree).onPointer(new PointerEvent(PointerEvent.Kind.MOVED, 90, 0, null, 0, null));
            tree.flush();

            assertEquals(before, view(tree).position());
        }

        /// The minimums are pixels, so the fraction they forbid depends on how
        /// long the pane is — which is why the clamp needs the measurement.
        @Test
        @DisplayName("a drag stops at the minimum rather than passing it")
        void minimums() {
            var tree = measured(split(0.5));

            drag(tree, -1000, false);

            // 48 points of the 294 the two children share.
            assertEquals(LOWEST, view(tree).position(), 1e-6);
        }

        /// **The defect this pins, and the reason it needed a second test.**
        ///
        /// The minimum is a number of *pixels* — `SplitPane.DEFAULT_MINIMUM` is
        /// 48 of them — and the clamp turned it into a fraction by dividing by
        /// the pane's whole length, while the first pane was sized against the
        /// length **less the divider**. So a pane "at its minimum" was
        /// `firstMin × DIVIDER / length` pixels short of it: a hair here, and
        /// proportionally worse the narrower the pane, which is precisely when a
        /// minimum is the thing holding a sidebar usable.
        ///
        /// [#minimums] above cannot see any of that. It asserts the **fraction**,
        /// which is whatever the clamp said it was — so it agreed with the bug by
        /// construction. This asserts the width, which is what a minimum is.
        @Test
        @DisplayName("a pane stopped at its minimum is that many pixels wide")
        void minimumIsAWidth() {
            var tree = measured(split(0.5));

            drag(tree, -1000, false);

            assertEquals(
                    SplitPane.DEFAULT_MINIMUM,
                    view(tree).firstLength(),
                    1e-6,
                    "the first pane is short of the minimum it was supposed to stop at");
        }

        /// And the far pane, which is the same arithmetic from the other end:
        /// what is left over after the first pane and the divider.
        @Test
        @DisplayName("and the second pane is, going the other way")
        void otherMinimumIsAWidth() {
            var tree = measured(split(0.5));

            drag(tree, 1000, false);

            assertEquals(
                    SplitPane.DEFAULT_MINIMUM,
                    CONTENT - view(tree).firstLength(),
                    1e-6,
                    "the second pane is short of the minimum it was supposed to stop at");
        }

        @Test
        @DisplayName("and at the other one going the other way")
        void otherMinimum() {
            var tree = measured(split(0.5));

            drag(tree, 1000, false);

            assertEquals(1 - LOWEST, view(tree).position(), 1e-6);
        }

        /// A vertical split drags on the other axis, and must ignore the one it
        /// does not use.
        @Test
        @DisplayName("a vertical split follows the pointer down, not across")
        void verticalAxis() {
            var pane = new SplitPane(
                    SplitAxis.VERTICAL,
                    0.5,
                    null,
                    SplitPane.DEFAULT_MINIMUM,
                    SplitPane.DEFAULT_MINIMUM,
                    false,
                    List.of(new Text("top"), new Text("bottom")),
                    Attributes.NONE);
            var tree = measured(pane, true);

            drag(tree, 30, true);

            assertEquals(0.6, view(tree).position(), 1e-6);
        }

        /// Nothing has been laid out, so there is no length to convert pixels
        /// with. Doing the arithmetic anyway would divide by zero.
        @Test
        @DisplayName("a drag before the first layout does nothing")
        void unmeasuredDrag() {
            var tree = new ElementTree(split(0.5));

            drag(tree, 30, false);

            assertEquals(0.5, view(tree).position(), 1e-6);
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        /// §5's "keyboard-resizable when focused". A step in **pixels**, so it
        /// feels the same on a narrow split and a wide one.
        @Test
        @DisplayName("the arrows along the axis move the divider by a step")
        void arrows() {
            var tree = measured(split(0.5));

            key(tree, Key.RIGHT);
            assertEquals(0.5 + 16.0 / LENGTH, view(tree).position(), 1e-6);

            key(tree, Key.LEFT);
            assertEquals(0.5, view(tree).position(), 1e-6);
        }

        /// The pair across the axis is deliberately left alone, so the key
        /// reaches whatever else wants it.
        @Test
        @DisplayName("the arrows across the axis do nothing at all")
        void crossAxisArrows() {
            var tree = measured(split(0.5));

            key(tree, Key.UP);
            key(tree, Key.DOWN);

            assertEquals(0.5, view(tree).position(), 1e-6);
        }

        @Test
        @DisplayName("Page moves by more than an arrow")
        void pages() {
            var tree = measured(split(0.5));

            key(tree, Key.PAGE_DOWN);

            assertEquals(0.5 + 64.0 / LENGTH, view(tree).position(), 1e-6);
        }

        /// **Not 0 and 1.** `Home` and `End` go as far as the minimums allow;
        /// collapsing is `Enter`'s, and only when the split says it may.
        @Test
        @DisplayName("Home and End go to the minimums, not to the edges")
        void homeAndEnd() {
            var tree = measured(split(0.5));

            key(tree, Key.HOME);
            assertEquals(LOWEST, view(tree).position(), 1e-6);

            key(tree, Key.END);
            assertEquals(1 - LOWEST, view(tree).position(), 1e-6);
        }
    }

    @Nested
    @DisplayName("collapsing to an edge")
    class Collapsing {

        private static SplitPane collapsible(double position) {
            return new SplitPane(
                    SplitAxis.HORIZONTAL,
                    position,
                    null,
                    SplitPane.DEFAULT_MINIMUM,
                    SplitPane.DEFAULT_MINIMUM,
                    true,
                    List.of(new Text("first"), new Text("second")),
                    Attributes.NONE);
        }

        /// §5's "optional collapse-to-edge". Past *half* the minimum, so a
        /// divider parked at its minimum does not collapse because a window
        /// narrowed a little.
        @Test
        @DisplayName("a drag most of the way into the minimum collapses the pane")
        void dragCollapses() {
            var tree = measured(collapsible(0.5));

            drag(tree, -1000, false);

            assertEquals(0, view(tree).position(), 1e-9);
        }

        /// The same drag on a split that is not collapsible stops at the minimum,
        /// which is the whole of what the flag decides.
        @Test
        @DisplayName("and does not, when the split says it may not")
        void notCollapsible() {
            var tree = measured(split(0.5));

            drag(tree, -1000, false);

            assertEquals(LOWEST, view(tree).position(), 1e-6);
        }

        @Test
        @DisplayName("Enter collapses, and Enter again puts it back where it was")
        void enterToggles() {
            var tree = measured(collapsible(0.4));

            key(tree, Key.ENTER);
            assertEquals(0, view(tree).position(), 1e-9);

            key(tree, Key.ENTER);
            assertEquals(0.4, view(tree).position(), 1e-6);
        }

        /// A collapsed pane is still **built**. §5 asks for collapse-to-edge, not
        /// for unmounting — the opposite of `collapse`, where the absence is the
        /// point.
        @Test
        @DisplayName("a collapsed pane is still there, unlike a closed collapse")
        void collapsedPaneIsStillBuilt() {
            var tree = measured(collapsible(0.5));
            key(tree, Key.ENTER);

            assertTrue(
                    Described.in(tree).stream().anyMatch(w -> w instanceof Text text && "first".equals(text.content())),
                    "the pane is at zero width, not gone");
        }

        /// A divider flush against an edge has no pane on one side of it, which
        /// is a state neither `:hover` nor `:focus-visible` can express.
        @Test
        @DisplayName("a collapsed divider says so to the stylesheet")
        void collapsedIsAClass() {
            var tree = measured(collapsible(0.5));
            assertFalse(divider(tree).classes().contains("collapsed"));

            key(tree, Key.ENTER);
            assertTrue(divider(tree).classes().contains("collapsed"));
        }

        @Test
        @DisplayName("Enter on a split that is not collapsible does nothing")
        void enterDoesNothingOtherwise() {
            var tree = measured(split(0.5));

            key(tree, Key.ENTER);

            assertEquals(0.5, view(tree).position(), 1e-6);
        }
    }

    @Nested
    @DisplayName("controlled or not")
    class Ownership {

        /// The arrangement every value in this catalog has: a split whose
        /// `onResize` does nothing stays where it is.
        @Test
        @DisplayName("a controlled split asks and does not decide")
        void controlled() {
            var asked = new AtomicReference<Double>();
            var pane = new SplitPane(
                    SplitAxis.HORIZONTAL,
                    0.5,
                    asked::set,
                    SplitPane.DEFAULT_MINIMUM,
                    SplitPane.DEFAULT_MINIMUM,
                    false,
                    List.of(new Text("a"), new Text("b")),
                    Attributes.NONE);
            var tree = measured(pane);

            drag(tree, 30, false);

            assertEquals(0.6, asked.get(), 1e-6);
            assertEquals(0.5, view(tree).position(), 1e-6, "and stayed put, because nobody answered");
        }

        @Test
        @DisplayName("an uncontrolled split keeps the position itself")
        void uncontrolled() {
            var tree = measured(split(0.5));

            drag(tree, 30, false);

            assertEquals(0.6, view(tree).position(), 1e-6);
        }

        /// A drag that has hit a minimum reports the same number every frame, and
        /// an application rebuilding on it would rebuild sixty times a second for
        /// a divider that is not moving.
        @Test
        @DisplayName("a drag that changes nothing tells nobody")
        void noChangeNoCall() {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var pane = new SplitPane(
                    SplitAxis.HORIZONTAL,
                    0.5,
                    value -> calls.incrementAndGet(),
                    SplitPane.DEFAULT_MINIMUM,
                    SplitPane.DEFAULT_MINIMUM,
                    false,
                    List.of(new Text("a"), new Text("b")),
                    Attributes.NONE);
            var tree = measured(pane);

            drag(tree, 0, false);

            assertEquals(0, calls.get());
        }
    }

    @Nested
    @DisplayName("from markup")
    class Markup {

        @Test
        @DisplayName("a split pane inflates")
        void inflates() {
            var widget = Widgets.inflater().inflate(KdlParser.parse("""
                    split-pane axis="vertical" position=0.3 first-min=100 second-min=80 \
                    collapsible=#true {
                        text "top"
                        text "bottom"
                    }
                    """).getFirst());
            var pane = assertInstanceOf(SplitPane.class, widget);

            assertEquals(SplitAxis.VERTICAL, pane.axis());
            assertEquals(0.3, pane.position(), 1e-9);
            assertEquals(100f, pane.firstMin());
            assertEquals(80f, pane.secondMin());
            assertTrue(pane.collapsible());
        }

        @Test
        @DisplayName("an axis this toolkit has not got is refused")
        void badAxis() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("split-pane axis=\"diagonal\" { text \"a\"; text \"b\" }")
                                    .getFirst()));
        }
    }
}
