package dev.goldberry.css.background;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// One of a box's three rectangles, which `background-origin` places a layer
/// in and `background-clip` cuts it to.
///
/// ```css
/// .well  { background-clip: padding-box }
/// .stamp { background: url("classpath:/ui/stamp.png") no-repeat right 4px bottom 4px content-box }
/// ```
///
/// A border here takes no layout room: it is drawn inside the box's edge. So
/// the padding box is the box less its border's widths, and the content box is
/// the box less its padding, where the box's text and children start.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public enum BackgroundBox {

    /// The whole box: `background-clip`'s initial value.
    BORDER_BOX,

    /// Inside the border: `background-origin`'s initial value.
    PADDING_BOX,

    /// Inside the padding.
    CONTENT_BOX;

    /// A keyword as CSS spells it, or null.
    public static @Nullable BackgroundBox named(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "border-box" -> BORDER_BOX;
            case "padding-box" -> PADDING_BOX;
            case "content-box" -> CONTENT_BOX;
            default -> null;
        };
    }

    /// The keyword CSS writes for this value.
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
