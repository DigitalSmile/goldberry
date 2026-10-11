package dev.goldberry.css.value.colour;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// A colour space a colour can be interpolated in: what `in oklch` names in a
/// gradient and in `color-mix()`.
///
/// ```java
/// double[] lch = ColourSpace.OKLCH.coordinates(0xFF3B82F6);
/// int back = ColourSpace.OKLCH.argb(lch, 1);
/// ```
///
/// Each converts a colour into its three coordinates and back. The way in is
/// exact; the way out is [ColourSpaces]', clamped into sRGB. A polar space has
/// a hue, whose index [#hue()] gives, which interpolation turns the short or
/// the long way round.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public enum ColourSpace {

    /// sRGB as encoded: the space every colour here is stored in, and the one a
    /// gradient interpolates in when nobody says.
    SRGB,
    /// sRGB in linear light.
    SRGB_LINEAR,
    /// Display P3, encoded.
    DISPLAY_P3,
    /// CIE Lab, D50.
    LAB,
    /// CIE LCH, D50: Lab in polar form.
    LCH,
    /// OKLab.
    OKLAB,
    /// OKLCh: OKLab in polar form, which is where a hue stays itself as it
    /// lightens.
    OKLCH,
    /// CIE XYZ, D65.
    XYZ_D65,
    /// CIE XYZ, D50.
    XYZ_D50,
    /// HSL.
    HSL,
    /// HWB.
    HWB;

    /// The space a CSS name gives, or null when it names none of these:
    /// `srgb`, `srgb-linear`, `display-p3`, `lab`, `lch`, `oklab`, `oklch`,
    /// `xyz`, `xyz-d65`, `xyz-d50`, `hsl`, `hwb`.
    public static @Nullable ColourSpace parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "srgb" -> SRGB;
            case "srgb-linear" -> SRGB_LINEAR;
            case "display-p3" -> DISPLAY_P3;
            case "lab" -> LAB;
            case "lch" -> LCH;
            case "oklab" -> OKLAB;
            case "oklch" -> OKLCH;
            case "xyz", "xyz-d65" -> XYZ_D65;
            case "xyz-d50" -> XYZ_D50;
            case "hsl" -> HSL;
            case "hwb" -> HWB;
            default -> null;
        };
    }

    /// Which coordinate is a hue, or -1 for a space with none.
    public int hue() {
        return switch (this) {
            case HSL, HWB -> 0;
            case LCH, OKLCH -> 2;
            case SRGB, SRGB_LINEAR, DISPLAY_P3, LAB, OKLAB, XYZ_D65, XYZ_D50 -> -1;
        };
    }

    /// Whether `coordinates` have no hue to speak of: a grey, whose hue a mix
    /// takes from the other colour.
    public boolean isAchromatic(double[] coordinates) {
        return switch (this) {
            case HSL -> coordinates[1] < 1e-6;
            case HWB -> coordinates[1] + coordinates[2] >= 1 - 1e-6;
            case LCH -> coordinates[1] < 1e-3;
            case OKLCH -> coordinates[1] < 1e-5;
            case SRGB, SRGB_LINEAR, DISPLAY_P3, LAB, OKLAB, XYZ_D65, XYZ_D50 -> false;
        };
    }

    /// `argb`'s three coordinates in this space. Hues are in degrees; HSL and
    /// HWB's other two are `0..1`; Lab's lightness is `0..100` and OKLab's
    /// `0..1`.
    public double[] coordinates(int argb) {
        var r = ((argb >> 16) & 0xFF) / 255.0;
        var g = ((argb >> 8) & 0xFF) / 255.0;
        var b = (argb & 0xFF) / 255.0;
        return switch (this) {
            case SRGB -> new double[] {r, g, b};
            case SRGB_LINEAR -> new double[] {decode(r), decode(g), decode(b)};
            case DISPLAY_P3 -> {
                var xyz = xyzD65(decode(r), decode(g), decode(b));
                yield new double[] {
                    encode(2.4934969119414254 * xyz[0] - 0.9313836179191239 * xyz[1] - 0.40271078445071684 * xyz[2]),
                    encode(-0.8294889695615747 * xyz[0] + 1.7626640603183463 * xyz[1] + 0.023624685841943577 * xyz[2]),
                    encode(0.03584583024378447 * xyz[0] - 0.07617238926804182 * xyz[1] + 0.9568845240076872 * xyz[2])
                };
            }
            case XYZ_D65 -> xyzD65(decode(r), decode(g), decode(b));
            case XYZ_D50 -> d50(xyzD65(decode(r), decode(g), decode(b)));
            case LAB -> lab(d50(xyzD65(decode(r), decode(g), decode(b))));
            case LCH -> polar(lab(d50(xyzD65(decode(r), decode(g), decode(b)))));
            case OKLAB -> oklab(decode(r), decode(g), decode(b));
            case OKLCH -> polar(oklab(decode(r), decode(g), decode(b)));
            case HSL -> hsl(r, g, b);
            case HWB -> {
                var hsl = hsl(r, g, b);
                var max = Math.max(r, Math.max(g, b));
                var min = Math.min(r, Math.min(g, b));
                yield new double[] {hsl[0], min, 1 - max};
            }
        };
    }

    /// The colour at `coordinates` with `alpha`, as `0xAARRGGBB`, clamped
    /// into sRGB.
    public int argb(double[] coordinates, double alpha) {
        var c0 = coordinates[0];
        var c1 = coordinates[1];
        var c2 = coordinates[2];
        return switch (this) {
            case SRGB -> ColourSpaces.srgb(c0, c1, c2, alpha);
            case SRGB_LINEAR -> ColourSpaces.linearSrgb(c0, c1, c2, alpha);
            case DISPLAY_P3 -> ColourSpaces.displayP3(c0, c1, c2, alpha);
            case XYZ_D65 -> ColourSpaces.xyzD65(c0, c1, c2, alpha);
            case XYZ_D50 -> ColourSpaces.xyzD50(c0, c1, c2, alpha);
            case LAB -> ColourSpaces.lab(c0, c1, c2, alpha);
            case LCH -> ColourSpaces.lch(c0, c1, c2, alpha);
            case OKLAB -> ColourSpaces.oklab(c0, c1, c2, alpha);
            case OKLCH -> ColourSpaces.oklch(c0, c1, c2, alpha);
            case HSL -> ColourSpaces.hsl(c0, c1, c2, alpha);
            case HWB -> ColourSpaces.hwb(c0, c1, c2, alpha);
        };
    }

    private static double[] xyzD65(double r, double g, double b) {
        return new double[] {
            0.41239079926595934 * r + 0.357584339383878 * g + 0.1804807884018343 * b,
            0.21263900587151027 * r + 0.715168678767756 * g + 0.07219231536073371 * b,
            0.01933081871559182 * r + 0.11919477979462598 * g + 0.9505321522496607 * b
        };
    }

    /// D65 to D50, Bradford.
    private static double[] d50(double[] xyz) {
        return new double[] {
            1.0479298208405488 * xyz[0] + 0.022946793341019088 * xyz[1] - 0.05019222954313557 * xyz[2],
            0.029627815688159344 * xyz[0] + 0.990434484573249 * xyz[1] - 0.01707382502938514 * xyz[2],
            -0.009243058152591178 * xyz[0] + 0.015055144896577895 * xyz[1] + 0.7518742899580008 * xyz[2]
        };
    }

    private static double[] lab(double[] xyz) {
        final double kappa = 24389.0 / 27;
        final double epsilon = 216.0 / 24389;
        var white = new double[] {0.3457 / 0.3585, 1, (1 - 0.3457 - 0.3585) / 0.3585};
        var f = new double[3];
        for (var i = 0; i < 3; i++) {
            var t = xyz[i] / white[i];
            f[i] = t > epsilon ? Math.cbrt(t) : (kappa * t + 16) / 116;
        }
        return new double[] {116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])};
    }

    private static double[] oklab(double r, double g, double b) {
        var l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        var m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        var s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }

    /// `[L, a, b]` as `[L, C, h]`, the hue in degrees.
    private static double[] polar(double[] lab) {
        var chroma = Math.hypot(lab[1], lab[2]);
        var hue = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return new double[] {lab[0], chroma, ColourSpaces.normalisedHue(hue)};
    }

    private static double[] hsl(double r, double g, double b) {
        var max = Math.max(r, Math.max(g, b));
        var min = Math.min(r, Math.min(g, b));
        var lightness = (max + min) / 2;
        var delta = max - min;
        if (delta == 0) {
            return new double[] {0, 0, lightness};
        }
        var saturation = lightness <= 0.5 ? delta / (max + min) : delta / (2 - max - min);
        double hue;
        if (max == r) {
            hue = (g - b) / delta + (g < b ? 6 : 0);
        } else if (max == g) {
            hue = (b - r) / delta + 2;
        } else {
            hue = (r - g) / delta + 4;
        }
        return new double[] {hue * 60, saturation, lightness};
    }

    private static double decode(double encoded) {
        return encoded <= 0.04045 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    private static double encode(double linear) {
        var sign = linear < 0 ? -1 : 1;
        var abs = Math.abs(linear);
        return abs <= 0.0031308 ? linear * 12.92 : sign * (1.055 * Math.pow(abs, 1 / 2.4) - 0.055);
    }
}
