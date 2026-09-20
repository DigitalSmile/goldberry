package io.github.digitalsmile.goldberry.widgets.core.scroll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// §1's `scrollIntoView`, shipped as the API §1 words it as
/// (ADR-0120).
class ScrollControllerTest {

    private static final int VIEWPORT_HEIGHT = ScrollHarness.VIEWPORT_HEIGHT;

    /// Every harness a test made, closed together afterwards.
    ///
    /// A list and not a field, because one test below builds **two** — an
    /// unlimited reveal beside a limited one — and while each was closing the
    /// one before it, the first of the pair was leaked on every run.
    private final List<ScrollHarness> harnesses = new ArrayList<>();

    private ScrollHarness harness(Widget root) {
        return harness(root, "");
    }

    private ScrollHarness harness(Widget root, String css) {
        var made = new ScrollHarness(root, css);
        harnesses.add(made);
        return made;
    }

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void tearDown() {
        harnesses.forEach(ScrollHarness::close);
        harnesses.clear();
    }

    private static Widget document(ScrollController controller) {
        var rows = new ArrayList<Widget>();
        for (var i = 0; i < 30; i++) {
            rows.add(new Text("row " + i, Attributes.NONE.id("row" + i)));
        }
        return new Scroll(List.of(new Column(rows.toArray(Widget[]::new))), ScrollAxis.VERTICAL, Attributes.NONE)
                .controlledBy(controller);
    }

    @Nested
    @DisplayName("attaching")
    class Attaching {

        @Test
        @DisplayName("a controller with no viewport is inert rather than an error")
        void detachedIsInert() {
            var controller = new ScrollController();

            assertFalse(controller.isAttached());
            // It is perfectly ordinary for a controller to exist before the
            // `Scroll` that answers to it, so this must not throw.
            controller.scrollBy(0, 100);
            controller.reveal(LogicalRect.of(0, 0, 10, 10), LogicalRect.of(0, 0, 10, 10));
        }

        @Test
        @DisplayName("a viewport attaches when it is mounted")
        void attaches() {
            var controller = new ScrollController();
            harness(document(controller));

            assertTrue(controller.isAttached());
        }
    }

    @Nested
    @DisplayName("scrolling")
    class Scrolling {

        @Test
        @DisplayName("scrollBy moves the viewport, clamped like every other path")
        void scrollByMoves() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            var before = harness.rowRect("row0").top();

            controller.scrollBy(0, 40);
            harness.settle();

            assertEquals(before - 40, harness.rowRect("row0").top(), 0.5);
        }

        @Test
        @DisplayName("scrollBy cannot run off the end")
        void scrollByClamps() {
            var controller = new ScrollController();
            var harness = harness(document(controller));

            controller.scrollBy(0, 10_000);
            harness.settle();
            var atEnd = harness.rowRect("row0").top();
            controller.scrollBy(0, 10_000);
            harness.settle();

            assertEquals(atEnd, harness.rowRect("row0").top(), 0.01);
        }
    }

    @Nested
    @DisplayName("revealing")
    class Revealing {

        @Test
        @DisplayName("a row below the fold is brought to the near edge")
        void revealsFromBelow() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            var viewport = LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT);

            controller.reveal(harness.rowRect("row20"), viewport);
            harness.settle();

            var after = harness.rowRect("row20");
            assertTrue(
                    after.top() >= -1 && after.top() + after.size().height() <= VIEWPORT_HEIGHT + 1,
                    "row20 is at " + after.top() + ", still outside the viewport");
        }

        @Test
        @DisplayName("it scrolls the least it can, so the row lands at the edge it came from")
        void minimal() {
            var controller = new ScrollController();
            var harness = harness(document(controller));

            controller.reveal(harness.rowRect("row20"), LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            harness.settle();

            // Brought *up to* the bottom edge and no further: a reveal that
            // centred its target would throw away everything the user was
            // already looking at, and §1 asks only for it to be in view.
            var after = harness.rowRect("row20");
            assertTrue(
                    after.top() > VIEWPORT_HEIGHT / 2.0,
                    "the row was pulled further than it needed to be; it is at " + after.top());
        }

        @Test
        @DisplayName("a row already in view does not move anything")
        void alreadyVisible() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            var before = harness.rowRect("row0").top();

            controller.reveal(harness.rowRect("row1"), LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT));
            harness.settle();

            assertEquals(before, harness.rowRect("row0").top(), 0.01);
        }
    }

    /// §3.1: "`scrollIntoView` / programmatic: overlay duration" ([ADR-0363]).
    @Nested
    @DisplayName("gliding")
    class Gliding {

        @Test
        @DisplayName("a programmatic scroll is on its way part of the way through, and there at the end")
        void glides() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            var before = harness.rowRect("row0").top();

            controller.scrollBy(0, 100);
            harness.frame();
            harness.clock.advance(ScrollGlide.DURATION_MILLIS / 4);
            harness.frame();
            var partway = harness.rowRect("row0").top();
            harness.clock.advance(ScrollGlide.DURATION_MILLIS);
            harness.frame();

            assertTrue(partway < before - 1 && partway > before - 99, "not partway: " + partway);
            assertEquals(before - 100, harness.rowRect("row0").top(), 0.5);
        }

        @Test
        @DisplayName("the wheel takes over at once, from where the glide had got to")
        void wheelCancels() {
            var controller = new ScrollController();
            var harness = harness(document(controller));

            controller.scrollBy(0, 100);
            harness.frame();
            harness.clock.advance(ScrollGlide.DURATION_MILLIS / 4);
            harness.frame();
            harness.router.pointerWheel(100, 40, 0, 1, Modifiers.NONE);
            harness.frame();
            var afterWheel = harness.rowRect("row0").top();
            harness.clock.advance(ScrollGlide.DURATION_MILLIS);
            harness.frame();

            assertEquals(afterWheel, harness.rowRect("row0").top(), 0.01, "nothing kept gliding after the wheel");
        }

        @Test
        @DisplayName("a reveal asked again mid-glide measures where the row will be, and does not overshoot")
        void revealMidGlide() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            var viewport = LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT);

            controller.reveal(harness.rowRect("row20"), viewport);
            harness.frame();
            harness.clock.advance(ScrollGlide.DURATION_MILLIS / 3);
            harness.frame();
            controller.reveal(harness.rowRect("row20"), viewport);
            harness.settle();

            var after = harness.rowRect("row20");
            assertEquals(
                    VIEWPORT_HEIGHT,
                    after.top() + after.size().height(),
                    1.0,
                    "the row landed at the bottom edge, where one reveal puts it");
        }

        @Test
        @DisplayName("under reduced motion it jumps")
        void reducedMotionJumps() {
            var controller = new ScrollController();
            var harness = harness(document(controller));
            harness.renderer.reducedMotion(true);
            var before = harness.rowRect("row0").top();

            controller.scrollBy(0, 100);
            harness.frame();

            assertEquals(before - 100, harness.rowRect("row0").top(), 0.5);
        }
    }

    /// Cells that keep their width, so the grid is wider than the viewport.
    private static final String CELLS = "text { width: 60px; flex-shrink: 0 } scroll-content { width: 720px }";

    /// A grid wider and taller than the viewport, so a reveal has both axes to
    /// move on.
    private static Widget grid(ScrollController controller) {
        var rows = new ArrayList<Widget>();
        for (var r = 0; r < 30; r++) {
            var cells = new ArrayList<Widget>();
            for (var c = 0; c < 12; c++) {
                cells.add(new Text("cell " + r + "." + c + "   ", Attributes.NONE.id("cell" + r + "-" + c)));
            }
            rows.add(new Row(cells, Attributes.NONE));
        }
        return new Scroll(List.of(new Column(rows.toArray(Widget[]::new))), ScrollAxis.BOTH, Attributes.NONE)
                .controlledBy(controller);
    }

    /// A reveal limited to one axis ([ADR-0370]).
    @Nested
    @DisplayName("revealing along one axis")
    class OneAxis {

        @Test
        @DisplayName("both axes move by default, and a vertical reveal leaves the horizontal position alone")
        void vertical() {
            var viewport = LogicalRect.of(0, 0, 200, VIEWPORT_HEIGHT);

            var both = new ScrollController();
            var free = harness(grid(both), CELLS);
            both.reveal(free.rowRect("cell20-10"), viewport);
            free.settle();
            assertTrue(free.rowRect("cell0-0").left() < -1, "an unlimited reveal slid sideways too");

            var rows = new ScrollController();
            var limited = harness(grid(rows), CELLS);
            var leftBefore = limited.rowRect("cell0-0").left();
            rows.reveal(limited.rowRect("cell20-10"), viewport, ScrollAxis.VERTICAL);
            limited.settle();

            assertEquals(leftBefore, limited.rowRect("cell0-0").left(), 0.01, "the vertical reveal did not");
            var cell = limited.rowRect("cell20-10");
            assertTrue(
                    cell.top() >= -1 && cell.top() + cell.size().height() <= VIEWPORT_HEIGHT + 1, "the row is in view");
        }
    }
}
