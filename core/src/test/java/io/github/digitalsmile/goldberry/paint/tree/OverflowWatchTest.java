package io.github.digitalsmile.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.overflow.OverflowLog;
import io.github.digitalsmile.goldberry.paint.overflow.Overrun;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// A box that does not fit says so — [ADR-0375].
///
/// The gap this closes had been open since `flex-shrink` landed: a control
/// pushed off the edge of a window is silent, and looks exactly like a control
/// that was never built. What is asserted here is that it is no longer silent,
/// that it is silent when the clipping is the point, and that it says it once.
class OverflowWatchTest {

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(200, 200, 1.0f);
        OverflowLog.forget();
    }

    @AfterEach
    void tearDown() {
        if (target != null) {
            target.end();
        }
        OverflowLog.forget();
    }

    /// Three 80pt children that may not shrink, in a 200pt row: 240 into 200.
    private static Box tooWide() {
        return Box.of()
                .direction(FlexDirection.ROW)
                .size(Length.points(200), Length.points(100))
                .children(fixed(), fixed(), fixed());
    }

    private static Box fixed() {
        return Box.of().size(Length.points(80), Length.points(20)).shrink(0);
    }

    @Test
    @DisplayName("a row that does not fit its window is reported")
    void overrunIsReported() {
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), tooWide());
            assertEquals(1, OverflowLog.reported().size(), "one report, naming the box that did not fit");
            var said = OverflowLog.reported().getFirst().toString();
            assertTrue(said.contains("overruns"), said);
        }
    }

    @Test
    @DisplayName("and reported once, however many frames it survives")
    void reportedOnce() {
        try (var tree = RenderTree.create()) {
            for (var frame = 0; frame < 5; frame++) {
                tree.update(target.frame(), tooWide());
            }
            assertEquals(1, OverflowLog.reported().size(), "a layout is recomputed per frame; the warning is not");
        }
    }

    @Test
    @DisplayName("a row that fits says nothing")
    void fittingIsSilent() {
        try (var tree = RenderTree.create()) {
            tree.update(
                    target.frame(),
                    Box.of()
                            .direction(FlexDirection.ROW)
                            .size(Length.points(200), Length.points(100))
                            .children(fixed(), fixed()));
            assertEquals(List.of(), OverflowLog.reported());
        }
    }

    @Test
    @DisplayName("and neither does a box that clips on purpose")
    void clippingIsNotOverflowing() {
        // What a `scroll` viewport is: content longer than the box, cut at the
        // edge, and moved by an offset. Reporting that would be reporting the
        // widget working.
        try (var tree = RenderTree.create()) {
            tree.update(target.frame(), tooWide().overflow(Overflow.HIDDEN));
            assertEquals(List.of(), OverflowLog.reported());
        }
    }

    @Test
    @DisplayName("nor a child that was placed rather than flowed")
    void absoluteChildrenAreNotFlowed() {
        // A tab's underline is pinned across the bottom of its header and a
        // popover's arrow hangs off its panel: both are laid out where their
        // insets say, and neither overran anything.
        try (var tree = RenderTree.create()) {
            tree.update(
                    target.frame(),
                    Box.of()
                            .direction(FlexDirection.ROW)
                            .size(Length.points(200), Length.points(100))
                            .children(fixed(), fixed(), fixed().position(Position.ABSOLUTE)));
            assertEquals(List.of(), OverflowLog.reported());
        }
    }

    @Nested
    @DisplayName("the arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("a child inside its container is not an overrun")
        void inside() {
            assertEquals(
                    null, Overrun.between("a", "b", LogicalRect.of(0, 0, 100, 100), LogicalRect.of(10, 10, 80, 80)));
        }

        @Test
        @DisplayName("a fraction of a pixel is not one either")
        void fractional() {
            // A percentage width and a rounded layout disagree in the last digit
            // on almost every frame.
            assertEquals(
                    null,
                    Overrun.between("a", "b", LogicalRect.of(0, 0, 100, 100), LogicalRect.of(0, 0, 100.05f, 100)));
        }

        @Test
        @DisplayName("nor is a pixel or two, which is what layout rounding and a tight line box produce")
        void aPixelOrTwo() {
            // Measured rather than supposed: of 688 reports from the showcase's
            // own suite, 385 overran by more than a pixel and only 23 by more
            // than two. The cliff is there because layout is rounded onto the
            // device pixel grid and a line box may be shorter than the face's
            // natural leading ([ADR-0394]).
            assertEquals(
                    null, Overrun.between("a", "b", LogicalRect.of(0, 0, 100, 100), LogicalRect.of(0, 0, 102, 102)));
            assertNotNull(
                    Overrun.between("a", "b", LogicalRect.of(0, 0, 100, 100), LogicalRect.of(0, 0, 102.5f, 100)),
                    "past two pixels it is reported again");
        }

        @Test
        @DisplayName("a container with no size cannot be overrun, because nothing could fit in it")
        void aZeroSizedContainer() {
            // A slider's ticks hang off a zero-width mark. Every one of them
            // overruns it by its own whole width, which says nothing at all.
            assertEquals(
                    null,
                    Overrun.between(
                            "a box", "`slider-tick`", LogicalRect.of(133, 0, 0, 0), LogicalRect.of(-1, -2, 2, 4)));
            assertEquals(null, Overrun.between("a", "b", LogicalRect.of(0, 0, 100, 0), LogicalRect.of(0, 0, 50, 20)));
        }

        @Test
        @DisplayName("a child that starts outside was placed there, not flowed there")
        void aChildPlacedOutside() {
            // Flow never produces a negative offset — a flowed child begins at
            // its container's content origin. A slider's thumb is centred across
            // a four-pixel groove and hangs six pixels out of it on both sides.
            assertEquals(
                    null,
                    Overrun.between(
                            "`slider-groove`",
                            "`slider-thumb`",
                            LogicalRect.of(0, 14, 266, 4),
                            LogicalRect.of(100, -6, 16, 16)));
        }

        @Test
        @DisplayName("and the distance is measured from the container's own edge")
        void distance() {
            var overrun = Overrun.between("a", "b", LogicalRect.of(5, 5, 100, 100), LogicalRect.of(0, 0, 130, 100));
            assertEquals(30, overrun.overrunX());
            assertEquals(0, overrun.overrunY());
        }

        @Test
        @DisplayName("the log says a shape once and a second shape twice")
        void deduplication() {
            assertTrue(OverflowLog.report(new Overrun("`row`", "`button`", 4, 0)));
            assertFalse(OverflowLog.report(new Overrun("`row`", "`button`", 40, 0)), "the distance is not the shape");
            assertTrue(OverflowLog.report(new Overrun("`row`", "`chip`", 4, 0)));
        }
    }
}
