package dev.goldberry.motion;

import java.util.List;

import dev.goldberry.css.Border;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.cascade.Transitions.Animatable;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.IndividualTransform;
import dev.goldberry.css.value.Shadow;
import dev.goldberry.css.value.Transform;

/// How each animatable property is read off a style, written back
/// onto one, compared and interpolated.
///
/// One place for the four switches, shared by the two kinds of motion: a
/// transition between two styles the cascade resolved, and a keyframe sequence
/// a stylesheet named. Two copies would interpolate a
/// colour through OKLCH in one and through sRGB in the other the first time
/// either changed.
final class Animatables {

    private Animatables() {}

    /// A property's value read off a style.
    ///
    /// Colours are carried as their `0xAARRGGBB` bits in a `Double`, which is
    /// exact — a double holds every 32-bit integer — so the numeric properties
    /// share one representation. `transform` is the [Transform] itself,
    /// `box-shadow` the list of [Shadow]s, and `border-color` the [Border] itself,
    /// because a box's sides can be four colours and a transition
    /// moves each of them; see `Animations.Running` for why that is worth a
    /// boxed value.
    static Object valueOf(ComputedStyle style, Animatable property) {
        return switch (property) {
            case OPACITY -> style.opacity();
            case BACKGROUND_COLOR -> (double) style.background();
            case BACKGROUND_POSITION -> style.fill().positions();
            case BORDER_COLOR -> style.decoration().border();
            case BOX_SHADOW -> style.decoration().shadows();
            case COLOR -> (double) style.color();
            // The functions and the origin only: the individual properties and
            // the box are their own, and a `transform` transition leaves them.
            case TRANSFORM ->
                new Transform(style.transform().functions(), style.transform().origin());
            case TRANSLATE ->
                IndividualTransform.NONE.translate(
                        style.transform().individual().translate());
            case ROTATE ->
                IndividualTransform.NONE.rotate(style.transform().individual().rotate());
            case SCALE ->
                IndividualTransform.NONE.scale(style.transform().individual().scale());
            case BORDER_RADIUS -> style.decoration().corners();
            case OUTLINE_COLOR -> (double) style.decoration().outlineColor();
            case OUTLINE_OFFSET -> style.decoration().outlineOffset();
        };
    }

    static ComputedStyle withValue(ComputedStyle style, Animatable property, Object value) {
        return switch (property) {
            case OPACITY -> style.opacity((Double) value);
            case BACKGROUND_COLOR -> style.background(argb(value));
            case BACKGROUND_POSITION -> style.fill(style.fill().positions(positions(value)));
            // The colours only: a width is not animatable, and the cascade's own
            // width for this frame is the one that stands.
            case BORDER_COLOR ->
                style.decoration(
                        style.decoration().border(style.decoration().border().coloursOf((Border) value)));
            case BOX_SHADOW -> style.decoration(style.decoration().shadows(shadows(value)));
            case COLOR -> style.color(argb(value));
            case TRANSFORM -> {
                var moved = (Transform) value;
                yield style.transform(
                        style.transform().functions(moved.functions()).origin(moved.origin()));
            }
            case TRANSLATE ->
                style.transform(style.transform()
                        .individual(
                                style.transform().individual().translate(((IndividualTransform) value).translate())));
            case ROTATE ->
                style.transform(style.transform()
                        .individual(style.transform().individual().rotate(((IndividualTransform) value).rotate())));
            case SCALE ->
                style.transform(style.transform()
                        .individual(style.transform().individual().scale(((IndividualTransform) value).scale())));
            case BORDER_RADIUS -> style.decoration(style.decoration().corners((Corners) value));
            case OUTLINE_COLOR -> style.decoration(style.decoration().outlineColor(argb(value)));
            case OUTLINE_OFFSET -> style.decoration(style.decoration().outlineOffset((Double) value));
        };
    }

    static int argb(Object value) {
        return (int) Math.round((Double) value);
    }

    /// A `box-shadow` value back from its boxed form: the list
    /// [#valueOf] read off a style.
    static List<Shadow> shadows(Object value) {
        return ((List<?>) value).stream().map(Shadow.class::cast).toList();
    }

    /// A `background-position` list back from its boxed form.
    static List<BackgroundPosition> positions(Object value) {
        return ((List<?>) value).stream().map(BackgroundPosition.class::cast).toList();
    }

    static boolean sameValue(Animatable property, Object a, Object b) {
        // Opacity is the one that arrives from arithmetic rather than from a
        // literal -- `45%` of an inherited value -- so two "equal" opacities can
        // differ in the last bit and a transition would restart every frame.
        if (property == Animatable.OPACITY) {
            return Math.abs((Double) a - (Double) b) < 1e-6;
        }
        // A width change with every colour held is not a colour transition.
        if (property == Animatable.BORDER_COLOR) {
            return ((Border) a).sameColours((Border) b);
        }
        return a.equals(b);
    }

    /// Where a property is at eased progress `t`.
    ///
    /// Numbers move linearly; colours move through **OKLCH**, because the sRGB
    /// midpoint of two saturated colours is a muddy grey that is neither of them,
    /// which is why the space is specified rather than left to the
    /// implementation; a transform moves function by function, which is what
    /// makes halfway between `rotate(0)` and `rotate(180deg)` a rotation rather
    /// than a collapsed box.
    static Object interpolate(Animatable property, Object from, Object to, double t) {
        return switch (property) {
            // Clamped: a curve an application wrote may overshoot, and an
            // opacity past 1 or below 0 is not one a box can have.
            case OPACITY -> Math.max(0, Math.min(1, (Double) from + ((Double) to - (Double) from) * t));
            case BACKGROUND_COLOR, COLOR, OUTLINE_COLOR -> (double) CssColor.mix(argb(from), argb(to), t);
            case OUTLINE_OFFSET -> (Double) from + ((Double) to - (Double) from) * t;
            case BORDER_RADIUS -> ((Corners) from).mix((Corners) to, t);
            case TRANSLATE, ROTATE, SCALE -> ((IndividualTransform) from).mix((IndividualTransform) to, t);
            case BACKGROUND_POSITION -> BackgroundPosition.mix(positions(from), positions(to), t);
            case BORDER_COLOR -> ((Border) from).mixColours((Border) to, t);
            case BOX_SHADOW -> Shadow.mix(shadows(from), shadows(to), t);
            case TRANSFORM -> ((Transform) from).mix((Transform) to, t);
        };
    }
}
