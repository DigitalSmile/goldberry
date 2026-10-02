package dev.goldberry.css.background;

/// How far CSS's `background-position` moves a box's gradient layers, in
/// logical pixels.
///
/// A layer here is the size of its box, and CSS places a layer at a
/// percentage of *the box less the layer*, which is zero. So only a length
/// moves one, and a percentage or a keyword resolves to nothing at all — the
/// same answer a browser gives for a gradient with no `background-size`.
///
/// Animatable: moving a `repeating-linear-gradient` by one period is how a
/// stripe marches.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
///
/// @param x how far right; negative is left
/// @param y how far down; negative is up
public record BackgroundPosition(double x, double y) {

    /// Where every layer starts.
    public static final BackgroundPosition ZERO = new BackgroundPosition(0, 0);

    public BackgroundPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException(
                    "a background position is a finite offset, and (" + x + ", " + y + ") is not one");
        }
    }

    /// This position `t` of the way to `to`.
    public BackgroundPosition mix(BackgroundPosition to, double t) {
        return new BackgroundPosition(x + (to.x - x) * t, y + (to.y - y) * t);
    }
}
