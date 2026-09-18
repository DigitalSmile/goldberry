package io.github.digitalsmile.goldberry.widgets.core.scroll;

/// Where a [ScrollViewport] reports that its content moved **under** it.
///
/// [ScrollTarget]'s twin, and separate from it because the two mean opposite
/// things. A `moveTo` is the user going somewhere: the bars wake, a glide draws
/// the way there, and the controller is told. A shift is the content arriving
/// somewhere else while the user sat still, and the only correct response is for
/// the offset to move by exactly as much so that nothing on screen does.
///
/// A distance rather than a target for [ScrollTarget]'s reason, and two doubles
/// rather than a `BiConsumer<Double, Double>` for the other one.
@FunctionalInterface
interface ScrollShift {

    /// Move the offset by `dx`, `dy` without moving what is on screen.
    void shiftBy(double dx, double dy);
}
