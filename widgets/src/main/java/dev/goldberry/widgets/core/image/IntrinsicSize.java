package dev.goldberry.widgets.core.image;

import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Length;

/// The size a picture's box takes when a stylesheet leaves it to the picture:
/// what `image` sizes itself by, and what any widget that shows a picture with a
/// natural size can size itself by the same way.
///
/// `Box` has no measure hook for a picture and no `aspect-ratio`, so the natural
/// size is arithmetic here, per axis:
///
/// - **Both axes auto:** the natural size, capped in proportion by a `max-width`
///   or `max-height` in points: a cap on one axis alone would squash the
///   picture rather than shrink it.
/// - **One axis in points, the other auto:** the other follows the picture's
///   shape.
/// - **Width a percentage, height auto:** the height follows the width the last
///   frame laid out, which the widget hears through
///   [dev.goldberry.input.handler.Measured]. One frame late, which the first
///   frame after a load pays once.
/// - **Both given:** the box is the stylesheet's, and a [Fit] decides the
///   drawing.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#image).
public final class IntrinsicSize {

    private IntrinsicSize() {}

    /// The width and height the box should take, or null to leave the
    /// stylesheet's.
    ///
    /// @param naturalWidth  the picture's natural width, in logical pixels
    /// @param naturalHeight its natural height
    /// @param measuredWidth the width the last frame laid the box out at, or 0
    /// @param style         the box's computed style, for its size and limits
    public static Length @Nullable [] of(
            double naturalWidth, double naturalHeight, double measuredWidth, ComputedStyle style) {
        return of(style.width(), style.height(), naturalWidth, naturalHeight, measuredWidth, style);
    }

    /// The same, for a `width` and `height` other than the style's own: what a
    /// widget passes when it has resolved them some other way.
    public static Length @Nullable [] of(
            Length width,
            Length height,
            double naturalWidth,
            double naturalHeight,
            double measuredWidth,
            ComputedStyle style) {
        var ratio = naturalHeight / naturalWidth;
        var widthAuto = isAuto(width);
        var heightAuto = isAuto(height);
        if (widthAuto && heightAuto) {
            var scale = Math.min(
                    cap(style.limits().maxWidth(), naturalWidth),
                    cap(style.limits().maxHeight(), naturalHeight));
            return new Length[] {points(naturalWidth * scale), points(naturalHeight * scale)};
        }
        if (width instanceof Length.Points(var value) && heightAuto) {
            return new Length[] {width, points(value * ratio)};
        }
        if (height instanceof Length.Points(var value) && widthAuto) {
            return new Length[] {points(value / ratio), height};
        }
        if (width instanceof Length.Percent && heightAuto && measuredWidth > 0) {
            return new Length[] {width, points(measuredWidth * ratio)};
        }
        return null;
    }

    /// A consumer that is told a width only when it changed by at least half a
    /// point, so a settled layout does not rebuild on every frame.
    public static DoubleConsumer whenChanged(double current, DoubleConsumer onChange) {
        return width -> {
            if (Math.abs(width - current) >= 0.5) {
                onChange.accept(width);
            }
        };
    }

    static boolean isAuto(Length length) {
        return length.equals(Length.AUTO) || length.equals(Length.UNDEFINED);
    }

    /// How much of `natural` fits under `cap`, as a factor of 1 or less; 1 for
    /// no cap and for a percentage, which cannot be resolved here.
    private static double cap(Length limit, double natural) {
        return switch (limit) {
            case Length.Points(var value) when value > 0 && natural > 0 -> Math.min(1.0, value / natural);
            default -> 1.0;
        };
    }

    private static Length points(double value) {
        return Length.points((float) value);
    }
}
