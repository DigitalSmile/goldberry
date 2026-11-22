package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;

/// What [ImageBox] and [ImageFigure] share: how big the box is, and what is drawn
/// in it.
///
/// ## The size is the image's until a stylesheet says otherwise
///
/// The arithmetic is [IntrinsicSize]'s, which other widgets that show a picture
/// size themselves by too.
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
        return box.painting(new ImagePainter(ready.image(), fit, naturalWidth, naturalHeight));
    }

    /// The picture as a value. An [Image] is immutable and compared by
    /// identity, so the same loaded image placed the same way is an equal
    /// painter, and an animation's next frame, which is a new image, is not.
    private record ImagePainter(Image image, Fit fit, double naturalWidth, double naturalHeight) implements Painter {

        @Override
        public void paint(Frame frame, LogicalSize area) {
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
        }
    }

    /// The width and height the box should take, or null to leave the
    /// stylesheet's: [IntrinsicSize#of(Length, Length, double, double, double,
    /// ComputedStyle)].
    static Length @Nullable [] intrinsic(
            Length width,
            Length height,
            double naturalWidth,
            double naturalHeight,
            double measuredWidth,
            ComputedStyle style) {
        return IntrinsicSize.of(width, height, naturalWidth, naturalHeight, measuredWidth, style);
    }

    /// [IntrinsicSize#whenChanged].
    static DoubleConsumer whenChanged(double current, DoubleConsumer onChange) {
        return IntrinsicSize.whenChanged(current, onChange);
    }
}
