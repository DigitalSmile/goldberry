package io.github.digitalsmile.goldberry.natives.yoga;

/// Where a layout pass put a node, in its parent's coordinates.
///
/// Read after [YogaNode#calculateLayout]; before one, every field is zero rather
/// than undefined, so a stale result is indistinguishable from an unlaid-out
/// node. [YogaNode#hasNewLayout()] is the flag that tells them apart.
///
/// The numbers are in points — logical pixels — already snapped to the pixel
/// grid of the [YogaConfig#pointScaleFactor] the tree was built with. At a
/// fractional scale that snapping is the whole point: it is what keeps a
/// 1.5&times;-scaled border from landing on a half-physical-pixel and blurring.
///
/// @param left   offset from the parent's content box, along its main cross axis
/// @param top    offset from the parent's content box, vertically
/// @param width  the node's outer width
/// @param height the node's outer height
public record ComputedLayout(float left, float top, float width, float height) {

    @Override
    public String toString() {
        return width + "x" + height + " at (" + left + ", " + top + ")";
    }
}
