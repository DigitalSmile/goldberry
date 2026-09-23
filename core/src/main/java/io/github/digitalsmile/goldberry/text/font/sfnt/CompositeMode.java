package io.github.digitalsmile.goldberry.text.font.sfnt;

import org.jspecify.annotations.Nullable;

/// How a `PaintComposite` blends its source onto its backdrop — the `COLR`
/// version 1 `CompositeMode` enumeration, in the table's own order.
///
/// The Porter-Duff operators first, then the separable blend modes, then the four
/// non-separable ones that work in hue, saturation and luminosity. The order *is*
/// the encoding: [#of] indexes this enum by the byte the font writes, so a
/// constant moved here reads every font wrong.
///
/// The painter maps each to the rasterizer's own operator. The four HSL modes
/// have none there, and fall back to plain source-over — a smaller wrong than
/// drawing nothing, and one the shipped face never asks for ([ADR-0456]).
public enum CompositeMode {

    /// Nothing survives.
    CLEAR,

    /// The source alone.
    SRC,

    /// The backdrop alone.
    DEST,

    /// The source over the backdrop — ordinary drawing.
    SRC_OVER,

    /// The backdrop over the source.
    DEST_OVER,

    /// The source, only where the backdrop is: a mask.
    SRC_IN,

    /// The backdrop, only where the source is.
    DEST_IN,

    /// The source, only where the backdrop is not.
    SRC_OUT,

    /// The backdrop, only where the source is not.
    DEST_OUT,

    /// The source over the backdrop, kept inside the backdrop.
    SRC_ATOP,

    /// The backdrop over the source, kept inside the source.
    DEST_ATOP,

    /// Whichever is there alone.
    XOR,

    /// The sum.
    PLUS,

    /// Screen.
    SCREEN,

    /// Overlay.
    OVERLAY,

    /// The darker, per channel.
    DARKEN,

    /// The lighter, per channel.
    LIGHTEN,

    /// Colour dodge.
    COLOR_DODGE,

    /// Colour burn.
    COLOR_BURN,

    /// Hard light.
    HARD_LIGHT,

    /// Soft light — what shades Noto's waving flags.
    SOFT_LIGHT,

    /// The difference, per channel.
    DIFFERENCE,

    /// Exclusion.
    EXCLUSION,

    /// Multiply.
    MULTIPLY,

    /// The source's hue with the backdrop's saturation and luminosity.
    HSL_HUE,

    /// The source's saturation.
    HSL_SATURATION,

    /// The source's hue and saturation.
    HSL_COLOR,

    /// The source's luminosity.
    HSL_LUMINOSITY;

    private static final CompositeMode[] BY_CODE = values();

    /// The mode a font writes as `code`, or null for a value the specification
    /// does not define.
    public static @Nullable CompositeMode of(int code) {
        return code >= 0 && code < BY_CODE.length ? BY_CODE[code] : null;
    }
}
