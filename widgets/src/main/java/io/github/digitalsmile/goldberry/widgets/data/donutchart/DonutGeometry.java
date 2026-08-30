package io.github.digitalsmile.goldberry.widgets.data.donutchart;

import java.util.List;

/// Where the ring is inside the box, and which slice a point is on.
///
/// The donut's answer to
/// [io.github.digitalsmile.goldberry.widgets.data.linechart.PlotGeometry], and it
/// is simpler in one way that is worth naming: **a ring needs no text metrics.**
/// An axis chart's geometry depends on the shaped y labels, so it can only be
/// worked out inside the painter and has to be left somewhere for the pointer to
/// find (ADR-0054, `PaintedGeometry`). A ring depends on nothing but the box, and
/// a pointer event carries the box — so this is computed fresh on both sides and
/// there is nothing to bank.
///
/// @param cx    the centre, in the painter's own coordinates
/// @param cy    the centre
/// @param outer the outer radius
/// @param inner the hole
record DonutGeometry(double cx, double cy, double outer, double inner) {

    /// Where twelve o'clock is, in the angles `Math.atan2` speaks.
    ///
    /// A donut starts at the top and goes clockwise because that is where a
    /// reader's eye starts; the maths starts at three o'clock if nobody
    /// intervenes. Both the painter and the hit test have to agree about it, so
    /// it is one constant.
    static final double START = -Math.PI / 2;

    /// The geometry of a ring in a box this size, or **null** when there is no
    /// room for one.
    static DonutGeometry of(double width, double height, double hole) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        var outer = Math.min(width, height) / 2;
        if (outer <= 1) {
            return null;
        }
        return new DonutGeometry(width / 2, height / 2, outer, outer * hole);
    }

    /// Which slice `(x, y)` is on, or -1 for the hole, the corners, and outside.
    ///
    /// **The gaps between slices belong to a slice.** The painter takes a sliver
    /// out of each end of every arc so the slices do not touch, and a hit test
    /// that respected those slivers would put a dead ring of two-pixel wedges
    /// through the chart — a pointer crossing one would drop the readout and
    /// pick it up again. So this walks the *untrimmed* shares, which tile the
    /// circle exactly.
    ///
    /// A zero share is never drawn and can never be hovered: there is nothing
    /// there to point at.
    int sliceAt(double x, double y, List<Double> values) {
        var dx = x - cx;
        var dy = y - cy;
        var radius = Math.hypot(dx, dy);
        if (radius < inner || radius > outer) {
            // The hole is not the chart. A readout that appeared when the pointer
            // crossed the middle would be a donut that reacts to the space it
            // deliberately left empty.
            return -1;
        }
        var total = values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(v -> v > 0)
                .sum();
        if (total <= 0) {
            return -1;
        }
        // Turns clockwise from twelve o'clock, in `0..2π`.
        var turned = Math.atan2(dy, dx) - START;
        while (turned < 0) {
            turned += Math.PI * 2;
        }
        while (turned >= Math.PI * 2) {
            turned -= Math.PI * 2;
        }

        var swept = 0.0;
        for (var i = 0; i < values.size(); i++) {
            var value = Math.max(0, values.get(i));
            if (value <= 0) {
                continue;
            }
            var sweep = value / total * Math.PI * 2;
            if (turned < swept + sweep) {
                return i;
            }
            swept += sweep;
        }
        // Only reachable through floating-point drift at exactly 2π. The last
        // drawn slice is the honest answer, and it is one pixel wide.
        for (var i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) > 0) {
                return i;
            }
        }
        return -1;
    }

    /// The middle of slice `index`, as an angle — where a label or a marker for
    /// it belongs.
    double middleOf(int index, List<Double> values) {
        var total = values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(v -> v > 0)
                .sum();
        if (total <= 0 || index < 0 || index >= values.size()) {
            return START;
        }
        var swept = 0.0;
        for (var i = 0; i < index; i++) {
            swept += Math.max(0, values.get(i)) / total * Math.PI * 2;
        }
        return START + swept + Math.max(0, values.get(index)) / total * Math.PI;
    }
}
