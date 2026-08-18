# ADR-0114: A clip is a rectangle the painter carries

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §8 (`overflow` in the layout subset), §11
  ("hit-testing … respects clips and transforms"), `docs/core-widgets.md` §1's
  `scroll`; follows the reasoning of
  [ADR-0068](0068-the-transform-stack-is-java-side.md)

## Context

`scroll` is the single missing widget behind three separate pieces of work — a
gallery screen taller than its window, a tab strip with more tabs than fit, and a
`select` over a realistic option list — and every one of them is blocked on the
same thing underneath: **nothing clips.**

The absence had already been reported three times from three different
directions. An indeterminate progress bar reverses at the ends rather than
sweeping off one and back in at the other, because the sweep needs
`overflow: hidden`. A segment's label overflows its cell when it is longer than
1/n of the bar, because nothing clips. A window narrower than its content
overflows silently, because `flex-shrink: 0` stops a control being squashed and
does not stop it being cut off.

`overflow` is not a new property. It has been in §8's layout subset since the
subset was written, and Yoga's binding for it — `Overflow`, `YogaNode.setOverflow`
— has been sitting in `:natives` unused. What was missing is the other half:
Yoga decides *sizing* from `overflow` and clips nothing, and there was no clip
anywhere above it.

### What the native side offers, and what it does not

Blend2D exports `bl_context_clip_to_rect_d` and `bl_context_restore_clipping`,
both already on the export list — they are how a partial repaint confines itself
to the damage rectangle ([ADR-0072](0072-a-partial-repaint-needs-a-promise.md)).
Two properties of that pair decide this record:

- `clipTo` **intersects** with the clip in force. It can narrow and cannot widen.
- `restoreClipping` goes back to **the whole surface** — not to whatever clip was
  in force before.

`bl_context_save` / `bl_context_restore` are *not* on the export list, so there is
no clip stack down there to push onto. A nested scroll view therefore cannot be
expressed as "clip, recurse, unclip": the inner viewport ending would take the
outer one's clip off with it, and the rest of the outer scroller would paint over
everything beside it. Worse, the damage clip is the outermost one — so an unclip
anywhere in the tree would silently widen a partial repaint into a full-frame
scribble, which is the bug that only shows up on the frames nobody is looking at.

## Decision

### The clip stack lives in Java, and every change assigns the whole rectangle

`Clip` is a rectangle in the frame's logical coordinates, held as four edges
because every operation on it is an intersection. The painter accumulates it on
the way down the tree, and the painter's `Painting` state tracks what the context
is currently set to — exactly as it already tracks the current transform matrix.

Each change is `resetClip()` followed by one `clipTo` of the **accumulated**
rectangle. Never a bare `clipTo` to narrow and never a bare `resetClip` to widen,
because neither of those composes.

This is [ADR-0068](0068-the-transform-stack-is-java-side.md)'s conclusion reached
from the other end. There, the stack was kept in Java so hit testing could invert
the matrix it painted with. Here it is kept in Java because the native side offers
no way to undo one clip without undoing all of them. The two now travel together
down the same walk, which is the argument for them having the same shape.

`Clip.NONE` is infinite rather than "the frame". An intersection with an infinite
rectangle is the identity, so a tree with no clipping box in it needs no null
checks and changes no context state at all.

### The base of the stack is the damage, not "no clip"

`RenderTree.paint(frame, damage)` computes the damage rectangle and passes it in
as the base. Every restore goes back to *that* rather than to the whole surface,
so a scroll view inside a damaged region narrows the damage clip and widens back
to it — never past it.

### The clip is the padding box, and it applies to the children

A box is painted under its **parent's** clip; its children are painted under its
own. That is what CSS means by `overflow: hidden` — it clips the content of a box,
not the box — so a viewport still paints its own background, its own border and
its own focus ring, and only what is inside it is cut off. The rectangle is the
padding box rather than the border box, which is CSS's rule and is what keeps a
viewport's 1px edge crisp while rows slide past underneath it.

### A transform maps the clip; a rotation bounds it

The clip rectangle is mapped through the accumulated matrix before it is
intersected, so a translated viewport clips where it is drawn rather than where it
was laid out. The four corners are mapped and their bounding box taken: **exact
for a translation or a scale, conservative for a rotation.** A viewport rotated
45° clips to the square around its diamond and lets a little content show at the
corners.

That is the honest cost of a rectangular clip. Blend2D's is a rectangle; the
alternative is clipping to a path, which is a different native call with a
different cost, and nothing in the catalog has asked for a rotated scroll view.

### An empty clip stops the walk

A subtree scrolled entirely out of its viewport cannot produce a pixel, so the
recursion stops rather than visiting every box for Blend2D to clip away. This is
the one place a clip saves the *traversal* as well as the rasterization — ADR-0072
noted that the damage clip does not, because the damaged node is somewhere inside
a tree that still has to be walked to find it, and a scrolled-away subtree is
different: it is contiguous, and it is known to be entirely outside.

### The clip reaches hit testing, in the frame's coordinates

`HitTest.Region` carries the clip and tests it **first**, before the transform
inverse and before the rectangle.

This is not an optimization; it is the only correct answer. A row scrolled out of
its viewport is still laid out exactly where it always was — Yoga never saw the
scroll, which is a transform on the content — so its own rectangle happily
contains a pointer that is nowhere near it on screen. Without the clip, a scroll
view would be a list where invisible rows above the viewport still take clicks
meant for the visible ones.

`ARCHITECTURE` §11 has promised since it was written that hit-testing "respects
clips and transforms". The transform half was true. This half had nothing to be
true about until now.

The two walks — the painter's and `forEachPlacedBox`'s — share one `clipFor`
method rather than each deriving the rectangle. Two walks that have to agree
exactly is how a pointer starts landing where the ink is not, and that failure is
silent: the control looks right and simply does not respond where it looks like it
should. The same argument `HitTest` already makes about the transform's inverse.

### `auto` resolves to `scroll`

CSS has four keywords and Yoga has three. `auto` sizes exactly as `scroll` does;
the difference in CSS is whether bars appear only when they are needed. The design
system routes that question elsewhere — §2.4 makes overlay auto-hiding bars the
default for *both*, and gives the always-visible gutter to an application setting
rather than to a keyword — so `auto` maps onto `Overflow.SCROLL` and nothing
downstream carries a distinction no rule in the canon can act on.

### A promoted layer is clipped at the blit

A layer is rasterized whole, in its own coordinates, and composited back with one
blit. The clip in force outside is applied to **the blit**, not inside the raster:
so the clip is set before the promoted branch is taken, not after it. A clip
*within* the promoted subtree still applies, and lands in the layer's own
coordinates for free, because every rectangle below is derived from the shifted
origin the layer walk is given.

## Consequences

**A clipping box costs two native calls, and a transform reset.** Blend2D applies
a clip in the context's current user space, so the transform goes to identity
before the clip is assigned — a clip set while a translated subtree's matrix was
in force would land at the translation twice over. The matrix the next box needs
is re-assigned on its way through, which costs nothing it was not already paying.
Only boxes that actually clip pay any of this.

**A scroll view's content must not shrink.** Yoga's default `flex-shrink: 1`
squashes a 200-tall child into a 50-tall parent, so a viewport built without
`flex-shrink: 0` on its content lays out perfectly, clips nothing, and has nothing
to scroll. This was found by writing the test rather than by reasoning about it,
and it is the same trap ADR-0076 documented from the other direction: there,
shrink made every fixed metric in §3 negotiable; here, its absence is what creates
the overflow in the first place.

**A rotated viewport clips loosely.** Documented above and asserted in the tests,
so the day someone needs it exact the test fails rather than the picture being
subtly wrong.

**Nothing else in the catalog clips yet.** The three faults that reported this
absence — the progress sweep, the segment label, the silently overflowing window —
are all now *fixable* and none is fixed here. Each is a change to that widget's
stylesheet or its box, and each belongs with the widget rather than with this.

**`overflow` is not inherited and not animatable**, which is CSS's rule for it.
Nothing had to be done to achieve that; it is recorded because the transition
whitelist is a closed set and this is not on it.
