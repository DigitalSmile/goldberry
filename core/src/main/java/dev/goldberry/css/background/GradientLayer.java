package dev.goldberry.css.background;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.colour.ColourMixing;
import dev.goldberry.css.value.colour.ColourSpace;
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
/// A **hint**, a bare position between two stops, moves where the colour is
/// half way between them; and `in <space>` names the colour space the stops
/// are blended in, sRGB when nobody says. Both are drawn as extra stops along
/// the curve they describe, since the rasterizer blends in sRGB and in
/// straight lines.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface GradientLayer extends CssImage {

    /// The colour stops, in the order they were written.
    List<ColorStop> stops();

    /// Whether this is a `repeating-` gradient.
    boolean repeating();

    /// The colour space the stops are blended in.
    Interpolation interpolation();

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
    /// @param hint     whether this is a hint rather than a stop: a position,
    ///                 never null, with no colour of its own, where the two
    ///                 stops around it are mixed half and half
    record ColorStop(int argb, @Nullable Length position, boolean hint) {

        public ColorStop {
            if (position != null && !(position instanceof Length.Points) && !(position instanceof Length.Percent)) {
                throw new IllegalArgumentException(
                        "a colour stop is placed by a length or a percentage, and " + position + " is neither");
            }
            if (hint && position == null) {
                throw new IllegalArgumentException("a hint is a position");
            }
        }

        /// A stop: a colour, placed or not.
        public ColorStop(int argb, @Nullable Length position) {
            this(argb, position, false);
        }

        /// A hint at `position`.
        public static ColorStop hint(Length position) {
            return new ColorStop(0, position, true);
        }

        /// This stop with its colour's alpha scaled by `alpha`.
        ColorStop fade(double alpha) {
            return hint ? this : new ColorStop(CssColor.fade(argb, alpha), position);
        }
    }

    /// How the stops are blended: a colour space, and the way round a hue
    /// goes in one that has a hue.
    record Interpolation(ColourSpace space, ColourMixing.HueMethod hue) {

        /// sRGB, which is what a gradient that names no space is blended in.
        public static final Interpolation SRGB = new Interpolation(ColourSpace.SRGB, ColourMixing.HueMethod.SHORTER);

        public Interpolation {
            Objects.requireNonNull(space, "space");
            Objects.requireNonNull(hue, "hue");
        }

        /// Whether this is the rasterizer's own blend, which needs no extra stops.
        boolean isSrgb() {
            return space == ColourSpace.SRGB;
        }

        /// `from` and `to` mixed `t` of the way.
        int mix(int from, int to, double t) {
            return ColourMixing.mix(from, to, t, space, hue);
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
    record Linear(Direction direction, List<ColorStop> stops, boolean repeating, Interpolation interpolation)
            implements GradientLayer {

        public Linear {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(interpolation, "interpolation");
            stops = requireStops(stops);
        }

        /// A gradient blended in sRGB.
        public Linear(Direction direction, List<ColorStop> stops, boolean repeating) {
            this(direction, stops, repeating, Interpolation.SRGB);
        }

        @Override
        public Gradient resolve(double x, double y, double width, double height, BackgroundPosition position) {
            var angle = switch (direction) {
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
            var ramp = Ramp.of(stops, length, interpolation);
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
                            repeating,
                            interpolation);
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
            boolean repeating,
            Interpolation interpolation)
            implements GradientLayer {

        /// A gradient blended in sRGB.
        public Radial(
                boolean circle,
                @Nullable Extent extent,
                @Nullable Length radiusX,
                @Nullable Length radiusY,
                Length centreX,
                Length centreY,
                List<ColorStop> stops,
                boolean repeating) {
            this(circle, extent, radiusX, radiusY, centreX, centreY, stops, repeating, Interpolation.SRGB);
        }

        public Radial {
            if (extent == null && (radiusX == null || radiusY == null)) {
                throw new IllegalArgumentException("a radial gradient is sized by a keyword or by two radii");
            }
            Objects.requireNonNull(centreX, "centreX");
            Objects.requireNonNull(centreY, "centreY");
            Objects.requireNonNull(interpolation, "interpolation");
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
            var ramp = Ramp.of(stops, rx, interpolation).fromZero(repeating);
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
                            repeating,
                            interpolation);
        }
    }

    /// `conic-gradient()` and `repeating-conic-gradient()`: colours turning
    /// round a centre, clockwise from the top, or from `from`.
    ///
    /// Its stops are placed by angle, written as a percentage of a turn: `90deg`
    /// is `25%`.
    ///
    /// @param from    where the turn starts, in radians clockwise from the top
    /// @param centreX where the centre is across the box
    /// @param centreY where the centre is down the box
    record Conic(
            double from,
            Length centreX,
            Length centreY,
            List<ColorStop> stops,
            boolean repeating,
            Interpolation interpolation)
            implements GradientLayer {

        public Conic {
            if (!Double.isFinite(from)) {
                throw new IllegalArgumentException(
                        "a conic gradient starts at a finite angle, and " + from + " is not");
            }
            Objects.requireNonNull(centreX, "centreX");
            Objects.requireNonNull(centreY, "centreY");
            Objects.requireNonNull(interpolation, "interpolation");
            stops = requireStops(stops);
            for (var stop : stops) {
                if (stop.position() instanceof Length.Points) {
                    throw new IllegalArgumentException("a conic gradient's stops are angles, not lengths");
                }
            }
        }

        @Override
        public Gradient resolve(double x, double y, double width, double height, BackgroundPosition position) {
            // In degrees: a percentage of a turn of 360.
            var ramp = Ramp.of(stops, 360, interpolation);
            var span = ramp.to() - ramp.from();
            // A repeating turn draws its first period as many times as fit;
            // CSS starts it at the turn's start, as the rasterizer does.
            var repeats = repeating && span >= MIN_PERIOD;
            var turns = repeats ? 360 / span : 1;
            return new Gradient.Conic(
                    x + position.x() + Length.resolve(centreX, (float) width),
                    y + position.y() + Length.resolve(centreY, (float) height),
                    // The rasterizer turns from the positive x axis; CSS from the top.
                    from - Math.PI / 2,
                    turns,
                    ramp.over(0, repeats ? span : 360, repeats));
        }

        @Override
        public GradientLayer fade(double alpha) {
            return alpha >= 1
                    ? this
                    : new Conic(
                            from,
                            centreX,
                            centreY,
                            stops.stream().map(stop -> stop.fade(alpha)).toList(),
                            repeating,
                            interpolation);
        }
    }

    /// The narrowest period a repeating conic gradient is drawn with, in
    /// degrees: under it, the stripes are finer than any pixel and the turn is
    /// drawn once instead.
    double MIN_PERIOD = 0.1;

    private static List<ColorStop> requireStops(List<ColorStop> stops) {
        Objects.requireNonNull(stops, "stops");
        var colours = stops.stream().filter(stop -> !stop.hint()).count();
        if (colours < 2) {
            throw new IllegalArgumentException("a gradient has two colour stops at least, and this has " + colours);
        }
        if (stops.getFirst().hint() || stops.getLast().hint()) {
            throw new IllegalArgumentException("a gradient's hints are between its colours, not at its ends");
        }
        for (var i = 1; i < stops.size(); i++) {
            if (stops.get(i).hint() && stops.get(i - 1).hint()) {
                throw new IllegalArgumentException(
                        "a gradient's hints are between two colours, not next to each other");
            }
        }
        return List.copyOf(stops);
    }
}
