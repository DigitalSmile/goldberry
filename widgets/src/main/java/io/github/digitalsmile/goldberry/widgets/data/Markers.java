package io.github.digitalsmile.goldberry.widgets.data;

/// Whether a line chart draws a dot at each reading — `charts.md` §3.1's "point
/// markers, size, show-always/never/auto".
///
/// A marker says *this is a reading* rather than a point on a curve, which
/// matters exactly when the two could be confused: a sparse series drawn as a
/// line looks like a continuous measurement, and a reader cannot tell whether the
/// bend at Tuesday is a reading or the place two segments happen to meet.
///
/// It stops mattering, and starts hurting, when the points are close together: a
/// hundred readings across two hundred pixels is a dotted mess where a line was
/// legible.
public enum Markers {

    /// Draw them when there is room — the **default**.
    ///
    /// "Room" is measured rather than assumed: a marker appears when its
    /// neighbours are more than a few marker-widths away, so the same chart shows
    /// dots at seven points and none at seven hundred, and shows them again when
    /// the window is made wider. A threshold in *pixels* rather than in points,
    /// because what makes a dotted mess is how close the dots are on screen.
    AUTO,

    /// Always, however crowded.
    ///
    /// For a chart of few enough readings that each one is a fact worth pointing
    /// at — and for the case `AUTO` cannot know about, where the reader has been
    /// told to expect one dot per sample.
    ALWAYS,

    /// Never, however sparse.
    ///
    /// For a series that is a trace rather than a set of readings, where a dot
    /// would be claiming a precision the sampling does not have.
    NEVER
}
