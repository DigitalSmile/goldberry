package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.value.CssColor;

/// What a [ColorPicker] is dragging — hue, saturation, value and alpha.
///
/// **A value**, like every other editing model in §4: every operation returns a
/// new one, and the whole of what a drag on the plane or a turn of the hue slider
/// means is testable with no widget, no font and no frame.
///
/// ## Why HSV and not OKLCH, which §4 asks for
///
/// §4 says "the model carries OKLCH internally because that is what §1.7
/// interpolates in and what the ramp utility uses". That sentence is right about
/// §1.7 and wrong about this control, and the reason is in §4's own next clause:
/// it also asks for **a saturation/value plane**, and a saturation/value plane is
/// HSV by construction — the axes *are* S and V.
///
/// OKLCH's axes are lightness, chroma and hue, and its chroma has a gamut
/// boundary that varies with both of the others: the most saturated blue an sRGB
/// screen can show is a long way from the most saturated yellow. A rectangular
/// plane over OKLCH therefore has **large unreachable regions** — corners that
/// clamp to something else, a cursor that cannot be put where it was clicked, and
/// a colour that changes when the hue slider moves under a stationary cursor. A
/// picker whose plane lies about where its colours are is worse than one that
/// interpolates in the wrong space, and this control interpolates nothing:
/// dragging is not a transition.
///
/// **`Oklch` keeps the job §1.7 gave it.** Every colour *transition* in the
/// toolkit still goes through it, which is what that sentence was protecting; a
/// colour picked here is an ordinary `0xAARRGGBB` and fades like any other
/// (ADR-0276).
///
/// ## Why the model is not simply the ARGB value
///
/// §4 makes the hex field the source of truth, and it is — [ColorPickerState]
/// holds text. But the *plane cursor* cannot be derived from a colour, because the
/// conversion is lossy in exactly the place a user drags to: every colour with
/// `s == 0` is a grey and has no hue, and every colour with `v == 0` is black and
/// has neither. A picker that re-derived HSV each frame would swing the hue slider
/// to red the moment somebody dragged to the left edge, and lose the hue
/// altogether at the bottom.
///
/// So the picker keeps one of these while it is being dragged, and it is the same
/// arrangement `TextEdit` has: the committed value is what leaves, and this is the
/// editing state that produces it.
///
/// @param hue        `0..360`, degrees round the wheel
/// @param saturation `0..1`
/// @param value      `0..1`
/// @param alpha      `0..1`
public record HsvColor(double hue, double saturation, double value, double alpha) {

    /// Opaque black, and what a picker holding nothing shows.
    public static final HsvColor BLACK = new HsvColor(0, 0, 0, 1);

    public HsvColor {
        hue = Double.isFinite(hue) ? Math.floorMod((int) Math.round(hue * 1000), 360_000) / 1000.0 : 0;
        saturation = clamp(saturation);
        value = clamp(value);
        alpha = clamp(alpha);
    }

    private static double clamp(double component) {
        return Double.isFinite(component) ? Math.clamp(component, 0, 1) : 0;
    }

    /// This colour as `0xAARRGGBB`.
    public int toArgb() {
        var chroma = value * saturation;
        var sector = hue / 60.0;
        var second = chroma * (1 - Math.abs(sector % 2 - 1));
        var match = value - chroma;

        double red;
        double green;
        double blue;
        switch ((int) sector % 6) {
            case 0 -> {
                red = chroma;
                green = second;
                blue = 0;
            }
            case 1 -> {
                red = second;
                green = chroma;
                blue = 0;
            }
            case 2 -> {
                red = 0;
                green = chroma;
                blue = second;
            }
            case 3 -> {
                red = 0;
                green = second;
                blue = chroma;
            }
            case 4 -> {
                red = second;
                green = 0;
                blue = chroma;
            }
            default -> {
                red = chroma;
                green = 0;
                blue = second;
            }
        }
        return byteOf(alpha) << 24 | byteOf(red + match) << 16 | byteOf(green + match) << 8 | byteOf(blue + match);
    }

    private static int byteOf(double component) {
        return (int) Math.round(Math.clamp(component, 0, 1) * 255);
    }

    /// `argb` read as hue, saturation and value.
    ///
    /// **Lossy for grey and for black**, which is the whole reason a picker keeps
    /// one of these rather than re-deriving it: a grey has no hue and black has
    /// neither hue nor saturation, so this answers zero for what the colour does
    /// not carry. A caller with a previous hue to preserve uses [#withArgb].
    public static HsvColor ofArgb(int argb) {
        var alpha = ((argb >>> 24) & 0xFF) / 255.0;
        var red = ((argb >>> 16) & 0xFF) / 255.0;
        var green = ((argb >>> 8) & 0xFF) / 255.0;
        var blue = (argb & 0xFF) / 255.0;

        var max = Math.max(red, Math.max(green, blue));
        var min = Math.min(red, Math.min(green, blue));
        var chroma = max - min;

        double hue;
        if (chroma == 0) {
            hue = 0;
        } else if (max == red) {
            hue = 60 * (((green - blue) / chroma) % 6);
        } else if (max == green) {
            hue = 60 * ((blue - red) / chroma + 2);
        } else {
            hue = 60 * ((red - green) / chroma + 4);
        }
        return new HsvColor(hue, max == 0 ? 0 : chroma / max, max, alpha);
    }

    /// `argb` read as HSV, **keeping this colour's hue** when the new one has
    /// none.
    ///
    /// What the hex field hands back: somebody who types `#808080` after dragging
    /// on a blue should keep the blue in the hue slider, because the next drag
    /// away from the left edge is what they meant. The same rule CSS Color 4 calls
    /// a powerless hue, which `Oklch` already applies for the same reason.
    public HsvColor withArgb(int argb) {
        var read = ofArgb(argb);
        return read.saturation == 0 || read.value == 0
                ? new HsvColor(hue, read.saturation, read.value, read.alpha)
                : read;
    }

    /// This colour at `degrees` round the wheel.
    public HsvColor withHue(double degrees) {
        return new HsvColor(degrees, saturation, value, alpha);
    }

    /// This colour at a point on the plane — §4's "saturation/value plane".
    public HsvColor withPlane(double newSaturation, double newValue) {
        return new HsvColor(hue, newSaturation, newValue, alpha);
    }

    /// This colour at `newAlpha`.
    public HsvColor withAlpha(double newAlpha) {
        return new HsvColor(hue, saturation, value, newAlpha);
    }

    /// This colour, opaque — what a picker with `alpha=#false` reports.
    public HsvColor opaque() {
        return alpha == 1 ? this : withAlpha(1);
    }

    /// The pure hue, fully saturated and bright — the colour the plane's top-right
    /// corner is, and the one its background is painted from.
    public int hueArgb() {
        return new HsvColor(hue, 1, 1, 1).toArgb();
    }

    /// This colour as CSS hex — `#rrggbb`, or `#rrggbbaa` when it is translucent.
    ///
    /// §4: "Value is the toolkit's `CssColor`, so a picked colour is directly
    /// usable in a stylesheet". Eight digits only when they say something, because
    /// `#88c0d0ff` in a stylesheet is a reader wondering what the `ff` is for.
    public String toHex() {
        return hex(toArgb());
    }

    /// `argb` as CSS hex — see [#toHex].
    public static String hex(int argb) {
        var opaque = String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
        return ((argb >>> 24) & 0xFF) == 0xFF
                ? opaque
                : opaque + String.format(Locale.ROOT, "%02x", (argb >>> 24) & 0xFF);
    }

    /// What `text` means as a colour, or null when it means nothing.
    ///
    /// [CssColor#parse]'s answer, which is every spelling §8's subset accepts —
    /// `#rgb`, `#rrggbb`, `rgb()`, a named colour — and not a syntax this widget
    /// invented. A leading `#` is optional, because a field somebody is typing
    /// into is a field they will forget it in.
    public static @Nullable Integer parse(String text) {
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        var parsed = CssColor.parse(trimmed);
        return parsed != null ? parsed : CssColor.parse("#" + trimmed);
    }
}
