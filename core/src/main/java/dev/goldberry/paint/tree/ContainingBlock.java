package dev.goldberry.paint.tree;

import java.util.Objects;

import dev.goldberry.css.Border;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;

/// Where an absolutely positioned box's insets are measured from: its
/// containing block's **padding box**, the border box less each side's border
/// width.
///
/// CSS puts the padding box at the *outer* edge of the padding, just inside the
/// border. `left: 0; top: 0` in a box with `padding: 12px` and no border is
/// therefore the box's own corner, (0, 0), and not the corner of its content,
/// which is where a child in flow starts. Yoga already measures an inset from
/// the outer edge of the node, and that is right for every box except one with
/// a border.
///
/// A border is the exception because it never reaches Yoga. The toolkit paints
/// it over the padding, inside the box (see [Border]), so the node's outer edge
/// is the border's outer edge and an inset of zero would put the child on top
/// of the border. CSS puts it inside. So the correction is the border width on
/// each side the box named, and it is applied where the inset is put onto the
/// node rather than where the layout comes back: with `left` and `right` both
/// given, Yoga derives the child's *width* from the containing block's width
/// less the two insets, and shifting both insets makes that width the padding
/// box's. A correction applied after the layout pass could move the child and
/// could not resize it.
///
/// The padding box here is the same rectangle [dev.goldberry.paint.shadow.ShadowGeometry]
/// draws an inner shadow in.
///
/// ## Which edges shift
///
/// Only the ones the box actually named. Yoga's fallback for an edge with no
/// inset is the static position, which is inside the padding and already
/// right, so defining an edge in order to correct it would replace a right
/// answer with a placement the box never asked for.
///
/// ## Which do not
///
/// A **percentage** inset, because it resolves against the containing block's
/// size, and adding a length in points to a percentage of an unknown width is
/// not arithmetic that can be done before the layout pass that produces the
/// width. Percentages are left where Yoga puts them, which differs from CSS by
/// the border width alone.
///
/// A **relative** box is not corrected either. Its inset offsets it from where
/// flow put it, and flow already placed it inside the padding.
///
/// ## Parts placed in other coordinates
///
/// A widget that lays its own parts out absolutely sometimes means another box
/// of its own: a text field's caret is measured from the start of its text,
/// which is the content box, and a tab's underline spans the whole tab, which
/// is the border box. [#inContentBox] and [#acrossBorderBox] write those insets
/// in terms of the style the widget resolved, so the numbers are never repeated
/// in a stylesheet.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#flexbox-from-yoga).
public final class ContainingBlock {

    private ContainingBlock() {}

    /// The inset to put on a Yoga node, given the one its box declared and the
    /// border of the box that owns it.
    ///
    /// Returns `inset` **itself**, not an equal copy, whenever nothing shifts —
    /// which is the overwhelming majority of nodes, since almost nothing is
    /// absolutely positioned and most containing blocks have no border. The
    /// identity matters: [RenderObject] compares the result against the inset
    /// already on the node to decide whether to call Yoga at all, and a fresh
    /// equal instance every frame would cost an allocation to reach the same
    /// answer.
    ///
    /// @param position    the child's `position`
    /// @param inset       the child's declared `inset`
    /// @param blockBorder the containing block's border
    /// @return the inset Yoga has to be given for the child to land where CSS
    ///         says it should
    public static Insets insetFor(Position position, Insets inset, Border blockBorder) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockBorder, "blockBorder");

        if (position != Position.ABSOLUTE) {
            return inset;
        }
        return shifted(
                inset,
                blockBorder.top().width(),
                blockBorder.right().width(),
                blockBorder.bottom().width(),
                blockBorder.left().width());
    }

    /// The inset to **write** when a child means its containing block's
    /// *border* box: the exact inverse of [#insetFor], so that the two cancel.
    ///
    /// A rule drawn across a control is the case: `tab`'s underline spans the
    /// whole tab, border included, and an overlay pinned to a window's corner
    /// means the corner of the window. Each says so in terms of the border its
    /// own style resolved rather than by repeating the number.
    ///
    /// Edges that [#insetFor] would not shift are left alone here for the same
    /// reasons, which is what makes the round trip exact.
    ///
    /// @param inset       the inset the child wants, measured from the border box
    /// @param blockBorder the containing block's border
    /// @return the inset to put on the child's box, which [#insetFor] will shift
    ///         back to what was asked for
    public static Insets acrossBorderBox(Insets inset, Border blockBorder) {
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockBorder, "blockBorder");

        return shifted(
                inset,
                -blockBorder.top().width(),
                -blockBorder.right().width(),
                -blockBorder.bottom().width(),
                -blockBorder.left().width());
    }

    /// The inset to **write** when a child means its containing block's
    /// *content* box, the padding box less the padding: where a child in flow
    /// would start.
    ///
    /// A part a control places absolutely beside its text is the case: a text
    /// field's caret, selection and value are measured from the first character,
    /// which sits inside the padding. Padding in points is added; a percentage
    /// padding cannot be resolved before layout and reads as zero, as it does
    /// for the control measuring its text.
    ///
    /// @param inset        the inset the child wants, measured from the content box
    /// @param blockPadding the containing block's `padding`
    /// @param blockBorder  the containing block's border
    /// @return the inset to put on the child's box, which [#insetFor] will shift
    ///         onto the content box
    public static Insets inContentBox(Insets inset, Insets blockPadding, Border blockBorder) {
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockPadding, "blockPadding");
        Objects.requireNonNull(blockBorder, "blockBorder");

        return shifted(
                inset,
                points(blockPadding.top()) - blockBorder.top().width(),
                points(blockPadding.right()) - blockBorder.right().width(),
                points(blockPadding.bottom()) - blockBorder.bottom().width(),
                points(blockPadding.left()) - blockBorder.left().width());
    }

    /// `inset` with each named edge in points moved by the amount for its side.
    ///
    /// Returns `inset` itself when no edge moves.
    private static Insets shifted(Insets inset, double top, double right, double bottom, double left) {
        var shiftedTop = shift(inset.top(), top);
        var shiftedRight = shift(inset.right(), right);
        var shiftedBottom = shift(inset.bottom(), bottom);
        var shiftedLeft = shift(inset.left(), left);
        if (shiftedTop == inset.top()
                && shiftedRight == inset.right()
                && shiftedBottom == inset.bottom()
                && shiftedLeft == inset.left()) {
            return inset;
        }
        return new Insets(shiftedTop, shiftedRight, shiftedBottom, shiftedLeft);
    }

    /// One edge, moved by `by`.
    ///
    /// Returns the argument unchanged — by identity, which is what
    /// [#insetFor] reads — for every case that cannot or must not shift.
    private static Length shift(Length inset, double by) {
        if (!(inset instanceof Length.Points offset) || by == 0) {
            // UNDEFINED is the edge Yoga already gets right; AUTO is not a value
            // Yoga has for an inset at all, and a percentage cannot be added to.
            return inset;
        }
        return Length.points((float) (offset.value() + by));
    }

    /// A padding edge in points, or zero when it is not one.
    private static double points(Length padding) {
        return padding instanceof Length.Points space ? space.value() : 0;
    }
}
