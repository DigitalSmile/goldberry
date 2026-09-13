package io.github.digitalsmile.goldberry.paint.shadow;

import java.util.Objects;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.paint.Path;

/// The shape one band of a shadow is filled with.
///
/// The other half of [ShadowRamp]: that one says how opaque a band is, this one
/// says where it is. Separated because the alpha solution is subtle arithmetic
/// worth testing on its own, and the geometry is subtle in a different way and
/// worth testing on its own too (ADR-0310).
///
/// ## What a band's shape is
///
/// The **border box**, moved by the shadow's offset and grown on every side by
/// `grow` — the band's own distance from the shape's edge, with the shadow's
/// `spread` already folded in. Coordinates are the box's own: `(0, 0)` is its
/// top-left corner, which is what
/// [io.github.digitalsmile.goldberry.paint.Frame#fillPath(double, double, Path, int)]
/// places against the box's position on the frame.
///
/// The corners grow with it. A shadow cast by a box with an 8px radius, spread
/// 4px out, has a 12px radius — the two shapes are concentric, which is the same
/// rule the focus ring follows and for the same reason: a shadow that kept the
/// box's radius while growing would pull away from the corners and leave four
/// dark ears. A **square** corner stays square, again like the ring: a box with
/// sharp corners casts a shadow with sharp corners.
///
/// ## The shadow is drawn *under* the box and not cut out of it
///
/// CSS paints an outer shadow only outside the border box: the box's own
/// rectangle is knocked out of it, so a translucent background does not have the
/// shadow showing through from underneath. This paints the whole shape and
/// relies on the box being drawn on top of it, because the rasterizer binding
/// has neither a path clip nor a fill rule to cut a hole with — a reversed
/// sub-path under Blend2D's non-zero winding fills the parts of itself that the
/// outer shape does not cover, which is worse than the thing it would fix.
///
/// The difference is invisible under an opaque background, which is every
/// shadowed surface in the design system. It shows under a **fading** one: a box
/// mid-`opacity` transition fades its shadow by the same factor
/// ([io.github.digitalsmile.goldberry.css.Decoration#fade]), so what is under it
/// darkens it slightly rather than being hidden. Recorded in ADR-0310 and in
/// `book/src/TODO.md`; the fix is a fill rule on the context, not a different
/// shape here.
public final class ShadowGeometry {

    private ShadowGeometry() {}

    /// The path one band is filled with.
    ///
    /// @param width   the box's width, in logical pixels
    /// @param height  the box's height
    /// @param corners the **box's** corner radii; this grows them by `grow`
    /// @param shadow  the shadow being drawn, for its offset
    /// @param grow    how much larger than the box this band is on every side,
    ///                spread included; negative for a band inside the edge
    /// @return the band's outline, or [Path#EMPTY] when `grow` has shrunk it
    ///         away — which a large negative spread does, and which is a shadow
    ///         that draws nothing rather than an error
    public static Path band(double width, double height, Corners corners, Shadow shadow, double grow) {
        Objects.requireNonNull(corners, "corners");
        Objects.requireNonNull(shadow, "shadow");
        var w = width + grow * 2;
        var h = height + grow * 2;
        if (!(w > 0) || !(h > 0)) {
            return Path.EMPTY;
        }
        return Path.roundRect(shadow.offsetX() - grow, shadow.offsetY() - grow, w, h, grown(corners, grow));
    }

    /// `corners` moved out by `by`, which is [Corners#grownBy] one way and
    /// [Corners#shrunkBy] the other.
    ///
    /// One method rather than a sign test at the call site: the inner half of a
    /// blur is made of bands at a *negative* distance, so both directions are
    /// ordinary here in a way they are not for a focus ring.
    static Corners grown(Corners corners, double by) {
        return by >= 0 ? corners.grownBy(by) : corners.shrunkBy(-by);
    }
}
