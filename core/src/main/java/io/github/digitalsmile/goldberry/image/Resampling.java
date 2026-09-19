package io.github.digitalsmile.goldberry.image;

/// How [Image#scaled(int, int, Resampling)] invents the pixels it does not have.
///
/// ## Why this is a choice and not a quality slider
///
/// It is tempting to pick one filter, call it "good", and not have this type at
/// all. The reason not to is that the four below are not ordered by quality —
/// they are ordered by what they assume about the image:
///
/// - Making a photograph **smaller** wants as much of the source averaged in as
///   possible, because every source pixel that is not consulted is detail
///   thrown away. [#LANCZOS] consults the most.
/// - Making a 16×16 icon **twice as big** wants the opposite: it wants the
///   sixteen pixels it already has, doubled, and nothing invented between them.
///   [#NEAREST] is the only filter here that invents no colours, and it is the
///   only one that is not simply a worse version of the others.
///
/// There is no default that is right for both, and the mistake is silent in
/// both directions: a nearest-neighbour photograph looks like a mistake, and a
/// Lanczos icon looks like a slightly blurry icon that nobody files a bug about.
/// So [Image#scaled(int, int)] names a default for the case the operation
/// exists for — a thumbnail, which is a downscale — and anything else says which
/// it wants (ADR-0428).
///
/// ## The underlying call
///
/// Blend2D's `BLImageScaleFilter`, four of its five. The fifth is "no filter",
/// which is the enum's zero value rather than a choice anybody can make.
public enum Resampling {

    /// Take the nearest source pixel and nothing else.
    ///
    /// The only one that invents no colours. What an upscaled icon, a sprite
    /// sheet or anything else whose pixels are *the point* wants — and what a
    /// photograph never wants.
    NEAREST,

    /// Average the four neighbours.
    ///
    /// Cheap, and soft once the factor is far from one. A reasonable answer for
    /// a small change of size and a poor one for a large reduction, where it
    /// consults too few source pixels to represent what it dropped.
    BILINEAR,

    /// A cubic through sixteen neighbours.
    ///
    /// Sharper than [#BILINEAR] without the halo a windowed sinc can leave
    /// beside a hard edge, which makes it the safer of the two sharp filters
    /// when an image is being made **larger** and is not pixel art.
    BICUBIC,

    /// A windowed sinc over a wide neighbourhood.
    ///
    /// The sharpest when an image is made smaller, which is what a thumbnail is,
    /// and the default of [Image#scaled(int, int)]. It can overshoot at a hard
    /// edge and leave a bright or dark fringe beside it — the cost of consulting
    /// the most source pixels, and the reason [#BICUBIC] is here too.
    LANCZOS
}
