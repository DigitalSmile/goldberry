package dev.goldberry.vector.view;

import java.util.List;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.image.Image;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Painter;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.core.image.IntrinsicSize;

/// What [SvgBox] and [SvgFigure] share: how big the box is, and the document
/// drawn in it in the box's `color`.
final class SvgPaint {

    private SvgPaint() {}

    static Box render(SvgLoad load, Fit fit, double measuredWidth, ComputedStyle style, List<Box> children) {
        var box = Box.of().style(style).children(children.toArray(Box[]::new));
        if (!(load instanceof SvgLoad.Ready(var document))) {
            return box;
        }
        var natural = document.naturalSize();
        var size = IntrinsicSize.of(natural.width(), natural.height(), measuredWidth, style);
        if (size != null) {
            box = box.size(size[0], size[1]);
        }
        Image drawing;
        try {
            drawing = document.in(style.color());
        } catch (RuntimeException e) {
            // The document parsed in black when it was read; another colour
            // cannot make it unreadable, but a library released since can.
            return box;
        }
        return box.painting(new SvgPainter(drawing, fit, natural.width(), natural.height()));
    }

    /// The drawing as a value: the same document in the same colour, placed the
    /// same way, is an equal painter, and a new colour is a new drawing.
    ///
    /// The frame draws it from a raster of the device pixels it covers.
    private record SvgPainter(Image drawing, Fit fit, double naturalWidth, double naturalHeight) implements Painter {

        @Override
        public void paint(Frame frame, LogicalSize area) {
            var placement = fit.place(
                    drawing.width(), drawing.height(), naturalWidth, naturalHeight, area.width(), area.height());
            if (placement != null) {
                frame.drawImage(
                        drawing,
                        placement.source(),
                        placement.x(),
                        placement.y(),
                        placement.width(),
                        placement.height(),
                        1);
            }
        }
    }
}
