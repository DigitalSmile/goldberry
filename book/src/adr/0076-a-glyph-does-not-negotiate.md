# ADR-0076: A glyph does not negotiate

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/ARCHITECTURE.md` §8; `docs/design-system.md` §1.3, §3;
  fixes what [ADR-0075](0075-a-gestures-origin-is-the-routers.md),
  [ADR-0073](0073-a-composite-is-one-tab-stop.md) and
  [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md) each shipped
  without

## Context

Reported as *"the knob is outside the pill when I resize the window"*, and it was
not a toggle bug. Narrowing the window shrinks the **pill**, and the thumb inside
it does not shrink by the same amount, so the disc hangs over the end.

The cause is one line nobody wrote. `YogaConfig.create()` asks for CSS's defaults
(`useWebDefaults`), and CSS's default is **`flex-shrink: 1`** — so every node in
the toolkit gives up width when its row runs out of room. A `width: 36px` was
never a width; it was a *preferred* width that a cramped parent could take back.

`docs/ARCHITECTURE.md` §8 lists `flex-grow/shrink/basis` in the layout subset.
Only `flex-grow` was implemented. `flex-shrink` was in the specification, absent
from the engine, and its **default was the wrong one for every fixed-size thing
in the catalog** — so there was no way to say so and nothing had noticed.

Measured at 40px of room, which is absurd and is the point — a bug that needs the
window dragged to exactly the wrong size is a bug that reaches a user and not CI:

| what | specified | at 40px |
|---|---|---|
| `toggle-track` | 36 | **16** |
| `check-indicator` | 16 | **10** |
| `radio-indicator` | 16 | **10** (an ellipse — `border-radius` follows the box) |
| control height, in a short column | 32 | **13** |

The reported symptom was the only one of the four that is obvious at a glance.
The last row is the worst: §1.3's "hit targets ≥ 32×32" quietly became 13.

## Decision

### `flex-shrink` is implemented, because §8 already said it was

`ComputedStyle.flexShrink`, `Box.flexShrink`, and one guarded setter in
`RenderObject`, exactly as `flex-grow` is plumbed. **No native symbol was
added and no binding was written**: `YGNodeStyleSetFlexShrink` is already on the
export list and `YogaNode.setFlexShrink` already existed — this was a gap in the
CSS engine alone, not at the boundary. So it costs nothing at the layer where a
change is expensive, and no CI run across four targets is needed to believe it.

The default is **1**, in `ComputedStyle.INITIAL` and in `Box.of()`, because that
is CSS's default and Yoga's under `useWebDefaults`. A box built by anything that
predates this field therefore behaves exactly as it did.

### The controls declare `flex-shrink: 0`, once, over a type list

```css
button, checkbox, radio, toggle,
check-indicator, check-mark, radio-indicator, radio-dot,
toggle-track, toggle-thumb { flex-shrink: 0 }
```

Written once rather than beside each `width`, for the reason the focus ring is:
the rule is *"a control's metrics are fixed"*, and a copy per control is how that
stops being true. A control added to the catalog joins this list, and one that
forgets to now fails `ControlShrinkTest` rather than shipping a glyph that
squashes.

**The label is deliberately absent and still shrinks.** Text is the one thing in
a control that *should* give: a `text` that refused would push the glyph out of
the window rather than ellipsing, which is worse than the bug being fixed. §3
sizes the glyph and says nothing about the label, and that asymmetry is the
correct reading of it.

### Why not turn `useWebDefaults` off

`flex-shrink: 0` everywhere by default is Yoga's own convention, and it would
have fixed this in one line. It is refused for the reason ADR-0013-era decisions
keep landing on: **§8 promises a CSS subset**, and a stylesheet whose `flex`
behaviour silently differs from every author's expectation is a worse trap than
the one being fixed — it would move the surprise from "my glyph squashed" to
"my flexible row does not flex", which is harder to see and impossible to look
up. The defaults stay CSS's; the controls say what they mean.

### The golden scenes were sized by the bug

Six radio images moved, and the reason is worth recording: their frames were
300×**132** for content that needs 136 — three options at 32, two 8px gaps, 12px
of padding each side. They fitted only because the options were being squashed.

They are 300×140 now. A frame 4px too short used to quietly shrink all three
options; it now clips the last one, which is the visible failure that scene
should always have had. The comment on `paint` says what the arithmetic is, since
those numbers are load-bearing rather than round.

## Consequences

- Every fixed metric in §3 is now actually fixed, and §1.3's 32×32 hit target
  holds at any window size. Four defects closed by one property, only one of
  which had been reported.
- **`ControlShrinkTest` runs over the whole catalog**, not over the control that
  was reported. The reported symptom was `toggle`'s, and three of the four
  failures were in `checkbox` and `radio` — a test written about the switch would
  have fixed the switch and left the rest.
- The test frames are **deliberately absurd** — 40px for a row that wants ~200.
  A regression here is a function of window size, and a test at a plausible size
  is the one that cannot fail.
- **`flex-shrink` is now available to applications**, which §8 had promised and
  the engine had not delivered. `flex-basis` remains unimplemented and is the
  last of that trio; nothing needs it yet.
- **Open: nothing else in the toolkit declares it.** `:core`'s five primitives —
  `row`, `column`, `text`, `panel`, `spacer` — all still shrink, which is right
  for containers and unexamined for `spacer`. A `spacer` with a fixed size is
  presumably meant to keep it.
- **Open: no minimum size anywhere.** `flex-shrink: 0` stops a control being
  squashed and does not stop it being *clipped* — at 40px of room the switch now
  overflows its parent rather than deforming, which is CSS's behaviour and is
  what a scroll view or an ellipsis is for. Neither exists yet, so a window
  narrower than its content overflows silently. That is M3's problem and is
  named here because this change is what makes it visible.
