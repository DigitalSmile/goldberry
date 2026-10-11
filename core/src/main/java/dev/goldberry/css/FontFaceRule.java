package dev.goldberry.css;

import java.util.Objects;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.assets.Face;

/// One `@font-face`: a face a stylesheet ships, and the family, weight and
/// style `font-family` finds it by.
///
/// ```css
/// @font-face {
///   font-family: "Atkinson";
///   src: url("fonts/Atkinson-Bold.ttf") format("truetype");
///   font-weight: 700;
/// }
/// ```
///
/// A renderer drawing with a book of fonts adds each one to the book when it
/// is built, as a face the application shipped; a sheet read as a resource has
/// its `src` resolved beside itself, as its other `url()`s are.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html#shipping-a-face).
///
/// @param family the family name a `font-family` declaration writes
/// @param weight the CSS weight, 1 to 1000
/// @param style  upright or italic
/// @param src    where the font file is: `classpath:/…` for a resource, a file
///               path otherwise
/// @param line   1-based line of the `@font-face`
/// @param column 1-based column of the `@font-face`
public record FontFaceRule(String family, int weight, BundledFont.Style style, String src, int line, int column) {

    public FontFaceRule {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(src, "src");
        if (family.isBlank()) {
            throw new IllegalArgumentException("an @font-face names its family");
        }
        if (src.isBlank()) {
            throw new IllegalArgumentException("an @font-face names its file");
        }
        Face.requireWeight(weight);
    }

    /// This face found at `value`: what anchoring a resource sheet's `src` is.
    public FontFaceRule src(String value) {
        return new FontFaceRule(family, weight, style, value, line, column);
    }
}
