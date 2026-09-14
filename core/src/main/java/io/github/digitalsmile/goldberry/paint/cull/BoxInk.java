package io.github.digitalsmile.goldberry.paint.cull;

import java.util.Objects;

import io.github.digitalsmile.goldberry.paint.Box;

/// What **one** box draws, in its own coordinates, where its border box is
/// `(0, 0, width, height)`.
///
/// Three things reach outside that rectangle, and each of them is a deliberate
/// part of what a box is rather than an overflow:
///
/// - the **focus ring**, which CSS draws outside the border box and which takes
///   no layout space — `outline-offset` plus `outline-width`, all four sides;
/// - the **drop shadow**, which is cast behind the box and is *asymmetric*:
///   `0 8px 32px` reaches 24px below and 8px above, so one outset for four sides
///   would either repaint a band nothing drew in or, the day a shadow is offset
///   further than it is blurred, miss one (ADR-0310);
/// - an **icon larger than its slot**, which
///   [io.github.digitalsmile.goldberry.paint.BoxPainter] centres rather than
///   corners — a 20px glyph in the 16px lead column of a menu row hangs 2px out
///   on every side (ADR-0143).
///
/// ## What is deliberately not here
///
/// Content that simply **overflows** its own box: a paragraph in a box a
/// stylesheet gave a height too small for it, or a child wider than its parent.
/// Those are covered where they belong — a child is a node of its own and
/// contributes its own ink to [Ink#union], and a measured leaf is sized by the
/// text in it, so a text box fits its text by construction. A box whose *own*
/// content spills past a height a rule pinned is drawing over its siblings
/// already; the culler treats it the way the painter does.
///
/// Shared with the layer bounds in
/// [io.github.digitalsmile.goldberry.paint.tree.RenderTree], which asks the same
/// question for a different reason — a promoted subtree rasterized into a box
/// smaller than it draws loses its focus ring — so the rule about what is outside
/// a box lives in exactly one place.
public final class BoxInk {

    private BoxInk() {}

    /// The ink `box` puts down at `width` × `height`, in its own coordinates.
    public static Ink of(Box box, double width, double height) {
        Objects.requireNonNull(box, "box");
        var decoration = box.decoration();
        // The ring is symmetric and outside the edge; the shadow is neither.
        var ring = decoration.hasOutline() ? decoration.outlineOffset() + decoration.outlineWidth() : 0;
        var shadow = decoration.shadow();
        // An icon is centred in its slot, so a glyph wider than the box hangs out
        // by half the difference on each side -- and by nothing at all in the
        // common case, where `Box.icon` sized the box to the glyph.
        var glyph = box.icon() == null ? 0 : box.icon().icon().size();
        var overhangX = Math.max(0, (glyph - width) / 2);
        var overhangY = Math.max(0, (glyph - height) / 2);
        // Subtracted rather than negated: `-Math.max(0, 0)` is **negative zero**,
        // which is equal to zero everywhere it matters and prints as `-0.0` in
        // the one place it does not -- a failing assertion, where it reads as a
        // bug that is not there.
        return new Ink(
                0 - Math.max(Math.max(ring, shadow.outsetLeft()), overhangX),
                0 - Math.max(Math.max(ring, shadow.outsetTop()), overhangY),
                width + Math.max(Math.max(ring, shadow.outsetRight()), overhangX),
                height + Math.max(Math.max(ring, shadow.outsetBottom()), overhangY));
    }
}
