package dev.goldberry.image.lottie;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.paint.stroke.Cap;
import dev.goldberry.paint.stroke.Join;

/// One item of a shape layer's contents, as the document lists them: geometry,
/// a paint, a modifier, or a group of more items.
///
/// **Order is meaning.** A paint draws every piece of geometry listed before it
/// in its group, nested groups included, and an item earlier in the list is
/// drawn over a later one. A trim cuts every path listed before it. That is
/// how After Effects stacks a shape layer's contents, and every player reads
/// it the same way.
sealed interface Shape {

    /// A group: its own items under its own transform.
    ///
    /// @param items     the contents, in the document's order, the transform
    ///                  taken out
    /// @param transform the group's `tr`
    record Group(List<Shape> items, Transform transform) implements Shape {

        public Group {
            items = List.copyOf(items);
        }
    }

    /// Something that makes a path: a rectangle, an ellipse, a star, a path.
    sealed interface Geometry extends Shape {}

    /// A rectangle about `position`, its corners rounded by `roundness`.
    ///
    /// @param reversed whether it runs anticlockwise, which only a trim sees
    record Rect(Property position, Property size, Property roundness, boolean reversed) implements Geometry {}

    /// An ellipse about `position`, `size` across.
    record Ellipse(Property position, Property size, boolean reversed) implements Geometry {}

    /// A star or a polygon.
    ///
    /// @param star           whether it is a star (inner and outer points) or a
    ///                       polygon (outer points only)
    /// @param points         how many points
    /// @param rotation       degrees, clockwise, from pointing up
    /// @param innerRoundness percent
    /// @param outerRoundness percent
    record Star(
            boolean star,
            Property position,
            Property points,
            Property rotation,
            Property innerRadius,
            Property outerRadius,
            Property innerRoundness,
            Property outerRoundness,
            boolean reversed)
            implements Geometry {}

    /// A path the document draws vertex by vertex.
    record PathShape(ShapeProperty path, boolean reversed) implements Geometry {}

    /// Something that draws the geometry before it.
    sealed interface Paint extends Shape {

        /// 0 to 100.
        Property opacity();
    }

    /// A flat fill.
    ///
    /// @param color   red, green, blue (and perhaps alpha), each 0 to 1
    /// @param evenOdd whether a path inside another is a hole
    record Fill(Property color, Property opacity, boolean evenOdd) implements Paint {}

    /// A flat stroke.
    record Stroke(Property color, Property opacity, Line line) implements Paint {}

    /// A gradient fill.
    record GradientFill(Ramp ramp, Property opacity, boolean evenOdd) implements Paint {}

    /// A gradient stroke.
    record GradientStroke(Ramp ramp, Property opacity, Line line) implements Paint {}

    /// What a stroke shares with a gradient stroke: the pen.
    ///
    /// @param width      in the group's units
    /// @param miterLimit how far a mitred corner may reach, in widths
    /// @param dash       the dash, gap, dash... lengths, and an offset last when
    ///                   the document gave one; empty for a solid line
    /// @param dashOffset the offset, or null for none
    record Line(
            Property width,
            Cap cap,
            Join join,
            double miterLimit,
            List<Property> dash,
            @Nullable Property dashOffset) {

        public Line {
            dash = List.copyOf(dash);
        }
    }

    /// A gradient: which kind, where it runs, and its stops.
    ///
    /// @param radial  whether it is radial (from `start`, radius to `end`) or
    ///                linear (from `start` to `end`)
    /// @param stops   the flattened stops as the document writes them: `count`
    ///                colour stops of offset, red, green, blue, then any number
    ///                of opacity stops of offset, alpha
    /// @param count   how many colour stops lead `stops`
    record Ramp(boolean radial, Property start, Property end, Property stops, int count) {}

    /// Cuts the paths before it down to a stretch of their length.
    ///
    /// @param start        percent of the way along
    /// @param end          percent of the way along
    /// @param offset       degrees, a whole turn being the whole length
    /// @param individually whether every path is cut by the whole range (false)
    ///                     or the paths are laid end to end and cut as one (true)
    record Trim(Property start, Property end, Property offset, boolean individually) implements Shape {}
}
