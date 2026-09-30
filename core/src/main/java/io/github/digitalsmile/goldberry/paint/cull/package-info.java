/// Culling: the rectangle a box or a subtree actually puts ink in, so the painter
/// can skip what lands wholly outside the clip (ADR-0313).
///
/// A box's ink reaches past its border box in deliberate ways — a focus ring, an
/// asymmetric drop shadow — and a culler that is wrong by a pixel drops a row off
/// the bottom of a list. Its own exported package for `paint.geom`'s reason: it is
/// arithmetic over rectangles, needs no `Frame`, and is tested without one.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.paint.cull;

import org.jspecify.annotations.NullMarked;
