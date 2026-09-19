package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// The one number a [Slider] remembers: **how wide its thumb is**.
///
/// ## Why a control whose value belongs to the application has state at all
///
/// ADR-0063's rule is unchanged and this is not an exception to it. What is
/// banked here is not a value — it is a **measurement of the stylesheet**, and
/// the application has no opinion about it. `scroll` reached the same shape for
/// the same reason, one token earlier ([ADR-0251]): `--gb-scroll-line` is
/// resolved at `render`, where the cascade is in hand, and consumed at
/// `onPointer`, where it is not.
///
/// A slider needs the thumb's width for one thing and needs it badly: the thumb's
/// **centre** cannot reach within half a thumb of either end of the groove, so a
/// pointer mapped over the track's full width asks for a value the thumb is up to
/// 8px away from. Mapping over the *travel* is the fix and the travel is
/// `width − thumb` — a number the stylesheet owns and
/// [io.github.digitalsmile.goldberry.widget.style.Paints.Context#length] is the
/// only door to (ADR-0430).
///
/// ## It is a frame late, by construction
///
/// [SliderControl#render] reads the token and calls back into here; the value is
/// used by the *next* pointer event. That is `scroll`'s bargain unchanged and it
/// is sound for the same reason: a paint always precedes an input, so there is no
/// frame in which a finger arrives before the tree it is pointing at has been
/// drawn. The one frame where it is stale is the frame before the first paint,
/// and on that frame there is no layout to map against either.
///
/// **Only when it moved.** `setState` on every frame would be a rebuild on every
/// frame, which is the cost `scroll` names and declines to pay.
final class SliderState extends State<Slider> {

    /// The thumb's width in logical pixels, from `--gb-slider-thumb-size` or its
    /// default — [SliderControl#THUMB] until the first `render` says otherwise.
    private double thumb = SliderControl.THUMB;

    private void sized(double value) {
        if (value != thumb) {
            setState(() -> thumb = value);
        }
    }

    @Override
    public Widget build(BuildContext context) {
        return new SliderControl(widget(), thumb, this::sized);
    }
}
