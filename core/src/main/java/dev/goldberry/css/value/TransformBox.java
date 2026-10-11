package dev.goldberry.css.value;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The box a transform's percentages and origin are measured against — CSS's
/// `transform-box`.
///
/// ```css
/// .badge { transform-box: content-box; transform-origin: 100% 0; rotate: 12deg }
/// ```
///
/// A box here is not an SVG shape, so `fill-box` is the content box and
/// `stroke-box` and `view-box` are the border box, as CSS says they are for
/// one that is not.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transform).
public enum TransformBox {

    /// The border box: the initial value.
    BORDER_BOX,

    /// The content box, inside the padding.
    CONTENT_BOX;

    /// The box a keyword names, or null.
    public static @Nullable TransformBox named(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "border-box", "stroke-box", "view-box" -> BORDER_BOX;
            case "content-box", "fill-box" -> CONTENT_BOX;
            default -> null;
        };
    }

    /// The keyword CSS writes for this value.
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
