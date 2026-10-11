package dev.goldberry.css.value.colour;

/// The conversions from each colour space CSS can name into sRGB.
///
/// The matrices are CSS Color 4's own sample code, and the OKLab ones Björn
/// Ottosson's. Every result is **clamped** into sRGB, not gamut-mapped: a colour
/// outside sRGB keeps its in-gamut channels and loses the rest, which can
/// shift its hue at the extremes. The packing is `0xAARRGGBB`, not
/// premultiplied, as everywhere else in the toolkit.
final class ColourSpaces {

    private ColourSpaces() {}

    /// sRGB channels in `0..1`, encoded.
    static int srgb(double r, double g, double b, double alpha) {
        return pack(byteOf(r), byteOf(g), byteOf(b), alpha);
    }

    /// Linear-light sRGB channels.
    static int linearSrgb(double r, double g, double b, double alpha) {
        return srgb(encode(r), encode(g), encode(b), alpha);
    }

    /// `hsl()`: hue in degrees, saturation and lightness in `0..1`.
    static int hsl(double hue, double saturation, double lightness, double alpha) {
        var s = clamp01(saturation);
        var l = clamp01(lightness);
        var h = normalisedHue(hue);
        return srgb(hslChannel(0, h, s, l), hslChannel(8, h, s, l), hslChannel(4, h, s, l), alpha);
    }

    /// `hwb()`: hue in degrees, whiteness and blackness in `0..1`. A whiteness
    /// and blackness that add up to 1 or more are a grey, in proportion.
    static int hwb(double hue, double whiteness, double blackness, double alpha) {
        var w = clamp01(whiteness);
        var k = clamp01(blackness);
        if (w + k >= 1) {
            var grey = w / (w + k);
            return srgb(grey, grey, grey, alpha);
        }
        var h = normalisedHue(hue);
        var scale = 1 - w - k;
        return srgb(
                hslChannel(0, h, 1, 0.5) * scale + w,
                hslChannel(8, h, 1, 0.5) * scale + w,
                hslChannel(4, h, 1, 0.5) * scale + w,
                alpha);
    }

    /// CIE `lab()`, D50: lightness `0..100`, `a` and `b` unbounded.
    static int lab(double lightness, double a, double b, double alpha) {
        // CIE Lab to XYZ, D50 white.
        final double kappa = 24389.0 / 27;
        final double epsilon = 216.0 / 24389;
        var fy = (lightness + 16) / 116;
        var fx = a / 500 + fy;
        var fz = fy - b / 200;
        var x = cube(fx) > epsilon ? cube(fx) : (116 * fx - 16) / kappa;
        var y = lightness > kappa * epsilon ? cube(fy) : lightness / kappa;
        var z = cube(fz) > epsilon ? cube(fz) : (116 * fz - 16) / kappa;
        return xyzD50(x * 0.3457 / 0.3585, y, z * (1 - 0.3457 - 0.3585) / 0.3585, alpha);
    }

    /// CIE `lch()`, D50: the polar form of [#lab].
    static int lch(double lightness, double chroma, double hue, double alpha) {
        var radians = Math.toRadians(hue);
        var c = Math.max(0, chroma);
        return lab(lightness, c * Math.cos(radians), c * Math.sin(radians), alpha);
    }

    /// `oklab()`: lightness `0..1`, `a` and `b` unbounded.
    static int oklab(double lightness, double a, double b, double alpha) {
        var l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b);
        var m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b);
        var s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b);
        return linearSrgb(
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
                alpha);
    }

    /// `oklch()`: the polar form of [#oklab].
    static int oklch(double lightness, double chroma, double hue, double alpha) {
        var radians = Math.toRadians(hue);
        var c = Math.max(0, chroma);
        return oklab(lightness, c * Math.cos(radians), c * Math.sin(radians), alpha);
    }

    /// `color(display-p3 …)`: encoded channels in `0..1`.
    static int displayP3(double r, double g, double b, double alpha) {
        var lr = decode(r);
        var lg = decode(g);
        var lb = decode(b);
        return xyzD65(
                0.4865709486482162 * lr + 0.26566769316909306 * lg + 0.1982172852343625 * lb,
                0.2289745640697488 * lr + 0.6917385218365064 * lg + 0.079286914093745 * lb,
                0.0000000000000000 * lr + 0.04511338185890264 * lg + 1.043944368900976 * lb,
                alpha);
    }

    /// CIE XYZ relative to D65.
    static int xyzD65(double x, double y, double z, double alpha) {
        return linearSrgb(
                3.2409699419045226 * x - 1.537383177570094 * y - 0.4986107602930034 * z,
                -0.9692436362808796 * x + 1.8759675015077202 * y + 0.04155505740717559 * z,
                0.05563007969699366 * x - 0.20397695888897652 * y + 1.0569715142428786 * z,
                alpha);
    }

    /// CIE XYZ relative to D50, adapted to D65 by Bradford.
    static int xyzD50(double x, double y, double z, double alpha) {
        return xyzD65(
                0.955473421488075 * x - 0.02309845494876471 * y + 0.06325924320057072 * z,
                -0.0283697093338637 * x + 1.0099953980813041 * y + 0.021041441191917323 * z,
                0.012314014864481998 * x - 0.020507649298898964 * y + 1.330365926242124 * z,
                alpha);
    }

    // --- shared arithmetic ----------------------------------------------------

    /// A hue in degrees, into `0..360`.
    static double normalisedHue(double hue) {
        var h = hue % 360;
        return h < 0 ? h + 360 : h;
    }

    /// One channel of `hsl()`, by CSS Color 4's sample code: `offset` is 0 for
    /// red, 8 for green and 4 for blue.
    private static double hslChannel(int offset, double hue, double saturation, double lightness) {
        var k = (offset + hue / 30) % 12;
        var a = saturation * Math.min(lightness, 1 - lightness);
        return lightness - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1));
    }

    private static double decode(double encoded) {
        var sign = encoded < 0 ? -1 : 1;
        var abs = Math.abs(encoded);
        return abs <= 0.04045 ? encoded / 12.92 : sign * Math.pow((abs + 0.055) / 1.055, 2.4);
    }

    private static double encode(double linear) {
        var sign = linear < 0 ? -1 : 1;
        var abs = Math.abs(linear);
        return abs <= 0.0031308 ? linear * 12.92 : sign * (1.055 * Math.pow(abs, 1 / 2.4) - 0.055);
    }

    private static double cube(double value) {
        return value * value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int byteOf(double unit) {
        return (int) Math.round(clamp01(unit) * 255);
    }

    private static int pack(int r, int g, int b, double alpha) {
        return byteOf(alpha) << 24 | r << 16 | g << 8 | b;
    }
}
