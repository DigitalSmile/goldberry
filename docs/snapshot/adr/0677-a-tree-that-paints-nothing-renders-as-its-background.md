# ADR-0677: A tree that paints nothing renders as its background

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0227 (a widget may describe nothing), which it amends

## Context

ADR-0227 let a widget build to `Widget.nothing()` and kept one refusal: a tree
whose *root* describes nothing, or only composition, threw
`IllegalStateException` ("nothing in this widget tree paints"), on the grounds
that a window with nothing to paint is a mistake.

A downstream game showed the other side. An overlay that is often empty (an
event dialog with no event open, a result panel) is a tree of its own, and its
"shows nothing" test could not render it on its own: it needed a backdrop the
game does not have. The same holds for a window whose root is an overlay host
with nothing open yet.

## Decision

**An empty tree renders as one empty box**, in a window and offscreen alike:
the frame is its background and nothing else. `WidgetRenderer.render` returns
`Box.of()` where it threw. `Offscreen.render`, strips and sessions inherit it.

## Consequences

- ADR-0227's last consequence is reversed: `NothingTest.rootIsRefused` became
  `rootRendersEmpty`.
- A tree that is empty by mistake shows a blank window rather than an
  exception. That was the price ADR-0227 would not pay; the owner chose it on
  2026-11-08, because an empty overlay is an ordinary state and not a mistake.
- Tested by `NothingTest.rootRendersEmpty` and `OffscreenTest.rendersNothing`
  (a render and a strip of `Widget.nothing()` are the background).
