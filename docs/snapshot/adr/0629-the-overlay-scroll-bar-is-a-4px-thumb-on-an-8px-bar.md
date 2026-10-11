# ADR-0629: The overlay scroll bar is a 4px thumb on an 8px bar

- **Status:** Accepted
- **Date:** 2026-10-11
- **Amends:** ADR-0364 (always-shown scroll bars are a token sheet), for the overlay numbers only

## Context

The design system's overlay scroll bar was a 6px thumb on a 10px bar,
widening to 10px when the pointer is over the viewport. The user found the
stock bar too big. Next to the showcase's 15px body text and 8px card gaps,
a 10px bar is wider than the gutters around it.

## Decision

**The overlay bar is 8px across. Its thumb is 4px at rest and widens to 8px
on hover.** These are three tokens in `controls.css`:

| Token | Before | After |
|---|---|---|
| `--gb-scrollbar-size` | `10px` | `8px` |
| `--gb-scroll-thumb-size` | `6px` | `4px` |
| `--gb-scroll-thumb-hover-size` | `10px` | `8px` |

The hover still widens the thumb before the pointer reaches it, so an 8px
target is what gets grabbed. `Scrollbars.ALWAYS` keeps its 12px gutter and
8px thumb: it is an accessibility setting, and a smaller bar there would work
against it. The tab strip's own 6px/4px bar is unchanged.

## Consequences

- Every overlay scroll bar in every application is 2px narrower, with no code
  change. An application that wants the old size sets the three tokens on
  `:root`.
- `ScrollGutterTest` pins the overlay bar's 8px and the resting thumb's 4px.
- `docs/design-system.md` §2.4 says 4px → 8px. The guide's numbers move at
  release (`docs/snapshot/layout-scroll-overlay-size.md`).
