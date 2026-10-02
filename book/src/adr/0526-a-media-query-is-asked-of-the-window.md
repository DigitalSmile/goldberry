# ADR-0526: A media query is asked of the window

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md),
  [ADR-0322](0322-the-desktop-says-light-or-dark-or-says-nothing.md),
  [ADR-0383](0383-the-desktop-is-asked-whether-to-move-less.md),
  [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/goldberry-gaps.md` #5

## Context

`CssParser` read `@media`, threw the prelude away and kept the rules inside
unconditionally. The comment called that the permissive choice until an
evaluator existed; the class documentation said the condition was kept on the
rules, which it was not. So `@media (prefers-reduced-motion: reduce) { * {
animation: none } }` switched motion off for everybody, a dark-mode block
applied in light mode, and a prototype's breakpoints could not be ported.
Deploy Orc pinned its window's minimum size instead.

The facts a query asks about all exist: the window's logical size on every
frame, the desktop's theme with a change event (ADR-0322), and the renderer's
reduced-motion switch, read from the desktop at start-up (ADR-0383).

## Decision

**Every rule carries a `MediaCondition`, the resolver evaluates it against a
`MediaContext`, and a context change that flips an answer swaps the resolver.**

- **`dev.goldberry.css.media`**, a new package. `MediaCondition` is sealed:
  `Width` and `Height` with a comparison, `Orientation`, `ColorScheme`,
  `ReducedMotion`, `And`, `Or`, `Not`, the two constants `ALWAYS` and `NEVER`,
  and `Unsupported`. `MediaQueries.parse` reads a prelude: `min-`/`max-width`
  and `-height`, the range form `(width >= 600px)`, `orientation`,
  `prefers-color-scheme`, `prefers-reduced-motion`, joined by `and`, `or`,
  `not`, `only` and comma lists, with `all` and `screen` holding and `print`
  not. Lengths are `px`, or `em` and `rem` at CSS's fixed 16px.
- **An unreadable query never holds**: a feature outside the list, a unit it
  does not take, a mistake in the writing. That is CSS's own rule, and the safe
  direction: a block never applied is visibly missing, where one applied
  regardless styles every window for a condition nobody checked. `not` does not
  rescue it. In a comma list the readable queries still count. A strict sheet
  refuses it; a lenient one keeps the block, never applies it, and warns once
  with the sheet and the line (ADR-0529).
- **`StyleRule.media`**, `ALWAYS` outside any block and compared by identity,
  so the cascade's cost for an unconditional rule is one comparison. Nested
  blocks are joined with `And`. `@keyframes` inside `@media` is outside the
  subset, as ADR-0353 said.
- **`MediaContext`** is the window's logical width and height, the desktop's
  theme read as light where it says nothing (CSS's reading of no preference),
  and reduced motion. Before the first frame the size is NaN and no size
  condition holds.
- **`StyleResolver.under(context)`** answers with **this very instance** when
  every condition in the sheets answers the same under the new context, and
  otherwise with a new resolver that shares every index. Identity is what the
  element tree's style cache is keyed on, so a flip invalidates every cached
  style at once, exactly the route a theme swap already takes, and a resize
  that crosses no breakpoint keeps every one.
- **Who moves the context**: `FrameSequence.layOut` hands the renderer the
  frame's logical size before the tree is prepared, so a window and an
  offscreen buffer both restyle on the frame that crossed the breakpoint.
  `WidgetRenderer.reducedMotion` moves it too. The launcher sets the colour
  scheme when it builds a renderer and again when the desktop's theme changes,
  then repaints.

## Consequences

- `@media (prefers-color-scheme: dark)` follows the desktop with no application
  code. ADR-0322 said the toolkit chooses nothing with the theme; it still
  chooses nothing, and a sheet that asks now gets the desktop's answer.
- `prefers-reduced-motion` reads the renderer's switch, so it changes when
  something calls `reducedMotion` on the renderer. The desktop's own setting is
  still read once at start-up, which is ADR-0383's limit.
- Sheets with no `@media`, which is all of the toolkit's, never swap the
  resolver: `under` loops over an empty list and returns `this`.
- `ThemeAudit` and `StyleLint` build their own resolvers and evaluate under the
  unknown context, so a conditional rule is linted as written but not
  cascaded. That is the right answer for a lint and not for an audit of a
  theme that varies by width, which no theme does.
- `hover`, `pointer`, `resolution`, `aspect-ratio` and the viewport units are
  not in the list. Each is a fact some backend could answer; none was asked
  for.
