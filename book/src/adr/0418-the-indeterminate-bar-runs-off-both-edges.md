# 418. The indeterminate bar runs off both edges

Date: 2026-09-19

## Status

Accepted. Takes the decision [ADR-0235](0235-a-cut-label-needs-nowrap-not-text-overflow.md)
left open and closes the last item on its list, the label half having been built
by [ADR-0255](0255-a-label-that-must-not-wrap-says-so.md). Spends a mechanism
[ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md) shipped eleven
months ago.

## Context

`progress`'s indeterminate sweep travelled **there and back inside its track**.
The code said why:

> It travels there and back within the track, rather than off one end and in at
> the other. The off-the-edges version is the more common drawing and it depends
> on clipping: a bar that ran past its track would otherwise be drawn across
> whatever is beside it, and the wrap from one end to the other — which clipping
> is what hides — would be a visible jump once a loop.

That reasoning is sound and its premise stopped being true with ADR-0114.
ADR-0235 caught four comments claiming the toolkit could not clip, corrected them,
and was explicit that this one was now a **choice**: "clipping exists, so that
drawing is now available and changing a shipped animation is a design decision
rather than a bug fix". It then declined to take the decision, which was right —
that record was about comments — and left it as the only thing on its list.

So the question is not *can we* but *should we*, and it has an answer. The
there-and-back sweep says something the work has not earned: that there is a far
end to turn at. An indeterminate bar exists precisely because nothing knows where
the far end is. A bar that reverses reads as a scan of a bounded thing — a
cylon eye, a seek — where a bar that leaves reads as "more is coming". Every other
toolkit ships the second, and not out of habit: the drawing is the claim.

The there-and-back version also has a tell that nobody notices until it is named.
At the turn the bar is momentarily *stationary* — its velocity passes through
zero — so a control whose entire job is to say "something is happening" holds
perfectly still twice a second. The linear-each-way easing makes it a sharp
reversal rather than a hesitation, which is the best that shape can do, and it is
still two dead points per loop.

## Decision

**The bar crosses the track and leaves, and `progress` clips it.**

The travel is now a straight line in one direction. The bar's leading edge runs
from `-SWEEP_WIDTH` to `1` in track fractions: it begins one whole bar to the left
of the groove and ends with its left edge on the groove's right-hand edge. In the
unit `translate` is written in — a percentage of the moving box, which is CSS's
rule and is why `travelAt` became `offsetAt` — that is `-100%` to `333%`, a travel
of `433%` of the bar's own width.

```java
static double offsetAt(double phase) {
    return (phase * (1 + SWEEP_WIDTH) - SWEEP_WIDTH) / SWEEP_WIDTH * 100;
}
```

**The clip is `overflow: hidden` on `progress` in `controls.css`, not a flag set
on the box in Java.** `overflow` has been a cascaded property since ADR-0114 and
every other number in that rule — the 4px track, the 2px radius, the 100% width —
is the theme's. A stylesheet that writes `overflow: visible` has asked for the
overhang and should get it. The widget's correctness already depends on that rule
existing: a `progress` with no base stylesheet has no height and no background and
is not a control at all.

It hides two things and needs to hide both. The **overhang**, which would
otherwise paint across whatever is beside the bar; and the **wrap**, which is a
real discontinuity of the entire travel once every 1.2 seconds and is invisible
only because the bar is outside the clip on both sides of it. That second one is
the reason a test asserts the jump *exists* rather than asserting it away.

## Consequences

**Two goldens moved, and only two.** `progress-sweeping` and
`progress-sweeping-end`. `progress-determinate`, `progress-light`,
`progress-reduced`, `spinner-turning` and `spinner-half-turn` are unchanged, which
is the evidence that `overflow: hidden` costs the other drawings nothing: a fill
that is a width has never left its track, and reduced motion returns
`Transform.NONE` before any of this arithmetic runs.

The new pictures are deliberately a **pair that could not exist before**. At
180 ms the bar is cut off by the *leading* edge with its left third outside the
groove; at 1080 ms it is cut off by the *far* edge with its leading tenth outside.
In the old drawing the bar was a free-floating rectangle in one image and a
rectangle flush against the right-hand wall in the other — a picture that would
look entirely plausible with the clip broken. In the new pair the bar touches an
edge and is *truncated* by it in both, which is what makes the images able to fail.

`progress-sweeping-end`'s frame moved from 600 ms to 1080 ms, because 600 ms is
now the midpoint — the least interesting frame in the loop. 1200 ms would be the
true far end and is a golden of an empty groove, which is accurate and worthless.

**The clip is a rectangle where CSS's is a rounded one.** `Clip` holds four edges
and `RenderTree.clipFor` intersects rectangles; it does not follow `border-radius`.
So while the bar passes an end, it paints into the two corner wedges the track's
2px radius rounds off, and the track's cap looks momentarily square. At §3's
metrics each wedge is `(1 - π/4) × 2²` ≈ 0.86 square pixels, it is the fill colour
against the track colour rather than against the window, and it appears for about
7% of the loop at each end. It is named in `ProgressFill` rather than left to be
found. Fixing it properly means a clip that carries corner radii, which is a
change to `Clip`, `RenderTree` and the hit test, and is not worth one square pixel.

**There is now a discontinuity in the animation where there was none.** This is
the cost the old drawing was buying, and it is bought back with a clip rather than
removed. If an application sets `progress { overflow: visible }` it does not get
the old bar — it gets the new one, unclipped, wrapping visibly and painting
outside its control. That is the correct consequence of asking for it, and it is
worth stating because "the widget still works with the rule off" was true before
this record and is not true now.

**`travelAt` is gone and `offsetAt` replaces it.** The old one returned a
fraction of the crossing and left the conversion to the caller, which is why the
translate arithmetic was spread across two places. There is one number and one
method now, and `ProgressTest` asserts it at both limits directly — including
that `phaseAt(1200)` is zero, because 1200 ms is the top of the next loop and a
test that expected `333%` there would be asserting the modulus was broken.

**Nothing on ADR-0235's list is open.** The four false comments were corrected
there, `white-space: nowrap` was built by ADR-0255, and this was the last of it.
The unrelated bug that record filed — a `Box.of()` wrapper defaulting to Yoga's
`column` direction — is untouched and still filed; no clip box was added here.
