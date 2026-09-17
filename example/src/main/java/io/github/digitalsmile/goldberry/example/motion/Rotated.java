package io.github.digitalsmile.goldberry.example.motion;

import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.paint.Path.Segment;

/// A [Path] turned about a point and moved, by rewriting its coordinates.
///
/// A painter could set the frame's transform instead, but `Frame.transform`
/// states the **whole** matrix. On a canvas that would discard the translation
/// that puts the canvas where it is on screen, so a tile would be drawn at the
/// window's corner. A path that is already turned needs no matrix at all
/// (ADR-0354).
final class Rotated {

    private Rotated() {}

    /// `path` turned `radians` clockwise about (`cx`, `cy`), then moved by
    /// (`dx`, `dy`).
    static Path of(Path path, double cx, double cy, double radians, double dx, double dy) {
        if (radians == 0 && dx == 0 && dy == 0) {
            return path;
        }
        var cos = Math.cos(radians);
        var sin = Math.sin(radians);
        var builder = Path.builder();
        for (var segment : path.segments()) {
            switch (segment) {
                case Segment.MoveTo(var x, var y) ->
                    builder.moveTo(px(x, y, cx, cy, cos, sin, dx), py(x, y, cx, cy, cos, sin, dy));
                case Segment.LineTo(var x, var y) ->
                    builder.lineTo(px(x, y, cx, cy, cos, sin, dx), py(x, y, cx, cy, cos, sin, dy));
                case Segment.QuadTo(var qx, var qy, var x, var y) ->
                    builder.quadTo(
                            px(qx, qy, cx, cy, cos, sin, dx),
                            py(qx, qy, cx, cy, cos, sin, dy),
                            px(x, y, cx, cy, cos, sin, dx),
                            py(x, y, cx, cy, cos, sin, dy));
                case Segment.CubicTo(var ax, var ay, var bx, var by, var x, var y) ->
                    builder.cubicTo(
                            px(ax, ay, cx, cy, cos, sin, dx),
                            py(ax, ay, cx, cy, cos, sin, dy),
                            px(bx, by, cx, cy, cos, sin, dx),
                            py(bx, by, cx, cy, cos, sin, dy),
                            px(x, y, cx, cy, cos, sin, dx),
                            py(x, y, cx, cy, cos, sin, dy));
                case Segment.ArcTo(var rx, var ry, var rotation, var large, var sweep, var x, var y) ->
                    builder.arcTo(
                            rx,
                            ry,
                            rotation + radians,
                            large,
                            sweep,
                            px(x, y, cx, cy, cos, sin, dx),
                            py(x, y, cx, cy, cos, sin, dy));
                case Segment.Close() -> builder.close();
            }
        }
        return builder.build();
    }

    private static double px(double x, double y, double cx, double cy, double cos, double sin, double dx) {
        return cx + (x - cx) * cos - (y - cy) * sin + dx;
    }

    private static double py(double x, double y, double cx, double cy, double cos, double sin, double dy) {
        return cy + (x - cx) * sin + (y - cy) * cos + dy;
    }
}
