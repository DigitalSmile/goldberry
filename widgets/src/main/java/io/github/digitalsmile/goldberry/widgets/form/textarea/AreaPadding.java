package io.github.digitalsmile.goldberry.widgets.form.textarea;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Length;

/// A `text-area`'s padding, in logical pixels, on all four edges.
///
/// All four, because the wrap comes off the left **and** the right, and
/// they are not the same number. Doubling the left edge was right for every
/// stylesheet the catalog ships and wrong for `padding: 12px 16px 12px 0`: the
/// text then wrapped 16 pixels wider than the room it had and ran under the
/// right padding (`docs/gaps.md` G43, ADR-0350).
///
/// A percentage padding reads as zero, which `TextField` does too: resolving
/// one needs the width Yoga has not computed yet.
///
/// @param left   the leading edge, where the text (or the gutter) starts
/// @param top    above the first line
/// @param right  after the end of the longest line
/// @param bottom below the last visible line
record AreaPadding(double left, double top, double right, double bottom) {

    /// No padding, which is what an editor has before its first frame.
    static final AreaPadding NONE = new AreaPadding(0, 0, 0, 0);

    /// What the cascade resolved for this control.
    static AreaPadding of(ComputedStyle style) {
        var padding = style.padding();
        return new AreaPadding(
                points(padding.left()), points(padding.top()), points(padding.right()), points(padding.bottom()));
    }

    /// Both horizontal edges together — what comes off the width a line wraps at.
    double horizontal() {
        return left + right;
    }

    /// Both vertical edges together — what comes off the height lines are shown in.
    double vertical() {
        return top + bottom;
    }

    private static double points(Length length) {
        return length instanceof Length.Points points ? points.value() : 0;
    }
}
