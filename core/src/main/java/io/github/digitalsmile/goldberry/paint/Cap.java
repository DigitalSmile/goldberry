package io.github.digitalsmile.goldberry.paint;

/// What a stroke does at the end of an open sub-path.
///
/// The toolkit's own vocabulary rather than Blend2D's. The three values are the
/// three SVG has, which is also the three the rasterizer under this one exposes
/// — but the agreement is a fact about today's backend rather than a promise,
/// and a `canvas` painter naming `BL_STROKE_CAP_ROUND` would be an application
/// reaching through `:core` into `:natives` for a concept that has nothing
/// native about it (ADR-0277).
///
/// The numbering is deliberately absent. Blend2D's is not alphabetical and not
/// obvious — round is 2, with a reversed round at 3 — and that ordering is a
/// property of the C header, checked against the compiled library where it
/// belongs. Here a cap is a name.
public enum Cap {

    /// Stops flat at the endpoint. The default, and SVG's.
    BUTT,

    /// Extends half the stroke width past the endpoint, squared off.
    SQUARE,

    /// A half-disc at the endpoint. What Lucide's icons are drawn with, and the
    /// difference between an icon that looks like the design and one that looks
    /// like a chopped-off version of it.
    ROUND
}
