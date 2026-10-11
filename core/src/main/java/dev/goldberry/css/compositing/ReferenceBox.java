package dev.goldberry.css.compositing;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The box a `clip-path` shape is drawn in, and the one its percentages are
/// of.
///
/// The border box is the default. The padding box is the border box less the
/// border, and the content box less the padding too, as CSS has them.
/// `margin-box` reads as the border box, and the SVG boxes (`fill-box`,
/// `stroke-box`, `view-box`) as the border box too.
public enum ReferenceBox {
    BORDER_BOX,
    PADDING_BOX,
    CONTENT_BOX;

    /// The box a keyword names, or null for one that is not a box.
    public static @Nullable ReferenceBox parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "border-box", "margin-box", "fill-box", "stroke-box", "view-box" -> BORDER_BOX;
            case "padding-box" -> PADDING_BOX;
            case "content-box" -> CONTENT_BOX;
            default -> null;
        };
    }

    /// The keyword as CSS writes it.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
