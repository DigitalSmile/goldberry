package io.github.digitalsmile.goldberry.motion;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.cascade.Transitions.Animatable;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.css.value.Transform;

/// How each of the six animatable properties is read off a style, written back
/// onto one, compared and interpolated.
///
/// One place for the four switches, shared by the two kinds of motion: a
/// transition between two styles the cascade resolved, and a keyframe sequence
/// a stylesheet named (ADR-0067, ADR-0353). Two copies would interpolate a
/// colour through OKLCH in one and through sRGB in the other the first time
/// either changed.
final class Animatables {

    private Animatables() {}

    /// A property's value read off a style.
    ///
    /// Colours are carried as their `0xAARRGGBB` bits in a `Double`, which is
    /// exact — a double holds every 32-bit integer — so the four numeric
    /// properties share one representation. `transform` is the [Transform]
    /// itself and `box-shadow` the [Shadow] itself; see `Animations.Running` for
    /// why that is worth a boxed value.
    static Object valueOf(ComputedStyle style, Animatable property) {
        return switch (property) {
            case OPACITY -> style.opacity();
            case BACKGROUND_COLOR -> (double) style.background();
            case BORDER_COLOR -> (double) style.decoration().borderColor();
            case BOX_SHADOW -> style.decoration().shadow();
            case COLOR -> (double) style.color();
            case TRANSFORM -> style.transform();
        };
    }

    static ComputedStyle withValue(ComputedStyle style, Animatable property, Object value) {
        return switch (property) {
            case OPACITY -> style.opacity((Double) value);
            case BACKGROUND_COLOR -> style.background(argb(value));
            case BORDER_COLOR -> style.decoration(style.decoration().borderColor(argb(value)));
            case BOX_SHADOW -> style.decoration(style.decoration().shadow((Shadow) value));
            case COLOR -> style.color(argb(value));
            case TRANSFORM -> style.transform((Transform) value);
        };
    }

    static int argb(Object value) {
        return (int) Math.round((Double) value);
    }

    static boolean sameValue(Animatable property, Object a, Object b) {
        // Opacity is the one that arrives from arithmetic rather than from a
        // literal -- `45%` of an inherited value -- so two "equal" opacities can
        // differ in the last bit and a transition would restart every frame.
        if (property == Animatable.OPACITY) {
            return Math.abs((Double) a - (Double) b) < 1e-6;
        }
        return a.equals(b);
    }

    /// Where a property is at eased progress `t`.
    ///
    /// Numbers move linearly; colours move through **OKLCH**, because the sRGB
    /// midpoint of two saturated colours is a muddy grey that is neither of them
    /// (§1.7, and the reason the space is specified rather than left to the
    /// implementation); a transform moves function by function, which is what
    /// makes halfway between `rotate(0)` and `rotate(180deg)` a rotation rather
    /// than a collapsed box.
    static Object interpolate(Animatable property, Object from, Object to, double t) {
        return switch (property) {
            case OPACITY -> (Double) from + ((Double) to - (Double) from) * t;
            case BACKGROUND_COLOR, BORDER_COLOR, COLOR -> (double) CssColor.mix(argb(from), argb(to), t);
            case BOX_SHADOW -> ((Shadow) from).mix((Shadow) to, t);
            case TRANSFORM -> ((Transform) from).mix((Transform) to, t);
        };
    }
}
