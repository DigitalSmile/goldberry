package dev.goldberry.paint.border;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.css.Border;
import dev.goldberry.css.Corners;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.geom.Flattener;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.render.model.LogicalPoint;

/// Where the dashes and dots of a `dashed` or `dotted` border side go.
///
/// A styled side is drawn along its **run**: the line down the middle of the
/// side, from one corner to the next. A solid side is a filled region and never
/// comes here.
///
/// ## Where a run starts and ends
///
/// At a **rounded** corner the run starts half-way round the corner's arc, so
/// the two sides that meet there share the corner and each draws half of it.
/// The arc is the outer radius less half the side's width, so the run stays in
/// the middle of the band.
///
/// At a **square** corner a dashed side and a dotted side end differently:
///
/// - a dashed top or bottom side runs to the box's outer edge and owns the
///   corner, and a dashed left or right side runs between them. The corner is
///   a dash's end, as a browser draws it;
/// - a dotted side runs to the point where its centreline meets its
///   neighbour's, so a corner has one dot on it rather than two touching.
///
/// ## Fitting the pattern to the run
///
/// A dash is three times the side's width and so is the gap. A dot is as wide
/// as the side, and the dots are two widths apart, centre to centre. The gaps
/// are stretched or squeezed so that a run **starts and ends on ink**: a dash at
/// a square corner, half a dash at a shared rounded one (so that the corner
/// carries one dash, half from each side), and a dot at every corner.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
public final class BorderPattern {

    /// A dash's length and a gap's, as multiples of the side's width.
    static final double DASH = 3;

    /// The distance between two dots' centres, as a multiple of the width.
    static final double DOT_SPACING = 2;

    /// The cubic control length for a 45° arc of radius one, `4/3 · tan(π/16)`.
    private static final double KAPPA_45 = 4.0 / 3 * Math.tan(Math.PI / 16);

    private static final double EPSILON = 1e-9;

    private BorderPattern() {}

    /// One side's centreline, from the corner it starts at to the one it ends
    /// at, clockwise.
    ///
    /// @param path       the centreline, in the box's own coordinates
    /// @param length     how long it is, measured along what the dasher walks
    /// @param roundStart whether it starts half-way round a rounded corner,
    ///                   which it shares with the side before it
    /// @param roundEnd   whether it ends half-way round a rounded corner
    public record Run(Path path, double length, boolean roundStart, boolean roundEnd) {

        public Run {
            Objects.requireNonNull(path, "path");
        }
    }

    /// The run of `side` for a dashed side.
    ///
    /// @param width   the box's width, in logical pixels
    /// @param height  the box's height
    /// @param corners the box's corner radii, before fitting
    public static Run dashedRun(Border border, Border.Side side, double width, double height, Corners corners) {
        return run(border, side, width, height, corners, false);
    }

    /// The run of `side` for a dotted side, which ends on the corner's
    /// centreline point rather than at the box's edge.
    public static Run dottedRun(Border border, Border.Side side, double width, double height, Corners corners) {
        return run(border, side, width, height, corners, true);
    }

    /// The dash pattern for a run, fitted so that it starts and ends on a dash.
    ///
    /// @param halfStart whether the first dash is half a dash, because the side
    ///                  before shares the corner and draws the other half
    /// @param halfEnd   the same at the run's end
    /// @return the pattern, or [Dash#NONE] when the run is too short to hold
    ///         two dashes and a gap, which draws it whole
    public static Dash dashes(Run run, double lineWidth, boolean halfStart, boolean halfEnd) {
        Objects.requireNonNull(run, "run");
        var dash = DASH * lineWidth;
        var gap = DASH * lineWidth;
        var first = halfStart ? dash / 2 : dash;
        var last = halfEnd ? dash / 2 : dash;
        var free = run.length() - first - last;
        if (!(dash > 0) || free <= EPSILON) {
            return Dash.NONE;
        }
        var interior = Math.max(0, Math.round((free - gap) / (dash + gap)));
        var stretched = (free - interior * dash) / (interior + 1);
        while (stretched <= EPSILON && interior > 0) {
            interior--;
            stretched = (free - interior * dash) / (interior + 1);
        }
        if (stretched <= EPSILON) {
            return Dash.NONE;
        }
        // Started part-way into the first dash, so that it is `first` long.
        return Dash.of(dash, stretched).startedAt(dash - first);
    }

    /// The centres of a dotted run's dots, evenly spaced from its start to its
    /// end.
    ///
    /// @param ownEnd whether the last dot is this run's to draw. It is not when
    ///               the next side is dotted too and starts on the same point
    public static List<LogicalPoint> dots(Run run, double lineWidth, boolean ownEnd) {
        Objects.requireNonNull(run, "run");
        var flattened = Flattener.flatten(run.path());
        var length = run.length();
        if (length < lineWidth) {
            // Too short for two dots: one, in the middle.
            return pointsAt(flattened, new double[] {length / 2});
        }
        var intervals = Math.max(1, Math.round(length / (DOT_SPACING * lineWidth)));
        var spacing = length / intervals;
        var count = (int) intervals + (ownEnd ? 1 : 0);
        var at = new double[count];
        for (var i = 0; i < count; i++) {
            at[i] = Math.min(length, i * spacing);
        }
        return pointsAt(flattened, at);
    }

    private static Run run(
            Border border, Border.Side side, double width, double height, Corners corners, boolean toCentre) {
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(corners, "corners");
        var fitted = corners.fittedTo(width, height);
        var radii = new double[] {fitted.topLeft(), fitted.topRight(), fitted.bottomRight(), fitted.bottomLeft()};
        var top = border.top().width();
        var right = border.right().width();
        var bottom = border.bottom().width();
        var left = border.left().width();
        var half = border.side(side).width() / 2;
        // Corners are numbered clockwise from the top-left, so a side starts at
        // the corner with its own number and ends at the next.
        var s =
                switch (side) {
                    case TOP -> 0;
                    case RIGHT -> 1;
                    case BOTTOM -> 2;
                    case LEFT -> 3;
                };
        var startCorner = s;
        var endCorner = (s + 1) % 4;
        var startRadius = radii[startCorner] - half;
        var endRadius = radii[endCorner] - half;
        var roundStart = startRadius > EPSILON;
        var roundEnd = endRadius > EPSILON;

        // The straight part, from where the start corner gives it up to where
        // the end corner takes it over.
        double x0;
        double y0;
        double x1;
        double y1;
        switch (side) {
            case TOP -> {
                y0 = y1 = half;
                x0 = roundStart ? radii[0] : toCentre ? left / 2 : 0;
                x1 = roundEnd ? width - radii[1] : toCentre ? width - right / 2 : width;
            }
            case RIGHT -> {
                x0 = x1 = width - half;
                y0 = roundStart ? radii[1] : toCentre ? top / 2 : top;
                y1 = roundEnd ? height - radii[2] : toCentre ? height - bottom / 2 : height - bottom;
            }
            case BOTTOM -> {
                y0 = y1 = height - half;
                x0 = roundStart ? width - radii[2] : toCentre ? width - right / 2 : width;
                x1 = roundEnd ? radii[3] : toCentre ? left / 2 : 0;
            }
            case LEFT -> {
                x0 = x1 = half;
                y0 = roundStart ? height - radii[3] : toCentre ? height - bottom / 2 : height - bottom;
                y1 = roundEnd ? radii[0] : toCentre ? top / 2 : top;
            }
            default -> throw new IllegalStateException("a box has four sides, and " + side + " is not one");
        }

        var builder = Path.builder();
        // The side's outward normal, in degrees clockwise from three o'clock:
        // the angle at which both corner arcs touch the straight part.
        var normal = 270 + 90 * s;
        if (roundStart) {
            var centre = centre(startCorner, radii, width, height);
            var from = point(centre, startRadius, normal - 45);
            builder.moveTo(from[0], from[1]);
            arc(builder, centre, startRadius, normal - 45);
        } else {
            builder.moveTo(x0, y0);
        }
        builder.lineTo(x1, y1);
        if (roundEnd) {
            arc(builder, centre(endCorner, radii, width, height), endRadius, normal);
        }
        var path = builder.build();
        // Measured on the flattened path, which is what the dasher walks, so a
        // pattern fitted to this length ends where the dasher does.
        return new Run(path, measure(Flattener.flatten(path)), roundStart, roundEnd);
    }

    /// The length of a flattened path.
    private static double measure(Path flattened) {
        var length = 0.0;
        var x = 0.0;
        var y = 0.0;
        for (var segment : flattened.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    x = move.x();
                    y = move.y();
                }
                case Path.Segment.LineTo line -> {
                    length += Math.hypot(line.x() - x, line.y() - y);
                    x = line.x();
                    y = line.y();
                }
                default -> {
                    // A flattened run is one open sub-path of lines.
                }
            }
        }
        return length;
    }

    /// The centre of corner `corner`'s arc, corners numbered clockwise from the
    /// top-left.
    private static double[] centre(int corner, double[] radii, double width, double height) {
        var r = radii[corner];
        return switch (corner) {
            case 0 -> new double[] {r, r};
            case 1 -> new double[] {width - r, r};
            case 2 -> new double[] {width - r, height - r};
            default -> new double[] {r, height - r};
        };
    }

    private static double[] point(double[] centre, double radius, double degrees) {
        var radians = Math.toRadians(degrees);
        return new double[] {centre[0] + radius * Math.cos(radians), centre[1] + radius * Math.sin(radians)};
    }

    /// Appends the 45° of arc clockwise from `fromDegrees` as one cubic.
    private static void arc(Path.Builder builder, double[] centre, double radius, double fromDegrees) {
        var from = Math.toRadians(fromDegrees);
        var to = Math.toRadians(fromDegrees + 45);
        var control = KAPPA_45 * radius;
        var x0 = centre[0] + radius * Math.cos(from);
        var y0 = centre[1] + radius * Math.sin(from);
        var x3 = centre[0] + radius * Math.cos(to);
        var y3 = centre[1] + radius * Math.sin(to);
        // The tangent at an angle, travelling clockwise on screen: (-sin, cos).
        builder.cubicTo(
                x0 - control * Math.sin(from),
                y0 + control * Math.cos(from),
                x3 + control * Math.sin(to),
                y3 - control * Math.cos(to),
                x3,
                y3);
    }

    /// The points `at` the given distances along a flattened path, in order.
    private static List<LogicalPoint> pointsAt(Path flattened, double[] at) {
        var points = new ArrayList<LogicalPoint>(at.length);
        var next = 0;
        var walked = 0.0;
        var x = 0.0;
        var y = 0.0;
        for (var segment : flattened.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    x = move.x();
                    y = move.y();
                }
                case Path.Segment.LineTo line -> {
                    var step = Math.hypot(line.x() - x, line.y() - y);
                    while (next < at.length && at[next] <= walked + step + EPSILON) {
                        var t = step > EPSILON ? Math.clamp((at[next] - walked) / step, 0, 1) : 0;
                        points.add(
                                new LogicalPoint((float) (x + (line.x() - x) * t), (float) (y + (line.y() - y) * t)));
                        next++;
                    }
                    walked += step;
                    x = line.x();
                    y = line.y();
                }
                default -> {
                    // A flattened run is one open sub-path of lines.
                }
            }
        }
        // What rounding left past the end lands on it.
        while (next < at.length) {
            points.add(new LogicalPoint((float) x, (float) y));
            next++;
        }
        return List.copyOf(points);
    }
}
