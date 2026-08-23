# 193. A canvas is a second clip depth

Date: 2026-08-23

## Status

Accepted. The first step of `canvas` — `docs/core-widgets.md` §1's immediate-mode
painting surface, which charts, meters and every application's own drawing sit on.

## Context

M3 owes charts, and charts are not where charts start. `content-widgets.md` §3
builds them on the `canvas` primitive so they inherit the theme, the text stack,
hit testing and the golden corpus — and **`canvas` is not built**. It is already
blocking something shipped: `statistic`'s sparkline is specified and absent for
want of it (ADR-0164). So the order is `canvas`, then the chart substrate, then
the five widgets.

A canvas is unlike every other widget in one respect that turns out to decide its
implementation. Every painter inside the toolkit knows what it set on the context
and unsets it: `paintOne` clips to a box, draws, and restores; the damage path
clips to the changed region and restores. An application's `onPaint` is **not one
of those**. It runs inside whatever clip and transform the tree already
established — a canvas inside a `scroll` is inside the viewport's clip — and it
may set a clip, a transform, a style or a global alpha of its own and leave any of
them behind.

`Frame.resetClip()` cannot undo that. It maps to `bl_context_restore_clipping`,
which goes back to **the whole surface** rather than to the region in force
before. The export list said so in its own comment, and said why it was fine:

> `restore_clipping` rather than a save/restore pair, because there is only ever
> one clip depth here and `bl_context_save` is still not exported.

That was true of the frame path and stops being true the moment a widget hands
the context to somebody else. A canvas in a scroll viewport that reset the clip
would paint over the viewport's edge.

## Decision

### The export list grows a state stack

`bl_context_save` and `bl_context_restore`, taking the list from 203 symbols to
205, with `Frame.save()` and `Frame.restore()` over them. This is the second
widening of the paint surface and the second time
[ADR-0190](0190-a-content-module-brings-its-own-natives.md)'s observation has
paid: the export list is sized for what the toolkit's own painter needs, and
anything that hands the context to code the toolkit did not write needs more of
it. `goldberry-html`'s native `document_container` will need the same stack for
the same reason, one nesting level deeper.

The cookie argument is NULL. It is Blend2D's guard against a mismatched pair, and
the only pair here is the two lines around one call.

### `save`/`restore` is for handing the frame away, and says so

`resetClip` stays and is still what the frame path uses — one depth is all it has
and it is the cheaper call. The distinction is written on both methods rather than
left as a performance note, because the failure it prevents is silent: a canvas
that reset the clip paints correctly in every test that does not put it in a
scroll view.

### What comes next, so this record is not read as the whole of it

`canvas` proper is a **content slot on `Box`** — a painter callback beside `text`,
`icon` and `mark` — invoked by `paintOne` inside a `save`/`restore` pair, with the
frame translated so the painter's origin is the box's content corner and clipped
to it. The widget is `canvas` in `…widgets.core.canvas`, with `invalidate()`
asking for a frame the way every other state change does. None of that is built
yet; this record covers the layer underneath it, which is the part that needed a
symbol.

## Consequences

- **205 exported symbols**, and the comment in `goldberry.symbols` that justified
  their absence is now the comment explaining why both exist.
- **A canvas can be nested.** A canvas inside a scroll inside a dialog restores to
  the dialog's clip and not to the window, which is what makes the primitive
  composable rather than a top-level-only escape hatch.
- **The cost is one save/restore pair per canvas per frame**, and none for any box
  that is not one. A window with no canvas in it makes no extra call.
- **An application's painter can still leave the context wrong for itself.** The
  pair protects the *toolkit* from the painter, not the painter from itself: a
  canvas that sets a clip and draws outside it draws nothing, and that is its own
  bug. What cannot happen any more is that bug escaping into the rest of the
  window.
- **Nothing is drawn yet.** This is a symbol, a wrapper and a test; the widget and
  the charts on top of it are M3's remaining work, in that order.
