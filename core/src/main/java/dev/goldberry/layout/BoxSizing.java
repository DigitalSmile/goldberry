package dev.goldberry.layout;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// What a box's `width` and `height` measure: CSS's `box-sizing`.
///
/// A border takes room here as it does in CSS, outside the padding.
/// [#BORDER_BOX] is the initial value, unlike CSS's: a control given a
/// height keeps that height whatever border a theme draws on it, and its
/// content is what gives way.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public enum BoxSizing {

    /// The size includes the padding and the border. The initial value.
    BORDER_BOX,

    /// The size is the content's, and the padding and border are added.
    CONTENT_BOX;

    /// The keyword, or null: `border-box` or `content-box`.
    public static @Nullable BoxSizing parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "border-box" -> BORDER_BOX;
            case "content-box" -> CONTENT_BOX;
            default -> null;
        };
    }
}
