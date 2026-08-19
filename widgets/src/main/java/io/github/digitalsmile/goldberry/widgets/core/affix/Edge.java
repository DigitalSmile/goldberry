package io.github.digitalsmile.goldberry.widgets.core.affix;

/// Which side of the viewport an [Affix] pins itself to — §1's `edge=`.
public enum Edge {

    TOP,
    BOTTOM,
    LEFT,
    RIGHT;

    /// Whether this edge pins along the vertical axis.
    public boolean isVertical() {
        return this == TOP || this == BOTTOM;
    }

    /// Whether this edge is the near one — the top or the left, where the
    /// comparison is "has it gone above/before the viewport".
    public boolean isNear() {
        return this == TOP || this == LEFT;
    }

    /// The edge `name` spells, defaulting to [#TOP] rather than throwing: a
    /// document that misspells an attribute should still show its content, which
    /// is the registry's rule everywhere else.
    public static Edge parse(String name) {
        if (name == null) {
            return TOP;
        }
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "bottom" -> BOTTOM;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            default -> TOP;
        };
    }
}
