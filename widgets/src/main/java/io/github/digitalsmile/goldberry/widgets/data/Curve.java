package io.github.digitalsmile.goldberry.widgets.data;

/// How a line gets from one point to the next — `charts.md` §3.1's
/// "interpolation: linear, smooth, step".
///
/// The three are not decorations. Each says something different about what
/// happened *between* two readings, and a chart that picks the wrong one is
/// asserting something nobody measured.
public enum Curve {

    /// A straight segment — the **default**.
    ///
    /// It says the value moved steadily from one reading to the next, which is
    /// the weakest claim of the three and usually the honest one.
    LINEAR,

    /// A monotone cubic through the points.
    ///
    /// Prettier, and **specifically** monotone: a plain Catmull-Rom or natural
    /// spline overshoots after a sharp change, so a series of `0, 0, 100, 100`
    /// dips below zero on its way up. On a chart of a percentage, a queue depth
    /// or a byte count that is not merely wrong, it is *impossible* — and it is
    /// exactly where a reader looks, because it is where the interesting thing
    /// happened. Fritsch–Carlson (1980) is the fix and it is what [#SMOOTH]
    /// means here ([Curves]).
    SMOOTH,

    /// Hold the value until the next reading, then jump.
    ///
    /// For a series that is a **state** rather than a measurement: a build that
    /// was green at 09:00 and red at 09:05 was not "getting redder" in between,
    /// it was green until somebody looked again. A straight line between those
    /// two readings invents a transition; a step draws only what was observed.
    ///
    /// It holds **forward** from each reading rather than backwards into it,
    /// because that is what a sample means — a value read at 09:00 is what was
    /// true from 09:00 until the next read, and the alternative would say it was
    /// already true before anyone looked.
    STEP
}
