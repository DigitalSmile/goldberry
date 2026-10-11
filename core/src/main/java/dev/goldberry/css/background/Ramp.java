package dev.goldberry.css.background;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.layout.Length;
import dev.goldberry.paint.Gradient;

/// A layer's stops placed along its gradient line: the span the rasterizer's
/// ramp covers, and the stops' offsets within it.
///
/// @param from  where offset 0 is, in pixels along the line
/// @param to    where offset 1 is
/// @param stops the stops, offsets from 0 to 1
record Ramp(double from, double to, List<Gradient.Stop> stops) {

    /// The shortest span a ramp is drawn over: a gradient whose stops all sit
    /// on one point is a hard edge, which a hundredth of a pixel draws.
    static final double MIN_SPAN = 0.01;

    /// How many stops a segment between two colours is drawn with when it is
    /// not a straight sRGB line: a hint's curve, or another colour space.
    static final int SAMPLES = 16;

    /// Places `stops` along a line `length` long, by CSS's rules, blended in
    /// sRGB.
    static Ramp of(List<GradientLayer.ColorStop> stops, double length) {
        return of(stops, length, GradientLayer.Interpolation.SRGB);
    }

    /// Places `stops` along a line `length` long, by CSS's rules, blended as
    /// `interpolation` says.
    static Ramp of(List<GradientLayer.ColorStop> stops, double length, GradientLayer.Interpolation interpolation) {
        var at = new double[stops.size()];
        var placed = new boolean[stops.size()];
        for (var i = 0; i < stops.size(); i++) {
            var position = stops.get(i).position();
            if (position != null) {
                at[i] = resolve(position, length);
                placed[i] = true;
            }
        }
        var last = stops.size() - 1;
        if (!placed[0]) {
            at[0] = 0;
            placed[0] = true;
        }
        if (!placed[last]) {
            at[last] = Math.max(length, at[0]);
            placed[last] = true;
        }
        // A stop or hint placed before an earlier one is moved up to it.
        var furthest = at[0];
        for (var i = 1; i < at.length; i++) {
            if (placed[i]) {
                at[i] = Math.max(at[i], furthest);
                furthest = at[i];
            }
        }
        // An unplaced run of colours shares the distance between the placed
        // colours either side; a hint is never unplaced and never an end.
        var i = 1;
        while (i < at.length) {
            if (placed[i]) {
                i++;
                continue;
            }
            var end = i;
            while (!placed[end] || stops.get(end).hint()) {
                end++;
            }
            var start = i - 1;
            while (stops.get(start).hint()) {
                start--;
            }
            var count = 0;
            for (var k = start + 1; k <= end; k++) {
                count += stops.get(k).hint() ? 0 : 1;
            }
            var step = 0;
            for (var k = start + 1; k < end; k++) {
                if (!stops.get(k).hint()) {
                    step++;
                    at[k] = at[start] + (at[end] - at[start]) * step / count;
                }
            }
            i = end;
        }
        var points = new ArrayList<Point>(at.length);
        var previous = -1;
        for (var k = 0; k < at.length; k++) {
            var stop = stops.get(k);
            if (stop.hint()) {
                continue;
            }
            if (previous >= 0) {
                var hint = k - 1 > previous ? at[k - 1] : Double.NaN;
                segment(points, at[previous], stops.get(previous).argb(), at[k], stop.argb(), hint, interpolation);
            }
            points.add(new Point(at[k], stop.argb()));
            previous = k;
        }
        var from = points.getFirst().at();
        var to = Math.max(points.getLast().at(), from + MIN_SPAN);
        var out = new ArrayList<Gradient.Stop>(points.size());
        for (var point : points) {
            var offset = Math.clamp((point.at() - from) / (to - from), 0, 1);
            out.add(new Gradient.Stop(offset, point.argb()));
        }
        return new Ramp(from, to, List.copyOf(out));
    }

    /// A colour at a position along the line, in pixels.
    private record Point(double at, int argb) {}

    /// The stops strictly between a colour at `a` and the next at `b`: none
    /// for a straight sRGB line, and [#SAMPLES] along the curve otherwise.
    ///
    /// @param hint where the two are mixed half and half, or NaN for half way
    private static void segment(
            List<Point> points,
            double a,
            int from,
            double b,
            int to,
            double hint,
            GradientLayer.Interpolation interpolation) {
        if (b <= a || (Double.isNaN(hint) && interpolation.isSrgb())) {
            return;
        }
        var middle = Double.isNaN(hint) ? 0.5 : Math.clamp((hint - a) / (b - a), 0, 1);
        for (var n = 1; n < SAMPLES; n++) {
            var t = (double) n / SAMPLES;
            points.add(new Point(a + (b - a) * t, interpolation.mix(from, to, curve(t, middle))));
        }
    }

    /// How far from one colour to the next a point `t` of the way along is,
    /// when the two are mixed half and half `middle` of the way: CSS's hint
    /// curve, a power that puts one half at `middle`.
    static double curve(double t, double middle) {
        if (middle <= 0) {
            return 1;
        }
        if (middle >= 1) {
            return 0;
        }
        return Math.pow(t, Math.log(0.5) / Math.log(middle));
    }

    /// The ramp's colours over the stretch of the line from `start` to `end`,
    /// as offsets from 0 to 1 across it: held past the ramp's ends, or the
    /// ramp again when `repeating`. What a conic gradient's turn is drawn from.
    List<Gradient.Stop> over(double start, double end, boolean repeating) {
        var span = to - from;
        var width = end - start;
        var out = new ArrayList<Gradient.Stop>();
        out.add(new Gradient.Stop(0, colourAtPosition(start, repeating, false)));
        var first = repeating ? (long) Math.floor((start - from) / span) : 0;
        var last = repeating ? (long) Math.ceil((end - from) / span) : 0;
        for (var period = first; period <= last; period++) {
            for (var stop : stops) {
                var at = from + (period + stop.offset()) * span;
                if (at > start && at < end) {
                    out.add(new Gradient.Stop((at - start) / width, stop.argb()));
                }
            }
        }
        out.add(new Gradient.Stop(1, colourAtPosition(end, repeating, true)));
        return List.copyOf(out);
    }

    /// The colour at `at` pixels along the line: coming from before it when
    /// `fromBefore`, so the end of a period is its last colour and not the
    /// next period's first.
    private int colourAtPosition(double at, boolean repeating, boolean fromBefore) {
        var offset = (at - from) / (to - from);
        if (repeating) {
            offset = fromBefore ? offset - Math.ceil(offset) + 1 : offset - Math.floor(offset);
        }
        return colourAt(Math.clamp(offset, 0, 1));
    }

    /// A stop's position in pixels along a line `length` long.
    private static double resolve(Length position, double length) {
        return switch (position) {
            case Length.Points(var value) -> value;
            case Length.Percent(var value) -> value * length / 100;
            case Length.Mixed(var points, var percent) -> points + percent * length / 100;
            case Length.Keyword _ -> 0;
        };
    }

    /// The same ramp with no part of it inside the centre, for a radial
    /// gradient: a circle has no negative radius.
    ///
    /// A repeating ramp is moved out by whole periods, which draws the same
    /// picture. A plain one is cut at the centre, with the colour it had there
    /// as its new first stop.
    Ramp fromZero(boolean repeating) {
        if (from >= 0) {
            return this;
        }
        var span = to - from;
        if (repeating) {
            var periods = Math.ceil(-from / span);
            return new Ramp(from + periods * span, to + periods * span, stops);
        }
        if (to <= 0) {
            // The whole ramp is inside the centre: the last colour holds.
            var colour = stops.getLast().argb();
            return new Ramp(0, MIN_SPAN, List.of(new Gradient.Stop(0, colour), new Gradient.Stop(1, colour)));
        }
        var cut = -from / span;
        var kept = new ArrayList<Gradient.Stop>();
        kept.add(new Gradient.Stop(0, colourAt(cut)));
        for (var stop : stops) {
            if (stop.offset() > cut) {
                kept.add(new Gradient.Stop((stop.offset() - cut) / (1 - cut), stop.argb()));
            }
        }
        return new Ramp(0, to, List.copyOf(kept));
    }

    /// The colour the ramp has at `offset`, mixed premultiplied as the
    /// rasterizer mixes it.
    int colourAt(double offset) {
        var before = stops.getFirst();
        for (var stop : stops) {
            if (stop.offset() > offset) {
                var span = stop.offset() - before.offset();
                var t = span > 0 ? (offset - before.offset()) / span : 1;
                return premultipliedMix(before.argb(), stop.argb(), t);
            }
            before = stop;
        }
        return before.argb();
    }

    private static int premultipliedMix(int from, int to, double t) {
        var fromAlpha = (from >>> 24) / 255.0;
        var toAlpha = (to >>> 24) / 255.0;
        var alpha = fromAlpha + (toAlpha - fromAlpha) * t;
        if (alpha <= 0) {
            return 0;
        }
        var argb = (int) Math.round(alpha * 255) << 24;
        for (var shift = 16; shift >= 0; shift -= 8) {
            var a = ((from >> shift) & 0xFF) * fromAlpha;
            var b = ((to >> shift) & 0xFF) * toAlpha;
            var channel = (int) Math.round((a + (b - a) * t) / alpha);
            argb |= Math.clamp(channel, 0, 255) << shift;
        }
        return argb;
    }
}
