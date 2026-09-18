package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// Where the cut-out is **right now**, between the stop being left and the one being
/// arrived at.
///
/// §3.1's tour row asks for the cut-out to "`translate`+size" between stops, and both
/// halves fall out of interpolating the rectangle: a target that moves and changes
/// size does both at once.
///
/// **One function rather than one per node, because three nodes draw it.** [TourVeil]
/// cuts the hole, [TourStop] frames it with the ring and places the card beside it,
/// and all three have to agree on every frame or the picture comes apart while it is
/// moving ([ADR-0269]). They each had their own copy of this arithmetic and their own
/// `lerp` beneath it; agreeing by construction is cheaper than agreeing by review.
///
/// Interpolated where it is drawn rather than handed down already interpolated,
/// because the frame clock reaches a widget in `render` and nowhere else — so the node
/// that draws the hole is the only one that can know how far through the travel it is.
/// This is a `Phase` and not a `transition` for the reason [Phase] itself gives: a
/// cut-out's rectangle is computed from an anchor the cascade has never seen, so there
/// are no two styles to interpolate between.
final class Lit {

    private Lit() {}

    /// The rectangle at `now` — `target` itself whenever there is no travel to be
    /// part of the way through.
    ///
    /// @param cameFrom the stop being left, or null before there was one
    /// @param target   the stop being arrived at, or null for a tour between stops
    /// @param travel   what says how far through, or null when nothing is travelling
    /// @param now      the frame clock, in milliseconds
    static @Nullable LogicalRect rectAt(
            @Nullable LogicalRect cameFrom, @Nullable LogicalRect target, @Nullable Phase travel, double now) {
        if (target == null || cameFrom == null || travel == null) {
            return target;
        }
        var t = travel.progressAt(now);
        if (t >= 1) {
            return target;
        }
        return LogicalRect.of(
                lerp(cameFrom.left(), target.left(), t),
                lerp(cameFrom.top(), target.top(), t),
                lerp(cameFrom.size().width(), target.size().width(), t),
                lerp(cameFrom.size().height(), target.size().height(), t));
    }

    private static float lerp(double from, double to, double t) {
        return (float) (from + (to - from) * t);
    }
}
