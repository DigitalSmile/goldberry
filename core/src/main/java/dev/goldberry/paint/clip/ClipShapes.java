package dev.goldberry.paint.clip;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Corners;
import dev.goldberry.css.compositing.ClipPath;
import dev.goldberry.css.compositing.ReferenceBox;
import dev.goldberry.css.value.Affine;
import dev.goldberry.icon.SvgPath;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.BoxEdges;
import dev.goldberry.paint.Path;

/// A `clip-path` resolved against one laid-out box: the outline the painter
/// keeps, and whether a point is inside it.
///
/// Everything is in the box's own coordinates, its border box's top-left
/// corner at the origin. Percentages are of the reference box, a circle's
/// radius of its diagonal over the square root of two, as CSS says.
public final class ClipShapes {

    private ClipShapes() {}

    /// What the painter cuts a box's raster to.
    ///
    /// @param path    the outline, in the box's own coordinates
    /// @param evenOdd whether `evenodd` decides what is inside it
    public record Outline(Path path, boolean evenOdd) {
        public Outline {
            Objects.requireNonNull(path, "path");
        }
    }

    /// The outline `box`'s `clip-path` keeps of it, laid out `width` by
    /// `height`, or null when it keeps all of it.
    public static @Nullable Outline of(ClipPath clip, Box box, double width, double height) {
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(box, "box");
        return switch (clip) {
            case ClipPath.None _ -> null;
            case ClipPath.Boxed(var reference) -> {
                var area = area(reference, box, width, height);
                yield new Outline(
                        Path.roundRect(
                                area.x(),
                                area.y(),
                                area.width(),
                                area.height(),
                                corners(reference, box, area, width, height)),
                        false);
            }
            case ClipPath.Shaped(var shape, var reference) -> shape(shape, area(reference, box, width, height));
        };
    }

    /// Whether the point `(x, y)`, in the box's own coordinates, is inside what
    /// `box`'s `clip-path` keeps of it. True for a box it keeps whole.
    ///
    /// A `path()` is answered by its reference box, and an inset's rounded
    /// corners by its rectangle: the pointer is a point and a corner's curve a
    /// few pixels.
    public static boolean contains(ClipPath clip, Box box, double width, double height, double x, double y) {
        return switch (clip) {
            case ClipPath.None _ -> true;
            case ClipPath.Boxed(var reference) ->
                area(reference, box, width, height).contains(x, y);
            case ClipPath.Shaped(var shape, var reference) -> {
                var area = area(reference, box, width, height);
                yield switch (shape) {
                    case ClipPath.Inset inset -> inset(inset, area).contains(x, y);
                    case ClipPath.Circle circle -> {
                        var cx = area.x() + resolve(circle.at().x(), area.width());
                        var cy = area.y() + resolve(circle.at().y(), area.height());
                        var r = circleRadius(circle.radius(), area, cx, cy);
                        yield Math.hypot(x - cx, y - cy) <= r;
                    }
                    case ClipPath.Ellipse ellipse -> {
                        var cx = area.x() + resolve(ellipse.at().x(), area.width());
                        var cy = area.y() + resolve(ellipse.at().y(), area.height());
                        var rx = axisRadius(ellipse.rx(), cx - area.x(), area.right() - cx, area.width());
                        var ry = axisRadius(ellipse.ry(), cy - area.y(), area.bottom() - cy, area.height());
                        if (!(rx > 0) || !(ry > 0)) {
                            yield false;
                        }
                        var dx = (x - cx) / rx;
                        var dy = (y - cy) / ry;
                        yield dx * dx + dy * dy <= 1;
                    }
                    case ClipPath.Polygon polygon -> inPolygon(polygon, area, x, y);
                    case ClipPath.PathData _ -> area.contains(x, y);
                };
            }
        };
    }

    private static Outline shape(ClipPath.Shape shape, Area area) {
        return switch (shape) {
            case ClipPath.Inset inset -> {
                var rect = inset(inset, area);
                yield new Outline(
                        Path.roundRect(
                                rect.x(),
                                rect.y(),
                                rect.width(),
                                rect.height(),
                                inset.round().fittedTo(rect.width(), rect.height())),
                        false);
            }
            case ClipPath.Circle circle -> {
                var cx = area.x() + resolve(circle.at().x(), area.width());
                var cy = area.y() + resolve(circle.at().y(), area.height());
                yield new Outline(Path.circle(cx, cy, circleRadius(circle.radius(), area, cx, cy)), false);
            }
            case ClipPath.Ellipse ellipse -> {
                var cx = area.x() + resolve(ellipse.at().x(), area.width());
                var cy = area.y() + resolve(ellipse.at().y(), area.height());
                var rx = axisRadius(ellipse.rx(), cx - area.x(), area.right() - cx, area.width());
                var ry = axisRadius(ellipse.ry(), cy - area.y(), area.bottom() - cy, area.height());
                yield new Outline(Path.ellipse(cx, cy, rx, ry), false);
            }
            case ClipPath.Polygon polygon -> {
                var builder = Path.builder();
                for (var i = 0; i < polygon.points().size(); i++) {
                    var point = polygon.points().get(i);
                    var px = area.x() + resolve(point.x(), area.width());
                    var py = area.y() + resolve(point.y(), area.height());
                    if (i == 0) {
                        builder.moveTo(px, py);
                    } else {
                        builder.lineTo(px, py);
                    }
                }
                yield new Outline(builder.close().build(), polygon.evenOdd());
            }
            case ClipPath.PathData data -> {
                var builder = Path.builder();
                SvgPath.appendTo(builder, data.data());
                yield new Outline(builder.build().transformed(Affine.translate(area.x(), area.y())), data.evenOdd());
            }
        };
    }

    /// An `inset()`'s rectangle inside the reference box, never inside out.
    private static Area inset(ClipPath.Inset inset, Area area) {
        var left = resolve(inset.left(), area.width());
        var right = resolve(inset.right(), area.width());
        var top = resolve(inset.top(), area.height());
        var bottom = resolve(inset.bottom(), area.height());
        return new Area(
                area.x() + left,
                area.y() + top,
                Math.max(0, area.width() - left - right),
                Math.max(0, area.height() - top - bottom));
    }

    /// A circle's radius: a length, a percentage of the reference box's
    /// diagonal over the square root of two, or the distance to a side.
    private static double circleRadius(ClipPath.Radius radius, Area area, double cx, double cy) {
        return switch (radius) {
            case ClipPath.Radius.Of(var length) ->
                resolve(length, Math.hypot(area.width(), area.height()) / Math.sqrt(2));
            case ClipPath.Radius.ToSide(var farthest) -> {
                var sides = new double[] {
                    Math.abs(cx - area.x()),
                    Math.abs(area.right() - cx),
                    Math.abs(cy - area.y()),
                    Math.abs(area.bottom() - cy)
                };
                var pick = sides[0];
                for (var side : sides) {
                    pick = farthest ? Math.max(pick, side) : Math.min(pick, side);
                }
                yield pick;
            }
        };
    }

    /// One of an ellipse's radii: a length, a percentage of the box along its
    /// axis, or the distance to the nearer or farther side across it.
    private static double axisRadius(ClipPath.Radius radius, double before, double after, double extent) {
        return switch (radius) {
            case ClipPath.Radius.Of(var length) -> resolve(length, extent);
            case ClipPath.Radius.ToSide(var farthest) ->
                farthest ? Math.max(Math.abs(before), Math.abs(after)) : Math.min(Math.abs(before), Math.abs(after));
        };
    }

    /// Whether `(x, y)` is inside a polygon, by its fill rule.
    private static boolean inPolygon(ClipPath.Polygon polygon, Area area, double x, double y) {
        var points = polygon.points();
        var winding = 0;
        var crossings = 0;
        for (var i = 0; i < points.size(); i++) {
            var a = points.get(i);
            var b = points.get((i + 1) % points.size());
            var ax = area.x() + resolve(a.x(), area.width());
            var ay = area.y() + resolve(a.y(), area.height());
            var bx = area.x() + resolve(b.x(), area.width());
            var by = area.y() + resolve(b.y(), area.height());
            if ((ay <= y) != (by <= y)) {
                var crossX = ax + (y - ay) / (by - ay) * (bx - ax);
                if (crossX > x) {
                    crossings++;
                    winding += by > ay ? 1 : -1;
                }
            }
        }
        return polygon.evenOdd() ? crossings % 2 == 1 : winding != 0;
    }

    /// The reference box: the border box, less the border for the padding box,
    /// and less the padding too for the content box.
    private static Area area(ReferenceBox reference, Box box, double width, double height) {
        return switch (reference) {
            case BORDER_BOX -> new Area(0, 0, width, height);
            case PADDING_BOX -> {
                var border = box.decoration().border();
                yield shrunk(
                        width,
                        height,
                        border.top().width(),
                        border.right().width(),
                        border.bottom().width(),
                        border.left().width());
            }
            case CONTENT_BOX -> {
                var content = BoxEdges.content(box, width, height);
                yield shrunk(width, height, content.top(), content.right(), content.bottom(), content.left());
            }
        };
    }

    /// A reference box's own rounded corners: the box's, less how far in the
    /// reference box is from the border box on each side.
    private static Corners corners(ReferenceBox reference, Box box, Area area, double width, double height) {
        var outer = box.decoration().corners();
        if (outer.isSquare()) {
            return Corners.SQUARE;
        }
        var fitted = outer.fittedTo(width, height);
        if (reference == ReferenceBox.BORDER_BOX) {
            return fitted;
        }
        return new Corners(
                inner(fitted.topLeftRadius(), area.x(), area.y()),
                inner(fitted.topRightRadius(), area.x(), area.y()),
                inner(fitted.bottomRightRadius(), area.x(), area.y()),
                inner(fitted.bottomLeftRadius(), area.x(), area.y()));
    }

    private static Corners.Radius inner(Corners.Radius radius, double dx, double dy) {
        return new Corners.Radius(Math.max(0, radius.x() - dx), Math.max(0, radius.y() - dy), 0, 0);
    }

    private static Area shrunk(double width, double height, double top, double right, double bottom, double left) {
        return new Area(left, top, Math.max(0, width - left - right), Math.max(0, height - top - bottom));
    }

    private static double resolve(Length length, double base) {
        return Length.resolve(length, (float) base);
    }

    /// A rectangle in the box's own coordinates.
    private record Area(double x, double y, double width, double height) {

        double right() {
            return x + width;
        }

        double bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}
