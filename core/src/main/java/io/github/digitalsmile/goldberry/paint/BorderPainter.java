package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.css.Border;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;

/// Puts a border whose four sides are **not** the same line on the frame, one
/// side at a time (ADR-0505).
///
/// A uniform border never comes here. [BoxPainter] strokes it as it always has —
/// one rounded rectangle, inset by half the width — and every golden in the
/// corpus is that drawing. This is the other one, and it is a fill rather than a
/// stroke, because a stroke has one width.
///
/// ## Each side is a region
///
/// A side is the band between the box's outer edge and its inner edge — the
/// outer edge inset by every side's width — cut off at each end by the line
/// that divides it from its neighbour. With square corners that line runs from
/// the outer corner to the inner one, which is the **mitre** a browser draws: a
/// 1px top against a 4px left meets it on the diagonal from `(0, 0)` to
/// `(4, 1)`, and a side with no neighbour ink is a plain rectangle.
///
/// ## With a radius, an approximation
///
/// The outer corner is the box's own arc and the inner one is concentric-ish: a
/// circle of the outer radius less the **wider** of the two sides beside it.
/// CSS makes the inner corner an ellipse, with the outer radius less each side's
/// own width on its own axis; [Corners] are circles and so is this (ADR-0216).
/// The two agree exactly when the sides agree, and differ by at most the
/// difference between the widths where they do not.
///
/// The arc is divided between its two sides **in proportion to their widths**,
/// by parameter along the cubic: a 4px left and a 1px top give the left side
/// four fifths of the corner. With equal widths that is the arc's midpoint, which
/// is exactly where a mitre meets a concentric corner; with a zero on one side
/// the other takes the whole corner and tapers into it, which is what a browser
/// draws for a lone `border-left` on a rounded box.
///
/// ## One fill per colour
///
/// Every side of one colour goes into one path and is filled once. Two
/// anti-aliased fills meeting along a diagonal each cover half of the pixels on
/// it, and half over half is three quarters rather than all — a faint seam down
/// every mitre of a border that is one colour with two widths. Filled together,
/// the rasterizer computes coverage for the union and there is no seam. Between
/// two *different* colours there is one, as there is in every browser.
final class BorderPainter {

    /// A quarter circle as a cubic — [Path]'s constant, for [Path]'s reason.
    private static final double KAPPA = 0.5522847498307933;

    /// Seven points per arc once it is split — its start, the first half's two
    /// controls, the split point, the second half's two controls, its end — as
    /// fourteen numbers.
    private static final int ARC = 14;

    private BorderPainter() {}

    /// Draws `border` inside the box at `(x, y)`.
    ///
    /// @param path    a pooled rasterizer path, reset here before each colour
    /// @param corners the box's corner radii, before fitting
    static void paint(
            Frame frame,
            BlendPath path,
            Border border,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var lines = new Border.Line[] {border.top(), border.right(), border.bottom(), border.left()};
        var arcs = arcs(lines, width, height, corners);
        for (var side = 0; side < 4; side++) {
            var line = lines[side];
            if (!line.hasInk() || drawnBefore(lines, side)) {
                continue;
            }
            path.reset();
            for (var other = side; other < 4; other++) {
                if (lines[other].hasInk() && lines[other].argb() == line.argb()) {
                    addSide(path, arcs, other);
                }
            }
            frame.fillPath(x, y, path, line.argb());
        }
    }

    /// Whether an earlier side with ink had this side's colour, and so already
    /// drew it.
    private static boolean drawnBefore(Border.Line[] lines, int side) {
        for (var earlier = 0; earlier < side; earlier++) {
            if (lines[earlier].hasInk() && lines[earlier].argb() == lines[side].argb()) {
                return true;
            }
        }
        return false;
    }

    /// Every corner's outer and inner arc, split where its two sides meet.
    ///
    /// Corners are numbered clockwise from the top-left, and sides clockwise from
    /// the top, so corner `c` runs from side `c - 1` into side `c` and side `s`
    /// runs from corner `s` into corner `s + 1`.
    private static double[] arcs(Border.Line[] lines, double width, double height, Corners corners) {
        var top = lines[0].width();
        var right = lines[1].width();
        var bottom = lines[2].width();
        var left = lines[3].width();
        // Sides that together overrun the box are scaled down in proportion,
        // [Corners#fittedTo]'s rule for radii: a border drawn inside a box
        // cannot be wider than the box.
        if (left + right > width && left + right > 0) {
            var scale = width / (left + right);
            left *= scale;
            right *= scale;
        }
        if (top + bottom > height && top + bottom > 0) {
            var scale = height / (top + bottom);
            top *= scale;
            bottom *= scale;
        }
        var outer = corners.fittedTo(width, height);
        var innerWidth = Math.max(0, width - left - right);
        var innerHeight = Math.max(0, height - top - bottom);
        var inner = new Corners(
                        Math.max(0, outer.topLeft() - Math.max(top, left)),
                        Math.max(0, outer.topRight() - Math.max(top, right)),
                        Math.max(0, outer.bottomRight() - Math.max(bottom, right)),
                        Math.max(0, outer.bottomLeft() - Math.max(bottom, left)))
                .fittedTo(innerWidth, innerHeight);

        var widths = new double[] {top, right, bottom, left};
        var outerRadii = new double[] {outer.topLeft(), outer.topRight(), outer.bottomRight(), outer.bottomLeft()};
        var innerRadii = new double[] {inner.topLeft(), inner.topRight(), inner.bottomRight(), inner.bottomLeft()};
        var arcs = new double[8 * ARC];
        for (var corner = 0; corner < 4; corner++) {
            var from = widths[(corner + 3) % 4];
            var into = widths[corner];
            // The share of the corner that belongs to the side it starts on.
            var share = from + into > 0 ? from / (from + into) : 0.5;
            arc(arcs, corner * 2 * ARC, corner, 0, 0, width, height, outerRadii[corner], share);
            arc(
                    arcs,
                    (corner * 2 + 1) * ARC,
                    corner,
                    left,
                    top,
                    width - right,
                    height - bottom,
                    innerRadii[corner],
                    share);
        }
        return arcs;
    }

    /// One corner's quarter circle, clockwise, split at `share` and written at
    /// `at` as seven points.
    ///
    /// A square corner is seven copies of the corner point, which is what lets
    /// [#addSide] treat every corner alike and skip only the curve.
    private static void arc(
            double[] out,
            int at,
            int corner,
            double left,
            double top,
            double right,
            double bottom,
            double radius,
            double share) {
        var c = radius * KAPPA;
        // Start, two controls, end: from the side before the corner into the
        // side after it.
        double[] p =
                switch (corner) {
                    case 0 ->
                        new double[] {
                            left, top + radius, left, top + radius - c, left + radius - c, top, left + radius, top
                        };
                    case 1 ->
                        new double[] {
                            right - radius, top, right - radius + c, top, right, top + radius - c, right, top + radius
                        };
                    case 2 ->
                        new double[] {
                            right,
                            bottom - radius,
                            right,
                            bottom - radius + c,
                            right - radius + c,
                            bottom,
                            right - radius,
                            bottom
                        };
                    default ->
                        new double[] {
                            left + radius,
                            bottom,
                            left + radius - c,
                            bottom,
                            left,
                            bottom - radius + c,
                            left,
                            bottom - radius
                        };
                };
        for (var axis = 0; axis < 2; axis++) {
            // de Casteljau, once: the two halves of a cubic split at `share` are
            // cubics, so the pieces of a corner are exact pieces of the arc.
            var p0 = p[axis];
            var p1 = p[2 + axis];
            var p2 = p[4 + axis];
            var p3 = p[6 + axis];
            var p01 = lerp(p0, p1, share);
            var p12 = lerp(p1, p2, share);
            var p23 = lerp(p2, p3, share);
            var p012 = lerp(p01, p12, share);
            var p123 = lerp(p12, p23, share);
            var split = lerp(p012, p123, share);
            out[at + axis] = p0;
            out[at + 2 + axis] = p01;
            out[at + 4 + axis] = p012;
            out[at + 6 + axis] = split;
            out[at + 8 + axis] = p123;
            out[at + 10 + axis] = p23;
            out[at + 12 + axis] = p3;
        }
    }

    /// Appends side `side` as one closed, clockwise sub-path: along the outer
    /// edge from where it takes over its first corner to where it gives up its
    /// second, across to the inner edge, and back along that.
    private static void addSide(BlendPath path, double[] arcs, int side) {
        var start = side * 2 * ARC;
        var startInner = start + ARC;
        var end = ((side + 1) % 4) * 2 * ARC;
        var endInner = end + ARC;

        path.moveTo(arcs[start + 6], arcs[start + 7]);
        if (curved(arcs, start)) {
            path.cubicTo(
                    arcs[start + 8],
                    arcs[start + 9],
                    arcs[start + 10],
                    arcs[start + 11],
                    arcs[start + 12],
                    arcs[start + 13]);
        }
        path.lineTo(arcs[end], arcs[end + 1]);
        if (curved(arcs, end)) {
            path.cubicTo(arcs[end + 2], arcs[end + 3], arcs[end + 4], arcs[end + 5], arcs[end + 6], arcs[end + 7]);
        }
        path.lineTo(arcs[endInner + 6], arcs[endInner + 7]);
        if (curved(arcs, endInner)) {
            // The first half backwards: split point to start, controls swapped.
            path.cubicTo(
                    arcs[endInner + 4],
                    arcs[endInner + 5],
                    arcs[endInner + 2],
                    arcs[endInner + 3],
                    arcs[endInner],
                    arcs[endInner + 1]);
        }
        path.lineTo(arcs[startInner + 12], arcs[startInner + 13]);
        if (curved(arcs, startInner)) {
            // The second half backwards: end to split point.
            path.cubicTo(
                    arcs[startInner + 10],
                    arcs[startInner + 11],
                    arcs[startInner + 8],
                    arcs[startInner + 9],
                    arcs[startInner + 6],
                    arcs[startInner + 7]);
        }
        path.closeSubPath();
    }

    /// Whether the arc at `at` is a curve rather than a square corner's point.
    private static boolean curved(double[] arcs, int at) {
        return arcs[at] != arcs[at + 12] || arcs[at + 1] != arcs[at + 13];
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
