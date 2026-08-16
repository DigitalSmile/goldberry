package io.github.digitalsmile.goldberry.natives.yoga;

import java.util.Objects;

/// Space on the four edges of a box.
///
/// One value rather than four fields on whatever holds it, because the four are
/// only ever meaningful together and CSS writes them as one declaration.
///
/// Here beside [StyleLength] rather than in `layout` or `css`, and for a reason
/// that is not filing: both of those name it — a `ComputedStyle` carries one and
/// a `Box` is built from it — and `layout` already depends on `css`. Putting it
/// in either would make that dependency mutual for the sake of one record. This
/// package is where the vocabulary the two share already lives.
///
/// The order is **CSS's** — top, right, bottom, left, clockwise from the top —
/// rather than the reading order a Java author would pick. Two orders for one
/// concept is how a `padding: 0 12px` ends up applied to the wrong pair of edges,
/// which looks like a layout bug and is a transcription one.
///
/// @param top    space above the content
/// @param right  space to the right
/// @param bottom space below
/// @param left   space to the left
public record Insets(StyleLength top, StyleLength right, StyleLength bottom, StyleLength left) {

    /// No space on any edge.
    public static final Insets ZERO = all(StyleLength.points(0));

    public Insets {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    /// The same on every edge — CSS's one-value form.
    public static Insets all(StyleLength value) {
        return new Insets(value, value, value, value);
    }

    /// Vertical and horizontal — CSS's two-value form, and the one a control
    /// wants: a button is `padding: 0 12px` before it is anything else.
    public static Insets symmetric(StyleLength vertical, StyleLength horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    /// Whether every edge is the same value, which is what lets a caller that
    /// only ever wanted one number keep asking for one.
    public boolean isUniform() {
        return top.equals(right) && right.equals(bottom) && bottom.equals(left);
    }

    @Override
    public String toString() {
        if (isUniform()) {
            return top.toString();
        }
        return top + " " + right + " " + bottom + " " + left;
    }
}
