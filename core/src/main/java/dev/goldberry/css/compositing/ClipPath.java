package dev.goldberry.css.compositing;

import java.util.List;
import java.util.Objects;

import dev.goldberry.css.Corners;
import dev.goldberry.layout.Length;

/// What part of a box is drawn: CSS's `clip-path`.
///
/// ```css
/// .avatar  { clip-path: circle(50%) }
/// .ticket  { clip-path: inset(0 8px round 6px) }
/// .arrow   { clip-path: polygon(0 0, 100% 50%, 0 100%) }
/// .card    { clip-path: padding-box }
/// ```
///
/// A shape, a reference box, or both. Everything outside the shape is not
/// drawn, the box's own painting and its children's alike, and the pointer
/// does not find the box there. Lengths and percentages are resolved when the
/// box is painted, against the reference box.
///
/// Sealed, so the painter and the hit test each read it with a `switch` that
/// has no default arm.
public sealed interface ClipPath {

    /// `none`: the whole box is drawn.
    ClipPath NONE = new None();

    /// `none`.
    record None() implements ClipPath {}

    /// A shape, in a reference box.
    ///
    /// @param shape the shape
    /// @param box   the box it is drawn in, the border box when none is written
    record Shaped(Shape shape, ReferenceBox box) implements ClipPath {
        public Shaped {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(box, "box");
        }
    }

    /// A reference box alone: the box clipped to it, with its own rounded
    /// corners.
    ///
    /// @param box the box
    record Boxed(ReferenceBox box) implements ClipPath {
        public Boxed {
            Objects.requireNonNull(box, "box");
        }
    }

    /// A basic shape.
    sealed interface Shape {}

    /// `inset(top right bottom left round radii)`: a rectangle drawn in from the
    /// reference box's edges, with rounded corners.
    ///
    /// @param top    how far in from the top edge
    /// @param right  how far in from the right
    /// @param bottom how far in from the bottom
    /// @param left   how far in from the left
    /// @param round  the corners, square when nothing is written
    record Inset(Length top, Length right, Length bottom, Length left, Corners round) implements Shape {
        public Inset {
            Objects.requireNonNull(top, "top");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(bottom, "bottom");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(round, "round");
        }
    }

    /// `circle(radius at position)`.
    ///
    /// @param radius the radius; a percentage is of the reference box's
    ///               diagonal over the square root of two, as CSS says
    /// @param at     the centre
    record Circle(Radius radius, Position at) implements Shape {
        public Circle {
            Objects.requireNonNull(radius, "radius");
            Objects.requireNonNull(at, "at");
        }
    }

    /// `ellipse(rx ry at position)`.
    ///
    /// @param rx the horizontal radius; a percentage is of the box's width
    /// @param ry the vertical radius; a percentage is of the box's height
    /// @param at the centre
    record Ellipse(Radius rx, Radius ry, Position at) implements Shape {
        public Ellipse {
            Objects.requireNonNull(rx, "rx");
            Objects.requireNonNull(ry, "ry");
            Objects.requireNonNull(at, "at");
        }
    }

    /// `polygon(fill-rule, x y, x y, …)`.
    ///
    /// @param evenOdd whether `evenodd` decides what is inside, rather than
    ///                `nonzero`
    /// @param points  the vertices, at least three
    record Polygon(boolean evenOdd, List<Position> points) implements Shape {
        public Polygon {
            points = List.copyOf(Objects.requireNonNull(points, "points"));
            if (points.size() < 3) {
                throw new IllegalArgumentException("a polygon has at least three points, not " + points.size());
            }
        }
    }

    /// `path(fill-rule, "svg path data")`, in the reference box's own
    /// coordinates, unscaled.
    ///
    /// @param evenOdd whether `evenodd` decides what is inside
    /// @param data    the SVG path data
    record PathData(boolean evenOdd, String data) implements Shape {
        public PathData {
            Objects.requireNonNull(data, "data");
        }
    }

    /// A radius: a length, or the distance to the nearest or the farthest side.
    sealed interface Radius {
        /// `closest-side`, the default.
        Radius CLOSEST_SIDE = new ToSide(false);

        /// A length or a percentage.
        record Of(Length length) implements Radius {
            public Of {
                Objects.requireNonNull(length, "length");
            }
        }

        /// `closest-side` or `farthest-side`.
        ///
        /// @param farthest whether it is the farthest
        record ToSide(boolean farthest) implements Radius {}
    }

    /// A point, each coordinate a length or a percentage of the reference box.
    ///
    /// @param x across from the left
    /// @param y down from the top
    record Position(Length x, Length y) {

        /// The middle, which is where a shape with no `at` is centred.
        public static final Position CENTER = new Position(Length.percent(50), Length.percent(50));

        public Position {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
        }
    }
}
