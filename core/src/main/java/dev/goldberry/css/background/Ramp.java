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

    /// Places `stops` along a line `length` long, by CSS's rules.
    static Ramp of(List<GradientLayer.ColorStop> stops, double length) {
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
        // A stop placed before an earlier one is moved up to it.
        var furthest = at[0];
        for (var i = 1; i < at.length; i++) {
            if (placed[i]) {
                at[i] = Math.max(at[i], furthest);
                furthest = at[i];
            }
        }
        // An unplaced run shares the distance between its placed ends.
        var i = 1;
        while (i < at.length) {
            if (placed[i]) {
                i++;
                continue;
            }
            var end = i;
            while (!placed[end]) {
                end++;
            }
            var start = i - 1;
            for (var k = i; k < end; k++) {
                at[k] = at[start] + (at[end] - at[start]) * (k - start) / (end - start);
            }
            i = end;
        }
        var from = at[0];
        var to = Math.max(at[last], from + MIN_SPAN);
        var out = new ArrayList<Gradient.Stop>(at.length);
        for (var k = 0; k < at.length; k++) {
            var offset = Math.clamp((at[k] - from) / (to - from), 0, 1);
            out.add(new Gradient.Stop(offset, stops.get(k).argb()));
        }
        return new Ramp(from, to, List.copyOf(out));
    }

    /// A stop's position in pixels along a line `length` long.
    private static double resolve(Length position, double length) {
        return switch (position) {
            case Length.Points(var value) -> value;
            case Length.Percent(var value) -> value * length / 100;
            default -> 0;
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
