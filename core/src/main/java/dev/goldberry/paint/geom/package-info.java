/// Geometry over a `Path` that needs no frame: flattening its curves into straight
/// segments, cutting it into the runs of a dash pattern, and mapping it through an
/// affine transform.
///
/// Each is a pure function from one path value to another, so a shape can be
/// prepared once and drawn many times, and each is tested without a rasterizer
/// under it. The `Frame` reaches for them on the way to a drawing call; an
/// application may use them directly to measure or hit-test a shape in the
/// coordinates it will be drawn at.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#the-painter).
@NullMarked
package dev.goldberry.paint.geom;

import org.jspecify.annotations.NullMarked;
