package io.github.digitalsmile.goldberry.css;

/// Colour interpolation in OKLCH — `docs/design-system.md` §1.7.
///
/// ## Why not sRGB
///
/// Mixing two colours by averaging their sRGB channels goes through a **grey
/// dead zone**. Nord's danger red and success green, `#bf616a` and `#a3be8c`:
///
/// | Midpoint | Result    | Channel spread |
/// |----------|-----------|----------------|
/// | sRGB     | `#b18f7b` | 54             |
/// | OKLCH    | `#bf9152` | 109            |
///
/// The sRGB answer is a muddy tan that reads as neither end. sRGB is not
/// perceptually uniform and its channels are gamma-encoded, so the arithmetic
/// mean of two encoded values is not the colour halfway between them — it is
/// pulled towards grey, and the more saturated the ends, the further.
///
/// OKLab is uniform by construction, and OKLCH is OKLab in polar form — lightness,
/// chroma, hue. Interpolating there keeps the midpoint of two saturated colours
/// saturated, which is what a hover transition between two accent tokens needs.
///
/// ## Why polar and not OKLab directly
///
/// The two differ only when the hues differ, and then they differ a lot: OKLab's
/// straight line between two hues passes *through* low chroma, while OKLCH's arc
/// keeps chroma up and sweeps the hue. §1.7 says OKLCH, and a hover from blue to
/// teal going via grey is exactly the artefact it is there to prevent.
///
/// ## The powerless hue
///
/// A colour with no chroma — any grey, and every `transparent` — has no hue: the
/// angle is undefined and whatever value the conversion produced is noise.
/// Interpolating towards it around an arbitrary arc would swing a fading colour
/// through hues that are in neither endpoint. So when one end is achromatic, the
/// other end's hue is used for both, which is CSS Color 4's own rule.
final class Oklch {

    /// Below this, a colour is achromatic and its hue is powerless. OKLCH chroma
    /// runs to about 0.37 for the most saturated sRGB colours, so 1e-4 is
    /// comfortably below anything visible and safely above conversion noise.
    private static final double ACHROMATIC = 1e-4;

    private Oklch() {
    }

    /// `from` and `to` mixed, with `t` in `0..1`.
    ///
    /// Lightness, chroma and alpha move linearly; hue takes the **shorter arc**,
    /// which is CSS's default and the only one that does not send a 10° change
    /// the long way round the wheel.
    ///
    /// Alpha is interpolated separately and in sRGB, not premultiplied: these are
    /// unpremultiplied `0xAARRGGBB` values throughout the toolkit, and
    /// premultiplying to interpolate and back would lose the colour of anything
    /// fading to fully transparent.
    ///
    /// @param from `0xAARRGGBB`, not premultiplied
    /// @param to   `0xAARRGGBB`, not premultiplied
    /// @return the mix, in the same form
    static int mix(int from, int to, double t) {
        if (t <= 0) {
            return from;
        }
        if (t >= 1) {
            return to;
        }

        var alpha = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);

        var a = toOklch(from);
        var b = toOklch(to);

        // A powerless hue takes its partner's, so a fade to grey does not sweep
        // through hues neither end has.
        var hueA = a[2];
        var hueB = b[2];
        if (a[1] < ACHROMATIC) {
            hueA = hueB;
        }
        if (b[1] < ACHROMATIC) {
            hueB = hueA;
        }

        // The shorter arc: a 350 degree difference is really a 10 degree one in
        // the other direction.
        var delta = hueB - hueA;
        if (delta > 180) {
            delta -= 360;
        } else if (delta < -180) {
            delta += 360;
        }

        return fromOklch(
                lerp(a[0], b[0], t),
                lerp(a[1], b[1], t),
                hueA + delta * t,
                (int) Math.round(alpha));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /// sRGB to OKLCH: `{ lightness, chroma, hue in degrees }`.
    private static double[] toOklch(int argb) {
        var r = linear(((argb >>> 16) & 0xFF) / 255.0);
        var g = linear(((argb >>> 8) & 0xFF) / 255.0);
        var b = linear((argb & 0xFF) / 255.0);

        // Björn Ottosson's OKLab matrices, verbatim. The cube root is what makes
        // the space uniform: it is the perceptual response curve, and it is why
        // averaging here means something that averaging sRGB does not.
        var l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        var m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        var s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

        var lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        var aAxis = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        var bAxis = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;

        var chroma = Math.hypot(aAxis, bAxis);
        var hue = Math.toDegrees(Math.atan2(bAxis, aAxis));
        if (hue < 0) {
            hue += 360;
        }
        return new double[] {lightness, chroma, hue};
    }

    /// OKLCH back to `0xAARRGGBB`.
    ///
    /// The result is **clamped** into sRGB rather than gamut-mapped. A true
    /// gamut map preserves hue while reducing chroma until the colour fits;
    /// clamping can shift the hue slightly at the extremes. Every colour being
    /// interpolated here is a mix of two colours that were *already* in sRGB, and
    /// the OKLCH arc between two in-gamut colours stays very close to in-gamut,
    /// so the difference is invisible — and a gamut mapper is a solver nobody has
    /// asked for yet.
    private static int fromOklch(double lightness, double chroma, double hueDegrees, int alpha) {
        var hue = Math.toRadians(hueDegrees);
        var aAxis = chroma * Math.cos(hue);
        var bAxis = chroma * Math.sin(hue);

        var l = lightness + 0.3963377774 * aAxis + 0.2158037573 * bAxis;
        var m = lightness - 0.1055613458 * aAxis - 0.0638541728 * bAxis;
        var s = lightness - 0.0894841775 * aAxis - 1.2914855480 * bAxis;

        l = l * l * l;
        m = m * m * m;
        s = s * s * s;

        var r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        var g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        var b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;

        return (clampByte(alpha) << 24)
                | (channel(r) << 16)
                | (channel(g) << 8)
                | channel(b);
    }

    /// sRGB transfer function, encoded to linear.
    private static double linear(double encoded) {
        return encoded <= 0.04045
                ? encoded / 12.92
                : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /// Linear back to an sRGB byte.
    private static int channel(double value) {
        var encoded = value <= 0.0031308
                ? value * 12.92
                : 1.055 * Math.pow(value, 1 / 2.4) - 0.055;
        return clampByte((int) Math.round(encoded * 255));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
