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
/// ## The box's own rectangle is cut out of it
///
/// CSS paints an outer shadow only outside the border box, so a translucent
/// background does not have its own shadow showing through from underneath.
/// Until ADR-0427 this did not: it filled the whole shape and relied on the box
/// being drawn on top, because the rasterizer binding had no way to cut a hole —
/// and a reversed sub-path under Blend2D's default non-zero winding *fills* the
/// parts of itself the outer shape does not cover, which is worse than the thing
/// it would fix.
///
/// The fix is not a different shape; it is a different **rule**.
/// [#borderBox] is added to the band as a second sub-path and the pair is filled
/// even-odd, so a point inside both is crossed twice and left alone — whichever
/// direction either sub-path happens to be wound in. The band's own geometry
/// below is untouched by this.
///
/// One consequence worth stating, because a caller of [#band] can be surprised
/// by it: a band whose `grow` puts it entirely inside the border box now paints
/// **nothing**, whatever its alpha says. That is why [ShadowRamp] drops those
/// bands unconditionally rather than only under an opaque box.
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

    /// The hole every band is cut with — the **border box itself**.
    ///
    /// In the box's own coordinates, at `(0, 0)`, with the box's own radii and
    /// **not** the shadow's offset: the shadow moves and the hole does not,
    /// which is the whole of why an offset shadow is visible at all. A hole that
    /// travelled with the band would be a band that never painted anything.
    ///
    /// Square-cornered boxes get [Path#rect] out of this, which is the same
    /// point sequence [#band] emits for them — so the hole and the shape it is
    /// cut from are described the same way and a fill rule has nothing to
    /// disagree about.
    ///
    /// @param width  the box's width, in logical pixels
    /// @param height the box's height
    /// @param corners the box's corner radii, ungrown
    public static Path borderBox(double width, double height, Corners corners) {
        Objects.requireNonNull(corners, "corners");
        return Path.roundRect(0, 0, width, height, corners);
    }

    /// The largest `grow` whose band is **entirely inside the border box**, and
    /// therefore paints nothing once the hole is cut out of it.
    ///
    /// A band is the border box grown by `grow` on every side and then moved by
    /// the shadow's offset. For it to stay inside the unmoved border box it must
    /// be inset by at least the offset it is about to be moved by — on both
    /// axes, so by the larger of the two:
    ///
    /// ```text
    /// grow <= -max(|offsetX|, |offsetY|)
    /// ```
    ///
    /// The corners come out right for free: a band inset by `-grow` has its
    /// radii shrunk by the same amount, which is a rounded rectangle concentric
    /// with the box and inside it.
    ///
    /// `grow` only decreases across [ShadowRamp#bands], so a band that fails
    /// this test is followed only by bands that also fail it — the painter stops
    /// rather than filtering.
    ///
    /// **This is not an optimisation that assumes an opaque background.** Before
    /// ADR-0427 it was exactly that, and it was wrong under a translucent box in
    /// the same way the missing hole was wrong. Now the band really does paint
    /// nothing, whatever is drawn over it.
    public static double coveredAt(Shadow shadow) {
        Objects.requireNonNull(shadow, "shadow");
        // `0.0 - x` rather than `-x`, so an unoffset shadow answers positive
        // zero. Negative zero compares `<=` identically and prints identically,
        // and is a difference only a test's `assertEquals` can see -- which is
        // exactly the kind of thing to remove rather than to work around.
        return 0.0 - Math.max(Math.abs(shadow.offsetX()), Math.abs(shadow.offsetY()));
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
