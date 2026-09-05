# 254. A build may ask the cascade for a number

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry
[ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md) left
open, and with it the last of
[ADR-0213](0213-a-virtual-list-is-two-spacers-and-a-window.md)'s.

## Context

ADR-0251 gave a widget `Paints.Context.length`, and said plainly what it did not
close:

> `list` is unchanged, and its entry stays open. `ListView.virtualized(h)` takes
> the row height as an argument, which is an *API* choice rather than a missing
> reader: the number decides which rows to build in `children()`, and a value
> banked from `render` would be a frame late in the one place a frame late means
> building the wrong rows. The door this opens is the one `scroll` needed; `list`
> needs a different one.

**The stakes are higher than "a number is stated twice."** `density-compact.css`
sets `--gb-list-row-height: 26px`. A list written `virtualized(32)` against the
regular density therefore virtualizes on the **wrong pitch** the moment an
application switches density — the spacers and the window disagree with the rows.
The number was not merely inconvenient to repeat; repeating it was a bug waiting
for a setting to be changed.

## Decision

**`BuildContext.token(name, fallback)`** — `Paints.Context.length`'s build-time
twin, for the numbers wanted before there is a box to paint.

It is three lines, because the pieces were already there and had not been put
together: `Element` already implements **both** `BuildContext` and
`StyleElement`, and `ElementTree` has held a `StyleResolver` since ADR-0149 so a
node whose state changed could ask what the sheets say. What was missing was the
method.

**`WidgetRenderer.prepare(tree)`** is new and is about *ordering*. `render`
already hands the tree its resolver on the way in, and that is a frame too late
for a reader in `build` — a build runs before the frame it produces. `Launcher`
and `Popup` now call `prepare` before the flush.

**`ListView.virtualized()`**, with no argument, is the form to prefer.
`virtualized(h)` is unchanged.

### `rowHeightFromToken` is a component, and the first attempt was a sentinel

`rowHeight` is already a tagged number — `0` means "do not virtualize" — so a
second tag, `-1` for "ask the token", looked consistent and cost nothing.

It cost a guard. `ListVirtualTest` asserts that `virtualized(-1)` **throws**, and
the test's name says why: *"a negative row height is refused where it is
written"*. `-1` is what a typo looks like, and the refusal catches it at the call
site. Making it meaningful would have deleted a check that exists to catch a real
mistake, in exchange for not adding a field.

So it is a `boolean` beside the number. Seven constructor sites, and none of them
could be got wrong silently: `double`, `boolean`, `Attributes` in a row is a
transposition the compiler refuses.

## The first build of a tree has no cascade, and that is worth writing down

A `Stateful` widget builds once inside the `ElementTree` constructor — before any
renderer has taken the tree on, and therefore before any cascade exists. So a
token asked for there answers its **default**, and the second build is the first
that can see the stylesheet.

This was found by measuring rather than assumed: the value resolved correctly on
every build except the first, which is the one `prepare` cannot reach.

It is acceptable because everything that reads a token is expected to settle, and
a virtualized list settles by construction — its window is recomputed from the
geometry every frame, so the frame after the first is already right. Closing it
means handing the resolver to the tree at construction, which nothing has needed
enough to widen the constructor for.

## The token must be declared at or above the list

`ListView` is a composition node whose *state* builds the `list` element, so the
build that decides the row count runs **one level above** the node a
`list { … }` rule would match. Custom properties inherit downward, so the token
has to be set on an ancestor.

That is where it ships — `:root`, in both `controls.css` and
`density-compact.css` — so the case this exists for works. It is stated here
because `list { --gb-list-row-height: 26px }` looks like it should work and will
not.

## Alternatives considered

- **Bank it from `render`, as `scroll` does.** ADR-0251 already gave the reason
  it does not transfer: the number decides which rows to *build*, so a frame-late
  value builds the wrong rows rather than moving the right ones.
- **Read the token in `ListBox`**, which *is* the `list` element and would honour
  `list { … }`. It is built by the state that needs the answer, so the question
  would be asked after it was needed.
- **Make `virtualized()` mean `virtualized(32)`.** It is the same repeated number
  with the repetition moved into the toolkit, and it is wrong under compact
  density in exactly the same way.
- **A general `token(String)` returning tokens rather than a number.** The
  argument `Paints.Context.color` made and this inherits: a widget would parse
  them, and there would be two parsers.

## Consequences

- **Four tests**, and the one that says what this is for asserts that a *compact*
  density changes the pitch with **no Java change at all**.
- **`ListVirtualTest`'s harness calls `prepare` before its flush**, matching
  `Launcher`. Without it the tests would pass for the wrong reason — the harness
  would be measuring the fallback and so would production.
- **The token tests settle over two extra frames**, and the comment says which
  property that is rather than treating it as flakiness.
- **`--gb-list-row-height` finally has a consumer**, which is what its `TODO.md`
  entry has wanted since it shipped with the density tokens: "it shipped before
  any of them existed, because the density they would have to honour was decided
  here rather than there".
