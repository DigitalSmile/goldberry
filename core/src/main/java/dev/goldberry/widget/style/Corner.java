package dev.goldberry.widget.style;

import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;

/// One of a box's four corners, named the way a stylesheet names them.
///
/// `start` and `end` rather than `left` and `right`, which is the vocabulary a
/// floating button's `corner="bottom-end"` uses. In a left-to-right window
/// `start` is the left edge; when right-to-left layout arrives it is the right
/// one, and every corner in the toolkit flips with it because none of them wrote
/// down a side.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html).
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
        for (var corner : values()) {
            if (corner.cssName().equals(text.trim())) {
                return corner;
            }
        }
        throw new IllegalArgumentException(
                "\"" + text + "\" is not a corner. Use one of: top-start, top-end," + " bottom-start, bottom-end");
    }

    /// Whether this corner is on the top edge.
    public boolean isTop() {
        return this == TOP_START || this == TOP_END;
    }

    /// Whether this corner is on the start edge — the left one, until
    /// right-to-left layout says otherwise.
    public boolean isStart() {
        return this == TOP_START || this == BOTTOM_START;
    }

    /// Insets that pin a box to this corner, `margin` logical pixels from each of
    /// the two edges it touches.
    ///
    /// The other two edges are [Length#UNDEFINED] and not zero, and the
    /// difference is the whole point: an inset of zero on all four edges pins a
    /// box to every edge and stretches it across the window, which is a scrim
    /// rather than a corner. Undefined leaves the box its own size and lets the
    /// two edges that *are* set decide where that size sits.
    public Insets insets(float margin) {
        var edge = Length.points(margin);
        return new Insets(
                isTop() ? edge : Length.UNDEFINED,
                isStart() ? Length.UNDEFINED : edge,
                isTop() ? Length.UNDEFINED : edge,
                isStart() ? edge : Length.UNDEFINED);
    }
}
