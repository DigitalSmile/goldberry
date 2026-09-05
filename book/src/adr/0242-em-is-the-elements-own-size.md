# 242. `em` is the element's own size

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md).

## Context

The entry is short and its last clause is why it stayed open:

> `em` and `rem` do not resolve against the node's own `font-size`. They use
> `CssLength.Context`'s fixed numbers, so `font-size: 1.2em` means 1.2 × 16 and
> not 1.2 × the parent's size. Nothing in the toolkit's own stylesheets uses
> `em`, so it has no effect today — but it is wrong, and the typography scale is
> what makes it reachable.

`CssLength.Context` was always the right shape — `(fontSize, rootFontSize)`, with
`em` and `rem` reading one each. What was missing is that nothing ever built one
per element: `WidgetRenderer` holds a single `Context` for the whole tree and
hands the same instance to every `ComputedStyle.of` call, so `em` was one
constant at every depth.

**Measuring it turned up a second number the entry did not mention.**
`CssLength.Context.DEFAULT` is `(16, 16)`, and `Typography.INITIAL`'s size is
**13**. So `1em` was not merely "not the parent's size" — it was not the
element's own size either, and not any size the toolkit actually renders text
at. The two constants had no relationship and nothing made them agree.

## Decision

**`em` resolves against the element's computed font size, in two passes.**

CSS has one exception and it is the reason a single pass cannot work:

- `1.2em` on **`font-size`** means "a fifth larger than my parent", because the
  value being computed cannot be its own input.
- `1.2em` on **anything else** means "a fifth larger than my own text".

So `ComputedStyle.of` now resolves `font-size` first against the parent's size,
then everything else against the size that produced. The parent's size comes from
`parent.typography().size()` — already in hand, because `WidgetRenderer` passes
the parent's `ComputedStyle` for inheritance. **No plumbing changed**: the fix is
entirely inside the method that was already given everything it needed.

The undeclared case needs no branch. A node that says nothing about its size has
whatever it inherited, and that is exactly what `em` should resolve against.

### `Transform` was the same bug in a second place

`Transform.parse` reached for `CssLength.Context.DEFAULT` directly, and its
comment named the gap:

> `em` and `rem` against the fixed context numbers, which is the same
> approximation the rest of the cascade makes and the same known gap.

It takes a `Context` now, threaded from `ComputedStyle.with`, which had one all
along. Two public call sites, both in `ComputedStyle`.

### `rem` is the configured root size, and that is a smaller claim

`rem` continues to use `Context.rootFontSize()`. In CSS `rem` is the *root
element's* computed font size, so these agree unless the root element itself
declares one — and recovering that inside `ComputedStyle.of` is not possible,
because a node is handed its parent's style and not the root's. Nothing in the
catalog styles a root's `font-size`, so this is exact today and is written down
rather than fixed.

## Alternatives considered

- **Resolve every property in one pass against the parent's size.** Simpler and
  wrong for the common case: `padding: 1em` on a 20px heading would be 13, the
  parent's size, which is the opposite of what `em` is for.
- **Give `ComputedStyle` a `fontSize` parameter instead of deriving it.** Every
  caller would have to know the rule, and the two that matter — `WidgetRenderer`
  and the tests — would derive it the same way from the same parent style.
- **Make `Context.DEFAULT` `(13, 13)` so the constants at least agreed.** It
  hides the bug rather than fixing it: the numbers would match for a node that
  declared nothing and diverge again the moment one declared a size.
- **Thread the root's computed size for `rem`.** It needs a third thing passed
  down beside the parent style, or a mutable field on the renderer that is
  correct only after the root has resolved. Worth doing when something styles a
  root's `font-size`; nothing does.
- **Leave it, since no shipped stylesheet uses `em`.** That was the state, and
  the entry's own answer is the right one: it is wrong, and the typography scale
  makes it reachable. A unit that silently means something else is worse than an
  unimplemented one, because it looks like it works.

## Consequences

- **No shipped rendering changes**, and this was checked rather than assumed: not
  one `em` or `rem` appears in `nord-dark.css`, `nord-light.css`, `controls.css`
  or the showcase's sheets, and the full golden corpus passes untouched.
- **One existing test changed meaning and was rewritten.** `ComputedStyleTest`'s
  "em multiplies the font size in force" passed `Context(20, 16)` with no parent
  and asserted `1.5em` was 30 — asserting the old semantics, on an element whose
  computed font size was 13. It now declares `font-size: 20px` and asserts the
  same 30 for a reason that is true.
- **Six new tests**, four of which fail against the old code: `em` against a
  declared size, against an undeclared one (19.5, not the old 24), against an
  inherited one, on `font-size` itself against the parent's, and two for
  `transform: translate`.
- **`computeChild` is a new test helper** that resolves the parent through the
  real cascade and hands it down, which is what `WidgetRenderer` does and what
  the existing `compute` — every element a root — could not express.
- **`Transform.parse` and `parseOrigin` take a `Context`.** Both are public; both
  have exactly two callers, both in `ComputedStyle`.
- **`Context.fontSize` now means "what the root's `em` resolves against"** rather
  than "what every `em` resolves against". It is consulted once per tree instead
  of once per node, and `Context.DEFAULT`'s 16 no longer contradicts
  `Typography.INITIAL`'s 13 — because it no longer competes with it.
