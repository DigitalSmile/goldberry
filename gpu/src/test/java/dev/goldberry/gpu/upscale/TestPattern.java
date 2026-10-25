package dev.goldberry.gpu.upscale;

import dev.goldberry.gpu.upscale.fsr.Fsr1Reference;

/// A picture with what an upscaler is judged on: a disc's curved edge, a
/// diagonal's straight one, a smooth gradient, one-pixel lines and flat areas,
/// in colour, so each channel differs.
public final class TestPattern {

    private TestPattern() {}

    /// The pattern at `width` by `height`, as 8-bit values.
    public static Fsr1Reference.Rgb of(int width, int height) {
        var rgb = new float[3 * width * height];
        var cx = width * 0.35;
        var cy = height * 0.5;
        var radius = Math.min(width, height) * 0.3;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                float r;
                float g;
                float b;
                var dx = x + 0.5 - cx;
                var dy = y + 0.5 - cy;
                if (dx * dx + dy * dy < radius * radius) {
                    r = 0.9f;
                    g = 0.3f;
                    b = 0.2f;
                } else if (x > width * 0.6 && y > x - width * 0.45) {
                    r = 0.15f;
                    g = 0.25f;
                    b = 0.8f;
                } else if (x % 7 == 3 || y % 9 == 4) {
                    r = 1f;
                    g = 1f;
                    b = 1f;
                } else {
                    r = (float) x / width;
                    g = 0.5f * (float) y / height;
                    b = 0.1f;
                }
                var i = 3 * (y * width + x);
                rgb[i] = r;
                rgb[i + 1] = g;
                rgb[i + 2] = b;
            }
        }
        return new Fsr1Reference.Rgb(width, height, rgb).quantized();
    }
}
