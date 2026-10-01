package dev.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// A drag held at the edge of a viewport ([ADR-0500]).
///
/// The arithmetic first — the speed, the band and the clamp are functions, and a
/// function is asserted by its values — then the stepping against a target that
/// records what it was asked for, and last the one thing a viewport adds:
/// [ScrollScope#nudge] moving a real `scroll` without a glide. What a selection does
/// with all this is the two views' and `text-area`'s own tests.
class EdgeScrollTest {

    /// A viewport 200 tall and 100 wide at (0, 100) — far enough from the origin that
    /// an edge measured from zero rather than from `top` would be caught.
    private static final LogicalRect VIEWPORT = LogicalRect.of(0, 100, 100, 200);

    @Nested
    @DisplayName("the speed")
    class Speed {

        @Test
        @DisplayName("is nothing at or before the band's inner edge")
        void nothingBeforeTheBand() {
            assertEquals(0, EdgeScroll.speed(0));
            assertEquals(0, EdgeScroll.speed(-40));
            assertEquals(0, EdgeScroll.speed(Double.NaN));
        }

        @Test
        @DisplayName("grows by the gain for every pixel further in")
        void growsWithDistance() {
            assertEquals(EdgeScroll.GAIN, EdgeScroll.speed(1), 1e-9);
            assertEquals(10 * EdgeScroll.GAIN, EdgeScroll.speed(10), 1e-9);
            assertTrue(EdgeScroll.speed(50) > EdgeScroll.speed(20), "further past the edge is faster");
        }

        @Test
        @DisplayName("and stops growing at the cap, however far the pointer goes")
        void capped() {
            assertEquals(EdgeScroll.MAX_SPEED, EdgeScroll.speed(10_000), 1e-9);
            assertEquals(EdgeScroll.MAX_SPEED, EdgeScroll.speed(EdgeScroll.MAX_SPEED / EdgeScroll.GAIN), 1e-9);
        }
    }

    @Nested
    @DisplayName("the band")
    class Band {

        @Test
        @DisplayName("the middle of a viewport does not move it")
        void theMiddleIsStill() {
            assertEquals(0, EdgeScroll.velocity(200, 100, 300));
            assertEquals(0, EdgeScroll.velocity(100 + EdgeScroll.BAND, 100, 300));
        }

        @Test
        @DisplayName("the viewport's own edge is a band's depth in, down at the bottom and up at the top")
        void theEdgeIsInsideTheBand() {
            assertEquals(EdgeScroll.speed(EdgeScroll.BAND), EdgeScroll.velocity(300, 100, 300), 1e-9);
            assertEquals(-EdgeScroll.speed(EdgeScroll.BAND), EdgeScroll.velocity(100, 100, 300), 1e-9);
        }

        @Test
        @DisplayName("and past it is faster, in the same direction")
        void pastTheEdgeIsFaster() {
            assertTrue(EdgeScroll.velocity(360, 100, 300) > EdgeScroll.velocity(300, 100, 300));
            assertTrue(EdgeScroll.velocity(40, 100, 300) < EdgeScroll.velocity(100, 100, 300));
        }

        @Test
        @DisplayName("is at most an eighth of a small viewport, so a short one still has a middle")
        void shrinksWithTheViewport() {
            // Sixty pixels: a band of sixteen at each end would leave it half edge.
            var near = 0;
            var far = 60;
            assertEquals(0, EdgeScroll.velocity(far - 60 / 8.0, near, far), 1e-9);
            assertTrue(EdgeScroll.velocity(far - 60 / 8.0 + 1, near, far) > 0);
            assertEquals(0, EdgeScroll.velocity(far - EdgeScroll.BAND + 1, near, far), 1e-9);
        }

        @Test
        @DisplayName("an empty viewport never moves")
        void emptyViewport() {
            assertEquals(0, EdgeScroll.velocity(500, 100, 100));
        }
    }

    @Nested
    @DisplayName("the clamp")
    class Clamp {

        @Test
        @DisplayName("pulls a pointer past the edge back inside, a pixel short of the far one")
        void pullsInside() {
            assertEquals(100, EdgeScroll.clamp(20, 100, 300));
            assertEquals(299, EdgeScroll.clamp(900, 100, 300));
            assertEquals(150, EdgeScroll.clamp(150, 100, 300));
        }

        @Test
        @DisplayName("and the held point is the clamped one")
        void theHeldPointIsClamped() {
            var edge = new EdgeScroll();
            edge.hold(null, ScrollAxis.BOTH);
            edge.pointer(-30, 900, VIEWPORT);

            assertEquals(0, edge.x());
            assertEquals(299, edge.y());
        }

        @Test
        @DisplayName("before anybody said where the viewport is, the pointer is left alone")
        void noViewportNoClamp() {
            var edge = new EdgeScroll();
            edge.hold(null, ScrollAxis.BOTH);
            edge.pointer(-30, 900, null);

            assertEquals(-30, edge.x());
            assertEquals(900, edge.y());
        }
    }

    /// A target that records every step and refuses to go below `limit`.
    private static final class Recorder implements EdgeScroll.Target {

        final List<double[]> steps = new ArrayList<>();

        double offset;

        double limit = Double.MAX_VALUE;

        @Override
        public boolean scrollBy(double dx, double dy) {
            var next = Math.min(limit, offset + dy);
            steps.add(new double[] {dx, dy});
            if (next == offset) {
                return false;
            }
            offset = next;
            return true;
        }
    }

    @Nested
    @DisplayName("stepping")
    class Stepping {

        private final Recorder target = new Recorder();

        private final EdgeScroll edge = new EdgeScroll();

        /// Held with the pointer `below` pixels under the viewport's bottom edge.
        private void heldBelow(double below) {
            edge.hold(target, ScrollAxis.VERTICAL);
            edge.pointer(50, VIEWPORT.bottom() + below, VIEWPORT);
        }

        @Test
        @DisplayName("the first frame starts the clock, and every one after moves by speed times time")
        void speedTimesTime() {
            heldBelow(24);
            var speed = EdgeScroll.speed(EdgeScroll.BAND + 24);

            assertTrue(edge.isScrolling());
            assertFalse(edge.tick(1000), "the first frame of a run has no time to move by");
            assertTrue(edge.tick(1016));
            assertEquals(speed * 0.016, target.offset, 1e-9);
            assertTrue(edge.tick(1024));
            assertEquals(speed * 0.024, target.offset, 1e-9);
        }

        @Test
        @DisplayName("the steps are fractional and are kept, so a slow edge still moves at 144 Hz")
        void fractionsAreKept() {
            // Just inside the band: well under a pixel a frame. Rounded, it would
            // never move at all.
            edge.hold(target, ScrollAxis.VERTICAL);
            edge.pointer(50, VIEWPORT.bottom() - EdgeScroll.BAND + 1, VIEWPORT);
            var frame = 1000 / 144.0;
            var now = 0.0;
            edge.tick(now);
            for (var i = 0; i < 144; i++) {
                now += frame;
                edge.tick(now);
            }

            assertTrue(
                    target.steps.getFirst()[1] < 1, "a step of " + target.steps.getFirst()[1] + " is not slow");
            assertEquals(EdgeScroll.speed(1), target.offset, 1e-6);
        }

        @Test
        @DisplayName("a late frame moves by at most the longest step, not by everything it missed")
        void aLateFrameDoesNotLeap() {
            heldBelow(100);
            edge.tick(0);
            edge.tick(2000);

            assertEquals(
                    EdgeScroll.speed(EdgeScroll.BAND + 100) * EdgeScroll.LONGEST_STEP_MILLIS / 1000,
                    target.offset,
                    1e-9);
        }

        @Test
        @DisplayName("only along the axes it was held on")
        void onlyItsAxes() {
            // Past the right-hand side of a vertical pane, and nowhere near its ends.
            edge.hold(target, ScrollAxis.VERTICAL);
            edge.pointer(500, 200, VIEWPORT);

            assertFalse(edge.isScrolling(), "a vertical viewport woke for a pointer beside it");
        }

        @Test
        @DisplayName("at the end it stops asking for frames, until the pointer moves again")
        void stallsAtTheEnd() {
            heldBelow(40);
            target.limit = 0;
            edge.tick(0);

            assertFalse(edge.tick(16), "the target refused, and nothing moved");
            assertFalse(edge.isScrolling(), "a viewport at its end kept the frame loop awake");

            edge.pointer(50, VIEWPORT.bottom() + 41, VIEWPORT);
            assertTrue(edge.isScrolling(), "a pointer that moved may have somewhere to go again");
        }

        @Test
        @DisplayName("stops when the pointer comes back inside")
        void stopsInside() {
            heldBelow(40);
            edge.tick(0);
            edge.tick(16);
            var moved = target.offset;

            edge.pointer(50, 200, VIEWPORT);
            assertFalse(edge.isScrolling());
            assertFalse(edge.tick(32));
            assertEquals(moved, target.offset);
        }

        @Test
        @DisplayName("and on the release")
        void stopsOnRelease() {
            heldBelow(40);
            edge.tick(0);
            edge.release();

            assertFalse(edge.isHeld());
            assertFalse(edge.isScrolling());
            assertFalse(edge.tick(16));
            assertEquals(0, target.offset);
        }

        @Test
        @DisplayName("a press in no viewport clamps and never scrolls")
        void noTarget() {
            edge.hold(null, ScrollAxis.VERTICAL);
            edge.pointer(50, VIEWPORT.bottom() + 40, VIEWPORT);

            assertFalse(edge.isScrolling());
            assertFalse(edge.tick(16));
        }

        @Test
        @DisplayName("a run that ended and began again starts its clock again")
        void aNewRunRestartsTheClock() {
            heldBelow(40);
            edge.tick(0);
            edge.tick(16);
            edge.pointer(50, 200, VIEWPORT);
            edge.tick(32);
            var before = target.offset;

            edge.pointer(50, VIEWPORT.bottom() + 40, VIEWPORT);
            assertFalse(edge.tick(5000), "a run resumed after five seconds moved by the five seconds");
            assertEquals(before, target.offset);
        }
    }

    @Nested
    @DisplayName("nudging a viewport")
    class Nudging {

        private ScrollHarness harness;

        @AfterEach
        void close() {
            if (harness != null) {
                harness.close();
            }
        }

        private ScrollController mounted() {
            RendererRequirement.enforce();
            var rows = new ArrayList<Widget>();
            for (var i = 0; i < 30; i++) {
                rows.add(new Text("row " + i, Attributes.NONE.id("row" + i)));
            }
            var controller = new ScrollController();
            harness = new ScrollHarness(new Scroll(
                            List.of(new Column(rows.toArray(Widget[]::new))),
                            ScrollAxis.VERTICAL,
                            Attributes.NONE.id("outer"))
                    .controlledBy(controller));
            return controller;
        }

        @Test
        @DisplayName("moves the offset at once, with no glide to wait for")
        void atOnce() {
            var controller = mounted();
            var scope = ScrollScope.enclosing(harness.element("row0")).orElseThrow();
            var before = harness.rowRect("row0").top();

            assertTrue(scope.nudge(0, 7.5));
            // One frame, not a settle: a glide would still be on its way here.
            harness.frame();

            assertEquals(7.5, controller.position().offsetY(), 1e-9);
            assertEquals(before - 7.5, harness.rowRect("row0").top(), 0.01);
        }

        @Test
        @DisplayName("clamps at the end, and says it did not move")
        void clamps() {
            var controller = mounted();
            var scope = ScrollScope.enclosing(harness.element("row0")).orElseThrow();

            assertFalse(scope.nudge(0, -10), "the top of a viewport has nowhere further up");
            assertTrue(scope.nudge(0, 1_000_000));
            assertEquals(
                    controller.position().overflowY(), controller.position().offsetY(), 1e-9);
            assertFalse(scope.nudge(0, 1), "the bottom of a viewport has nowhere further down");
        }

        @Test
        @DisplayName("and drops a step along the axis it does not move on")
        void onlyItsAxis() {
            var controller = mounted();
            var scope = ScrollScope.enclosing(harness.element("row0")).orElseThrow();

            assertFalse(scope.nudge(40, 0));
            assertEquals(0, controller.position().offsetX());
        }
    }
}
