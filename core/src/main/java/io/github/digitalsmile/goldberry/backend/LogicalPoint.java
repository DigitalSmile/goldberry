package io.github.digitalsmile.goldberry.backend;

/// A position in logical pixels.
///
/// The companion of [LogicalSize], and here for the same reason [LogicalSize] is:
/// a pair of floats passed as two arguments is a pair that eventually gets
/// transposed, and an x/y swap is a popup that opens in the wrong place rather
/// than an error anyone sees.
///
/// **Whose coordinates** is the caller's to know and is never carried here. A
/// [PopupSpec]'s position is in its owner window's; a top-level window's would be
/// the display's.
///
/// @param x distance from the left edge
/// @param y distance from the top edge
public record LogicalPoint(float x, float y) {

    /// The origin.
    public static final LogicalPoint ZERO = new LogicalPoint(0, 0);

    public LogicalPoint {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException(
                    "a position must be finite, and (" + x + ", " + y + ") is not");
        }
    }

    public static LogicalPoint of(float x, float y) {
        return new LogicalPoint(x, y);
    }

    /// This point moved by `dx` and `dy`.
    public LogicalPoint offsetBy(float dx, float dy) {
        return new LogicalPoint(x + dx, y + dy);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
