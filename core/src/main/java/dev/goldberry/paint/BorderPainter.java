package dev.goldberry.paint;

import java.util.function.Predicate;

import dev.goldberry.css.Border;
import dev.goldberry.css.Corners;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.natives.blend2d.BlendPath;
import dev.goldberry.paint.border.BorderPattern;
import dev.goldberry.paint.stroke.Stroke;

/// Puts a border whose four sides are **not** the same solid line on the
/// frame, one side at a time.
///
/// A uniform solid border never comes here. [BoxPainter] strokes it as it always
/// has — one rounded rectangle, inset by half the width — and every golden in
/// the corpus is that drawing. This is the other one, and it is a fill rather
/// than a stroke, because a stroke has one width.
///
/// ## Styled sides
///
/// A `dashed` side is a stroke of its centreline with a dash pattern, and a
/// `dotted` side is a row of filled dots along it; [BorderPattern] says where
/// both go. A `double` side is two solid bands a third of its width each, drawn
/// as two thinner borders. The solid sides around them are still the regions
/// below, with the styled sides' widths kept so the corners divide as before.
///
/// ## Bevelled sides
///
/// `inset`, `outset`, `groove` and `ridge` are lit from the top left. An `inset`
/// side is solid in a darker tone of its colour on the top and the left and a
/// lighter one on the bottom and the right, and `outset` the other way round.
/// A `groove` is two bands of half the width each, the outer one as `inset` and
/// the inner one as `outset`, and a `ridge` the reverse. The tones are the
/// colour mixed a third of the way to black and a quarter of the way to white,
/// in OKLCH, so a black border still shows both.
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
/// ## With a radius
///
/// The outer corner is the box's own arc, a quarter ellipse when its two radii
/// differ, and the inner one is CSS's: the outer radii less each side's own
/// width on its own axis, so a corner between a thick side and a thin one has
/// an elliptical inside.
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
        if (border.isDrawnSolid()) {
            paintRegions(frame, path, border, x, y, width, height, corners);
            return;
        }
        // The solid sides first, as regions. A styled side keeps its width with
        // its ink taken away, so the corners it shares are still divided as
        // they would be with every side solid.
        paintRegions(frame, path, inkOnly(border, Border.Style::isDrawnSolid), x, y, width, height, corners);
        paintDouble(frame, path, border, x, y, width, height, corners);
        paintBevelled(frame, path, border, x, y, width, height, corners);
        for (var side : Border.Side.values()) {
            var line = border.side(side);
            if (!line.hasInk()) {
                continue;
            }
            switch (line.style()) {
                case DASHED -> paintDashed(frame, border, side, x, y, width, height, corners);
                case DOTTED -> paintDotted(frame, border, side, x, y, width, height, corners);
                case SOLID, DOUBLE, GROOVE, RIDGE, INSET, OUTSET -> {
                    // Drawn above, as a region or as two.
                }
            }
        }
    }

    /// `border` with the ink taken off every side whose style `keep` refuses,
    /// widths kept.
    private static Border inkOnly(Border border, Predicate<Border.Style> keep) {
        return new Border(
                keepIf(border.top(), keep),
                keepIf(border.right(), keep),
                keepIf(border.bottom(), keep),
                keepIf(border.left(), keep));
    }

    private static Border.Line keepIf(Border.Line line, Predicate<Border.Style> keep) {
        return keep.test(line.style()) ? line : line.argb(CssColor.TRANSPARENT);
    }

    /// The `double` sides: two bands each a third of the side's width, at its
    /// outer and inner edges, with the middle third left empty.
    ///
    /// Each band is the region drawing of a border a third as wide, so the
    /// bands mitre and follow the corners exactly as a solid side does.
    private static void paintDouble(
            Frame frame,
            BlendPath path,
            Border border,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var doubles = inkOnly(border, style -> style == Border.Style.DOUBLE);
        if (!doubles.hasInk()) {
            return;
        }
        var top = border.top().width();
        var right = border.right().width();
        var bottom = border.bottom().width();
        var left = border.left().width();
        var third = doubles.widths(top / 3, right / 3, bottom / 3, left / 3);
        paintRegions(frame, path, third, x, y, width, height, corners);
        // The inner band is the same drawing on the box inset by two thirds of
        // each side, its corners pulled in to stay concentric.
        var inner = corners.fittedTo(width, height).inset(top * 2 / 3, right * 2 / 3, bottom * 2 / 3, left * 2 / 3);
        var innerWidth = width - (left + right) * 2 / 3;
        var innerHeight = height - (top + bottom) * 2 / 3;
        if (innerWidth > 0 && innerHeight > 0) {
            paintRegions(frame, path, third, x + left * 2 / 3, y + top * 2 / 3, innerWidth, innerHeight, inner);
        }
    }

    /// The bevelled sides: `inset` and `outset` as one band each, `groove` and
    /// `ridge` as two of half the width. See the type's note for the tones.
    private static void paintBevelled(
            Frame frame,
            BlendPath path,
            Border border,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var bevelled = inkOnly(border, Border.Style::isBevelled);
        if (!bevelled.hasInk()) {
            return;
        }
        // The outer band: all of an inset or outset side, half of a groove or
        // a ridge. Every side's width is kept, halved where the side is in two,
        // so the corners divide as they would.
        var outer = new Border(
                outerBand(bevelled.top(), Border.Side.TOP),
                outerBand(bevelled.right(), Border.Side.RIGHT),
                outerBand(bevelled.bottom(), Border.Side.BOTTOM),
                outerBand(bevelled.left(), Border.Side.LEFT));
        paintRegions(frame, path, outer, x, y, width, height, corners);
        var inner = new Border(
                innerBand(bevelled.top(), Border.Side.TOP),
                innerBand(bevelled.right(), Border.Side.RIGHT),
                innerBand(bevelled.bottom(), Border.Side.BOTTOM),
                innerBand(bevelled.left(), Border.Side.LEFT));
        if (!inner.hasInk()) {
            return;
        }
        // The inner band is the same drawing on the box inset by the outer
        // band, its corners pulled in to stay concentric.
        var ot = outer.top().width();
        var or = outer.right().width();
        var ob = outer.bottom().width();
        var ol = outer.left().width();
        var innerCorners = corners.fittedTo(width, height).inset(ot, or, ob, ol);
        var innerWidth = width - ol - or;
        var innerHeight = height - ot - ob;
        if (innerWidth > 0 && innerHeight > 0) {
            paintRegions(frame, path, inner, x + ol, y + ot, innerWidth, innerHeight, innerCorners);
        }
    }

    /// A bevelled side's outer band: its whole width for `inset` and `outset`,
    /// half for `groove` and `ridge`, in the tone the style lights it with.
    /// A side that is not bevelled keeps its width with no ink.
    private static Border.Line outerBand(Border.Line line, Border.Side side) {
        var style = line.style();
        if (!style.isBevelled()) {
            return line;
        }
        var split = style == Border.Style.GROOVE || style == Border.Style.RIDGE;
        var darkOnTopLeft = style == Border.Style.INSET || style == Border.Style.GROOVE;
        return new Border.Line(split ? line.width() / 2 : line.width(), tone(line.argb(), side, darkOnTopLeft));
    }

    /// The inner half of a `groove` or `ridge` side, lit the other way from its
    /// outer half; nothing for every other side.
    private static Border.Line innerBand(Border.Line line, Border.Side side) {
        var style = line.style();
        if (style != Border.Style.GROOVE && style != Border.Style.RIDGE) {
            return new Border.Line(0, CssColor.TRANSPARENT);
        }
        return new Border.Line(line.width() / 2, tone(line.argb(), side, style == Border.Style.RIDGE));
    }

    /// `argb` darker on the top and left sides when `darkOnTopLeft`, and on the
    /// bottom and right ones otherwise; lighter on the other two.
    static int tone(int argb, Border.Side side, boolean darkOnTopLeft) {
        var topLeft = side == Border.Side.TOP || side == Border.Side.LEFT;
        var alpha = argb & 0xFF000000;
        return topLeft == darkOnTopLeft
                ? CssColor.mix(argb, alpha, 1.0 / 3)
                : CssColor.mix(argb, alpha | 0xFFFFFF, 1.0 / 4);
    }

    /// One `dashed` side: its run stroked with a pattern that starts and ends
    /// on a dash.
    private static void paintDashed(
            Frame frame,
            Border border,
            Border.Side side,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var line = border.side(side);
        var run = BorderPattern.dashedRun(border, side, width, height, corners);
        var dash = BorderPattern.dashes(
                run,
                line.width(),
                run.roundStart() && sharesCorner(border, side.previous(), Border.Style.DASHED),
                run.roundEnd() && sharesCorner(border, side.next(), Border.Style.DASHED));
        frame.strokePath(x, y, run.path(), Stroke.of(line.width()).dash(dash), line.argb());
    }

    /// One `dotted` side: a round dot as wide as the side at every point the
    /// pattern puts one, filled together.
    private static void paintDotted(
            Frame frame,
            Border border,
            Border.Side side,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var line = border.side(side);
        var run = BorderPattern.dottedRun(border, side, width, height, corners);
        // The dot on the corner this side ends at is the next side's when that
        // side is dotted too, so a corner is one dot rather than two on top of
        // each other.
        var dots = BorderPattern.dots(run, line.width(), !sharesCorner(border, side.next(), Border.Style.DOTTED));
        var radius = line.width() / 2;
        var builder = Path.builder();
        for (var dot : dots) {
            builder.append(Path.circle(dot.x(), dot.y(), radius));
        }
        frame.fillPath(x, y, builder.build(), line.argb());
    }

    /// Whether `neighbour` has ink in `style`, and so draws its half of the
    /// corner it shares with the side being drawn.
    private static boolean sharesCorner(Border border, Border.Side neighbour, Border.Style style) {
        var line = border.side(neighbour);
        return line.hasInk() && line.style() == style;
    }

    /// The drawing of a border whose sides are all solid bands — see the class
    /// note.
    private static void paintRegions(
            Frame frame,
            BlendPath path,
            Border border,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        if (!border.hasInk()) {
            return;
        }
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
        var inner = outer.inset(top, right, bottom, left).fittedTo(innerWidth, innerHeight);

        var widths = new double[] {top, right, bottom, left};
        var outerRadii = outer.clockwise();
        var innerRadii = inner.clockwise();
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

    /// One corner's quarter ellipse, clockwise, split at `share` and written at
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
            Corners.Radius radius,
            double share) {
        var rx = radius.x();
        var ry = radius.y();
        var cx = rx * KAPPA;
        var cy = ry * KAPPA;
        // Start, two controls, end: from the side before the corner into the
        // side after it.
        double[] p = switch (corner) {
            case 0 -> new double[] {left, top + ry, left, top + ry - cy, left + rx - cx, top, left + rx, top};
            case 1 -> new double[] {right - rx, top, right - rx + cx, top, right, top + ry - cy, right, top + ry};
            case 2 ->
                new double[] {right, bottom - ry, right, bottom - ry + cy, right - rx + cx, bottom, right - rx, bottom};
            default ->
                new double[] {left + rx, bottom, left + rx - cx, bottom, left, bottom - ry + cy, left, bottom - ry};
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
