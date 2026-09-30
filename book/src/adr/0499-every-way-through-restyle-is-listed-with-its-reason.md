# 499. Every way through restyle is listed with its reason

Date: 2026-09-30

## Status

Accepted. Closes `book/src/TODO.md`'s "`Styled.restyle` is an escape hatch with
nine overrides now". Keeps
[ADR-0099](0099-an-indicator-travels-on-a-grid.md)'s rule and says what it has
come to mean.

## Context

ADR-0099 added `Styled.restyle`, the widget's last word on its own style. It
runs after the cascade and the style cache and before the transitions observe
the result. That order is what lets a widget-computed value animate. It is also
why whatever is written there is unthemeable and unoverridable. The ADR gave one
rule, "a widget may write here only what a stylesheet could not have written",
and one warning: the toolkit had one caller, and a second that was *not* a count
would be the signal to look again. `Styled`'s own note still said the toolkit
used it for "exactly two values, both derived from a count".

There were nine overrides, all in `:widgets`, and nothing had read them against
the rule since the first:

| Override | What it writes |
|---|---|
| `ColorSwatch` | `background`: the colour it shows |
| `SegmentedDivider` | `inset` at `boundary / count`; `opacity: 0` beside the pill |
| `SegmentedIndicator` | `width` of `1/count`; a translation of `index` cells; which corners stay round |
| `Tab` | `color`: the tab's own colour; a `transform` while it is dragged |
| `TabIndicator` | `background`: the tab's colour when selected; the displacement it slides out of |
| `ScrollContent` | `flex-shrink: 0`; padding plus the gutter; the scroll offset as a `transform` |
| `ScrollThumb` | its length and its travel |
| `ScrollViewport` | the caller's `height`, when one was given |
| `AffixContent` | how far the pinned box is shifted from its hole |

Most of the nine are not counts, and most of those are still right. A swatch's
colour is the application's data, a thumb's length is a measurement, and a
scroll offset is input. No stylesheet can know any of them. The warning was
narrower than the rule. The question is whether a stylesheet could have written
the number, not whether it is a count.

Read that way, two of the nine wrote something a stylesheet could have
written:

- **`SegmentedDivider`'s `opacity: 0`.** No selector can tell which lines are
  beside the pill. Once the widget says which, `opacity: 0` is an ordinary
  declaration, and a theme might reasonably want to dim the lines instead.
  `scroll-thumb.dragging` already has this shape: the widget supplies a fact as
  a class, and the sheet decides what the fact means.
- **`ScrollContent`'s `flex-shrink: 0`.** `controls.css` already declares it.
  The widget wrote it again so that no later rule could set it back to 1, which
  would squash the content into its viewport and leave nothing to scroll. That
  is a pin against the stylesheet, not a number the stylesheet could not have
  written. The comment said it was in `restyle` because `Box` had no wither for
  it. `Box.shrink` has existed since ADR-0076. Every other pin in the catalog
  is applied in `render` after `.style(style)`, for example the segmented
  indicator's `position: absolute`.

## Decision

**`restyle` writes five kinds of number and nothing else. Every override is
listed, with its reason, in `RestyleSweepTest`, and an override that is not
on the list fails the build.**

The five kinds, as `Styled.restyle`'s note and the test's `Because` enum now
name them:

- **count**: derived from how many of something there are.
- **data**: the application's own value.
- **measurement**: what only layout can say.
- **input**: where the pointer or the wheel has taken the widget.
- **arithmetic** over the cascade's own values. §8 defers `calc()`, so no rule
  can write it.

A fact a selector could match on is a **class**. A property the widget must
hold against the stylesheet is a **pin in `render`**. Neither goes in
`restyle`.

The verdicts:

| Override | Verdict | Kind |
|---|---|---|
| `ColorSwatch` | Kept. The colour is the model's value. Written here so the closed control fades between colours rather than jumping. | data |
| `SegmentedDivider` | **The place is kept. The `opacity` moved.** The widget reports `beside-selection` as a class, and `controls.css` has `segmented-divider.beside-selection { opacity: 0 }`. | count |
| `SegmentedIndicator` | Kept. Width, travel and which corners stay round all depend on which cell of how many. The radius it squares stays in the sheet, and §8 has no per-corner longhand a rule could use instead. | count |
| `Tab` | Kept. The colour is application data ([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)). The drag offset is the pointer's ([ADR-0372](0372-a-tab-is-dragged-and-the-strip-asks-where.md)). | data, input |
| `TabIndicator` | Kept. The tab's colour, and a displacement that is the difference between two painted rectangles ([ADR-0377](0377-an-underline-travels-by-being-let-go-of.md)). | data, measurement |
| `ScrollContent` | **The offset and the gutter are kept. The `flex-shrink` pin moved to `render`**, as `.shrink(0)` after `.style(style)`. The sheet still declares it, so the sheet still reads true. The gutter is the author's padding plus a token ([ADR-0364](0364-always-shown-scroll-bars-are-a-token-sheet.md)), which needs `calc()`. | input, arithmetic |
| `ScrollThumb` | Kept. The proportion of the content on screen, from measured extents ([ADR-0117](0117-a-widget-may-be-told-what-it-measured.md)). | measurement |
| `ScrollViewport` | Kept. The caller's height, which in the toolkit is `Fitted` capping a menu at the screen it opens on. | measurement |
| `AffixContent` | Kept. The shift comes from the rectangles the box and its scroll container were painted in. | measurement |

**The guard** is `widgets/src/test/.../arch/RestyleSweepTest`. It imports every
Goldberry class on `:widgets`' test classpath, excluding tests, the same import
`BoundaryTest` uses. It finds every concrete `Styled` that declares
`restyle(ComputedStyle)` and compares that set with a list of entries. Each
entry has a type, at least one `Because`, and a sentence saying what it writes
and why. Three checks:

- An override missing from the list fails, and the message quotes the rule and
  gives the two ways out: a class, or a pin in `render`.
- An entry for a type that no longer overrides `restyle` fails, so the list
  does not become a history.
- A sanity check requires the sweep to find `SegmentedIndicator`. An import
  that found nothing would otherwise pass vacuously.

## Consequences

- No picture changes. The divider's opacity comes from the cascade instead of
  from `restyle`, at the same point before the transitions observe it, so it
  still fades on `fast`. The content box still does not shrink. The segmented
  and scroll goldens pass unchanged, as do `SegmentedTest`'s hairline tests,
  which read the fill that reaches the screen.
- A theme can restyle the lines beside a segmented control's selection. This is
  the first thing moved out of `restyle`.
- Changing the selection now invalidates two dividers' cached styles, the same
  way `scroll-thumb.dragging` does. Before, `restyle` rebuilt the style on every
  frame anyway, so nothing is slower.
- A tenth override is a failing test until someone writes down which kind of
  number it is, which is the question the rule asks. The test cannot tell
  whether the reason is true. Reviewing the entry does that, and the list keeps
  every entry on one page for that review.
- **The sweep sees `:core`, `:common` and `:widgets`, not the modules
  downstream.** `:html`, `:media` and `:example` implement `Styled` and none of
  them overrides `restyle`. If one does, the same test belongs in that module's
  `arch` package. A source scan from `:widgets` would not be enough, because
  Gradle would treat `:widgets:test` as up to date when only another module's
  sources had changed.
- `Styled`'s note no longer claims two callers. It names the five kinds and the
  test, so the rule and its list are one hop apart.
