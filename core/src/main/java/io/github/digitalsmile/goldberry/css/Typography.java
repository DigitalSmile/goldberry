package io.github.digitalsmile.goldberry.css;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.value.CssLength;

/// Which face text is drawn with, and how big — `docs/design-system.md` §1.4.
///
/// One record rather than four components on [ComputedStyle], for the reason the
/// other grouped record has: they travel together. All four **inherit**, all four
/// are read together by the one thing that turns them into a
/// [io.github.digitalsmile.goldberry.text.font.Font], and a caller that has one
/// invariably wants the rest.
///
/// ## Why the weight is an enum and not a number
///
/// §1.4 ships **two** weights, 400 and 600, and Principle 3 says a screen needing
/// a third extends the system rather than improvising one. A stylesheet may still
/// write any CSS number: [BundledFont.Weight#nearest] resolves it the way CSS's
/// own font matching does, so `font-weight: bold` gets SemiBold rather than
/// nothing. Storing the raw number instead would mean carrying a value no face
/// can honour and discovering it in the painter.
///
/// ## Why the size is a `double` and not a `StyleLength`
///
/// A font size in percent or `auto` has no meaning the shaper could use, and
/// `em` — a size relative to the *parent's* size — is resolved by
/// [CssLength.Context] before it reaches here. What survives is logical pixels.
///
/// ## Why the style is here and not a decoration
///
/// An italic is a **face**, like a weight (ADR-0066, ADR-0323): it is a different
/// drawing rather than a slant applied to this one, so it belongs beside the
/// family and the weight, which together name a file. `font-style` therefore
/// resolves here and `text-decoration` — which *is* a mark added to the glyphs —
/// resolves on [ComputedStyle] beside the other text-flow properties.
///
/// @param family     the family name, matched against [BundledFont#of] — Inter,
///                   JetBrains Mono, OpenMoji
/// @param size       the em size in logical pixels
/// @param weight     which of the two shipped weights
/// @param style      upright or italic
/// @param lineHeight the line box height in logical pixels, or a **negative**
///                   value meaning "a multiple of the size", stored negated —
///                   see [#resolvedLineHeight()]
public record Typography(
        String family, double size, BundledFont.Weight weight, BundledFont.Style style, double lineHeight) {

    /// Inter 400 upright at 13/18 — `body`, the design system's default UI text
    /// (§1.4).
    ///
    /// Deliberately the *specified* default rather than something neutral: a
    /// window with no stylesheet at all should read as the design system, because
    /// the alternative is a toolkit whose out-of-the-box text is a size nobody
    /// chose.
    public static final Typography INITIAL =
            new Typography("Inter", 13, BundledFont.Weight.REGULAR, BundledFont.Style.UPRIGHT, 18);

    public Typography {
        if (family == null || family.isBlank()) {
            family = "Inter";
        }
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException("font-size must be a positive length, not " + size);
        }
        if (weight == null) {
            weight = BundledFont.Weight.REGULAR;
        }
        if (style == null) {
            style = BundledFont.Style.UPRIGHT;
        }
        if (!Double.isFinite(lineHeight) || lineHeight == 0) {
            throw new IllegalArgumentException("line-height must be non-zero, not " + lineHeight);
        }
    }

    /// The four-argument form, which is every caller written before `font-style`
    /// was in the subset: upright.
    public Typography(String family, double size, BundledFont.Weight weight, double lineHeight) {
        this(family, size, weight, BundledFont.Style.UPRIGHT, lineHeight);
    }

    /// The line box height in logical pixels.
    ///
    /// A `line-height` written as a bare number is a **multiple** of the font
    /// size, and that is the form that survives: a container at `line-height: 1.4`
    /// gives a 20px heading a 28px line box and an 11px caption a 15px one, where
    /// an inherited absolute `18px` would give both the same and crush the
    /// heading. Stored negated so one field carries both forms without a second
    /// component or a boxed enum.
    public double resolvedLineHeight() {
        return lineHeight < 0 ? -lineHeight * size : lineHeight;
    }

    /// The bundled face this asks for, or null if no family matches.
    public @Nullable BundledFont face() {
        return BundledFont.of(family, weight, style);
    }

    public Typography family(String value) {
        return new Typography(value, size, weight, style, lineHeight);
    }

    public Typography size(double value) {
        return new Typography(family, value, weight, style, lineHeight);
    }

    /// This, with every length multiplied by `factor` — §1.4's **text-scale
    /// token**.
    ///
    /// The size *and* the line height, because a line box that did not grow with
    /// its text is a paragraph whose lines overlap. A negative `lineHeight` is a
    /// **ratio** rather than a length ([#resolvedLineHeight()]) and is left
    /// alone: a multiple of the size scales by scaling the size, and multiplying
    /// it too would square the factor.
    ///
    /// @param factor 1 for no scaling; §1.4's range is 0.9 to 1.5
    /// @throws IllegalArgumentException if the factor is not a positive, finite
    ///         number — a zero or negative text scale is a window with no text in
    ///         it, which is worse than any argument for tolerating it
    public Typography scaled(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) {
            throw new IllegalArgumentException("a text scale must be a positive, finite factor, not " + factor);
        }
        if (factor == 1) {
            return this;
        }
        return new Typography(family, size * factor, weight, style, lineHeight < 0 ? lineHeight : lineHeight * factor);
    }

    public Typography weight(BundledFont.Weight value) {
        return new Typography(family, size, value, style, lineHeight);
    }

    /// This, upright or italic — `font-style`.
    public Typography style(BundledFont.Style value) {
        return new Typography(family, size, weight, value, lineHeight);
    }

    /// An absolute line height, in logical pixels.
    public Typography lineHeight(double value) {
        return new Typography(family, size, weight, style, value);
    }

    /// A line height as a multiple of the font size — CSS's bare-number form.
    public Typography lineHeightRatio(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 0) {
            throw new IllegalArgumentException("a line-height ratio must be positive, not " + ratio);
        }
        return new Typography(family, size, weight, style, -ratio);
    }

    @Override
    public String toString() {
        return family + " " + weight.value()
                + (style == BundledFont.Style.ITALIC ? " italic " : " ")
                + size + "/" + resolvedLineHeight();
    }
}
