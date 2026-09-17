package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.List;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;

/// What [ImageBox] and [ImageFigure] share: how big the box is, and what is drawn
/// in it.
///
/// ## The size is the image's until a stylesheet says otherwise
///
/// `Box` has no measure hook for an image and no `aspect-ratio`, so the natural
/// size is arithmetic here, per axis:
///
/// - **Both axes auto:** the natural size, capped in proportion by a `max-width`
///   or `max-height` in points — a cap on one axis alone would squash the
///   picture rather than shrink it, which is
///   [io.github.digitalsmile.goldberry.content.image.Picture]'s argument.
/// - **One axis in points, the other auto:** the other follows the image's shape.
/// - **Width a percentage, height auto:** the height follows the width the last
///   frame laid out, through [io.github.digitalsmile.goldberry.input.handler.Measured].
///   One frame late, which the first frame after a load pays once; it is the one
///   case the renderer cannot answer before layout (ADR-0358).
/// - **Both given:** the box is the stylesheet's, and [Fit] decides the drawing.
final class ImagePaint {

    private ImagePaint() {}

    static Box render(ImageLoad load, Fit fit, double measuredWidth, ComputedStyle style, List<Box> children) {
        var box = Box.of().style(style).children(children.toArray(Box[]::new));
        if (!(load instanceof ImageLoad.Ready ready)) {
            return box;
        }
        var naturalWidth = ready.naturalWidth();
        var naturalHeight = ready.naturalHeight();
        var width = style.width();
        var height = style.height();
        var size = intrinsic(width, height, naturalWidth, naturalHeight, measuredWidth, style);
        if (size != null) {
            box = box.size(size[0], size[1]);
        }
        var image = ready.image();
        return box.painting((frame, area) -> {
            var placement =
                    fit.place(image.width(), image.height(), naturalWidth, naturalHeight, area.width(), area.height());
            if (placement != null) {
                frame.drawImage(
                        image,
                        placement.source(),
                        placement.x(),
                        placement.y(),
                        placement.width(),
                        placement.height(),
                        1);
            }
        });
    }

    /// The width and height the box should take, or null to leave the
    /// stylesheet's.
    static Length @Nullable [] intrinsic(
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

    /// A consumer that is told a width only when it changed by at least half a
    /// point, so a settled layout does not rebuild on every frame.
    static DoubleConsumer whenChanged(double current, DoubleConsumer onChange) {
        return width -> {
            if (Math.abs(width - current) >= 0.5) {
                onChange.accept(width);
            }
        };
    }
}
