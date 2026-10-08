package dev.goldberry.css.background;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Gradient;

/// One gradient in a box's `background`, as the stylesheet wrote it.
///
/// What a stylesheet says about a gradient is in proportions of a box —
/// `to bottom right`, `at top`, `farthest-corner`, `40%` — and a box has no
/// size until layout has run. So the cascade carries this, and the painter
/// asks [#resolve] for the [Gradient] it fills with once the box has a
/// rectangle.
///
/// ## Stops
///
/// Each stop is a colour and an optional position. Resolving follows CSS: a
/// first stop with no position is at 0, a last one at 100%, a stop placed
/// before an earlier one is moved up to it, and the stops between two placed
/// ones share the distance evenly. A `repeating-` gradient repeats the span
/// from its first stop to its last.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface GradientLayer extends CssImage {

    /// The colour stops, in the order they were written.
    List<ColorStop> stops();

    /// Whether this is a `repeating-` gradient.
    boolean repeating();

    /// The gradient this layer is over a box at `(x, y)`, `width` by `height`,
    /// moved by `position`.
    Gradient resolve(double x, double y, double width, double height, BackgroundPosition position);

    /// This layer with every stop's alpha scaled by `alpha`.
    @Override
    GradientLayer fade(double alpha);

    /// A colour, and where along the gradient it sits.
    ///
    /// @param argb     `0xAARRGGBB`, not premultiplied
    /// @param position how far along, a length or a percentage of the
    ///                 gradient's length; null to be placed by its neighbours
    record ColorStop(int argb, @Nullable Length position) {

        public ColorStop {
            if (position != null && !(position instanceof Length.Points) && !(position instanceof Length.Percent)) {
                throw new IllegalArgumentException(
                        "a colour stop is placed by a length or a percentage, and " + position + " is neither");
            }
        }

        /// This stop with its colour's alpha scaled by `alpha`.
        ColorStop fade(double alpha) {
            return new ColorStop(CssColor.fade(argb, alpha), position);
        }
    }

    /// Which way a linear gradient runs.
    sealed interface Direction {

        /// Towards `radians`, CSS's way: zero is up and the angle turns
        /// clockwise, so `90deg` runs left to right.
        record Angle(double radians) implements Direction {

            public Angle {
                if (!Double.isFinite(radians)) {
                    throw new IllegalArgumentException("a gradient's angle is finite, and " + radians + " is not");
                }
            }
        }

        /// Towards a corner — `to bottom right`. The angle depends on the
        /// box's shape: the middle of the gradient runs through the other two
        /// corners.
        record Corner(boolean right, boolean bottom) implements Direction {}
    }

    /// `linear-gradient()` and `repeating-linear-gradient()`.
    record Linear(Direction direction, List<ColorStop> stops, boolean repeating) implements GradientLayer {

        public Linear {
            Objects.requireNonNull(direction, "direction");
            stops = requireStops(stops);
        }

        @Override
        public Gradient resolve(double x, double y, double width, double height, BackgroundPosition position) {
            var angle =
                    switch (direction) {
                        case Direction.Angle(var radians) -> radians;
                        // Perpendicular to the diagonal between the two other corners.
                        case Direction.Corner(var right, var bottom) -> {
                            var toTopRight = Math.atan2(height, width);
                            if (right) {
                                yield bottom ? Math.PI - toTopRight : toTopRight;
                            }
                            yield bottom ? Math.PI + toTopRight : -toTopRight;
                        }
                    };
            var dx = Math.sin(angle);
            var dy = -Math.cos(angle);
            // CSS's gradient line: through the middle, long enough that the
            // corners furthest along it are at 0% and 100%.
            var length = Math.abs(width * dx) + Math.abs(height * dy);
            var ramp = Ramp.of(stops, length);
            var startX = x + position.x() + width / 2 - dx * length / 2;
            var startY = y + position.y() + height / 2 - dy * length / 2;
            return new Gradient.Linear(
                    startX + dx * ramp.from(),
                    startY + dy * ramp.from(),
                    startX + dx * ramp.to(),
                    startY + dy * ramp.to(),
                    ramp.stops(),
                    repeating ? Gradient.Extend.REPEAT : Gradient.Extend.PAD);
        }

        @Override
        public GradientLayer fade(double alpha) {
            return alpha >= 1
                    ? this
                    : new Linear(
                            direction,
                            stops.stream().map(stop -> stop.fade(alpha)).toList(),
                            repeating);
        }
    }

    /// How large a radial gradient's ending shape is, by keyword.
    enum Extent {
        CLOSEST_SIDE,
        CLOSEST_CORNER,
        FARTHEST_SIDE,
        FARTHEST_CORNER
    }

    /// `radial-gradient()` and `repeating-radial-gradient()`.
    ///
    /// @param circle  whether the ending shape is a circle rather than an
    ///                ellipse
    /// @param extent  the size by keyword, or null when [#radiusX] and
    ///                [#radiusY] give it
    /// @param radiusX the explicit horizontal radius, or null
    /// @param radiusY the explicit vertical radius, or null; a circle's is
    ///                its horizontal one
    /// @param centreX where the centre is across the box
    /// @param centreY where the centre is down the box
    record Radial(
            boolean circle,
            @Nullable Extent extent,
            @Nullable Length radiusX,
            @Nullable Length radiusY,
            Length centreX,
            Length centreY,
            List<ColorStop> stops,
            boolean repeating)
            implements GradientLayer {

        public Radial {
            if (extent == null && (radiusX == null || radiusY == null)) {
                throw new IllegalArgumentException("a radial gradient is sized by a keyword or by two radii");
            }
            Objects.requireNonNull(centreX, "centreX");
            Objects.requireNonNull(centreY, "centreY");
            stops = requireStops(stops);
        }

        @Override
        public Gradient resolve(double x, double y, double width, double height, BackgroundPosition position) {
            var cx = (double) Length.resolve(centreX, (float) width);
            var cy = (double) Length.resolve(centreY, (float) height);
            var radii = radii(cx, cy, width, height);
            // A zero radius is a centre on the box's edge; the ramp is then a
            // hard edge, drawn over a very small one.
            var rx = Math.max(radii[0], Ramp.MIN_SPAN);
            var ry = Math.max(radii[1], Ramp.MIN_SPAN);
            var ramp = Ramp.of(stops, rx).fromZero(repeating);
            return new Gradient.Radial(
                    x + position.x() + cx,
                    y + position.y() + cy,
                    ramp.to(),
                    ramp.to() * ry / rx,
                    ramp.from() / ramp.to(),
                    ramp.stops(),
                    repeating ? Gradient.Extend.REPEAT : Gradient.Extend.PAD);
        }

        /// The ending shape's two radii, for a centre at `(cx, cy)` in a box
        /// `width` by `height`.
        private double[] radii(double cx, double cy, double width, double height) {
            if (extent == null) {
                return new double[] {
                    Length.resolve(Objects.requireNonNull(radiusX), (float) width),
                    Length.resolve(Objects.requireNonNull(radiusY), (float) height)
                };
            }
            var left = Math.abs(cx);
            var right = Math.abs(width - cx);
            var top = Math.abs(cy);
            var bottom = Math.abs(height - cy);
            var nearX = Math.min(left, right);
            var nearY = Math.min(top, bottom);
            var farX = Math.max(left, right);
            var farY = Math.max(top, bottom);
            // An ellipse through a corner keeps the shape its sides would give
            // it, which makes it √2 larger on each axis.
            return switch (extent) {
                case CLOSEST_SIDE -> circle ? square(Math.min(nearX, nearY)) : new double[] {nearX, nearY};
                case FARTHEST_SIDE -> circle ? square(Math.max(farX, farY)) : new double[] {farX, farY};
                case CLOSEST_CORNER ->
                    circle
                            ? square(Math.min(
                                    Math.min(Math.hypot(left, top), Math.hypot(right, top)),
                                    Math.min(Math.hypot(left, bottom), Math.hypot(right, bottom))))
                            : new double[] {nearX * Math.sqrt(2), nearY * Math.sqrt(2)};
                case FARTHEST_CORNER ->
                    circle ? square(Math.hypot(farX, farY)) : new double[] {farX * Math.sqrt(2), farY * Math.sqrt(2)};
            };
        }

        private static double[] square(double radius) {
            return new double[] {radius, radius};
        }

        @Override
        public GradientLayer fade(double alpha) {
            return alpha >= 1
                    ? this
                    : new Radial(
                            circle,
                            extent,
                            radiusX,
                            radiusY,
                            centreX,
                            centreY,
                            stops.stream().map(stop -> stop.fade(alpha)).toList(),
                            repeating);
        }
    }

    private static List<ColorStop> requireStops(List<ColorStop> stops) {
        Objects.requireNonNull(stops, "stops");
        if (stops.size() < 2) {
            throw new IllegalArgumentException(
                    "a gradient has two colour stops at least, and this has " + stops.size());
        }
        return List.copyOf(stops);
    }
}
