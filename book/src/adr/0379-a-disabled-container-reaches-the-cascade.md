# 379. A disabled container reaches the cascade

Date: 2026-09-17

## Status

Accepted. Closes `docs/ARCHITECTURE.md` §17.1's "A disabled container disabling
its descendants", and completes ADR-0077.

## Context

`core-widgets.md`'s widget contract says the disabled state propagates down the
tree for input *and* semantics. ADR-0077 built the input half: `PointerRouter`
walks up from an element and refuses a click, a focus or a hover to anything with
a disabled ancestor, so a button inside a disabled `form` is unavailable without
having to know it.

The style half was deliberately left out, and the reason was real: the fade is
`opacity: 0.45`, opacity multiplies down a subtree, and a control that faded
itself inside a container that had already faded it landed at 0.2 — which reads
as broken rather than as unavailable. ADR-0077 concluded that `:disabled` should
stay on the node that declared it, and deleted a
`radio-group:disabled radio:disabled { opacity: 1 }` undo as evidence that the
mechanism was wrong.

What that leaves is a cascade that cannot see the state at all. A stylesheet can
say nothing about a disabled subtree — no `cursor`, no muted label colour, no
rule of an application's own — because nothing inside one matches `:disabled`.
And a container with no fade of its own, which is what `form` and `group-box`
will be, disables its contents invisibly.

## Decision

**`:disabled` propagates, and one rule says the fade belongs to the outermost of
them.**

- `WidgetRenderer` carries "an ancestor is disabled" down the render walk — the
  direction styles already resolve in — and mirrors `:disabled` onto every
  element under a disabled one. The router keeps its own walk **up**: input is
  routed before a frame is rendered, and the two must not depend on each other's
  timing.
- `controls.css` gains `:disabled :disabled { opacity: 1 }`. This is the general
  form of the undo ADR-0077 deleted, and it is a different rule: that one undid
  a mechanism for one widget, this one states what the mechanism means for every
  widget — a disabled thing inside a disabled thing is not faded twice.
- The state is recomputed every render, so a container that is enabled again
  takes it back from everything under it.

## Consequences

- Every golden image is unchanged, `segmented-disabled` included: the fade lands
  in exactly the same place, on the outermost disabled node.
- An application can now write `panel:disabled text { color: … }` and have it
  apply, which was the half of §17.1's entry that was simply missing.
- `:disabled :disabled` is the ninth untyped selector in the toolkit's own
  sheets, and `RuleBucketTest` records why it cannot name a type: it is true of
  every control in the catalog and of every container that can hold one.
- The **semantics** half of the contract is still owed, and is still owed by
  something that does not exist: there is no semantics tree, so nothing yet
  reports a role as unavailable. That is M5's AccessKit bridge, and it will read
  the same propagated flag.
