package io.github.digitalsmile.goldberry.paint.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.paint.Dash;
import io.github.digitalsmile.goldberry.paint.Path;

/// The arithmetic that cuts a path into dashes, with no rasterizer under it.
///
/// This is the whole of dashing: Blend2D stores a dash array and never strokes
/// with it, so what comes out of here is what a dashed stroke *is* (ADR-0278).
/// Every length below is countable by hand, which is the only way a walk over
/// arc length can be checked at all.
class DasherTest {

    private static final double EPSILON = 1e-9;

    @Nested
    @DisplayName("what dashing does to a straight line")
    class Lines {

        @Test
        @DisplayName("a solid pattern gives back the very same path")
        void solidIsIdentity() {
            // Not an equal path -- the same one. This is what keeps a
            // dash-capable drawing call free for the strokes that are not
            // dashed, which is nearly all of them.
            var line = Path.line(0, 0, 100, 0);
            assertSame(line, Dasher.dash(line, Dash.NONE));
            assertSame(line, Dasher.dash(line, Dash.of(0, 0)));
        }

        @Test
        @DisplayName("an empty path is not walked")
        void emptyIsEmpty() {
            assertSame(Path.EMPTY, Dasher.dash(Path.EMPTY, Dash.of(4, 4)));
        }

        @Test
        @DisplayName("a line is cut into runs of the on length")
        void cutsIntoRuns() {
            // 20 long, 4 on and 4 off: on at 0-4, 8-12, 16-20. Three dashes,
            // each a move and a line.
            var dashed = Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4));

            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(0, 0),
                            new Path.Segment.LineTo(4, 0),
                            new Path.Segment.MoveTo(8, 0),
                            new Path.Segment.LineTo(12, 0),
                            new Path.Segment.MoveTo(16, 0),
                            new Path.Segment.LineTo(20, 0)),
                    dashed.segments());
        }

        @Test
        @DisplayName("a run that reaches the end of the path is cut short there")
        void thePathEndsTheRun() {
            // 6 long with 4 on and 4 off: one whole dash and then the gap runs
            // out of line. Nothing is drawn past the end.
            var dashed = Dasher.dash(Path.line(0, 0, 6, 0), Dash.of(4, 4));

            assertEquals(List.of(new Path.Segment.MoveTo(0, 0), new Path.Segment.LineTo(4, 0)), dashed.segments());
        }

        @Test
        @DisplayName("a line shorter than the first run is drawn whole")
        void shorterThanOneDash() {
            assertEquals(
                    List.of(new Path.Segment.MoveTo(0, 0), new Path.Segment.LineTo(3, 0)),
                    Dasher.dash(Path.line(0, 0, 3, 0), Dash.of(4, 4)).segments());
        }

        @Test
        @DisplayName("the pattern is walked in arc length, not in coordinates")
        void diagonalIsWalkedByLength() {
            // A 3-4-5 triangle: the segment is 5 long, so 5 on and 5 off puts
            // exactly one dash on it and it reaches the far end.
            var dashed = Dasher.dash(Path.line(0, 0, 3, 4), Dash.of(5, 5));

            assertEquals(2, dashed.segmentCount());
            var end = (Path.Segment.LineTo) dashed.segments().getLast();
            assertEquals(3, end.x(), EPSILON);
            assertEquals(4, end.y(), EPSILON);
        }
    }

    @Nested
    @DisplayName("the offset")
    class Offset {

        @Test
        @DisplayName("an offset starts the pattern part way in")
        void offsetSkipsAhead() {
            // Four in, the first on run is spent: the line starts in the gap and
            // the first dash is at 4-8.
            var dashed = Dasher.dash(Path.line(0, 0, 12, 0), Dash.of(4, 4).startedAt(4));

            // And nothing at the end: the walk stops at the path, so a run that
            // has only just turned on when the line runs out emits no bare move.
            assertEquals(List.of(new Path.Segment.MoveTo(4, 0), new Path.Segment.LineTo(8, 0)), dashed.segments());
        }

        @Test
        @DisplayName("a partial offset shortens the first run rather than moving it")
        void partialOffset() {
            // Two into a four-long on run: the first dash is the remaining two.
            var dashed = Dasher.dash(Path.line(0, 0, 12, 0), Dash.of(4, 4).startedAt(2));

            assertEquals(new Path.Segment.MoveTo(0, 0), dashed.segments().get(0));
            assertEquals(new Path.Segment.LineTo(2, 0), dashed.segments().get(1));
            assertEquals(new Path.Segment.MoveTo(6, 0), dashed.segments().get(2));
        }

        @Test
        @DisplayName("an offset of a whole period is no offset at all")
        void offsetWrapsAtThePeriod() {
            // What lets a marching-ants timer advance forever without the walk
            // counting to the number it reached.
            var plain = Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4));
            assertEquals(
                    plain, Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4).startedAt(8)));
            assertEquals(
                    plain, Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4).startedAt(800)));
        }

        @Test
        @DisplayName("a negative offset runs the pattern the other way")
        void negativeOffset() {
            assertEquals(
                    Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4).startedAt(4)),
                    Dasher.dash(Path.line(0, 0, 20, 0), Dash.of(4, 4).startedAt(-4)));
        }
    }

    @Nested
    @DisplayName("sub-paths and corners")
    class SubPaths {

        @Test
        @DisplayName("the pattern runs on around a corner rather than restarting")
        void cornersDoNotResetThePattern() {
            // SVG's rule. A rectangle drawn as four sides has one dash pattern
            // around it, not four -- otherwise every corner is a seam.
            var square = Path.builder().moveTo(0, 0).lineTo(6, 0).lineTo(6, 6).build();
            var dashed = Dasher.dash(square, Dash.of(4, 4));

            // On 0-4 along the top, off 4-8 which ends 2 down the right side,
            // then on again from there.
            var third = (Path.Segment.MoveTo) dashed.segments().get(2);
            assertEquals(6, third.x(), EPSILON);
            assertEquals(2, third.y(), EPSILON);
        }

        @Test
        @DisplayName("a new sub-path lifts the pen but keeps the pattern's place")
        void aMoveLiftsThePen() {
            // The gap between two sub-paths is not part of either one's length,
            // so the pattern does not advance across it -- but the dash does not
            // span it either.
            var two = Path.builder()
                    .moveTo(0, 0)
                    .lineTo(2, 0)
                    .moveTo(10, 0)
                    .lineTo(12, 0)
                    .build();
            var dashed = Dasher.dash(two, Dash.of(4, 4));

            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(0, 0),
                            new Path.Segment.LineTo(2, 0),
                            new Path.Segment.MoveTo(10, 0),
                            new Path.Segment.LineTo(12, 0)),
                    dashed.segments());
        }

        @Test
        @DisplayName("a closed sub-path comes back open")
        void closedComesBackOpen() {
            // A dashed ring is a sequence of arcs with gaps between them; there
            // is nothing left to close, and the join at the start point goes
            // with it.
            var dashed = Dasher.dash(Path.rect(0, 0, 10, 10), Dash.of(4, 4));

            assertFalse(dashed.segments().contains(new Path.Segment.Close()));
            assertTrue(dashed.segmentCount() > 0);
        }

        @Test
        @DisplayName("a close is walked as the segment back to the start")
        void closeIsWalked() {
            // The closing side is dashed like any other, rather than being drawn
            // whole or dropped.
            var triangle = Path.builder()
                    .moveTo(0, 0)
                    .lineTo(8, 0)
                    .lineTo(8, 8)
                    .close()
                    .build();
            var dashed = Dasher.dash(triangle, Dash.of(2, 2));

            var farthest = dashed.segments().stream()
                    .filter(segment -> segment instanceof Path.Segment.LineTo)
                    .map(Path.Segment.LineTo.class::cast)
                    .mapToDouble(Path.Segment.LineTo::y)
                    .max()
                    .orElseThrow();
            assertTrue(farthest > 0, "the closing hypotenuse was walked");
        }
    }

    @Nested
    @DisplayName("curves")
    class Curves {

        @Test
        @DisplayName("a dashed curve comes back as lines, and covers about half of it")
        void curvesAreFlattened() {
            // A circle of radius 10 is about 62.8 long, so an even on/off
            // pattern inks about half of it. Checked as a total length rather
            // than as points: where each dash lands on a flattened circle is
            // arithmetic nobody should have to reproduce to read this test.
            var circle = Path.circle(0, 0, 10);
            var dashed = Dasher.dash(circle, Dash.of(2, 2));

            var inked = inkedLength(dashed);
            var circumference = 2 * Math.PI * 10;
            assertTrue(
                    Math.abs(inked - circumference / 2) < circumference * 0.05,
                    () -> "about half of " + circumference + " is inked, and " + inked + " is not");
        }

        @Test
        @DisplayName("nothing curved survives dashing")
        void onlyMovesAndLines() {
            var dashed = Dasher.dash(Path.roundRect(0, 0, 40, 20, 8), Dash.of(3, 3));

            for (var segment : dashed.segments()) {
                assertTrue(
                        segment instanceof Path.Segment.MoveTo || segment instanceof Path.Segment.LineTo,
                        () -> "a dashed path holds only moves and lines, and this is " + segment);
            }
        }
    }

    @Nested
    @DisplayName("degenerate patterns")
    class Degenerate {

        @Test
        @DisplayName("a zero-length gap does not break the run in two")
        void zeroGapKeepsTheRunGoing() {
            // `4 0 4` is a solid eight. Breaking it would put two caps in the
            // middle of a dash, which a round cap makes visible.
            var dashed = Dasher.dash(Path.line(0, 0, 8, 0), Dash.of(4, 0, 4));

            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(0, 0),
                            new Path.Segment.LineTo(4, 0),
                            new Path.Segment.LineTo(8, 0)),
                    dashed.segments());
        }

        @Test
        @DisplayName("a zero-length dash is a dot, which is how a dotted line is written")
        void zeroLengthDashesAreDots() {
            // SVG's rule, and a real idiom rather than a degenerate case:
            // `stroke-dasharray="0 4"` with a round cap is a dotted line. The
            // zero-length run is emitted rather than skipped, and what a dot
            // looks like is then the cap's business.
            var dashed = Dasher.dash(Path.line(0, 0, 12, 0), Dash.of(0, 4));

            // Three dots, not four: the fourth would begin exactly where the
            // path ends, and a run that starts at the end is not drawn -- the
            // same rule that stops `offsetSkipsAhead` emitting a bare move.
            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(0, 0),
                            new Path.Segment.LineTo(0, 0),
                            new Path.Segment.MoveTo(4, 0),
                            new Path.Segment.LineTo(4, 0),
                            new Path.Segment.MoveTo(8, 0),
                            new Path.Segment.LineTo(8, 0)),
                    dashed.segments());
        }

        @Test
        @DisplayName("a pattern far longer than the path draws it whole or not at all")
        void patternLongerThanThePath() {
            assertEquals(
                    List.of(new Path.Segment.MoveTo(0, 0), new Path.Segment.LineTo(5, 0)),
                    Dasher.dash(Path.line(0, 0, 5, 0), Dash.of(100, 100)).segments());
            assertEquals(
                    List.of(),
                    Dasher.dash(Path.line(0, 0, 5, 0), Dash.of(100, 100).startedAt(100))
                            .segments());
        }
    }

    /// The total length of every line in a dashed path.
    private static double inkedLength(Path path) {
        var total = 0d;
        var x = 0d;
        var y = 0d;
        for (var segment : path.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    x = move.x();
                    y = move.y();
                }
                case Path.Segment.LineTo line -> {
                    total += Math.hypot(line.x() - x, line.y() - y);
                    x = line.x();
                    y = line.y();
                }
                default -> throw new AssertionError("a dashed path holds only moves and lines, not " + segment);
            }
        }
        return total;
    }
}
