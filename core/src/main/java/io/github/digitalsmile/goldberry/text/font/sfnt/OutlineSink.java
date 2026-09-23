package io.github.digitalsmile.goldberry.text.font.sfnt;

/// Where [GlyphOutlines] sends a glyph's contours.
///
/// An interface rather than a path type, because the path types live in `paint`
/// and `paint` reads this package — a reader here returning one would make the
/// two packages depend on each other. Whoever draws supplies the sink; the
/// reader knows only that outlines are moves, lines and quadratic curves.
///
/// Coordinates are the font's **design units, y up**, as the `glyf` table writes
/// them.
public interface OutlineSink {

    /// Starts a contour at `(x, y)`.
    void moveTo(double x, double y);

    /// A straight segment to `(x, y)`.
    void lineTo(double x, double y);

    /// A quadratic Bézier through the control point `(cx, cy)` to `(x, y)` —
    /// the only curve TrueType outlines have.
    void quadTo(double cx, double cy, double x, double y);

    /// Closes the contour back to where it started.
    void close();
}
