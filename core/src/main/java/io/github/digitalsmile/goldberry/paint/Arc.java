package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;

/// A circular arc, appended to a Blend2D path.
///
/// [RoundRect]'s sibling and what is left of it for the same reason: the cubics
/// and the quarter-at-a-time sweep moved to [Path#arc] with ADR-0277, and this is
/// the adapter that gets one into the pooled `BlendPath` `:core`'s painters build
/// into. An application wanting an arc wants [Path#arc].
final class Arc {

    private Arc() {}

    /// Appends an arc of `radius` about `(cx, cy)`, running `sweep` radians from
    /// `start`, to `path`.
    ///
    /// Angles are in radians, clockwise, with zero pointing right — the y axis
    /// points down, so this is the direction a clock's hands go on screen. A
    /// non-positive radius or a zero sweep appends nothing, which is what a
    /// spinner at rest and a donut slice of no value both need.
    static void addTo(BlendPath path, double cx, double cy, double radius, double start, double sweep) {
        Path.arc(cx, cy, radius, start, sweep).replayInto(path);
    }
}
