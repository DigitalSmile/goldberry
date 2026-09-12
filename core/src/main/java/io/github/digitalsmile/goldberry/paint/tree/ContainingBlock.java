package io.github.digitalsmile.goldberry.paint.tree;

import java.util.Objects;

import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;

/// The one rule Yoga does not implement: an absolutely positioned box is placed
/// against its containing block's **padding** box.
///
/// CSS is unambiguous about this. An absolutely positioned box's containing
/// block is the padding box of its nearest positioned ancestor, and
/// `overflow: hidden` clips to that same padding box — the two agree by
/// construction, which is why placing against one and clipping to the other is a
/// disagreement rather than a pair of choices.
///
/// Yoga contradicts itself here, and it is one path of two (ADR-0265).
/// Given **no insets** it places the child at the padding edge, which is right.
/// Given an inset it measures that inset from the **border** box, which is
/// wrong by exactly the containing block's padding. `YogaLayoutTest` records
/// both halves against the compiled library.
///
/// So the correction is a shift, and it is applied where the inset is put onto
/// the node rather than where the layout comes back: with `left` and `right`
/// both given, Yoga derives the child's *width* from the containing block's
/// width less the two insets, and shifting both insets makes that width the
/// padding box's too. A correction applied after the layout pass could move the
/// child and could not resize it.
///
/// ## Which edges shift
///
/// Only the ones the box actually named. Yoga's fallback for an edge with no
/// inset is the static position, which already includes the padding and is
/// already right, so defining an edge in order to correct it would replace a
/// right answer with a placement the box never asked for.
///
/// ## Which do not
///
/// A **percentage** on either side of the sum, and for a reason that is not
/// laziness: a percentage inset resolves against the containing block's size,
/// and adding a length in points to a percentage of an unknown width is not
/// arithmetic that can be done before the layout pass that produces the width.
/// Percentages are left where Yoga puts them, which is the behaviour every
/// widget in the toolkit had before this class existed. `TextField` states the
/// same restriction for the same reason.
///
/// A **relative** box is not corrected either. Its inset offsets it from where
/// flow put it, and flow already placed it inside the padding.
public final class ContainingBlock {

    private ContainingBlock() {}

    /// The inset to put on a Yoga node, given the one its box declared and the
    /// padding of the box that owns it.
    ///
    /// Returns `inset` **itself**, not an equal copy, whenever nothing shifts —
    /// which is the overwhelming majority of nodes, since almost nothing is
    /// absolutely positioned and most containing blocks have no padding. The
    /// identity matters: [RenderObject] compares the result against the inset
    /// already on the node to decide whether to call Yoga at all, and a fresh
    /// equal instance every frame would cost an allocation to reach the same
    /// answer.
    ///
    /// @param position      the child's `position`
    /// @param inset         the child's declared `inset`
    /// @param blockPadding  the containing block's `padding`
    /// @return the inset Yoga has to be given for the child to land where CSS
    ///         says it should
    public static Insets insetFor(Position position, Insets inset, Insets blockPadding) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockPadding, "blockPadding");

        if (position != Position.ABSOLUTE) {
            return inset;
        }
        var top = shift(inset.top(), blockPadding.top());
        var right = shift(inset.right(), blockPadding.right());
        var bottom = shift(inset.bottom(), blockPadding.bottom());
        var left = shift(inset.left(), blockPadding.left());
        if (top == inset.top() && right == inset.right() && bottom == inset.bottom() && left == inset.left()) {
            return inset;
        }
        return new Insets(top, right, bottom, left);
    }

    /// The inset to **write** when a child means its containing block's *border*
    /// box rather than its padding box — the exact inverse of [#insetFor], so
    /// that the two cancel.
    ///
    /// A rule drawn across the bottom of a control is the case, and `tab`'s
    /// underline is the one that found it. `left: 0; right: 0` inside a
    /// `padding: 0 12px` header means the padding box now, which is what CSS
    /// says and 24 points narrower than the tab it is underlining. An underline
    /// that stops short of its own label is not an underline, so the widget has
    /// to say it meant the whole header — and this is how it says so, in terms of
    /// the padding its own style resolved rather than by repeating the number.
    ///
    /// Not a stylesheet's job, and that is the argument for it being here.
    /// A `tab-indicator { left: -12px }` would be the same 12 written twice,
    /// once beside the padding it has to match and once three rules away, with
    /// `density-compact.css` obliged to change both. This reads the padding.
    ///
    /// Edges that [#insetFor] would not shift are left alone here for the same
    /// reasons, which is what makes the round trip exact.
    ///
    /// @param inset        the inset the child wants, measured from the border box
    /// @param blockPadding the containing block's `padding`
    /// @return the inset to put on the child's box, which [#insetFor] will shift
    ///         back to what was asked for
    public static Insets acrossBorderBox(Insets inset, Insets blockPadding) {
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockPadding, "blockPadding");

        var top = shift(inset.top(), negated(blockPadding.top()));
        var right = shift(inset.right(), negated(blockPadding.right()));
        var bottom = shift(inset.bottom(), negated(blockPadding.bottom()));
        var left = shift(inset.left(), negated(blockPadding.left()));
        if (top == inset.top() && right == inset.right() && bottom == inset.bottom() && left == inset.left()) {
            return inset;
        }
        return new Insets(top, right, bottom, left);
    }

    /// A padding to subtract rather than add. Anything that is not a length in
    /// points is handed back untouched, so that [#shift] declines it for exactly
    /// the reasons it declines the same value on the way in.
    private static Length negated(Length padding) {
        return padding instanceof Length.Points space ? Length.points(-space.value()) : padding;
    }

    /// One edge, moved from the border box to the padding box.
    ///
    /// Returns the argument unchanged — by identity, which is what
    /// [#insetFor] reads — for every case that cannot or must not shift.
    private static Length shift(Length inset, Length padding) {
        if (!(inset instanceof Length.Points offset)) {
            // UNDEFINED is the edge Yoga already gets right; AUTO is not a value
            // Yoga has for an inset at all, and a percentage cannot be added to.
            return inset;
        }
        if (!(padding instanceof Length.Points space) || space.value() == 0f) {
            // No padding to move past — and an undefined padding is Yoga's zero,
            // so this is the common case rather than the unhandled one.
            return inset;
        }
        return Length.points(offset.value() + space.value());
    }
}
