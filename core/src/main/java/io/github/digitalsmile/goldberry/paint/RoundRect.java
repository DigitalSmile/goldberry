package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;

/// A rounded rectangle, appended to a Blend2D path.
///
/// ## What is left of this class
///
/// The four cubics, the KAPPA constant and the corner-fitting rule moved to
/// [Path#roundRect(double, double, double, double, Corners)] when `:core` grew a
/// path type of its own (ADR-0277). What remains is the adapter `:core`'s own
/// painters reach for: they build into a **pooled** `BlendPath` and reset it
/// between shapes, and this is how a toolkit-owned shape gets into one.
///
/// It delegates rather than keeping its own copy of the arithmetic, because two
/// derivations of the same four arcs that must agree is exactly what ADR-0216
/// was written about — and the golden images could not tell them apart until one
/// of them drifted.
///
/// Package-private since ADR-0277: an application wanting a rounded rectangle
/// wants [Path#roundRect], which takes no native type and needs no pool.
final class RoundRect {

    private RoundRect() {}

    /// Appends the outline of `(x, y, width, height)` with corner radius
    /// `radius` to `path`.
    static void addTo(BlendPath path, double x, double y, double width, double height, double radius) {
        addTo(path, x, y, width, height, Corners.all(radius));
    }

    /// The same, with **a radius per corner** — CSS's `border-radius: 7px 7px 0 0`.
    static void addTo(BlendPath path, double x, double y, double width, double height, Corners corners) {
        Path.roundRect(x, y, width, height, corners).replayInto(path);
    }
}
