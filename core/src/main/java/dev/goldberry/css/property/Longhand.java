package dev.goldberry.css.property;

import java.util.Objects;

import dev.goldberry.css.ComputedStyle;

/// A property that holds a value of its own.
///
/// Its [Copier] moves exactly that value from one style into another and leaves
/// everything else alone: `padding-top` copies one edge of the padding, not the
/// padding. That is the operation `inherit` is, with the parent's style as the
/// source, and `initial`, with the initial style as the source.
///
/// @param name      the property's name
/// @param inherited whether a child takes the value from its parent when no
///                  rule sets it. This is goldberry's list, which follows CSS
///                  with one addition: `text-decoration-line` inherits, because
///                  a decoration has to reach the anonymous box holding an
///                  element's text
/// @param readable  whether a declaration of this name is read; see
///                  [Property#readable()]
/// @param copier    moves this property's value between styles
public record Longhand(String name, boolean inherited, boolean readable, Copier copier) implements Property {

    public Longhand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(copier, "copier");
    }

    /// `into`, with this property's value taken from `from`.
    public ComputedStyle copy(ComputedStyle from, ComputedStyle into) {
        return copier.copy(Objects.requireNonNull(from, "from"), Objects.requireNonNull(into, "into"));
    }

    /// Moves one property's value from one style into another.
    @FunctionalInterface
    public interface Copier {

        /// `into`, with the value taken from `from`.
        ComputedStyle copy(ComputedStyle from, ComputedStyle into);
    }
}
