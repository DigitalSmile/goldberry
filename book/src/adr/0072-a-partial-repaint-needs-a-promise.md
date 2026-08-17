# ADR-0072: A partial repaint needs a promise, and a fading group needs three flags

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.7; `docs/ARCHITECTURE.md` §3.1, §5;
  finishes the two things [ADR-0071](0071-a-layer-is-a-subtrees-raster.md) named
  as unfinished; the third and fourth symbols added to the export list

## Context

[ADR-0071](0071-a-layer-is-a-subtrees-raster.md) shipped layers and damage
tracking and ended with two things stated as not done:

> **A fading group still re-rasterizes.** `opacity` lives on the promoted node's
> own box, so changing it counts as a change to that box and invalidates the
> raster — which is precisely the case §1.7 wanted promotion for.

> **The frame is still painted in full.** Damage says what an *upload* has to
> carry, not what the rasterizer may skip.

Both are finished here. They are one record because they share a shape: each is a
case where the machinery was right and one question was being answered by the
wrong thing.

## Decision

### 1. One flag was answering three questions

A promoted node's `opacity` and `transform` are applied to the **blit**, not
drawn into the raster — that is ADR-0071's decision and the reason a layer is
worth having. But `RenderObject` had a single `selfChanged` flag, and the raster's
validity was read from it. So an opacity transition invalidated the raster on
every frame of itself: promotion did exactly the work it exists to avoid.

Three questions, and they genuinely have three answers:

| Question | Flag | Does the node's own `opacity`/`transform` count? |
|---|---|---|
| Does the **screen** look different? (damage) | `selfChanged` | **Yes** |
| Does an **ancestor's** raster need redrawing? | `changed` | **Yes** — an ancestor bakes in this node's finished blit |
| Does **this node's** raster need redrawing? | `contentChanged` | **No** — they are applied to the composite |

The asymmetry in the middle row is the one worth pausing on, and it is why this
could not be fixed by simply dropping `opacity` from the comparison: a
*descendant's* opacity **is** drawn into the raster, because only the promoted
node's own is deferred. There is a test for exactly that.

Measured on the showcase's tree wrapped in a group at 45% — which is `:disabled`
on a real control (§2.1), the thing that actually fades in this toolkit:

| a frame of the fade | median |
|---|---|
| raster rebuilt each frame | 554 µs |
| **raster reused** | **199 µs** |

**2.8×**, with `layersRepainted` reporting 0 of 1. I would not have made the
change without that number, because a layer costs an allocation and a blit and
reusing its raster has to beat re-rasterizing by more than those.

### 2. `layersRepainted()` is exposed, because pixels cannot answer this

A cached raster and a freshly drawn one produce the *same image*. Every
assertion anyone would naturally write — on a golden, on a pixel — passes
whichever happened. That is precisely how the bug above survived a test file
written specifically about layer caching.

So `RenderTree` reports how many promoted layers it rasterized in the last paint,
and the tests assert on that. It is the outcome rather than the flag behind it,
which is what makes it worth being public API rather than a test hook.

### 3. A partial repaint is only correct if the backend promises it

Damage tracking can say precisely which region changed. Repainting only that
region is correct **only if everything outside it is still on the buffer** — and
nothing in the SPI said whether it is. Against a backend that hands over a fresh
or recycled buffer, a partial repaint draws one control on a field of whatever
was there before.

So `BackendWindow.retainsFrameContents()` is a question a backend answers, and it
is **false by default**: a backend that says nothing gets a full repaint, exactly
as every backend did before this existed. `sdl3` answers true, on both branches
of `SDL_GetWindowSurface` — where the platform lends mapped memory it is the
platform's own surface, and where SDL falls back to a heap buffer and copies into
a texture on present ([ADR-0046](0046-what-present-actually-does.md)) that heap
buffer is equally persistent.

`Window` then checks **three** things, because they fail independently:

1. the backend promises it;
2. it is the *same* buffer as last frame — a backend may promise retention and
   still rotate between two, and identity is what catches that;
3. the size is unchanged.

And a fourth case falls out: when the backend lends **nothing**, the buffer is
`Window`'s own — allocated there, reused there, disturbed by nothing between
frames — so it retains by construction whatever the backend says about its own.
That is why the check is not simply a delegation.

### 4. The clip is one rectangle, and that is a choice

Blend2D's clip is a rectangle, so honouring several damage regions separately
would mean one full tree walk per region. The union is used instead. `damage`
already merges overlapping rectangles and gives up past a handful, so in every
case that reaches here the union is close to the rectangles themselves.

| one small box changed, 960×640 | median |
|---|---|
| repaint the whole frame | 367 µs |
| **repaint only the damage** | **117 µs** |

**3.1×** — and worth reading carefully, because the damaged area was 1440 of
614400 pixels, which is 0.23%. The saving is nothing like proportional: the clip
saves *rasterization*, and the tree walk still visits every box and issues every
call for Blend2D to clip away cheaply. Skipping the traversal too means testing
each box against the damage, which is a further change and is not made here.

## Consequences

- **Two more exports**: `bl_context_clip_to_rect_d` and
  `bl_context_restore_clipping`. `restore_clipping` rather than a save/restore
  pair, because there is only one clip depth in this frame path and
  `bl_context_save` is still not exported.
- **`RenderTree.paint(frame, damage)`** clips and paints; empty damage draws
  **nothing at all**, which is the best case rather than a degenerate one — a
  window sitting still costs no rasterization. A test paints a *different* tree
  under empty damage and asserts the old pixels are untouched.
- **A clipped repaint is asserted pixel-identical to a full one**, every pixel of
  a 200×200 frame. That is the invariant the whole second half rests on: if a
  clipped frame differs anywhere, damage is not an optimisation, it is a
  rendering bug with a performance excuse.
- **The application chooses.** `window.canRepaintPartially()` is read inside the
  paint callback and the caller picks `paint(frame, damage)` or `paint(frame)`.
  Deciding inside `Window` would mean `Window` knowing what a `RenderTree` is,
  and the two are deliberately independent — `BoxPainter.paint` still works with
  neither.
- **The traversal is still full.** Above.
- **`canRepaintPartially` is false on the first frame of every window and after
  every resize**, which is correct and is also the path that gets exercised least
  — the tests cover both explicitly for that reason.
- **None of it has run off `linux-x64`.** Four symbols now, across the ELF version
  script, the MSVC `.def` and the Mach-O `-exported_symbols_list`. This is the
  class of change the export list has caught three times, and it is still the next
  CI run that answers it rather than any argument here.
