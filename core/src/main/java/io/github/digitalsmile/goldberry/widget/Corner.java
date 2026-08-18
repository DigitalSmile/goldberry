package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;

/// One of a box's four corners, named the way a stylesheet names them.
///
/// `start` and `end` rather than `left` and `right`, which is the vocabulary
/// `docs/core-widgets.md` §3 already uses for a floating button's
/// `corner="bottom-end"`. In a left-to-right window `start` is the left edge;
/// when right-to-left layout lands (`docs/ARCHITECTURE.md` §17) it is the right
/// one, and every corner in the toolkit flips with it because none of them wrote
/// down a side.
public enum Corner {

    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END;

    /// The corner this name is written as in markup and CSS: `bottom-end`.
    public String cssName() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /// Parses [#cssName]'s spelling.
    ///
    /// @throws IllegalArgumentException naming the four legal values, because an
    ///         author who wrote `bottom-right` has made a guess this vocabulary
    ///         does not take and should be told what it does take
    public static Corner parse(String text) {
        if (text != null) {
            for (var corner : values()) {
                if (corner.cssName().equals(text.trim())) {
                    return corner;
                }
            }
        }
        throw new IllegalArgumentException(
                "\"" + text + "\" is not a corner. Use one of: top-start, top-end,"
                        + " bottom-start, bottom-end");
    }

    /// Whether this corner is on the top edge.
    public boolean isTop() {
        return this == TOP_START || this == TOP_END;
    }

    /// Whether this corner is on the start edge — the left one, until §17's
    /// right-to-left work says otherwise.
    public boolean isStart() {
        return this == TOP_START || this == BOTTOM_START;
    }

    /// Insets that pin a box to this corner, `margin` logical pixels from each of
    /// the two edges it touches.
    ///
    /// The other two edges are [StyleLength#UNDEFINED] and not zero, and the
    /// difference is the whole point: an inset of zero on all four edges pins a
    /// box to every edge and stretches it across the window, which is a scrim
    /// rather than a corner. Undefined leaves the box its own size and lets the
    /// two edges that *are* set decide where that size sits.
    public Insets insets(float margin) {
        var edge = StyleLength.points(margin);
        return new Insets(
                isTop() ? edge : StyleLength.UNDEFINED,
                isStart() ? StyleLength.UNDEFINED : edge,
                isTop() ? StyleLength.UNDEFINED : edge,
                isStart() ? edge : StyleLength.UNDEFINED);
    }
}
