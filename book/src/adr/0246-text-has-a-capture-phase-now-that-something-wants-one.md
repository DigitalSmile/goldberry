# 246. Text has a capture phase, now that something wants one

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0141](0141-a-select-is-a-closed-control-and-a-list.md).

## Context

The entry names the fix and the reason it had not been applied:

> A `select`'s typeahead works closed and not open. §3 asks for typeahead and a
> `TextEvent` goes to the *focused* node, which in an open list is an `option` —
> so the list has nothing to intercept it in. `Handles` has an `onKeyCapture` and
> no `onTextCapture`, and adding one is the whole fix; it was not added on spec,
> because a capture phase is a routing rule and inventing one for a single
> consumer is how a router grows two.

The restraint was right and its condition is now met: there is a consumer. The
open list is a **second tree in a second window** (ADR-0103) with its own router,
and the focused node inside it is an `option` — a row that does not know what
typing means and is where the letters stopped.

`dispatchKey` has captured root-first and then bubbled since the beginning.
`textInput` only bubbled. One event kind had a phase the other did not, for no
reason anybody had written down.

## Decision

**`Handles.onTextCapture`, and a capture phase in `textInput` that is
`dispatchKey`'s shape exactly** — the chain walked backwards, stopping on
`isConsumed`, then the ordinary bubble.

**`SelectList` reads the text on the way down** and calls the same
`SelectState.typeahead` the closed control calls. That is the point of fixing it
this way rather than writing a second search for the open case: typing `n`, `n`,
`n` cycles the same options in the same order whether the list is showing or not,
because it is one implementation.

Three details:

- **It consumes what it acted on**, so a letter the list searched with does not
  also reach a row.
- **Blank text is left alone.** A space in an open list means "pick this one"
  everywhere else, and a typeahead that swallowed it would take the key from
  whatever means to act on it.
- **A `tree` gets none.** `select tree=#true` puts a `Tree` in the panel, whose
  rows are nodes rather than options; matching a prefix against a lazily built
  hierarchy is a different search from the flat one, and inventing it here would
  be the same over-reach the entry warned about.

## Alternatives considered

- **Give the popup's router a rule that text goes to the tree's root first.** A
  routing special case for one widget, where the capture phase is the general
  mechanism the router already has for the other event kind.
- **Have `option` forward text it does not understand to its list.** Every row
  would need to know what encloses it, which is the coupling `Option#within`
  already exists to avoid, and a row outside a `select` would have nowhere to
  send it.
- **Move the focus to the list rather than to a row when the popup opens.** It
  breaks arrow-key navigation, `:focus-visible` on the highlighted row, and the
  reason the popup has a focus scope at all.
- **A second typeahead in `SelectList`** over the options it was handed. It works
  and it is the version that drifts: the closed control's cycling rule, its
  staleness window and its case-folding would have to be kept in step by hand.

## Consequences

- **`SelectList` gains a component**, `onTypeahead`, and a one-argument
  constructor without it — which is what a golden wants, since a picture has
  nobody to report to.
- **Four tests.** Two in `KeyboardTest` for the phase itself: capture runs
  root-first before the focused node is told, and a container that consumes on
  the way down stops it reaching the focus. Two in `SelectTest` for the list:
  it forwards and consumes, and the callback-less form leaves the text alone.
- **`KeyboardTest`'s node gained a `consumeText` field**, beside the `consumeKey`
  it already had, because a capture phase only means anything if something can
  stop the event there.
- **Every other widget is unaffected.** `onTextCapture` defaults to doing
  nothing, and the bubble is unchanged — a `text-input` still receives text
  exactly as it did.
