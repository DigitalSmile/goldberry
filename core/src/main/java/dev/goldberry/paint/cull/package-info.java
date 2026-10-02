/// Culling: the rectangle a box or a subtree actually puts ink in, so the painter
/// can skip what lands wholly outside the clip.
///
/// A box's ink reaches past its border box in deliberate ways — a focus ring, an
/// asymmetric drop shadow — and a culler that is wrong by a pixel drops a row off
/// the bottom of a list. Its own exported package for `paint.geom`'s reason: it is
/// arithmetic over rectangles, needs no `Frame`, and is tested without one.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more:
/// [Keeping frames cheap](https://goldberry.dev/docs/performance/frames.html#what-the-toolkit-does-for-you).
@NullMarked
package dev.goldberry.paint.cull;

import org.jspecify.annotations.NullMarked;
