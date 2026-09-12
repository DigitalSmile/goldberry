package io.github.digitalsmile.goldberry.layout;

import java.util.Objects;

/// Space on the four edges of a box.
///
/// One value rather than four fields on whatever holds it, because the four are
/// only ever meaningful together and CSS writes them as one declaration.
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
public record Insets(Length top, Length right, Length bottom, Length left) {

    /// No space on any edge.
    public static final Insets ZERO = all(Length.points(0));

    /// [Length#UNDEFINED] on every edge — which for an `inset` is "not pinned",
    /// and is a different thing from zero.
    ///
    /// An inset of zero pins a node to that edge; an undefined one leaves it
    /// where flow put it. A `Box` starts with this and not with [#ZERO]
    /// (ADR-0272).
    public static final Insets NONE = all(Length.UNDEFINED);

    public Insets {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(bottom, "bottom");
        Objects.requireNonNull(left, "left");
    }

    /// The same on every edge — CSS's one-value form.
    public static Insets all(Length value) {
        return new Insets(value, value, value, value);
    }

    /// Vertical and horizontal — CSS's two-value form, and the one a control
    /// wants: a button is `padding: 0 12px` before it is anything else.
    public static Insets symmetric(Length vertical, Length horizontal) {
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
