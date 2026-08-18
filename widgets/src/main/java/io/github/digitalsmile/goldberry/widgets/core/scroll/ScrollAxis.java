package io.github.digitalsmile.goldberry.widgets.core.scroll;

/// Which way a [Scroll] moves — `docs/core-widgets.md` §1's "one or both axes".
public enum ScrollAxis {

    /// Down the page. The overwhelmingly common one, and the default.
    VERTICAL,

    /// Along it — a wide table, a filmstrip.
    HORIZONTAL,

    /// Both, which is a map or a canvas rather than a document.
    BOTH;

    /// Whether this axis moves horizontally at all.
    public boolean isHorizontal() {
        return this != VERTICAL;
    }

    /// Whether this axis moves vertically at all.
    public boolean isVertical() {
        return this != HORIZONTAL;
    }

    /// The axis `name` spells, for KDL's `axis=` — defaulting to [#VERTICAL]
    /// rather than throwing, because a document that misspells an attribute
    /// should still show its content.
    public static ScrollAxis parse(String name) {
        if (name == null) {
            return VERTICAL;
        }
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "horizontal", "x" -> HORIZONTAL;
            case "both", "xy" -> BOTH;
            default -> VERTICAL;
        };
    }
}
