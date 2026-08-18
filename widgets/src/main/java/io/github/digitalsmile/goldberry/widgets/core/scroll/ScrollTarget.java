package io.github.digitalsmile.goldberry.widgets.core.scroll;

/// Where a [ScrollViewport] reports the offset it wants.
///
/// The viewport is a value rebuilt every frame and the offset outlives it, so
/// the one is not the place the other can live. This is the wire between them,
/// and it points at the [ScrollState] that holds the position — the same shape a
/// control's `change` handler has, and the same direction: what the user did
/// travels **up**, and the new offset comes back **down** on the next build
/// ([ADR-0063](../../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)).
///
/// Not a `BiConsumer<Double, Double>`: two boxes per wheel event, on the one path
/// in this widget that runs at the pointer's rate.
@FunctionalInterface
interface ScrollTarget {

    /// Move to `x`, `y` — already clamped to what there is to show.
    void moveTo(double x, double y);
}
