# 145. A dropdown is as wide as what it drops from

Date: 2026-08-19

## Status

Accepted. Adds one parameter to
[ADR-0104](0104-a-popup-is-measured-then-placed.md)'s measure-then-place, for the
one caller that needs it.

## Context

`select` opens a list measured from its content. A field stretched across a form
therefore opened a panel as wide as the word "Dark", hanging off its left-hand
end — which reads as a mistake rather than as a menu. Every dropdown on every
desktop is at least as wide as the control it drops from.

No measurement of the *content* can produce that number: it is a fact about the
anchor, and the anchor is the caller's.

## Decision

**`Host.popup(content, anchor, placement, minimumWidth)`** — a floor, not a
width. The content still decides the rest, so an option longer than the field
widens the list past it, which is the other half of the same rule.

The floor is applied inside the existing two-pass measurement rather than beside
it. Pass one is the content's natural size, as before; pass two lays it out with
a definite width — which is what both the window-width cap and this floor need,
and which is what makes the content *fill* the floor rather than merely be placed
in a wider window. Content with a width of its own, which will not stretch, still
gets the window the floor asked for: the floor is about where the popup's edges
are, not about what is drawn inside it.

**Opt-in per call, and not a property of `Placement`.** It is false for the other
two callers: a menu is as wide as its commands and a tooltip as wide as its text,
and neither has any business being as wide as the thing it points at. Placement
is about position; this is about size.

## Consequences

**`select` passes its own painted width**, which it already knows because
`SelectField` is `Located` (ADR-0141). No new plumbing, and no id to anchor by.

**The floor is bounded by the window's width**, like the cap it shares a pass
with: a popup wider than the window it belongs to is not what any anchor meant.

**`Host` grew a method rather than a default**, so every implementation says what
it does with the floor. There are two stubs in the test suite and they were the
only cost.
