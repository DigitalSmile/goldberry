/// The pen: a stroke's width, its caps and joins, and its dash pattern.
///
/// Values with no native handle, for `css.value`'s reason (ADR-0172). A
/// `paint.Frame` maps them to the rasterizer's own in one private switch
/// (ADR-0496).
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.paint.stroke;

import org.jspecify.annotations.NullMarked;
