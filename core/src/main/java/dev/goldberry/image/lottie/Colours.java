package dev.goldberry.image.lottie;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import dev.goldberry.paint.Gradient;

/// A Lottie colour or gradient as the painter's: `0xAARRGGBB`, and a
/// [Gradient] with its colour and opacity stops merged into one list.
final class Colours {

    private Colours() {}

    /// `rgba` (each 0 to 1, alpha optional) at `opacity`, as `0xAARRGGBB`.
    ///
    /// A colour with a component above 1 is read as 0 to 255, which is how the
    /// oldest exporters wrote it.
    static int argb(double[] rgba, double opacity) {
        var scale = 1.0;
        for (var i = 0; i < Math.min(3, rgba.length); i++) {
            if (rgba[i] > 1) {
                scale = 255;
                break;
            }
        }
        var r = channel(rgba.length > 0 ? rgba[0] / scale : 0);
        var g = channel(rgba.length > 1 ? rgba[1] / scale : 0);
        var b = channel(rgba.length > 2 ? rgba[2] / scale : 0);
        var a = channel((rgba.length > 3 ? Math.clamp(rgba[3] / scale, 0, 1) : 1) * opacity);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0, 1) * 255);
    }

    /// `ramp` at `frame`, at `opacity`, in its group's units; null when it has
    /// no stops or no extent.
    static @Nullable Gradient gradient(Shape.Ramp ramp, double frame, double opacity) {
        var values = ramp.stops().at(frame);
        var count = ramp.count();
        if (count <= 0 || count * 4 > values.length) {
            count = values.length / 4;
        }
        if (count == 0) {
            return null;
        }
        var alphaCount = (values.length - count * 4) / 2;
        // Every offset either list names, in order: a colour stop takes the
        // opacity at its offset, and an opacity stop the colour at its own.
        var offsets = new ArrayList<Double>(count + alphaCount);
        for (var i = 0; i < count; i++) {
            offsets.add(Math.clamp(values[i * 4], 0, 1));
        }
        for (var i = 0; i < alphaCount; i++) {
            offsets.add(Math.clamp(values[count * 4 + i * 2], 0, 1));
        }
        offsets.sort(Double::compare);
        var stops = new ArrayList<Gradient.Stop>(offsets.size());
        for (var offset : offsets) {
            var r = colourAt(values, count, offset, 1);
            var g = colourAt(values, count, offset, 2);
            var b = colourAt(values, count, offset, 3);
            var a = alphaCount == 0 ? 1 : alphaAt(values, count * 4, alphaCount, offset);
            stops.add(new Gradient.Stop(offset, argb(new double[] {r, g, b, a}, opacity)));
        }
        var start = ramp.start().at(frame);
        var end = ramp.end().at(frame);
        var x1 = start.length > 0 ? start[0] : 0;
        var y1 = start.length > 1 ? start[1] : 0;
        var x2 = end.length > 0 ? end[0] : 0;
        var y2 = end.length > 1 ? end[1] : 0;
        if (!ramp.radial()) {
            return new Gradient.Linear(x1, y1, x2, y2, stops);
        }
        var radius = Math.hypot(x2 - x1, y2 - y1);
        if (!(radius > 0)) {
            return null;
        }
        // The highlight, After Effects' focal point, is not drawn: the
        // painter's radial gradient shares one centre.
        return new Gradient.Radial(x1, y1, radius, radius, 0, stops, Gradient.Extend.PAD);
    }

    /// One channel (1 red, 2 green, 3 blue) of the colour stops at `offset`.
    private static double colourAt(double[] values, int count, double offset, int channel) {
        if (offset <= values[0]) {
            return values[channel];
        }
        for (var i = 1; i < count; i++) {
            var at = values[i * 4];
            if (offset <= at) {
                var before = values[(i - 1) * 4];
                var t = at > before ? (offset - before) / (at - before) : 1;
                var from = values[(i - 1) * 4 + channel];
                return from + (values[i * 4 + channel] - from) * t;
            }
        }
        return values[(count - 1) * 4 + channel];
    }

    /// The opacity stops' alpha at `offset`.
    private static double alphaAt(double[] values, int first, int count, double offset) {
        if (offset <= values[first]) {
            return values[first + 1];
        }
        for (var i = 1; i < count; i++) {
            var at = values[first + i * 2];
            if (offset <= at) {
                var before = values[first + (i - 1) * 2];
                var t = at > before ? (offset - before) / (at - before) : 1;
                var from = values[first + (i - 1) * 2 + 1];
                return from + (values[first + i * 2 + 1] - from) * t;
            }
        }
        return values[first + (count - 1) * 2 + 1];
    }
}
