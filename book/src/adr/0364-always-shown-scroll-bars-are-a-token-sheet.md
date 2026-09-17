# 364. Always-shown scroll bars are a token sheet

Date: 2026-09-17

## Status

Accepted. Closes both of `book/src/TODO.md`'s gutter entries: "The 'always show
scroll bars' reserved gutter is not built" and "... has nothing to switch it".

## Context

`docs/design-system.md` §2.4: "'Always show scroll bars' app/user setting swaps
to a classic reserved 12px gutter — components must survive the gutter appearing
(layout, not overlay)", and §4 lists it among the accessibility switches. The
overlay bar was built; the gutter was not, and the TODO said it waited on "a
settings mechanism that does not exist for reduced motion or density either".

Density did not wait for one. It is a token stylesheet in the THEME layer that an
application passes to `Controls.stylesheets` from its own preferences (ADR-0074).
The same shape serves here.

## Decision

**`Scrollbars.ALWAYS` is a token stylesheet. The overlay bar's metrics become
tokens, and a viewport that finds a non-zero `--gb-scrollbar-gutter` pads its
content by it and stops fading its bars.**

- `controls.css` declares `--gb-scrollbar-gutter: 0px`, `--gb-scrollbar-size:
  10px`, `--gb-scrollbar-track: transparent`, `--gb-scroll-thumb-size: 6px` and
  `--gb-scroll-thumb-hover-size: 10px`, which are the numbers the rules held, so
  every existing golden is unchanged. The `scrollbar` and `scroll-thumb` rules
  read them.
- `scrollbars-always.css` sets a 12px gutter, a 12px bar, the `--gb-surface-2`
  track, and an 8px thumb that does not widen.
- `Scrollbars { OVERLAY, ALWAYS }` in `:widgets`, with `stylesheets()` and
  `source()` shaped like `Density`'s, and
  `Controls.stylesheets(theme, density, scrollbars)`.
- `ScrollViewport` reads the gutter token in `render` and banks it in
  `ScrollState`, as it already banks `--gb-scroll-line`. `ScrollContent` adds the
  gutter to its padding on the bar's side (right for a vertical axis, bottom for
  a horizontal one), so the content is narrower rather than under the bar. A
  viewport with a gutter draws its bars at full opacity, because §2.4's classic
  bar does not fade.

## Consequences

- The bar keeps its absolute position. The gutter is made by the content's
  padding, so the overflow arithmetic, the thumb and the wheel are unchanged.
- The gutter arrives one frame after the stylesheet changes, like the line
  token, because `children()` has no context to read it from.
- `text-area`'s bar reads the same tokens, but a text area reserves no gutter:
  its text width is its own arithmetic (ADR-0331), and a change there is a change
  to wrapping.
- The showcase's File menu has "Always show scroll bars".
