package dev.goldberry.image.lottie;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.Affine;

/// A layer's or a group's transform: anchor, position, scale, rotation, skew
/// and opacity, each of which may be animated.
///
/// Applied in After Effects' order: the anchor is moved to the origin, then
/// the shape is scaled, skewed, rotated, and moved to its position.
///
/// @param anchor   the point that is placed at `position`
/// @param position where the anchor goes, or null when it is split into `x`
///                 and `y`
/// @param x        the split position's x, or null
/// @param y        the split position's y, or null
/// @param scale    percent, per axis
/// @param rotation degrees, clockwise
/// @param opacity  0 to 100
/// @param skew     degrees
/// @param skewAxis degrees
record Transform(
        Property anchor,
        @Nullable Property position,
        @Nullable Property x,
        @Nullable Property y,
        Property scale,
        Property rotation,
        Property opacity,
        Property skew,
        Property skewAxis) {

    /// The transform that changes nothing.
    static final Transform NONE = new Transform(
            Property.of(0, 0),
            Property.of(0, 0),
            null,
            null,
            Property.of(100, 100),
            Property.of(0),
            Property.of(100),
            Property.of(0),
            Property.of(0));

    /// The matrix at `frame`, from the item's own space to its parent's.
    Affine matrixAt(double frame) {
        var a = anchor.at(frame);
        double px;
        double py;
        if (position != null) {
            var p = position.at(frame);
            px = p.length > 0 ? p[0] : 0;
            py = p.length > 1 ? p[1] : 0;
        } else {
            px = x == null ? 0 : x.scalar(frame);
            py = y == null ? 0 : y.scalar(frame);
        }
        var s = scale.at(frame);
        var sx = (s.length > 0 ? s[0] : 100) / 100;
        var sy = (s.length > 1 ? s[1] : sx * 100) / 100;
        var matrix = Affine.translate(-(a.length > 0 ? a[0] : 0), -(a.length > 1 ? a[1] : 0))
                .then(Affine.scale(sx, sy));
        var shear = skew.scalar(frame);
        if (shear != 0) {
            // About the skew axis: turned onto it, sheared along x, turned back.
            var axis = Math.toRadians(skewAxis.scalar(frame));
            matrix = matrix.then(Affine.rotate(axis))
                    .then(new Affine(1, 0, -Math.tan(Math.toRadians(shear)), 1, 0, 0))
                    .then(Affine.rotate(-axis));
        }
        var turn = rotation.scalar(frame);
        if (turn != 0) {
            matrix = matrix.then(Affine.rotate(Math.toRadians(turn)));
        }
        return matrix.then(Affine.translate(px, py));
    }

    /// The opacity at `frame`, 0 to 1.
    double opacityAt(double frame) {
        return Math.clamp(opacity.scalar(frame) / 100, 0, 1);
    }
}
