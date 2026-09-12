package io.github.digitalsmile.goldberry.paint;

/// What a stroke does where two segments meet.
///
/// [Cap]'s sibling, and here for its reason: these are drawing concepts, not
/// native ones, and an application that draws a chevron should not have to name
/// a `BL_*` constant to round its corner (ADR-0277).
///
/// ## Why there is one miter and not three
///
/// Blend2D has three miter variants — clip, and two that fall back to a bevel or
/// a round when the spike gets too long — and they differ only in what happens
/// *past the miter limit*. SVG and CSS both have one `miter`, with the limit as a
/// separate number, and that is the shape a caller thinks in: "join them to a
/// point, unless the point runs away". [Stroke#miterLimit()] is where the number
/// lives, so the choice here stays a choice about the corner.
public enum Join {

    /// Extend both edges to their intersection, cut off at
    /// [Stroke#miterLimit()]. The default.
    MITER,

    /// Cut the corner off with a straight edge.
    BEVEL,

    /// Round the corner with an arc of the stroke's radius. What Lucide is drawn
    /// with, and what makes a chevron look drawn rather than folded.
    ROUND
}
