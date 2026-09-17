package io.github.digitalsmile.goldberry.paint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;

/// An immutable outline in logical coordinates — a line, a curve, a ring, a
/// polygon.
///
/// ## Why this exists
///
/// [Frame] could fill a rectangle and nothing else without reaching for the
/// rasterizer's own path type, so an ellipse, a polyline or an arrowhead forced
/// an application into `:natives` — a module whose types are not meant to leave
/// it. Five widgets in this toolkit did exactly that, and so did the first
/// application built on it. This is the type that closes that (ADR-0277,
/// `docs/gaps.md` G1).
///
/// ## It is a value, and the rasterizer's path is not
///
/// A `BlendPath` is a native allocation with a confined `Arena` and a thread it
/// belongs to. That is right for a binding and wrong for something a widget
/// builds in the middle of a paint call: `:core` already worked around it by
/// pooling one path per paint walk and threading it through the painter's
/// signature, and `:widgets` did not, so a chart opened up to four arenas every
/// frame to draw the same four shapes.
///
/// A `Path` costs two Java arrays. [Frame] keeps a single scratch `BlendPath`
/// and replays paths into it, so building one allocates no native memory at all
/// and the pooling nobody remembered to do happens once, in the one place that
/// can see every drawing call.
///
/// ## Storage, and why it is not a list of records
///
/// Two parallel arrays: a verb per segment, and the coordinates those verbs
/// consume. Replaying is a loop over primitives with nothing boxed — which
/// matters, because replay happens once per shape per frame and the segment list
/// of a smoothed chart line runs to hundreds.
///
/// [#segments()] is the readable view, built on demand. It is for *inspecting* a
/// path — asserting on one in a test, writing one out as SVG — and deliberately
/// not what drawing uses.
///
/// ## Coordinates
///
/// Logical pixels, and every one of them must be finite. A NaN is refused where
/// it is written rather than passed on: Blend2D accepts one and fills the shape
/// with nothing, which looks exactly like arithmetic that went wrong three
/// methods earlier.
public final class Path {

    /// `4 * (sqrt(2) - 1) / 3` — the control-point distance that best fits a
    /// **quarter** circle.
    ///
    /// The approximation is good to about one part in 10,000 of the radius over
    /// 90° and an order of magnitude worse over 180°, which is why every sweep
    /// below is cut into quarters rather than drawn as one curve — the difference
    /// between invisible and visible on a 16px ring (ADR-0050, ADR-0064).
    private static final double KAPPA = 0.5522847498307933;

    private static final byte MOVE_TO = 0;
    private static final byte LINE_TO = 1;
    private static final byte QUAD_TO = 2;
    private static final byte CUBIC_TO = 3;
    private static final byte ARC_TO = 4;
    private static final byte CLOSE = 5;

    /// The low bits of a verb say what it is; the high bits carry an arc's two
    /// flags, which would otherwise be two doubles holding 0 or 1.
    private static final byte KIND_MASK = 0b0000_1111;

    private static final byte LARGE_ARC = 0b0001_0000;
    private static final byte SWEEP = 0b0010_0000;

    /// How many coordinates a verb consumes. An arc takes five — `rx`, `ry`,
    /// `rotation`, `x`, `y` — because its two booleans are in the verb byte.
    ///
    /// A switch rather than the array it used to be: a verb is masked out of a
    /// byte, and an array indexed by it has a bound nothing at the index proves.
    /// A switch over the six verbs has no bound to prove, and its default is the
    /// same refusal the callers' own switches make.
    private static int coordinates(byte kind) {
        return switch (kind) {
            case MOVE_TO, LINE_TO -> 2;
            case QUAD_TO -> 4;
            case CUBIC_TO -> 6;
            case ARC_TO -> 5;
            case CLOSE -> 0;
            default -> throw new IllegalStateException("unknown path verb " + kind);
        };
    }

    /// A path with nothing in it. Filling or stroking it draws nothing, which is
    /// what a chart with no data should do rather than refusing to paint.
    public static final Path EMPTY = new Path(new byte[0], new double[0]);

    private final byte[] verbs;
    private final double[] coords;

    private Path(byte[] verbs, double[] coords) {
        this.verbs = verbs;
        this.coords = coords;
    }

    /// A path built segment by segment.
    public static Builder builder() {
        return new Builder();
    }

    /// A single straight segment.
    public static Path line(double x1, double y1, double x2, double y2) {
        return builder().moveTo(x1, y1).lineTo(x2, y2).build();
    }

    /// A run of straight segments through `points`, optionally closed back to the
    /// first.
    ///
    /// Takes the toolkit's own [LogicalPoint] — the type a hit test already hands
    /// back — rather than a pair of arrays. A caller working in `double`s, which
    /// is every chart in this repository, wants [#builder()] instead: the point
    /// list is for the case where the points already exist as points.
    ///
    /// @throws IllegalArgumentException if `points` is empty
    public static Path polyline(List<LogicalPoint> points, boolean closed) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a polyline needs at least one point");
        }
        var first = points.getFirst();
        var builder = builder().moveTo(first.x(), first.y());
        for (var point : points.subList(1, points.size())) {
            builder.lineTo(point.x(), point.y());
        }
        return closed ? builder.close().build() : builder.build();
    }

    /// A closed rectangle.
    ///
    /// Four `lineTo`s and a close, which is the exact point sequence a square
    /// [#roundRect] emits — so a caller who rounds a corner later gets the same
    /// drawing with one corner changed rather than a different one.
    public static Path rect(double x, double y, double width, double height) {
        return builder()
                .moveTo(x, y)
                .lineTo(x + width, y)
                .lineTo(x + width, y + height)
                .lineTo(x, y + height)
                .close()
                .build();
    }

    /// A rectangle with a radius on every corner.
    public static Path roundRect(double x, double y, double width, double height, double radius) {
        return roundRect(x, y, width, height, Corners.all(radius));
    }

    /// A rectangle with **a radius per corner** — CSS's `border-radius: 7px 7px 0 0`.
    ///
    /// One drawing for both cases rather than a rounded path and a square one:
    /// the uniform case emits exactly the point sequence the single-radius
    /// version always did, which is what says the four corners did not move
    /// (ADR-0216). A square corner is a `lineTo` into the corner point and no
    /// cubic at all — a degenerate zero-length curve would otherwise be handed to
    /// the rasterizer on every square box in the window.
    ///
    /// The corners are fitted to the box first, so a pair that together overrun
    /// an edge is scaled down in proportion rather than crossing over — see
    /// [Corners#fittedTo].
    public static Path roundRect(double x, double y, double width, double height, Corners corners) {
        Objects.requireNonNull(corners, "corners");
        var fitted = corners.fittedTo(width, height);
        if (fitted.isSquare()) {
            return rect(x, y, width, height);
        }

        var right = x + width;
        var bottom = y + height;
        var topLeft = fitted.topLeft();
        var topRight = fitted.topRight();
        var bottomRight = fitted.bottomRight();
        var bottomLeft = fitted.bottomLeft();

        var builder = builder().moveTo(x + topLeft, y).lineTo(right - topRight, y);
        if (topRight > 0) {
            var c = topRight * KAPPA;
            builder.cubicTo(right - topRight + c, y, right, y + topRight - c, right, y + topRight);
        }
        builder.lineTo(right, bottom - bottomRight);
        if (bottomRight > 0) {
            var c = bottomRight * KAPPA;
            builder.cubicTo(
                    right, bottom - bottomRight + c, right - bottomRight + c, bottom, right - bottomRight, bottom);
        }
        builder.lineTo(x + bottomLeft, bottom);
        if (bottomLeft > 0) {
            var c = bottomLeft * KAPPA;
            builder.cubicTo(x + bottomLeft - c, bottom, x, bottom - bottomLeft + c, x, bottom - bottomLeft);
        }
        builder.lineTo(x, y + topLeft);
        if (topLeft > 0) {
            var c = topLeft * KAPPA;
            builder.cubicTo(x, y + topLeft - c, x + topLeft - c, y, x + topLeft, y);
        }
        return builder.close().build();
    }

    /// A closed ellipse about `(cx, cy)`.
    ///
    /// Two half-arcs rather than four quarters, because an elliptic arc is a verb
    /// the rasterizer has: this is the point sequence every ring and marker dot in
    /// the widget catalogue already drew by hand. A full circle in a single arc is
    /// **not** expressible — the start and end points would coincide and the sweep
    /// would be undefined — which is why there are two of them.
    public static Path ellipse(double cx, double cy, double rx, double ry) {
        return builder()
                .moveTo(cx - rx, cy)
                .arcTo(rx, ry, 0, false, true, cx + rx, cy)
                .arcTo(rx, ry, 0, false, true, cx - rx, cy)
                .close()
                .build();
    }

    /// A closed circle about `(cx, cy)`.
    public static Path circle(double cx, double cy, double radius) {
        return ellipse(cx, cy, radius, radius);
    }

    /// An **open** arc of `radius` about `(cx, cy)`, running `sweep` radians from
    /// `start`.
    ///
    /// Angles are in radians, clockwise, with zero pointing right — the y axis
    /// points down, so this is the direction a clock's hands go on screen. A
    /// non-positive radius or a zero sweep gives [#EMPTY] rather than an error: a
    /// spinner at rest and a donut slice of no value are both ordinary states.
    ///
    /// Built from cubics, a quarter circle at a time, for [#KAPPA]'s reason.
    public static Path arc(double cx, double cy, double radius, double start, double sweep) {
        requireFinite(cx, "cx");
        requireFinite(cy, "cy");
        requireFinite(start, "start");
        requireFinite(sweep, "sweep");
        if (!(radius > 0) || sweep == 0) {
            return EMPTY;
        }

        var builder = builder().moveTo(cx + radius * Math.cos(start), cy + radius * Math.sin(start));

        // Whole quarters, then whatever is left. A segment is never more than 90
        // degrees, which is what keeps the cubic within a ten-thousandth of the
        // circle it is approximating.
        var remaining = sweep;
        var angle = start;
        var direction = Math.signum(sweep);
        while (Math.abs(remaining) > 1e-9) {
            var segment = direction * Math.min(Math.abs(remaining), Math.PI / 2);
            addArcSegment(builder, cx, cy, radius, angle, segment);
            angle += segment;
            remaining -= segment;
        }
        return builder.build();
    }

    /// One segment of at most a quarter circle.
    ///
    /// The control points sit on the tangents at each end, `k` of the radius along
    /// them — where `k` is [#KAPPA] scaled to the segment, because KAPPA is the
    /// answer for 90° and a shorter arc needs a proportionally shorter handle.
    private static void addArcSegment(
            Builder builder, double cx, double cy, double radius, double start, double sweep) {

        var end = start + sweep;
        var k = KAPPA * radius * (sweep / (Math.PI / 2));

        var x1 = cx + radius * Math.cos(start);
        var y1 = cy + radius * Math.sin(start);
        var x2 = cx + radius * Math.cos(end);
        var y2 = cy + radius * Math.sin(end);

        builder.cubicTo(
                x1 - k * Math.sin(start),
                y1 + k * Math.cos(start),
                x2 + k * Math.sin(end),
                y2 - k * Math.cos(end),
                x2,
                y2);
    }

    /// Whether this path holds no segments at all.
    public boolean isEmpty() {
        return verbs.length == 0;
    }

    /// How many segments this path holds.
    ///
    /// Segments, not vertices: a cubic is one of these and three of those.
    public int segmentCount() {
        return verbs.length;
    }

    /// This path as a list of segments — the readable view.
    ///
    /// Built on demand, and not what drawing uses: [Frame] replays the arrays
    /// underneath directly. This is for a test asserting on the shape a factory
    /// produced, and for a caller writing a path out as something else — SVG's
    /// `d` attribute is a `switch` over exactly these six records.
    public List<Segment> segments() {
        var segments = new ArrayList<Segment>(verbs.length);
        var at = 0;
        for (var verb : verbs) {
            var kind = (byte) (verb & KIND_MASK);
            segments.add(
                    switch (kind) {
                        case MOVE_TO -> new Segment.MoveTo(coords[at], coords[at + 1]);
                        case LINE_TO -> new Segment.LineTo(coords[at], coords[at + 1]);
                        case QUAD_TO -> new Segment.QuadTo(coords[at], coords[at + 1], coords[at + 2], coords[at + 3]);
                        case CUBIC_TO ->
                            new Segment.CubicTo(
                                    coords[at],
                                    coords[at + 1],
                                    coords[at + 2],
                                    coords[at + 3],
                                    coords[at + 4],
                                    coords[at + 5]);
                        case ARC_TO ->
                            new Segment.ArcTo(
                                    coords[at],
                                    coords[at + 1],
                                    coords[at + 2],
                                    (verb & LARGE_ARC) != 0,
                                    (verb & SWEEP) != 0,
                                    coords[at + 3],
                                    coords[at + 4]);
                        case CLOSE -> new Segment.Close();
                        default -> throw new IllegalStateException("unknown path verb " + kind);
                    });
            at += coordinates(kind);
        }
        return List.copyOf(segments);
    }

    /// Replays this path's segments onto `path`, **appending** to whatever is
    /// already there.
    ///
    /// Package-private, and that is the whole point of the class: `BlendPath` is
    /// a `:natives` type, [Frame] is in this package, and so the one place the two
    /// models meet is invisible from outside it. An application holds a `Path`;
    /// nothing it can name holds a `BlendPath` (ADR-0277).
    void replayInto(BlendPath path) {
        var at = 0;
        for (var verb : verbs) {
            var kind = (byte) (verb & KIND_MASK);
            switch (kind) {
                case MOVE_TO -> path.moveTo(coords[at], coords[at + 1]);
                case LINE_TO -> path.lineTo(coords[at], coords[at + 1]);
                case QUAD_TO -> path.quadTo(coords[at], coords[at + 1], coords[at + 2], coords[at + 3]);
                case CUBIC_TO ->
                    path.cubicTo(
                            coords[at], coords[at + 1], coords[at + 2], coords[at + 3], coords[at + 4], coords[at + 5]);
                case ARC_TO ->
                    path.ellipticArcTo(
                            coords[at],
                            coords[at + 1],
                            coords[at + 2],
                            (verb & LARGE_ARC) != 0,
                            (verb & SWEEP) != 0,
                            coords[at + 3],
                            coords[at + 4]);
                case CLOSE -> path.closeSubPath();
                default -> throw new IllegalStateException("unknown path verb " + kind);
            }
            at += coordinates(kind);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Path path && Arrays.equals(verbs, path.verbs) && Arrays.equals(coords, path.coords);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(verbs) + Arrays.hashCode(coords);
    }

    @Override
    public String toString() {
        return "Path[" + verbs.length + " segments]";
    }

    /// One segment of a path.
    ///
    /// Sealed with six members, so a `switch` over one needs no default branch and
    /// a seventh kind — were there ever one — would stop every consumer compiling
    /// rather than falling quietly into a case that draws the wrong thing.
    public sealed interface Segment {

        /// Starts a new sub-path at `(x, y)` — SVG's `M`.
        record MoveTo(double x, double y) implements Segment {}

        /// A straight segment to `(x, y)` — SVG's `L`.
        record LineTo(double x, double y) implements Segment {}

        /// A quadratic curve through one control point — SVG's `Q`.
        record QuadTo(double cx, double cy, double x, double y) implements Segment {}

        /// A cubic curve through two control points — SVG's `C`.
        record CubicTo(double c1x, double c1y, double c2x, double c2y, double x, double y) implements Segment {}

        /// An elliptic arc to `(x, y)` — SVG's `A`.
        ///
        /// `rotation` is in **radians**, where an SVG document writes degrees. The
        /// conversion is the caller's, and it is stated here because a path drawn
        /// with 45 where 0.785 was meant produces a shape that is subtly wrong
        /// rather than absent.
        record ArcTo(double rx, double ry, double rotation, boolean largeArc, boolean sweep, double x, double y)
                implements Segment {}

        /// Closes the current sub-path — SVG's `Z`.
        record Close() implements Segment {}
    }

    /// Builds a [Path] segment by segment.
    ///
    /// Mutable, single-use and not thread-safe — it is a local variable inside a
    /// paint call, and [#build()] is where the value begins. The arrays grow
    /// geometrically and are trimmed once, so a path of three hundred segments
    /// costs a handful of copies rather than three hundred.
    public static final class Builder {

        private byte[] verbs = new byte[16];
        private double[] coords = new double[64];
        private int verbCount;
        private int coordCount;

        private Builder() {}

        /// Starts a new sub-path at `(x, y)` — SVG's `M`.
        public Builder moveTo(double x, double y) {
            return add(MOVE_TO, x, y);
        }

        /// A straight segment to `(x, y)` — SVG's `L`.
        public Builder lineTo(double x, double y) {
            return add(LINE_TO, x, y);
        }

        /// A quadratic curve through control `(cx, cy)` to `(x, y)` — SVG's `Q`.
        public Builder quadTo(double cx, double cy, double x, double y) {
            return add(QUAD_TO, cx, cy, x, y);
        }

        /// A cubic curve through two controls to `(x, y)` — SVG's `C`.
        public Builder cubicTo(double c1x, double c1y, double c2x, double c2y, double x, double y) {
            return add(CUBIC_TO, c1x, c1y, c2x, c2y, x, y);
        }

        /// An elliptic arc to `(x, y)` — SVG's `A`, with `rotation` in **radians**.
        public Builder arcTo(
                double rx, double ry, double rotation, boolean largeArc, boolean sweep, double x, double y) {

            var verb = (byte) (ARC_TO | (largeArc ? LARGE_ARC : 0) | (sweep ? SWEEP : 0));
            return add(verb, rx, ry, rotation, x, y);
        }

        /// Closes the current sub-path — SVG's `Z`.
        ///
        /// **Not** the end of the path: that is [#build()]. A path has two
        /// different "closes", and giving them one name is how a caller draws a
        /// closing segment they did not ask for.
        public Builder close() {
            return add(CLOSE);
        }

        /// Appends every segment of `path` to this one.
        ///
        /// What a shape made of many sub-paths is built from — a donut of eight
        /// slices, a dashed ring — without replaying it through the rasterizer
        /// eight times.
        public Builder append(Path path) {
            Objects.requireNonNull(path, "path");
            ensureVerbs(path.verbs.length);
            ensureCoords(path.coords.length);
            System.arraycopy(path.verbs, 0, verbs, verbCount, path.verbs.length);
            System.arraycopy(path.coords, 0, coords, coordCount, path.coords.length);
            verbCount += path.verbs.length;
            coordCount += path.coords.length;
            return this;
        }

        /// Whether nothing has been added yet.
        public boolean isEmpty() {
            return verbCount == 0;
        }

        /// The path built so far.
        ///
        /// The builder stays usable afterwards, and the returned value does not
        /// change if it is added to — the arrays are trimmed copies. That is what
        /// lets a chart build a line, take it, and go on to build the fill under
        /// it from the same points.
        public Path build() {
            if (verbCount == 0) {
                return EMPTY;
            }
            return new Path(Arrays.copyOf(verbs, verbCount), Arrays.copyOf(coords, coordCount));
        }

        /// The four arities the six verbs need, written out rather than taken as
        /// varargs.
        ///
        /// A `double...` here would allocate an array per segment, and a smoothed
        /// chart line is hundreds of segments per frame — which would put back, in
        /// the builder, most of what pooling the native path took out.
        private Builder add(byte verb) {
            ensureVerbs(1);
            verbs[verbCount++] = verb;
            return this;
        }

        private Builder add(byte verb, double a, double b) {
            requireFinite(a);
            requireFinite(b);
            ensureVerbs(1);
            ensureCoords(2);
            verbs[verbCount++] = verb;
            coords[coordCount++] = a;
            coords[coordCount++] = b;
            return this;
        }

        private Builder add(byte verb, double a, double b, double c, double d) {
            requireFinite(c);
            requireFinite(d);
            add(verb, a, b);
            ensureCoords(2);
            coords[coordCount++] = c;
            coords[coordCount++] = d;
            return this;
        }

        private Builder add(byte verb, double a, double b, double c, double d, double e) {
            requireFinite(e);
            add(verb, a, b, c, d);
            ensureCoords(1);
            coords[coordCount++] = e;
            return this;
        }

        private Builder add(byte verb, double a, double b, double c, double d, double e, double f) {
            requireFinite(e);
            requireFinite(f);
            add(verb, a, b, c, d);
            ensureCoords(2);
            coords[coordCount++] = e;
            coords[coordCount++] = f;
            return this;
        }

        private void ensureVerbs(int more) {
            if (verbCount + more > verbs.length) {
                verbs = Arrays.copyOf(verbs, Math.max(verbs.length * 2, verbCount + more));
            }
        }

        private void ensureCoords(int more) {
            if (coordCount + more > coords.length) {
                coords = Arrays.copyOf(coords, Math.max(coords.length * 2, coordCount + more));
            }
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            // Refused here rather than passed on: Blend2D takes a NaN and fills
            // nothing, which is indistinguishable from a shape whose arithmetic
            // went wrong several methods earlier.
            throw new IllegalArgumentException(name + " must be a finite number, not " + value);
        }
    }

    /// The same check without a name for the value, for the per-coordinate case.
    ///
    /// A named variant would mean building the name on every coordinate of every
    /// segment — an eagerly evaluated string argument that is thrown away
    /// whenever the check passes, which is always.
    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("a path coordinate must be a finite number, not " + value);
        }
    }
}
