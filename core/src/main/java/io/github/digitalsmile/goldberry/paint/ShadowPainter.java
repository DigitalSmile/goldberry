package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.paint.shadow.ShadowGeometry;
import io.github.digitalsmile.goldberry.paint.shadow.ShadowRamp;

/// Puts a box's `box-shadow` on the frame.
///
/// Three lines of work and two packages of reasoning behind it:
/// [ShadowRamp] decides how many bands the fade is drawn with and how opaque
/// each is, [ShadowGeometry] decides what shape each band is, and this fills
/// them — outermost first, so the alphas [ShadowRamp] solved for land in the
/// order it solved them in.
///
/// It lives in `paint` rather than in `paint.shadow` for one reason: the pooled
/// [BlendPath]. A band is built as a [Path] value and replayed into the
/// rasterizer's own path, which is reset between bands, so a forty-eight band
/// shadow costs one native path and no native allocation at all — the same trade
/// [RoundRect] makes for a border. `paint.shadow` has no business knowing what a
/// `BlendPath` is, and does not (ADR-0310).
final class ShadowPainter {

    private ShadowPainter() {}

    /// Draws `shadow` behind the box at `(x, y)`.
    ///
    /// **Before the background**, which is the whole of what "drop shadow" means
    /// in the paint order: CSS puts the shadow under the background, the
    /// background under the border, and the border under the content.
    ///
    /// @param path     a pooled rasterizer path, reset by this method between
    ///                 bands and left holding the last one
    /// @param corners  the box's corner radii — a shadow cast by a rounded box is
    ///                 rounded
    /// @param occluded whether the box will paint an opaque fill over its own
    ///                 rectangle, which lets [ShadowRamp] drop the bands that
    ///                 would be hidden under it
    static void paint(
            Frame frame,
            BlendPath path,
            Shadow shadow,
            double x,
            double y,
            double width,
            double height,
            Corners corners,
            boolean occluded) {

        for (var band : ShadowRamp.bands(shadow, occluded)) {
            var outline = ShadowGeometry.band(width, height, corners, shadow, band.grow());
            if (outline.isEmpty()) {
                continue;
            }
            path.reset();
            outline.replayInto(path);
            frame.fillPath(x, y, path, band.argb());
        }
    }
}
