package dev.goldberry.motion.layout;

/// A rectangle in logical pixels: where a box was laid out, or where it is
/// drawn while it moves.
public record Place(double x, double y, double width, double height) {

    /// How far two places may differ and still be the same one: under half a
    /// device pixel at any scale, so a rounding Yoga changed its mind about is
    /// not a move.
    static final double SAME = 0.25;

    public Place {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException(
                    "a place is four finite numbers, not " + x + ", " + y + ", " + width + ", " + height);
        }
    }

    /// Whether this is `other` within [#SAME].
    public boolean near(Place other) {
        return Math.abs(x - other.x) < SAME
                && Math.abs(y - other.y) < SAME
                && Math.abs(width - other.width) < SAME
                && Math.abs(height - other.height) < SAME;
    }

    /// The place `t` of the way from this one to `to`.
    public Place toward(Place to, double t) {
        return new Place(
                x + (to.x - x) * t,
                y + (to.y - y) * t,
                width + (to.width - width) * t,
                height + (to.height - height) * t);
    }
}
