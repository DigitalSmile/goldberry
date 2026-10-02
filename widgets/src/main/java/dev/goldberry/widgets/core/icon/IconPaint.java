package dev.goldberry.widgets.core.icon;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.model.LogicalSize;

/// How an [IconView]'s box draws its icon: the outline under a scale that fits
/// it to the box, in the box's colour.
///
/// Shared by the two parts, which differ in what a reader is told and in
/// nothing that is drawn.
final class IconPaint {

    private IconPaint() {}

    /// The box: the style's, with the icon painted into its content area.
    static Box render(@Nullable Icon icon, ComputedStyle style) {
        var box = Box.of().style(style);
        if (icon == null) {
            return box;
        }
        var argb = style.color();
        return box.painting((frame, size) -> paint(frame, size, icon, argb));
    }

    /// Strokes `icon` into a `size` box, scaled to the smaller side and
    /// centred along the larger.
    ///
    /// Under a transform rather than rebuilt at the box's size: the stroke is
    /// scaled with the outline, which is how the set is drawn, and the path
    /// stays the one value the icon already holds. The painter runs inside a
    /// save and restore, so the transform goes no further than this icon.
    static void paint(Frame frame, LogicalSize size, Icon icon, int argb) {
        var side = Math.min(size.width(), size.height());
        if (!(side > 0)) {
            return;
        }
        var scale = side / icon.size();
        frame.concat(scale, 0, 0, scale, (size.width() - side) / 2.0, (size.height() - side) / 2.0);
        frame.strokePath(icon.outline(), Icon.pen(icon.strokeWidth()), argb);
    }
}
