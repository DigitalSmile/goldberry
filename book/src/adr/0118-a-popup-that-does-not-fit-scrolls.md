# 118. A popup that does not fit scrolls, and so does everything else

Date: 2026-08-18

## Status

Accepted. Puts
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)'s viewport
to work in the three places that were waiting for it. Amends
[ADR-0104](0104-a-popup-is-measured-then-placed.md) (a popup taller than the work
area) and [ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md) (a
strip wider than its window).

## Context

`scroll` was written because three unrelated entries in `book/src/TODO.md` named
the same missing widget. Building it did not close any of them: a viewport that
nothing uses unblocks work rather than doing it. This is the work.

## Decision

### The gallery wraps every screen, not the tall ones

`docs/core-widgets.md`'s cross-cutting notes ask for a gallery that "exercises
every widget in every state", and a screen taller than the window lost its
bottom. Every screen is wrapped, including the short ones, for two reasons: a
viewport over content that fits draws no thumb and takes no input, so it costs an
element; and a screen that is short at one window size is tall at another, which
is exactly what a per-screen decision gets wrong.

### A menu longer than the screen becomes a menu of the screen's height

ADR-0104 clamped a too-tall popup to the near edge, which keeps the top visible
and silently drops the rest. That was the honest thing to do with no viewport,
and a menu that loses its last three commands without saying so is the worst
kind of wrong.

The cap is applied by **`Menus`, not by the popup facility**, and that placement
is the decision rather than an implementation detail:

- `:core` has no widgets to wrap anything in (ADR-0092), and `Scroll` lives in
  `:widgets`.
- More importantly, *whether long content should scroll* is a fact about the
  content. A menu should. A **tooltip should not** — a tooltip you have to
  scroll is a tooltip that should have been a dialog. A facility that wrapped
  everything it placed would be wrong for one of its three callers.

So `Host` gains `placeableArea()`, which it already computed for `Placement`, and
the caller that wants to keep its own content inside it can ask. `Scroll` gains
an explicit `height(double)` for the same reason: §8's subset has no
`max-height`, and no stylesheet knows how tall the display is.

The menu's own cap is an **estimate** — rows times an assumed height — because
`Menus` cannot lay anything out and the thing that can does not know what a menu
row costs. It rounds up, so it errs towards wrapping a menu that would have
fitted rather than clamping one that does not. A viewport over content that fits
is invisible; a clamped menu is missing commands.

### A tab strip scrolls its headers and not its rule

The viewport goes around the headers only. The rule is pinned across the bottom
of the whole strip, and inside the viewport it would scroll out of the left edge
— leaving the underline of a scrolled strip somewhere it does not belong.

**This is where a bug in the viewport surfaced.** `ScrollViewport` laid its
content out as a column regardless of axis. A horizontal viewport doing that
*stretches* its content to the viewport's width, so the content's measured width
equals the viewport's, the overflow computes to zero, and nothing ever
scrolls — while the tabs inside spill out of a box that claims to fit them. Both
the viewport and the content now lay out along their axis. It took a real
horizontal consumer to find; the vertical tests could not have.

## Consequences

Three TODO entries are closed by doing rather than by deferring.

**The tab strip's tree gained two levels**, and four tests navigated it by index.
That churn is the cost of a structural change and is worth naming: `tab-list`'s
children are now the rule and a viewport, and a header is two elements further
down. The box tree gains one level and the element tree two, because `Scroll` is
a composition node.

`Placement` still clamps. Nothing about ADR-0104 changed — a popup taller than
the work area is still clamped to the near edge — and what changed is that menus
no longer *ask* to be that tall. A caller that opens an oversized popup without
capping it gets the old behaviour, which is the right default for a facility that
cannot know what its content means.

**The gallery goldens cannot see typography**, which this found by accident while
looking at something else. `GalleryGoldenTest` builds its renderer with the
single-font constructor — whose own documentation says it ignores `font-family`,
`font-size` and `font-weight` entirely — so every screenshot draws prose,
headings and button labels at one size. A screen with no hierarchy looks exactly
like a screen with one. That is how a screen title and the paragraph under it
came to be the same 13px with nothing catching it: the tokens were right, the
cascade applied them correctly, and the only test that looks at the gallery is
blind to the difference. `ShowcaseTypographyTest` asserts the sizes through the
cascade instead, which is where they are decided.
