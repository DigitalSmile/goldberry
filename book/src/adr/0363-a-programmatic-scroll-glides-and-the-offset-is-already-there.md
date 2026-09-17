# 363. A programmatic scroll glides, and the offset is already there

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A revealed row lands rather than glides".

## Context

`docs/design-system.md` §3.1 gives `scroll` "wheel/drag: direct ·
`scrollIntoView` / programmatic: overlay duration". A reveal jumped: the offset
is `ScrollState`'s, and nothing interpolated it. The TODO entry called it "a
transition on a value the cascade cannot see".

Two constraints shape it:

- **Direct input must not wait.** §1.7's first rule: drags and the wheel track
  the pointer 1:1.
- **Only `render` has a clock.** `ScrollFade` already found this: a wake knows
  something happened and has no time, and `render` has a time and does not know
  what happened.

## Decision

**`ScrollController.scrollBy` and `reveal` move the offset to the target at
once, and the viewport draws the content on its way there for
`--gb-motion-overlay` (240ms) on `ease-enter`.**

- `ScrollGlide` holds where the drawing started, where it ends, and when it
  started. It is stamped in `ScrollViewport.render`, which replaces the content
  box's translation with the in-between one, and `isAnimating()` keeps frames
  coming until it lands.
- **The state's offset is the target.** Clamps, the scrollbar, the wheel and
  any later reveal all measure from where the view is going. A glide started
  during another starts from where the first had got to.
- Wheel, keys, thumb drags and track pages go through `moveTo`, which cancels
  the glide: the pointer takes over from wherever the drawing was.
- Reduced motion jumps, as §1.7's rule 6 says programmatic scroll does.
- **A reveal measures where its rectangle will be.** A `Located` rectangle is
  painted mid-glide while the offset is already at the end, so
  `ScrollController.reveal` first moves the rectangle by the glide's remaining
  travel. Without that, a reveal asked again on the next frame (which is how a
  widget that reveals itself from `located` works) scrolled a second time and
  overshot.

## Consequences

- Hit testing, `Located` and `affix` all see the painted, in-between position
  during a glide, which is where the content is on screen.
- The thumb jumps to the target while the content glides. The bar is built from
  the offset, and the offset is the target.
- `ScrollControllerTest` and the showcase's `ScrollingScreenTest` run on a
  virtual clock and let the glide land before they measure.
