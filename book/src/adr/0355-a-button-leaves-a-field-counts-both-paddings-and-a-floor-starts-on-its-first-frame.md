# 355. A floating button leaves, a field's room is both paddings, and a floor starts on its first frame

Date: 2026-09-17

## Status

Accepted. Closes the three items ADR-0350, ADR-0352 and ADR-0354 left open.
Changes one consequence of each of the last two.

## Context

The batch that closed `docs/gaps.md` G39 to G43 and built three kinds of motion
left three things written down as not done:

1. **`text-input` computed its room as `width - 2 × left padding`**, the same
   arithmetic that wrapped `text-area` wrong in G43. A field does not wrap, so
   under `padding: 0 16px 0 4px` the error was a caret scrolled into view 12px
   late, with the end of the value under the right padding where the clip cut it.
2. **A floating button had no way out.** §3.1 says "out: reverse, fast", and
   `Overlay.remove()` took the button down on the frame it was called, so it
   vanished.
3. **The Motion screen's floor was blank in any offscreen render**, so the screen
   had no golden. The settle started on the canvas's first *paint*. An offscreen
   render renders twice to measure and paints once, so it photographed the
   settle's first instant, with every tile still invisible above its place.

## Decision

**Each edge of a field's padding comes off once. A floating button leaves by
class and is removed by a timer. A floor starts on the first frame that renders
it.**

- **`TextEditor.laidOut`** takes the left and right padding separately, as
  `AreaEditor` does since ADR-0350. `caretArea` uses both as well.
- **`FloatSlot`** is what `Floated` now puts in the overlay layer: the button,
  and a `Property<Boolean>` the slot is bound to. An overlay's widget is fixed
  once made, so the slot cannot be swapped out, but a bound property can rebuild
  it. `FloatedState.detach()` sets the property, which puts `leaving` on the
  button, and `button.float.leaving` in `controls.css` is the exit: `opacity: 0`
  and `scale(0.9)` on `--gb-motion-fast` with `ease-exit`. §1.7's rule 2 holds,
  because the exit is faster than the 160ms entrance and on the exit curve. The
  overlay is removed by `host.after(--gb-motion-fast)`, read through
  `BuildContext.duration`. The state lets go at once, so a rebuild that attaches
  a new button while the old one leaves shows the two crossing, as a toast queue
  does.
- **A press on a leaving button does nothing.** §1.7 says input is disabled the
  instant closing starts. The press asks its *own* slot's switch rather than
  the state's current slot, which by then may belong to the next button.
- **`TileFloor.at(now)`** starts a settle or a fade that is waiting for a frame
  time. The canvas's `animating` predicate calls it, and the renderer asks that
  predicate on every render, painted or not. `paint` still calls it too, so a
  painter used without the predicate behaves as before.
- **`gallery-motion`** is a golden of the screen at the offscreen renderer's
  200ms: the floor part way through its ripple, the swatches part way through a
  breath, the mark part way round. All three are functions of a virtual clock.

## Consequences

- ADR-0352's "the way out is not built" and ADR-0354's "the screen has no golden"
  are no longer true, and both records say so in their status.
- A way out for overlays in general is still not built. Toasts have their own
  `Phase`, and a hud or a tour goes when removed. `FloatSlot` is the pattern
  another overlay would follow, and nothing else has asked for one.
- The box tree under a floating button gains a composition node. The
  Basic-screen golden is unchanged, because a stateless node draws nothing.
