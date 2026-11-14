package dev.goldberry.motion.layout;

import dev.goldberry.css.value.Affine;

/// A map that scales each axis and then moves: `(x, y) → (sx·x + tx, sy·y +
/// ty)`. What carries one rectangle onto another, and all a layout move ever
/// is: no rotation, no skew.
///
/// Kept apart from [Affine] because the three things done with it here,
/// composing, inverting and mapping one rectangle onto another, are exact and
/// short for this shape.
public record Stretch(double sx, double sy, double tx, double ty) {

    /// Leaves everything where it is.
    public static final Stretch IDENTITY = new Stretch(1, 1, 0, 0);

    /// The stretch that carries `from` onto `to`. An empty `from` cannot be
    /// stretched to anything, so its axis is only moved.
    public static Stretch onto(Place from, Place to) {
        var sx = from.width() > 0 ? to.width() / from.width() : 1;
        var sy = from.height() > 0 ? to.height() / from.height() : 1;
        return new Stretch(sx, sy, to.x() - from.x() * sx, to.y() - from.y() * sy);
    }

    /// How far from the identity a stretch may be and still move nothing: far
    /// under a device pixel across any window, and over the rounding that
    /// composing and inverting leave, `-0.0` among it.
    private static final double NOTHING = 1e-9;

    /// Whether this moves nothing.
    public boolean isIdentity() {
        return Math.abs(sx - 1) < NOTHING
                && Math.abs(sy - 1) < NOTHING
                && Math.abs(tx) < NOTHING
                && Math.abs(ty) < NOTHING;
    }

    /// This, then `next`.
    public Stretch then(Stretch next) {
        return new Stretch(sx * next.sx, sy * next.sy, tx * next.sx + next.tx, ty * next.sy + next.ty);
    }

    /// What undoes this. A stretch that flattens an axis cannot be undone, and
    /// leaves that axis alone instead, which is the least wrong answer for a box
    /// that has no size to draw anyway.
    public Stretch inverse() {
        var ix = sx == 0 ? 1 : 1 / sx;
        var iy = sy == 0 ? 1 : 1 / sy;
        return new Stretch(ix, iy, -tx * ix, -ty * iy);
    }

    /// Where this puts `place`.
    public Place map(Place place) {
        return new Place(place.x() * sx + tx, place.y() * sy + ty, place.width() * sx, place.height() * sy);
    }

    /// The same map as a matrix, for the painter.
    public Affine affine() {
        return new Affine(sx, 0, 0, sy, tx, ty);
    }
}
