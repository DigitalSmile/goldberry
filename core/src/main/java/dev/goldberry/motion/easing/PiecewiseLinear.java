package dev.goldberry.motion.easing;

import java.util.List;
import java.util.StringJoiner;

import dev.goldberry.motion.Easing;

/// `linear(0, 0.25 75%, 1)`: straight lines between points.
///
/// The points are the ones CSS's `linear()` resolves to, with every input
/// filled in: the first defaults to 0, the last to 1, and a missing one is
/// spread evenly between its neighbours. Reading the function's text into
/// points is the stylesheet's business; this type takes them resolved.
///
/// [#IDENTITY], two points from `(0, 0)` to `(1, 1)`, is `linear` with no
/// easing at all, the design system's [Easing#LINEAR].
///
/// @param points at least two, inputs never decreasing. Two points with the
///               same input are a jump
public record PiecewiseLinear(List<Point> points) implements Easing {

    /// No easing: `linear`.
    public static final PiecewiseLinear IDENTITY = new PiecewiseLinear(List.of(new Point(0, 0), new Point(1, 1)));

    public PiecewiseLinear {
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new IllegalArgumentException("linear() needs two points, not " + points.size());
        }
        for (var i = 1; i < points.size(); i++) {
            if (points.get(i).input() < points.get(i - 1).input()) {
                throw new IllegalArgumentException("linear()'s inputs go backwards at point " + i + ": " + points);
            }
        }
    }

    /// One point of the function.
    ///
    /// @param output the eased progress, any value: outside `0..1` overshoots
    /// @param input  the linear progress it is reached at
    public record Point(double output, double input) {

        public Point {
            if (!Double.isFinite(output) || !Double.isFinite(input)) {
                throw new IllegalArgumentException("a linear() point is finite, not " + output + " at " + input);
            }
        }
    }

    /// The output for `t`, by CSS's linear easing function.
    ///
    /// Between two points, the line through them. Where several points share an
    /// input, the last of them holds from that input on, which is what makes two
    /// points at one input a jump. Before the first input and after the last,
    /// the output holds at the first or the last point's.
    @Override
    public double at(double t) {
        var clamped = Math.max(0, Math.min(1, t));
        var first = points.getFirst();
        if (clamped < first.input()) {
            return first.output();
        }
        var last = points.getLast();
        if (clamped >= last.input()) {
            return last.output();
        }
        // The last point at or before `clamped`. One after it exists, and its
        // input is past `clamped`, so the span between them is never zero.
        var from = 0;
        for (var i = 0; i < points.size(); i++) {
            if (points.get(i).input() <= clamped) {
                from = i;
            }
        }
        var a = points.get(from);
        var b = points.get(from + 1);
        return a.output() + (b.output() - a.output()) * (clamped - a.input()) / (b.input() - a.input());
    }

    @Override
    public String cssName() {
        if (equals(IDENTITY)) {
            return "linear";
        }
        var joiner = new StringJoiner(", ", "linear(", ")");
        for (var point : points) {
            joiner.add(Numbers.css(point.output()) + " " + Numbers.percent(point.input()));
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return cssName();
    }
}
