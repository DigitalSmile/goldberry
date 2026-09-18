package io.github.digitalsmile.goldberry.css.value;

/// The one line every animated value in this package is made of.
///
/// [Oklch] fades a colour, [Affine.Decomposed] moves and turns a box, and [Shadow]
/// grows a blur; all three are `from + (to − from) × t` on each of their components,
/// and all three had written it out privately. It is small enough that three copies
/// cost nothing to run and exactly enough to disagree about — one of them clamping
/// `t`, or rounding, would be a transition that behaved differently depending on
/// which property it was.
///
/// **`t` is not clamped here.** A timing function may overshoot on purpose —
/// `cubic-bezier(.5,-.5,.5,1.5)` is legal CSS and a spring is the whole point of it —
/// so clamping would be this package deciding that no transition may overshoot. The
/// callers that must not (a colour's alpha, a blur radius) clamp their own result,
/// where the reason for the bound is visible.
final class Interpolate {

    private Interpolate() {}

    /// `from` at `t` = 0, `to` at `t` = 1, and the straight line between them.
    static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
