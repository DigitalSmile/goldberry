# 374. Wrapped lines share a cross axis

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "`align-content` is still absent".

## Context

`Align` has nine constants and three of them — `SPACE_BETWEEN`, `SPACE_AROUND`
and `SPACE_EVENLY` — are `align-content` only. `align-content` was not resolved,
so those three were values the toolkit's own enum advertised and no stylesheet
could reach; worse, `align-items: space-between` parsed, because the keyword
parser reads the whole enum, and then meant whatever Yoga does with a value CSS
does not allow there.

The entry's own argument for waiting was that nothing in the catalog wraps inside
a box with a fixed height. That is still true, and it is an argument about a
default rather than about a property: a wrapping container has a rule for its
lines whether or not anybody wrote one.

## Decision

**`align-content` is resolved over the same [Align] value space, defaulting to
`stretch`.**

- `stretch` is Yoga's default under `useWebDefaults` and CSS's `normal` for a
  flex container, so every box in the catalog lays out exactly as it did.
- It reaches Yoga through `YGNodeStyleSetAlignContent`, which was bound on the
  first day and had no caller.
- `Box.alignContent(Align)` is the builder, beside `alignItems` and `alignSelf`.

## Consequences

- The three constants that no property accepted are reachable, and reachable
  only from the property that means them.
- `align-items: space-between` is still admitted by the keyword parser and is
  still Yoga's business what to do with — refusing per-property keyword subsets
  is a table this does not build.
- A wrapped row inside a fixed-height box can now pack its lines, which is what a
  future `chip` row inside a sized panel will want.
