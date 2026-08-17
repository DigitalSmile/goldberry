# ADR-0067: Motion is an overlay on a frame clock

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.7, §3.1; `docs/ARCHITECTURE.md` §5,
  §8; extends [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md) and
  [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)

## Context

`controls.css` had said, in a comment, that it could not express a single one of
§1.7's transitions. Everything snapped: a hover, a press, a disable. §1.7 is not
a short section — three duration tokens, two easing keywords, a property
whitelist, an animation overlay, layer promotion, an enter/exit lifecycle,
OKLCH interpolation, reduced motion, and a virtual clock for tests — and it is
the last piece of the design system with nothing behind it.

## Decision

### The overlay is the whole design

§1.7, verbatim: *"Animated values live in a per-node **animation overlay**
applied at paint time, never written back into computed style, so style
recomputation and animation can't fight."*

Every frame the cascade resolves each node's **target** style from the
stylesheets and its current pseudo-classes. `Animations` holds where each moving
property has actually got to; `apply` returns a style with the in-flight values
substituted, and the *target* is what the next frame diffs against and what
children inherit.

Writing the animated value back is the obvious shortcut and it does not work: the
next cascade would see the halfway colour as the node's real one, diff **that**
against the target, and start a second transition from it. The control would
approach its hover colour asymptotically and never arrive. A test asserts that
`apply` does not mutate its argument for exactly this reason.

Retargeting follows: *"retargeting mid-flight starts from the current animated
value — values never jump"*. A pointer leaving a button 50 ms into a 100 ms fade
returns from where the colour **is**, not from the colour it never reached.

### The state lives on the element

For the same reason `setState` and `:hover` do
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)): a
widget is rebuilt constantly and could remember nothing, so a transition held by
one would restart on every rebuild and never finish. Held on the element, it
survives every rebuild that keeps that element and dies with the element —
which is the right lifetime, because an animation that outlived its node would
be animating something nobody can see.

Created lazily. Most nodes never animate, and an `Animations` per element for a
static tree is an allocation for nothing.

### A clock, not a frame counter

§1.7: *"animations are functions of the frame timestamp, not frame counts"*. A
frame-counting animation runs at a different speed on a 144 Hz panel than a
60 Hz one and slows down whenever a frame is late, turning a dropped frame into a
visibly slower transition.

The clock is read **once per frame** and every node animates against that one
value. Two properties §3.1 says "arrive together" — a toggle's thumb and its
track — would otherwise arrive microseconds apart and drift further the longer
they ran.

`Clock.system()` is `nanoTime`, not `currentTimeMillis`: an animation must not
jump because NTP stepped the wall clock.

`Clock.virtual()` is what makes any of this **testable**. A golden image of a
mid-animation frame is impossible against a wall clock — the test would have to
sleep and would then be asserting on whatever the scheduler gave it, which on a
loaded CI runner is a different frame every run. `clock.advance(50)` gives
exactly the frame at 50 ms, on every machine. `button-hover-midway.png` is three
buttons showing the start, the middle and the end of one transition in a single
frame, which is a picture no wall clock can take.

### The whitelist is refused, not ignored

`Transitions.Animatable` is a **closed enum**: `opacity`, `background-color`,
`border-color`, `color`. §1.7 says layout properties never transition, because
animating a width would run Yoga on every frame of every transition — on a CPU
renderer, the difference between a transition and a stutter.

So `transition: width 200ms` is a **dropped declaration with a warning naming
it**, not a rule that silently never fires. The author asked for something the
system deliberately refuses and needs to be told. One bad entry drops the whole
list, for the same reason a bad `padding` shorthand does: half a transition list
is worse than none, because the author sees two of their three properties moving
and has nothing to say which one was refused.

### Asymmetric timings need no new mechanism

§1.7's rule 1 is *"input feedback is instant — press states apply in 0ms, release
fades out in `fast`"*. That is expressible in CSS as written, because the timing
that applies is the one on the style being moved **to**:

```css
button { transition: background-color var(--gb-motion-fast) ease-enter }
button:active { transition: background-color 0ms }
```

Entering `:active` reads the pressed rule's zero duration and snaps; leaving it
reads the resting rule's and eases. It matters more than it looks — a press that
faded in makes every button feel disconnected from the finger that pressed it.

### OKLCH, measured

§1.7 specifies OKLCH for colour interpolation and it is worth the arithmetic.
Nord's danger red and success green:

| Midpoint | Result    | Channel spread |
|----------|-----------|----------------|
| sRGB     | `#b18f7b` | 54             |
| OKLCH    | `#bf9152` | 109            |

sRGB is gamma-encoded and not perceptually uniform, so the mean of two encoded
values is pulled towards grey — the more saturated the ends, the further. A
colour with no chroma has a **powerless hue**: its angle is noise, so a fade to
grey takes its partner's hue rather than sweeping through hues neither end has.
Hue takes the shorter arc.

*(An earlier draft of this record claimed the sRGB midpoint is also "darker than
both". The test written to prove it failed: it is not. What is true is the
chroma loss, and that is what is claimed now.)*

### Reduced motion keeps the declarations

§1.7's rule 6 collapses every transition to 0 ms. `Transitions.reduced()` sets
each duration to zero and **keeps the entries** rather than removing them, so the
machinery still runs and still ends and a reduced-motion user reaches the same
states by the same route rather than taking a different code path through the
toolkit. §4 asks for the same shape from the high-contrast theme — an alias swap,
never a special case.

## Consequences

- Hover, press and disable animate on both controls, to §3.1's table, with the
  durations as `--gb-motion-*` tokens in the theme layer so a slower or
  reduced-motion theme is an alias swap.
- **The frame loop stays idle.** `renderer.isAnimating()` is the whole of §1.7's
  "no polling, no battery cost": an application asks for another frame only while
  something is moving. The showcase does exactly that, and a test asserts the
  loop goes quiet the frame after a transition ends.
- The first frame starts nothing. A control appearing is not a control changing,
  or a window would fade every control in from black when it opened. §1.7's
  enter/exit animations belong to overlays, which announce themselves.
- **`transform` is not implemented, and it is in §1.7's whitelist.** It is what
  `checkbox`'s specified check animation ("scale 0.6→1 + opacity") needs for its
  scale; the opacity half ships and the scale does not. `Box` carries no
  transform, and adding one means the painter **and** hit testing — which needs
  the inverse to map a pointer back through it and silently mis-routes clicks if
  it does not. That is a correctness trap worth arriving on its own rather than
  inside this.
- **Layer promotion is not implemented.** §1.7 promotes a node animating
  `opacity`/`transform` to a repaint-boundary layer so per-frame cost is
  compositing only. There are no layers
  ([ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md) deferred them for group
  opacity, and damage tracking wants the same thing). Today every animating frame
  repaints the window, which at 960×640 is affordable and at 4K will not be. The
  three want one mechanism and should get it together.
- **The enter/exit lifecycle is not implemented.** `opening → open → closing →
  removed`, with input disabled the instant closing starts, applies to menus,
  popovers, tooltips, dialogs and toasts — none of which exist. It is a
  specification for M3 rather than a gap in M2.
- **The explicit `AnimationController` is not implemented.** §1.7 has it driving
  indeterminate progress, the spinner and toast reflow, none of which exist. It
  is the same clock when it arrives.
- **Reduced motion is not detected, only obeyed.** `renderer.reducedMotion(true)`
  is the switch; nothing reads the OS setting, because SDL exposes no query for
  it. An application that knows sets it.
- **Untested claim:** the easing solver and the OKLCH conversions have run only
  on linux-x64. They are pure arithmetic with no native code under them, so
  unlike the rounded corners there is no per-CPU JIT to differ — but
  `button-hover-midway.png` is compared on all three platforms like every other
  golden, which is what would show it.
