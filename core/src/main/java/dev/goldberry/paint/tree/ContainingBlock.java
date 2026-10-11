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
/// border. `left: 0; top: 0` in a box with `padding: 12px` is therefore the
/// corner just inside the border, and not the corner of its content, which is
/// where a child in flow starts.
///
/// A border takes room in layout, outside the padding, and Yoga measures an
/// absolute inset from the padding box itself, so [#insetFor] has nothing to
/// correct: it hands the inset back. What is left here are the insets a widget
/// writes for its own parts in other coordinates.
///
/// The padding box here is the same rectangle [dev.goldberry.paint.shadow.ShadowGeometry]
/// draws an inner shadow in.
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
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#flexbox).
public final class ContainingBlock {

    private ContainingBlock() {}

    /// The inset to put on a Yoga node, given the one its box declared and the
    /// border of the box that owns it: the declared one, **itself**. Yoga
    /// measures an absolute inset from the padding box, which is where CSS
    /// measures it, now that a border takes room.
    ///
    /// Kept as the one place [RenderObject] asks, so that a correction, should
    /// one ever be needed again, has a home.
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
        return inset;
    }

    /// The inset to **write** when a child means its containing block's
    /// *border* box: the padding box's inset moved out by the border.
    ///
    /// A rule drawn across a control is the case: `tab`'s underline spans the
    /// whole tab, border included, and an overlay pinned to a window's corner
    /// means the corner of the window. Each says so in terms of the border its
    /// own style resolved rather than by repeating the number.
    ///
    /// A percentage edge is left alone: it resolves against a size this cannot
    /// know before layout.
    ///
    /// @param inset       the inset the child wants, measured from the border box
    /// @param blockBorder the containing block's border
    /// @return the inset to put on the child's box
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
    /// @param blockBorder  the containing block's border, which Yoga already
    ///                     measures the padding box inside
    /// @return the inset to put on the child's box
    public static Insets inContentBox(Insets inset, Insets blockPadding, Border blockBorder) {
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(blockPadding, "blockPadding");
        Objects.requireNonNull(blockBorder, "blockBorder");

        return shifted(
                inset,
                points(blockPadding.top()),
                points(blockPadding.right()),
                points(blockPadding.bottom()),
                points(blockPadding.left()));
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
