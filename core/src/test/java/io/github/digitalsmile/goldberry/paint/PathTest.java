package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;

/// The geometry a path describes, with no rasterizer under it.
///
/// It is here rather than in a painting test for [io.github.digitalsmile.goldberry.css.Corners]'
/// reason: whether a quarter circle's control point sits at KAPPA of the radius,
/// whether a square corner emits a line instead of a degenerate curve, and
/// whether an arc is cut into quarters are all questions about numbers, and they
/// can be answered exactly. What pixels they produce is [Frame]'s, and the golden
/// images say it.
///
/// The point sequences asserted below are the ones [RoundRect] and `Arc` emitted
/// before this type existed. That is the claim this file exists to defend: no
/// golden image moved when the geometry did (ADR-0277).
class PathTest {

    /// `4 * (sqrt(2) - 1) / 3`, repeated here on purpose. A test that computed it
    /// from the same expression the code does would pass if both were wrong.
    private static final double KAPPA = 0.5522847498307933;

    private static final double EPSILON = 1e-12;

    @Nested
    @DisplayName("what a path may be")
    class Values {

        @Test
        @DisplayName("an empty path holds nothing and draws nothing")
        void empty() {
            assertTrue(Path.EMPTY.isEmpty());
            assertEquals(0, Path.EMPTY.segmentCount());
            assertEquals(List.of(), Path.EMPTY.segments());
            // A builder nobody added to is the same value, not merely a similar
            // one: a chart with no data must not produce a second empty path.
            assertEquals(Path.EMPTY, Path.builder().build());
        }

        @Test
        @DisplayName("a non-finite coordinate is refused where it is written")
        void nonFiniteIsRefused() {
            // Blend2D accepts a NaN and fills nothing, which looks exactly like
            // arithmetic that went wrong three methods earlier.
            assertThrows(IllegalArgumentException.class, () -> Path.builder().moveTo(Double.NaN, 0));
            assertThrows(IllegalArgumentException.class, () -> Path.builder().lineTo(0, Double.POSITIVE_INFINITY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Path.builder().cubicTo(0, 0, 0, 0, Double.NEGATIVE_INFINITY, 0));
            assertThrows(IllegalArgumentException.class, () -> Path.line(0, 0, Double.NaN, 0));
        }

        @Test
        @DisplayName("a refused coordinate leaves the builder as it was")
        void refusalDoesNotMutate() {
            var builder = Path.builder().moveTo(1, 2);
            assertThrows(IllegalArgumentException.class, () -> builder.cubicTo(3, 4, 5, 6, Double.NaN, 8));
            // One segment, not one and a half: the check runs before anything is
            // written, so a caught exception does not leave a path half-built.
            assertEquals(1, builder.build().segmentCount());
        }

        @Test
        @DisplayName("two paths written the same way are equal")
        void equality() {
            assertEquals(Path.line(0, 0, 10, 10), Path.line(0, 0, 10, 10));
            assertEquals(
                    Path.line(0, 0, 10, 10).hashCode(), Path.line(0, 0, 10, 10).hashCode());
            assertNotEquals(Path.line(0, 0, 10, 10), Path.line(0, 0, 10, 11));
            // Same coordinates, different verbs.
            assertNotEquals(
                    Path.builder().moveTo(0, 0).lineTo(1, 1).build(),
                    Path.builder().moveTo(0, 0).moveTo(1, 1).build());
        }
    }

    @Nested
    @DisplayName("the segment list")
    class Segments {

        @Test
        @DisplayName("every verb survives a round trip through segments()")
        void everyVerb() {
            var path = Path.builder()
                    .moveTo(1, 2)
                    .lineTo(3, 4)
                    .quadTo(5, 6, 7, 8)
                    .cubicTo(9, 10, 11, 12, 13, 14)
                    .arcTo(15, 16, 17, true, false, 18, 19)
                    .close()
                    .build();

            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(1, 2),
                            new Path.Segment.LineTo(3, 4),
                            new Path.Segment.QuadTo(5, 6, 7, 8),
                            new Path.Segment.CubicTo(9, 10, 11, 12, 13, 14),
                            new Path.Segment.ArcTo(15, 16, 17, true, false, 18, 19),
                            new Path.Segment.Close()),
                    path.segments());
        }

        @Test
        @DisplayName("an arc's two flags are carried, not lost")
        void arcFlags() {
            // The flags live in the verb byte rather than in the coordinate
            // array, which is the one place this encoding could silently drop
            // them -- and a large-arc flag dropped turns a ring into a lens.
            for (var largeArc : List.of(true, false)) {
                for (var sweep : List.of(true, false)) {
                    var path =
                            Path.builder().arcTo(1, 1, 0, largeArc, sweep, 2, 2).build();
                    var arc = assertInstanceOf(
                            Path.Segment.ArcTo.class, path.segments().getFirst());
                    assertEquals(largeArc, arc.largeArc());
                    assertEquals(sweep, arc.sweep());
                }
            }
        }

        @Test
        @DisplayName("segmentCount counts segments, not vertices")
        void countsSegments() {
            // A cubic is one of these and three of the rasterizer's.
            assertEquals(
                    2,
                    Path.builder()
                            .moveTo(0, 0)
                            .cubicTo(1, 1, 2, 2, 3, 3)
                            .build()
                            .segmentCount());
        }
    }

    @Nested
    @DisplayName("the straight factories")
    class Straight {

        @Test
        @DisplayName("a line is a move and a line")
        void line() {
            assertEquals(
                    List.of(new Path.Segment.MoveTo(1, 2), new Path.Segment.LineTo(3, 4)),
                    Path.line(1, 2, 3, 4).segments());
        }

        @Test
        @DisplayName("a polyline joins its points, and closes only when asked")
        void polyline() {
            var points = List.of(new LogicalPoint(0, 0), new LogicalPoint(10, 0), new LogicalPoint(10, 10));

            assertEquals(3, Path.polyline(points, false).segmentCount());
            assertEquals(4, Path.polyline(points, true).segmentCount());
            assertEquals(
                    new Path.Segment.Close(),
                    Path.polyline(points, true).segments().getLast());
        }

        @Test
        @DisplayName("a polyline of one point is a move and nothing else")
        void polylineOfOne() {
            // Not an error: a series filtered down to a single sample is an
            // ordinary state, and it should draw a dot rather than throw.
            assertEquals(
                    List.of(new Path.Segment.MoveTo(4, 5)),
                    Path.polyline(List.of(new LogicalPoint(4, 5)), false).segments());
        }

        @Test
        @DisplayName("a polyline of no points is a programming error")
        void polylineOfNone() {
            assertThrows(IllegalArgumentException.class, () -> Path.polyline(List.of(), false));
        }

        @Test
        @DisplayName("a rectangle is four corners and a close")
        void rect() {
            assertEquals(
                    List.of(
                            new Path.Segment.MoveTo(1, 2),
                            new Path.Segment.LineTo(11, 2),
                            new Path.Segment.LineTo(11, 22),
                            new Path.Segment.LineTo(1, 22),
                            new Path.Segment.Close()),
                    Path.rect(1, 2, 10, 20).segments());
        }
    }

    @Nested
    @DisplayName("rounded rectangles")
    class Rounded {

        @Test
        @DisplayName("a square-cornered round rect is exactly a rectangle")
        void squareIsARect() {
            // Not merely the same shape -- the same value. A degenerate
            // zero-length cubic per corner would otherwise be handed to the
            // rasterizer on every square box in the window.
            assertEquals(Path.rect(0, 0, 100, 50), Path.roundRect(0, 0, 100, 50, Corners.SQUARE));
            assertEquals(Path.rect(0, 0, 100, 50), Path.roundRect(0, 0, 100, 50, 0));
        }

        @Test
        @DisplayName("a uniform corner puts its controls at KAPPA of the radius")
        void kappa() {
            var path = Path.roundRect(0, 0, 100, 50, 10);
            var segments = path.segments();

            // Clockwise from the top-left corner's end, which is the order
            // RoundRect has always emitted.
            assertEquals(new Path.Segment.MoveTo(10, 0), segments.get(0));
            assertEquals(new Path.Segment.LineTo(90, 0), segments.get(1));

            var topRight = assertInstanceOf(Path.Segment.CubicTo.class, segments.get(2));
            var c = 10 * KAPPA;
            assertEquals(90 + c, topRight.c1x(), EPSILON);
            assertEquals(0, topRight.c1y(), EPSILON);
            assertEquals(100, topRight.c2x(), EPSILON);
            assertEquals(10 - c, topRight.c2y(), EPSILON);
            assertEquals(100, topRight.x(), EPSILON);
            assertEquals(10, topRight.y(), EPSILON);

            assertEquals(new Path.Segment.Close(), segments.getLast());
        }

        @Test
        @DisplayName("four rounded corners are four cubics; a mixed box is fewer")
        void onlyRoundCornersCurve() {
            assertEquals(4, cubics(Path.roundRect(0, 0, 100, 50, 8)));
            // `border-radius: 7px 7px 0 0` -- the group-box title case.
            assertEquals(2, cubics(Path.roundRect(0, 0, 100, 50, new Corners(7, 7, 0, 0))));
        }

        @Test
        @DisplayName("a radius too big for the box is fitted, not overrun")
        void fitted() {
            // CSS's own rule, and what makes `border-radius: 9999px` a pill
            // rather than a rendering error. Asserted on the corner's endpoint
            // rather than on path equality, because the fit is a multiplication
            // and half the shorter side is not exactly what comes back out.
            var corner = assertInstanceOf(
                    Path.Segment.CubicTo.class,
                    Path.roundRect(0, 0, 100, 50, 9999).segments().get(2));
            assertEquals(100, corner.x(), EPSILON);
            assertEquals(25, corner.y(), 1e-9);
        }

        private static long cubics(Path path) {
            return path.segments().stream()
                    .filter(segment -> segment instanceof Path.Segment.CubicTo)
                    .count();
        }
    }

    @Nested
    @DisplayName("ellipses and arcs")
    class Curved {

        @Test
        @DisplayName("an ellipse is two half arcs, because one cannot close")
        void ellipse() {
            // A full circle in a single elliptic arc has coincident endpoints and
            // an undefined sweep -- which is why every ring in the catalogue was
            // already drawn as two.
            var segments = Path.ellipse(50, 50, 20, 10).segments();

            assertEquals(new Path.Segment.MoveTo(30, 50), segments.get(0));
            assertEquals(new Path.Segment.ArcTo(20, 10, 0, false, true, 70, 50), segments.get(1));
            assertEquals(new Path.Segment.ArcTo(20, 10, 0, false, true, 30, 50), segments.get(2));
            assertEquals(new Path.Segment.Close(), segments.get(3));
        }

        @Test
        @DisplayName("a circle is an ellipse with one radius")
        void circle() {
            assertEquals(Path.ellipse(5, 6, 7, 7), Path.circle(5, 6, 7));
        }

        @Test
        @DisplayName("an arc that draws nothing is empty, not an error")
        void degenerateArc() {
            // A spinner at rest and a donut slice of no value are ordinary
            // states, and a window must not go down on either.
            assertSame(Path.EMPTY, Path.arc(0, 0, 10, 0, 0));
            assertSame(Path.EMPTY, Path.arc(0, 0, 0, 0, Math.PI));
            assertSame(Path.EMPTY, Path.arc(0, 0, -1, 0, Math.PI));
        }

        @Test
        @DisplayName("an arc is cut into quarters, and never more than one per 90 degrees")
        void quarters() {
            // KAPPA is the answer for a quarter circle and an order of magnitude
            // worse over a half, which is the difference between invisible and
            // visible on a 16px ring.
            assertEquals(1, cubics(Path.arc(0, 0, 10, 0, Math.PI / 2)));
            assertEquals(2, cubics(Path.arc(0, 0, 10, 0, Math.PI)));
            assertEquals(4, cubics(Path.arc(0, 0, 10, 0, 2 * Math.PI)));
            // Three quarters and a remainder.
            assertEquals(4, cubics(Path.arc(0, 0, 10, 0, 1.6 * Math.PI)));
        }

        @Test
        @DisplayName("an arc starts on the circle, at the angle it was given")
        void startsOnTheCircle() {
            // Zero points right, and y grows downward, so a quarter sweep from
            // zero ends at the bottom of the circle rather than the top.
            var segments = Path.arc(100, 100, 10, 0, Math.PI / 2).segments();
            var start = assertInstanceOf(Path.Segment.MoveTo.class, segments.getFirst());
            assertEquals(110, start.x(), EPSILON);
            assertEquals(100, start.y(), EPSILON);

            var quarter = assertInstanceOf(Path.Segment.CubicTo.class, segments.get(1));
            assertEquals(100, quarter.x(), EPSILON);
            assertEquals(110, quarter.y(), EPSILON);
        }

        @Test
        @DisplayName("a negative sweep runs the other way")
        void negativeSweep() {
            var quarter = assertInstanceOf(
                    Path.Segment.CubicTo.class,
                    Path.arc(100, 100, 10, 0, -Math.PI / 2).segments().get(1));
            assertEquals(100, quarter.x(), EPSILON);
            assertEquals(90, quarter.y(), EPSILON);
        }

        @Test
        @DisplayName("an arc is open — it is the caller who closes a slice")
        void arcIsOpen() {
            // A donut slice is two arcs and two lines, so closing here would put
            // a chord across every one of them.
            assertFalse(Path.arc(0, 0, 10, 0, Math.PI).segments().contains(new Path.Segment.Close()));
        }

        private static long cubics(Path path) {
            return path.segments().stream()
                    .filter(segment -> segment instanceof Path.Segment.CubicTo)
                    .count();
        }
    }

    @Nested
    @DisplayName("the builder")
    class Building {

        @Test
        @DisplayName("append copies another path's segments in order")
        void append() {
            var slice = Path.line(0, 0, 1, 1);
            var both = Path.builder().append(slice).append(slice).build();

            assertEquals(4, both.segmentCount());
            assertEquals(new Path.Segment.MoveTo(0, 0), both.segments().get(2));
        }

        @Test
        @DisplayName("appending nothing changes nothing")
        void appendEmpty() {
            assertEquals(
                    Path.line(0, 0, 1, 1),
                    Path.builder()
                            .append(Path.line(0, 0, 1, 1))
                            .append(Path.EMPTY)
                            .build());
        }

        @Test
        @DisplayName("a built path does not change when the builder goes on")
        void buildIsASnapshot() {
            // What lets a chart build a line, take it, and go on to build the
            // fill under it from the same points.
            var builder = Path.builder().moveTo(0, 0).lineTo(10, 0);
            var line = builder.build();
            builder.lineTo(10, 10).close();

            assertEquals(2, line.segmentCount());
            assertEquals(4, builder.build().segmentCount());
        }

        @Test
        @DisplayName("a builder grows past its initial arrays")
        void growth() {
            // Sixteen verbs and sixty-four coordinates are where it starts, and a
            // smoothed chart line is hundreds of segments.
            var builder = Path.builder().moveTo(0, 0);
            for (var index = 0; index < 500; index++) {
                builder.cubicTo(index, index, index, index, index, index);
            }
            var path = builder.build();

            assertEquals(501, path.segmentCount());
            assertEquals(
                    new Path.Segment.CubicTo(499, 499, 499, 499, 499, 499),
                    path.segments().getLast());
        }

        @Test
        @DisplayName("isEmpty is about what has been added, not what was built")
        void builderIsEmpty() {
            var builder = Path.builder();
            assertTrue(builder.isEmpty());
            assertFalse(builder.moveTo(0, 0).isEmpty());
        }
    }
}
