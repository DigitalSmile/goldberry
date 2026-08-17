package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.assets.BundledFont;

/// Which face text is drawn with, and how big — `docs/design-system.md` §1.4.
///
/// One record rather than four components on [ComputedStyle], for the reason the
/// other grouped record has: they travel together. All four **inherit**, all four
/// are read together by the one thing that turns them into a
/// [io.github.digitalsmile.goldberry.text.Font], and a caller that has one
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
/// @param family     the family name, matched against [BundledFont#of] — Inter,
///                   JetBrains Mono, OpenMoji
/// @param size       the em size in logical pixels
/// @param weight     which of the two shipped weights
/// @param lineHeight the line box height in logical pixels, or a **negative**
///                   value meaning "a multiple of the size", stored negated —
///                   see [#resolvedLineHeight()]
public record Typography(String family, double size, BundledFont.Weight weight, double lineHeight) {

    /// Inter 400 at 13/18 — `body`, the design system's default UI text (§1.4).
    ///
    /// Deliberately the *specified* default rather than something neutral: a
    /// window with no stylesheet at all should read as the design system, because
    /// the alternative is a toolkit whose out-of-the-box text is a size nobody
    /// chose.
    public static final Typography INITIAL =
            new Typography("Inter", 13, BundledFont.Weight.REGULAR, 18);

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
        if (!Double.isFinite(lineHeight) || lineHeight == 0) {
            throw new IllegalArgumentException("line-height must be non-zero, not " + lineHeight);
        }
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
    public BundledFont face() {
        return BundledFont.of(family, weight);
    }

    public Typography family(String value) {
        return new Typography(value, size, weight, lineHeight);
    }

    public Typography size(double value) {
        return new Typography(family, value, weight, lineHeight);
    }

    public Typography weight(BundledFont.Weight value) {
        return new Typography(family, size, value, lineHeight);
    }

    /// An absolute line height, in logical pixels.
    public Typography lineHeight(double value) {
        return new Typography(family, size, weight, value);
    }

    /// A line height as a multiple of the font size — CSS's bare-number form.
    public Typography lineHeightRatio(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 0) {
            throw new IllegalArgumentException("a line-height ratio must be positive, not " + ratio);
        }
        return new Typography(family, size, weight, -ratio);
    }

    @Override
    public String toString() {
        return family + " " + weight.value() + " " + size + "/" + resolvedLineHeight();
    }
}
