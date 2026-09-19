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
    /// ## The box's own rectangle is cut out of every band
    ///
    /// CSS paints an outer shadow only *outside* the border box. Two sub-paths
    /// go into the rasterizer's path — the band, then
    /// [ShadowGeometry#borderBox] — and the pair is filled **even-odd**, so a
    /// point inside both is crossed twice and left empty. It is the fill rule
    /// and not the winding that does this: a reversed inner sub-path under
    /// Blend2D's default non-zero rule fills the parts of itself the outer shape
    /// does not cover, which paints a dark ring exactly where a blur was
    /// supposed to be erased (ADR-0427).
    ///
    /// The hole costs one more sub-path per band and no extra fill. It is
    /// unconditional, which it has to be to mean anything: it is invisible under
    /// an opaque background and the whole point under a translucent one, and a
    /// painter that asked which it had would be making the deviation conditional
    /// rather than removing it.
    ///
    /// @param path     a pooled rasterizer path, reset by this method between
    ///                 bands and left holding the last one
    /// @param corners  the box's corner radii — a shadow cast by a rounded box is
    ///                 rounded, and the hole cut out of it has the box's own radii
    static void paint(
            Frame frame,
            BlendPath path,
            Shadow shadow,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {

        var bands = ShadowRamp.bands(shadow);
        if (bands.isEmpty()) {
            return;
        }
        // Built once, outside the loop: the hole is the same shape for every
        // band, and it is the band that moves.
        var hole = ShadowGeometry.borderBox(width, height, corners);
        var covered = ShadowGeometry.coveredAt(shadow);
        for (var band : bands) {
            if (band.grow() <= covered) {
                // This band is inside the hole, so the fill rule would erase all
                // of it -- and `grow` only decreases, so every band after it is
                // too. Half the fills of a centred blur, saved by not making
                // them rather than by making them invisible.
                break;
            }
            var outline = ShadowGeometry.band(width, height, corners, shadow, band.grow());
            if (outline.isEmpty()) {
                continue;
            }
            path.reset();
            outline.replayInto(path);
            hole.replayInto(path);
            frame.fillPathEvenOdd(x, y, path, band.argb());
        }
    }
}
