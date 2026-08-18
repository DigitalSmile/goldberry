# ADR-0111: A text box is painted inside its padding

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §8, `docs/core-widgets.md` §7,
  corrects [ADR-0036](0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)'s
  painting site, follows
  [ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)

## Context

Five things were reported against one window, and they turned out to be five
different faults rather than one:

1. Warning spam: `dropping "transition": color 100ms ease-out`, and
   `ignoring unsupported property "align-self"`.
2. A menu popup with **black corners** where its radius cut them.
3. A tooltip whose text was **not vertically aligned**, and which "looks poor".
4. The cursor changing from a hand to an arrow **the moment a tooltip appeared**.
5. The `+` in a tab strip **not aligned** with the tabs beside it.

## Decision

### 1. The vocabulary was wrong, not the engine

`ease-out` is not one of §1.7's easings — they are `ease-enter` and `ease-exit`,
named for what they do rather than for a curve — and `background` is not a
transitionable property, `background-color` is. Two rules in `controls.css` got
both wrong and the engine said so, once per node per frame.

`align-self` is genuinely **not in §8's subset**, and adding it is a 20-component
record change to `ComputedStyle` and `Box` for one `+`. The row says
`align-items: center` instead, which every header wanted anyway. The gap is
recorded rather than filled.

### 2. A popup is transparent, and its corners are nothing

A popup's panel has a radius; what is outside it is *nothing*, and nothing was
being presented as an opaque buffer that had never been cleared — black.

Popups are created with `SDL_WINDOW_TRANSPARENT` and their frame is cleared to
`0x00000000` before painting. The surface format was checked rather than assumed:
X11 hands back `SDL_PIXELFORMAT_ARGB8888` for these windows, so the alpha
survives the blit. It also fixes a second thing nobody had noticed: a popup that
shrinks no longer leaves the previous frame in the margin.

### 3. A text box was painted outside its padding

The real defect, and the oldest. `BoxPainter` drew a paragraph at the box's own
origin and wrapped it at the box's full width:

```java
box.text().paragraph().paint(frame, x, y, width, box.text().argb());
```

Yoga sizes a measured leaf as its measured content **plus** its padding. So a box
with `padding: 6px 10px` around text is 20px wider and 12px taller than its text —
and every one of those pixels ended up on the right and the bottom, with the text
hanging off the top-left corner. Magnified 3×, the tooltip's glyphs were visibly
outside their own plate.

Nothing had hit it before because **every other widget in the catalog puts text in
a child box rather than on its own box** — `button`, `option`, `badge` and the
rest, for the reason `Option` gives: a box with text is a measured leaf, and Yoga
never lays out a measured node's children, so a control that held its own text
could not also hold an icon. `TooltipPanel` is the first widget with padding
*and* text on one node.

The painter resolves the padding and paints inside it. Every other golden image
in the corpus is byte-identical afterwards, which is the evidence that this
touched exactly the case that was broken.

### 4. X11 says the pointer left a window when a window is mapped over it

The cursor changing was not the router: headlessly the same sequence keeps both
the hover and the `pointer` cursor, which is what pinned it on the platform.
Opening a tooltip maps a window near the pointer, X11 reports a leave for the
window underneath, and delivering it clears the hover and the cursor of the very
widget the tooltip is describing.

`Window.InputWatcher` gained `exited()`, and the launcher swallows an exit that
arrives **within 250 ms of opening a tooltip**. Bounded rather than a flag that
waits for the next exit: on a driver that sends no spurious exit at all, a flag
would swallow the user's real one whenever it finally came.

### 5. The `+` sat at the top because nothing centred it

`align-self: center` was ignored (see 1), and `align-items: stretch` leaves a
child with a definite height at the start of the cross axis. The row centres its
children now.

## Alternatives considered

- **Adding `align-self` to §8's subset.** A legitimate flexbox property and a
  real gap — and a 20-component record change in two records where every wither
  must be revisited, which is exactly the change that silently dropped a
  component two records ago ([ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)).
  Not for centring one `+`.
- **Giving `TooltipPanel` a child text box** instead of fixing the painter. It is
  the pattern every other widget uses and would have worked — and would have left
  a painter that silently mis-draws any text box an application puts padding on.
- **Filling a popup's frame with its panel's colour** rather than making the
  window transparent. Always works, on any driver, and gives square corners: the
  radius would be drawn and then filled in behind. Kept in reserve for a platform
  where transparency is refused.
- **Swallowing every exit while a tooltip is open.** Simpler, and it strands the
  hover when the pointer genuinely leaves — the tooltip would stay up until
  something else moved.

## Consequences

- **`padding` works on a text box now**, which is a thing an application's
  stylesheet could always write and never got.
- **A popup needs a compositor for its corners.** Without one the flag is ignored
  and the corners are whatever the platform puts there — no worse than before,
  and not verified on Windows or macOS.
- **The 250 ms window is a heuristic**, and the honest kind: it is bounded, it is
  named, and it is wrong only if a driver reports a real exit within a quarter of
  a second of a tooltip opening — which is a pointer that left while resting still
  enough to summon one.
- **`tooltip` has golden images** — three, plus one magnified 3× — where it had
  none. "Looks poor" was a claim about twenty-two pixels, and the magnification is
  what turned it into a bug report.
