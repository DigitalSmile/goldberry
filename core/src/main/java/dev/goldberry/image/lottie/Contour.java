package dev.goldberry.image.lottie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.goldberry.css.value.Affine;
import dev.goldberry.paint.Path;

/// One run of cubic segments: a start point and, per segment, two controls and
/// an end — the shape every Lottie path, rectangle, ellipse and star comes down
/// to before it is drawn.
///
/// A closed contour carries its closing segment like any other, so the length a
/// trim measures includes it, and [#closed()] only says whether the run ends in
/// a join or in two caps.
///
/// Mutable while it is built, and treated as a value after.
final class Contour {

    /// Chords per segment when a length is measured. Enough that a trim lands
    /// within a fraction of a pixel on a sticker-sized curve.
    private static final int STEPS = 16;

    /// The roundness of a quarter circle drawn with one cubic.
    static final double KAPPA = 0.5519150244935106;

    private double[] coords;
    private int size;
    private boolean closed;

    private Contour(double x, double y) {
        coords = new double[2 + 6 * 4];
        coords[0] = x;
        coords[1] = y;
        size = 2;
    }

    /// A contour starting at `(x, y)`.
    static Contour at(double x, double y) {
        return new Contour(x, y);
    }

    /// A cubic to `(x, y)`.
    Contour cubicTo(double c1x, double c1y, double c2x, double c2y, double x, double y) {
        if (size + 6 > coords.length) {
            coords = Arrays.copyOf(coords, coords.length * 2);
        }
        coords[size++] = c1x;
        coords[size++] = c1y;
        coords[size++] = c2x;
        coords[size++] = c2y;
        coords[size++] = x;
        coords[size++] = y;
        return this;
    }

    /// A straight segment to `(x, y)`, as a cubic whose controls sit on its ends.
    Contour lineTo(double x, double y) {
        return cubicTo(endX(), endY(), x, y, x, y);
    }

    /// Marks the run as closed.
    Contour close() {
        closed = true;
        return this;
    }

    boolean closed() {
        return closed;
    }

    int segments() {
        return (size - 2) / 6;
    }

    private double endX() {
        return coords[size - 2];
    }

    private double endY() {
        return coords[size - 1];
    }

    /// A Lottie path's vertices as a contour.
    static Contour of(Bezier path) {
        var count = path.size();
        var contour = at(path.x(0), path.y(0));
        var segments = path.closed() ? count : count - 1;
        for (var k = 0; k < segments; k++) {
            var next = (k + 1) % count;
            contour.cubicTo(
                    path.x(k) + path.outX(k),
                    path.y(k) + path.outY(k),
                    path.x(next) + path.inX(next),
                    path.y(next) + path.inY(next),
                    path.x(next),
                    path.y(next));
        }
        if (path.closed()) {
            contour.close();
        }
        return contour;
    }

    /// The same run backwards.
    Contour reversed() {
        var n = segments();
        var out = at(coords[size - 2], coords[size - 1]);
        for (var k = n - 1; k >= 0; k--) {
            var base = 2 + k * 6;
            var startX = coords[base - 2];
            var startY = coords[base - 1];
            out.cubicTo(coords[base + 2], coords[base + 3], coords[base], coords[base + 1], startX, startY);
        }
        out.closed = closed;
        return out;
    }

    /// The run under `matrix`.
    Contour transformed(Affine matrix) {
        var out = new Contour(0, 0);
        out.coords = new double[size];
        for (var i = 0; i < size; i += 2) {
            out.coords[i] = matrix.mapX(coords[i], coords[i + 1]);
            out.coords[i + 1] = matrix.mapY(coords[i], coords[i + 1]);
        }
        out.size = size;
        out.closed = closed;
        return out;
    }

    /// Appends this run to `path`.
    void appendTo(Path.Builder path) {
        path.moveTo(coords[0], coords[1]);
        for (var i = 2; i < size; i += 6) {
            path.cubicTo(coords[i], coords[i + 1], coords[i + 2], coords[i + 3], coords[i + 4], coords[i + 5]);
        }
        if (closed) {
            path.close();
        }
    }

    /// The length of each segment, measured along chords.
    double[] segmentLengths() {
        var n = segments();
        var lengths = new double[n];
        for (var k = 0; k < n; k++) {
            lengths[k] = segmentLength(k, 1);
        }
        return lengths;
    }

    /// The length of segment `k` from its start to parameter `t`.
    private double segmentLength(int k, double t) {
        var base = 2 + k * 6;
        var px = coords[base - 2];
        var py = coords[base - 1];
        var length = 0.0;
        for (var i = 1; i <= STEPS; i++) {
            var s = t * i / STEPS;
            var qx = point(k, s, 0);
            var qy = point(k, s, 1);
            length += Math.hypot(qx - px, qy - py);
            px = qx;
            py = qy;
        }
        return length;
    }

    /// The parameter on segment `k` at which `distance` of it has been covered.
    private double parameterAt(int k, double distance, double length) {
        if (length <= 0 || distance <= 0) {
            return 0;
        }
        if (distance >= length) {
            return 1;
        }
        var base = 2 + k * 6;
        var px = coords[base - 2];
        var py = coords[base - 1];
        var covered = 0.0;
        for (var i = 1; i <= STEPS; i++) {
            var s = (double) i / STEPS;
            var qx = point(k, s, 0);
            var qy = point(k, s, 1);
            var chord = Math.hypot(qx - px, qy - py);
            if (covered + chord >= distance) {
                var within = chord > 0 ? (distance - covered) / chord : 0;
                return (i - 1 + within) / STEPS;
            }
            covered += chord;
            px = qx;
            py = qy;
        }
        return 1;
    }

    /// One coordinate (`axis` 0 for x, 1 for y) of segment `k` at `t`.
    private double point(int k, double t, int axis) {
        var base = 2 + k * 6;
        var p0 = coords[base - 2 + axis];
        var p1 = coords[base + axis];
        var p2 = coords[base + 2 + axis];
        var p3 = coords[base + 4 + axis];
        var u = 1 - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    /// The part of this run from `from` to `to`, both distances along it, as an
    /// open run; or nothing when the stretch is empty.
    List<Contour> between(double from, double to, double[] lengths) {
        if (!(to > from)) {
            return List.of();
        }
        var pieces = new ArrayList<Contour>(1);
        Contour piece = null;
        var walked = 0.0;
        for (var k = 0; k < lengths.length; k++) {
            var length = lengths[k];
            var segmentStart = walked;
            var segmentEnd = walked + length;
            walked = segmentEnd;
            if (segmentEnd < from || segmentStart > to || (length == 0 && piece == null)) {
                continue;
            }
            var t0 = from > segmentStart ? parameterAt(k, from - segmentStart, length) : 0;
            var t1 = to < segmentEnd ? parameterAt(k, to - segmentStart, length) : 1;
            if (t1 <= t0 && length > 0) {
                continue;
            }
            var part = part(k, t0, t1);
            if (piece == null) {
                piece = at(part[0], part[1]);
                pieces.add(piece);
            }
            piece.cubicTo(part[2], part[3], part[4], part[5], part[6], part[7]);
        }
        return pieces;
    }

    /// Segment `k` cut down to `t0..t1`: start, two controls and end.
    private double[] part(int k, double t0, double t1) {
        var base = 2 + k * 6;
        var p = new double[] {
            coords[base - 2],
            coords[base - 1],
            coords[base],
            coords[base + 1],
            coords[base + 2],
            coords[base + 3],
            coords[base + 4],
            coords[base + 5]
        };
        if (t1 < 1) {
            p = left(p, t1);
            // t0 is measured on the whole segment; on what is left of it, it
            // sits proportionally further along.
            t0 = t1 > 0 ? t0 / t1 : 0;
        }
        if (t0 > 0) {
            p = right(p, t0);
        }
        return p;
    }

    /// The cubic's first part, up to `t`, by de Casteljau.
    private static double[] left(double[] p, double t) {
        var out = new double[8];
        for (var axis = 0; axis < 2; axis++) {
            var a = p[axis];
            var b = p[2 + axis];
            var c = p[4 + axis];
            var d = p[6 + axis];
            var ab = a + (b - a) * t;
            var bc = b + (c - b) * t;
            var cd = c + (d - c) * t;
            var abc = ab + (bc - ab) * t;
            var bcd = bc + (cd - bc) * t;
            out[axis] = a;
            out[2 + axis] = ab;
            out[4 + axis] = abc;
            out[6 + axis] = abc + (bcd - abc) * t;
        }
        return out;
    }

    /// The cubic's last part, from `t`, by de Casteljau.
    private static double[] right(double[] p, double t) {
        var out = new double[8];
        for (var axis = 0; axis < 2; axis++) {
            var a = p[axis];
            var b = p[2 + axis];
            var c = p[4 + axis];
            var d = p[6 + axis];
            var ab = a + (b - a) * t;
            var bc = b + (c - b) * t;
            var cd = c + (d - c) * t;
            var abc = ab + (bc - ab) * t;
            var bcd = bc + (cd - bc) * t;
            out[axis] = abc + (bcd - abc) * t;
            out[2 + axis] = bcd;
            out[4 + axis] = cd;
            out[6 + axis] = d;
        }
        return out;
    }

    /// `second` appended to this one, which must end where it starts.
    Contour joined(Contour second) {
        var out = new Contour(coords[0], coords[1]);
        out.coords = Arrays.copyOf(coords, size + second.size - 2);
        System.arraycopy(second.coords, 2, out.coords, size, second.size - 2);
        out.size = size + second.size - 2;
        return out;
    }
}
