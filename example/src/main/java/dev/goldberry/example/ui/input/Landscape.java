package dev.goldberry.example.ui.input;

import dev.goldberry.image.Image;

/// The drag-out card's picture: a sky, a sun and two hills, in the screen's
/// Nord colours, computed a pixel at a time so it needs no renderer to exist.
final class Landscape {

    private static final int SKY_TOP = 0xFF5E81AC;

    private static final int SKY_BOTTOM = 0xFF88C0D0;

    private static final int SUN = 0xFFEBCB8B;

    private static final int FAR_HILL = 0xFF8FBCBB;

    private static final int NEAR_HILL = 0xFFA3BE8C;

    private Landscape() {}

    /// The picture at `width` by `height` pixels, opaque.
    static Image paint(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a landscape needs pixels, not " + width + "x" + height);
        }
        var argb = new int[Math.multiplyExact(width, height)];
        var sunX = width * 0.72;
        var sunY = height * 0.36;
        var sunRadius = height * 0.16;
        for (var y = 0; y < height; y++) {
            var sky = mix(SKY_TOP, SKY_BOTTOM, (double) y / Math.max(1, height - 1));
            for (var x = 0; x < width; x++) {
                var colour = sky;
                // A soft edge one pixel wide, so the sun is round at any size.
                colour = mix(colour, SUN, coverage(sunRadius - Math.hypot(x + 0.5 - sunX, y + 0.5 - sunY)));
                colour = mix(colour, FAR_HILL, coverage(y + 0.5 - hill(x, width, height, 0.62, 0.10, 1.3)));
                colour = mix(colour, NEAR_HILL, coverage(y + 0.5 - hill(x, width, height, 0.78, 0.08, 2.1)));
                argb[y * width + x] = colour;
            }
        }
        return Image.ofArgb(width, height, argb);
    }

    /// The top of a hill at column `x`: a sine wave around `level` of the
    /// height, `swell` of it high, with `waves` crests across the width.
    private static double hill(int x, int width, int height, double level, double swell, double waves) {
        return height * (level - swell * Math.sin(Math.PI * waves * (x + 0.5) / width));
    }

    /// How much of a pixel lies inside an edge `distance` pixels away.
    private static double coverage(double distance) {
        return Math.clamp(distance + 0.5, 0, 1);
    }

    /// `from` moved towards `to` by `amount`, channel by channel, opaque.
    static int mix(int from, int to, double amount) {
        if (amount <= 0) {
            return from;
        }
        if (amount >= 1) {
            return to;
        }
        var red = channel(from >> 16, to >> 16, amount);
        var green = channel(from >> 8, to >> 8, amount);
        var blue = channel(from, to, amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int channel(int from, int to, double amount) {
        var a = from & 0xFF;
        var b = to & 0xFF;
        return (int) Math.round(a + (b - a) * amount);
    }
}
