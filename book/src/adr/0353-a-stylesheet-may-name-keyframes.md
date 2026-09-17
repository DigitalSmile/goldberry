# 353. A stylesheet may name keyframes

Date: 2026-09-17

## Status

Accepted. Reverses one sentence of
[ADR-0081](0081-a-perpetual-loop-has-no-state.md) ("§8's CSS subset has no
`@keyframes` and is not going to grow one") and keeps the rest of it. Extends
[ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md).

## Context

ADR-0081 turned down `@keyframes` for `progress` and `spinner`, and its argument
was about those two. A perpetual loop is a function of the clock. Stored state
per element leaves two spinners permanently out of phase, and `phaseAt(now)`
cannot be. That argument still holds, and both controls keep their functions.

What it did not cover is motion that is **authored**: a sequence of more than two
states, a stagger, or a choreography an application's designer wrote down in CSS
terms. Without keyframes, the only way to write one was a widget of its own or a
`canvas`. That means Java for what is a stylesheet's business, and every such
widget had to reimplement easing, direction, fill and reduced motion.

## Decision

**`@keyframes name { from/to/percentages { declarations } }` and `animation` with
its seven longhands are in the subset. A keyframe animation is a second layer of
the per-node overlay, beneath transitions, and it is subject to the same
whitelist.**

- **Parser.** A `Keyframes` block is a name and frames sorted by offset. `from, 50%`
  becomes two frames. An offset outside 0–100%, a word that is not `from` or `to`,
  `none` as a name, and `!important` inside a keyframe are all refused.
  `Stylesheet` gains a `keyframes` list, and its two-component constructor stays.
- **Cascade.** A later block with the same name replaces an earlier one whole,
  across layers too, which is CSS's rule. Keyframes do not merge.
  `resolveKeyframe(element, frame)` substitutes `var()` for the element the
  animation runs on, so a keyframe of `var(--gb-chart-1)` follows the theme.
- **`ComputedStyle.animations`** is `KeyframeAnimations`: seven lists, kept apart
  until `entries()` combines them the way CSS does, with names deciding the count
  and shorter lists repeating. That is what lets a later rule change only
  `animation-delay`, which is how a stagger is written. The shorthand takes its
  parts in any order, with the first time as the duration and the second as the
  delay. The curves are §1.7's three keywords, with `ease-enter` as the default,
  which is `transition`'s default. A bad value drops the declaration and names it.
- **Timing.** `KeyframeTrack.progress` implements CSS's model: a delay, which may
  be negative and is filled only under `backwards` or `both`; iterations, each
  played backwards per `animation-direction`; and an end that is held only under
  `forwards` or `both`. The easing applies **between each pair of keyframes**.
- **Start.** An animation starts on the frame its name first appears in the
  element's style, and keeps that start for as long as the name stays. This is the
  stored state ADR-0081 avoided for loops, and it is right here: an animation that
  arrives with an element has to start when the element does. Anything that must
  be phase-locked across elements stays a function of the clock.
- **Values.** A keyframe's declarations are applied **on top of** the element's
  resolved style (`ComputedStyle.applied`), and only the declared properties are
  read back. The implicit 0% and 100% are the element's own values. A property
  outside `Transitions.Animatable` is dropped with one warning per block and
  property. `Animatables` holds the read, write, compare and interpolate switches
  for both layers, so a colour moves through OKLCH in both.
- **Layering.** Keyframes are applied first and in-flight transitions over them,
  which is CSS's order.
- **Frames.** `Animations.settle` and `isAnimating` include keyframe animations
  that are waiting or running, and exclude one that has ended and is only holding
  its last frame. So a `forwards` animation keeps its value on a loop that has gone
  idle.
- **Reduced motion drops every keyframe animation.** A transition has an end state
  the cascade already resolved, so reducing it means snapping to that state. A loop
  never arrives, and a settle's last keyframe need not be the element's style, so
  the honest reduction is none at all. `KeyframeAnimations.reduced()` is that.
- **Unknown names** draw the element as it is and are logged once per renderer.

## Consequences

- §1.7's rules 4 and 5 ("nothing loops", "motion is meaning") still bind the
  toolkit's own stylesheets. The mechanism is now available to an application, and
  `ToolkitLoopsTest` checks that no toolkit sheet declares an infinite animation.
- `progress`, `spinner` and `skeleton` keep ADR-0081's clock functions.
- The showcase's Motion screen breathes five swatches on a stagger, turns a mark,
  and cycles a plate through three tokens, all in `showcase.css` (ADR-0354).
- `@keyframes` inside `@media`, `animation-play-state` and `animation-composition`
  are not in the subset.
