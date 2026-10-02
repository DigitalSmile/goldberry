package dev.goldberry.paint.border;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Border;
import dev.goldberry.css.Border.Side;
import dev.goldberry.css.Corners;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.render.model.LogicalPoint;

/// Where a dashed or dotted side puts its ink: the run it is drawn along, and
/// the pattern stretched to fit it.
///
/// Numbers rather than pixels, for `paint.shadow`'s reason: a pattern that is
/// off by one gap draws a picture that looks almost right, and only the
/// arithmetic says which corner went bare.
class BorderPatternTest {

    private static final int RED = 0xFFFF0000;

    private static final double EPSILON = 1e-6;

    private static Path.Segment.MoveTo start(BorderPattern.Run run) {
        return (Path.Segment.MoveTo) run.path().segments().getFirst();
    }

    private static Path.Segment last(BorderPattern.Run run) {
        return run.path().segments().getLast();
    }

    @Nested
    @DisplayName("the run")
    class Runs {

        @Test
        @DisplayName("a dashed top side on square corners runs the whole width, down its middle")
        void dashedTop() {
            var run =
                    BorderPattern.dashedRun(Border.all(2, RED, Border.Style.DASHED), Side.TOP, 60, 40, Corners.SQUARE);

            assertEquals(new Path.Segment.MoveTo(0, 1), start(run));
            assertEquals(new Path.Segment.LineTo(60, 1), last(run));
            assertEquals(60, run.length(), EPSILON);
            assertFalse(run.roundStart());
            assertFalse(run.roundEnd());
        }

        @Test
        @DisplayName("a dashed left side runs between the top and bottom sides, upwards")
        void dashedLeft() {
            var border = new Border(
                    new Border.Line(3, RED), Border.Line.NONE, new Border.Line(5, RED), new Border.Line(2, RED));
            var run = BorderPattern.dashedRun(border, Side.LEFT, 60, 40, Corners.SQUARE);

            assertEquals(new Path.Segment.MoveTo(1, 35), start(run), "clockwise, from the bottom");
            assertEquals(new Path.Segment.LineTo(1, 3), last(run));
            assertEquals(32, run.length(), EPSILON);
        }

        @Test
        @DisplayName("a dotted side ends where its centreline meets its neighbour's")
        void dottedTop() {
            var run =
                    BorderPattern.dottedRun(Border.all(4, RED, Border.Style.DOTTED), Side.TOP, 60, 40, Corners.SQUARE);

            assertEquals(new Path.Segment.MoveTo(2, 2), start(run));
            assertEquals(new Path.Segment.LineTo(58, 2), last(run));
            assertEquals(56, run.length(), EPSILON);
        }

        @Test
        @DisplayName("on a rounded corner a run starts half-way round the arc, which it shares")
        void rounded() {
            var run =
                    BorderPattern.dashedRun(Border.all(2, RED, Border.Style.DASHED), Side.TOP, 60, 40, Corners.all(11));

            assertTrue(run.roundStart());
            assertTrue(run.roundEnd());
            // The arc is the corner's radius less half the width, about the
            // corner's own centre, and the run starts at its 45° point.
            var radius = 10.0;
            var start = start(run);
            assertEquals(11 - radius * Math.sqrt(0.5), start.x(), EPSILON);
            assertEquals(11 - radius * Math.sqrt(0.5), start.y(), EPSILON);
            // Two eighths of a circle and the straight part, as flattened.
            assertEquals(60 - 22 + radius * Math.PI / 2, run.length(), 0.2);
        }

        @Test
        @DisplayName("a radius smaller than half the width is a square corner to the run")
        void tinyRadius() {
            var run =
                    BorderPattern.dashedRun(Border.all(8, RED, Border.Style.DASHED), Side.TOP, 60, 40, Corners.all(3));

            assertFalse(run.roundStart());
            assertEquals(new Path.Segment.MoveTo(0, 4), start(run));
        }
    }

    @Nested
    @DisplayName("dashes")
    class Dashes {

        @Test
        @DisplayName("a side starts and ends on a whole dash, with the gaps stretched to fit")
        void fitted() {
            var run =
                    BorderPattern.dashedRun(Border.all(2, RED, Border.Style.DASHED), Side.TOP, 60, 40, Corners.SQUARE);

            var dash = BorderPattern.dashes(run, 2, false, false);

            // Six-pixel dashes: six of them and five gaps of 4.8 make 60.
            assertEquals(6, dash.pattern().getFirst(), EPSILON);
            var gap = dash.pattern().get(1);
            assertEquals(4.8, gap, EPSILON);
            assertEquals(0, dash.offset(), EPSILON);
            assertEquals(60, 6 * 6 + 5 * gap, EPSILON);
        }

        @Test
        @DisplayName("at a shared corner the side starts half-way through a dash")
        void halfAtSharedCorners() {
            var run = new BorderPattern.Run(Path.line(0, 0, 60, 0), 60, true, true);

            var dash = BorderPattern.dashes(run, 2, true, true);

            assertEquals(3, dash.offset(), EPSILON, "the first dash is three pixels, half of six");
            var gap = dash.pattern().get(1);
            var interior = Math.round((60 - 6 - gap) / (6 + gap));
            assertEquals(60, 3 + 3 + interior * 6 + (interior + 1) * gap, EPSILON);
        }

        @Test
        @DisplayName("a side too short for two dashes and a gap is drawn whole")
        void shortSide() {
            var run = new BorderPattern.Run(Path.line(0, 0, 10, 0), 10, false, false);

            assertEquals(Dash.NONE, BorderPattern.dashes(run, 2, false, false));
        }
    }

    @Nested
    @DisplayName("dots")
    class Dots {

        @Test
        @DisplayName("dots are two widths apart, with one on each corner")
        void spaced() {
            var run = new BorderPattern.Run(Path.line(2, 2, 58, 2), 56, false, false);

            var dots = BorderPattern.dots(run, 4, true);

            assertEquals(8, dots.size(), "56 pixels is seven gaps of eight, so eight dots");
            assertEquals(new LogicalPoint(2, 2), dots.getFirst());
            assertEquals(new LogicalPoint(58, 2), dots.getLast());
            assertEquals(10, dots.get(1).x(), EPSILON);
        }

        @Test
        @DisplayName("the last dot is left to the next side when that side is dotted too")
        void sharedEnd() {
            var run = new BorderPattern.Run(Path.line(2, 2, 58, 2), 56, false, false);

            var dots = BorderPattern.dots(run, 4, false);

            assertEquals(7, dots.size());
            assertEquals(50, dots.getLast().x(), EPSILON);
        }

        @Test
        @DisplayName("a run shorter than one dot is one dot, in its middle")
        void tiny() {
            var run = new BorderPattern.Run(Path.line(0, 0, 2, 0), 2, false, false);

            assertEquals(List.of(new LogicalPoint(1, 0)), BorderPattern.dots(run, 4, true));
        }
    }
}
