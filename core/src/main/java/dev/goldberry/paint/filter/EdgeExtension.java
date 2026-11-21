package dev.goldberry.paint.filter;

/// How many pixels a raster is taken to go on past each of its edges while it is
/// filtered, each one a copy of the nearest edge pixel.
///
/// What a `backdrop-filter` reads at the edge of the window: there is nothing
/// drawn past it, and a blur that took the outside as transparent would darken
/// a frosted bar along the edge it touches. CSS reads the backdrop with its
/// edge pixels repeated for the same reason.
///
/// @param left   pixels repeated left of the raster
/// @param top    above it
/// @param right  right of it
/// @param bottom below it
public record EdgeExtension(int left, int top, int right, int bottom) {

    /// The raster as it is, transparent past its edges.
    public static final EdgeExtension NONE = new EdgeExtension(0, 0, 0, 0);

    public EdgeExtension {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("an edge is extended by no pixels or more, not " + left + ", " + top
                    + ", " + right + ", " + bottom);
        }
    }

    /// Whether this extends nothing.
    public boolean isNone() {
        return left == 0 && top == 0 && right == 0 && bottom == 0;
    }
}
