package io.github.digitalsmile.goldberry.natives.yoga;

/// Measures a leaf node under Yoga's constraints.
///
/// The Java half of `YGMeasureFunc`. Yoga's own signature leads with the
/// `YGNodeConstRef` being measured; it is absent here because a [MeasureCallback]
/// is created per node, so the implementation already knows which node it is —
/// and letting the node pointer through would put a raw `MemorySegment` in front
/// of code outside this module, which `docs/ARCHITECTURE.md` §3.1 forbids.
///
/// Implementations run on Yoga's calling thread, in the middle of a layout pass.
@FunctionalInterface
public interface MeasureFunction {

    /// @param width      the constraint on width, meaningless when `widthMode` is
    ///                   [MeasureMode#UNDEFINED]
    /// @param widthMode  how to read `width`
    /// @param height     the constraint on height
    /// @param heightMode how to read `height`
    MeasuredSize measure(float width, MeasureMode widthMode, float height, MeasureMode heightMode);
}
