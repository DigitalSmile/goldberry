# 293. A button that reads as a link

Date: 2026-09-12

## Status

Accepted. Adds `button.link` — the fifth button variant.

## Context

The catalogue has four button variants: `secondary` (the default), `primary`,
`ghost` and `danger`. All four are a **fill** plus the ink that fill carries.
There is a fifth thing a real application needs and none of them is: an action
that belongs in a sentence. "Take the tour." "Read the licence." "Show the six
that were filtered out." A `ghost` button is the closest and it is not close — it
is a button-shaped hole with body-strong text in `--gb-text`, which reads as a
control that has lost its fill rather than as a link.

`docs/core-widgets.md` §2 already specifies a **`link`**, and it is a different
widget: *text* that navigates, a word inside a paragraph, `href=` or `action=`.
That one is unbuilt and this record does not build it — see below, because the
difference decides the one contentious question here.

## Decision

### `button.link` is a class, like the other four

```kdl
button class="link" press="app.take-the-tour" "Take the tour"
```

Nothing new in Java. `Button` already carries no visual opinion — "the height,
the padding, the colours and the four variants are all in `controls.css`" — and
this is the fifth entry in that list. Semantically it is a button throughout: a
Tab stop, a focus ring, `Space` and `Enter`, `Role.BUTTON`.

Three declarations distinguish it, and each one is doing work:

- **`--gb-button-link-text`, not `--gb-accent`.** See below; this is the part
  that was measured rather than chosen.
- **`--gb-weight-regular`, where every other button is strong.** §1.4 puts the
  strong weight on buttons because a button is chrome. This one is meant to sit
  in a sentence, and a bold word in a paragraph is emphasis, not a control.
- **4px of horizontal padding, not 12.** The 12 holds a fill off its label and
  there is no fill; a link indented like a button reads as a button somebody
  forgot to colour in.

The **height stays** `--gb-button-height`. §1.3's ≥32 hit target is a floor
whatever the thing looks like, and a 20-pixel-tall click target in a card is the
failure that rule exists to prevent.

### The ink is its own token, and the accent was rejected by measurement

`--gb-accent` was the obvious answer and it does not clear §1.2's 4.5:1 as ink:

| | `--gb-bg` | `--gb-surface` | `--gb-surface-2` |
|---|---|---|---|
| dark, `--gb-accent` #88c0d0 | 6.24 | 5.03 | **4.31** |
| light, `--gb-accent` #5c7ea8 | **3.64** | **4.20** | **3.45** |

The light theme fails on every surface, and that is not a flaw in the accent — it
is ADR-0088's point restated. A light theme's accent is chosen as a **fill that
carries white text**; asking it to be ink on a white surface is the opposite job,
and the two ramps parted company for exactly this reason.

So there is a token per theme, with the numbers written beside it:

- **dark: `#a3d0dd`** — the accent one step lighter, an invented value on
  `--gb-checkbox-border`'s terms (ADR-0258): 7.51 / 6.05 / 5.19.
- **light: `var(--gb-accent-fill)`** — the two-steps-darker value ADR-0088
  derived for `button.primary`, used here as ink rather than as a fill: 5.38 /
  6.20 / 5.09. A fill dark enough to carry white text is dark enough to be read
  on white; the reuse is the Nord answer rather than a coincidence.

`ContrastTest` measures the ink on all three surfaces in both themes. It cannot
measure the variant itself — a `transparent` fill has no contrast ratio, which is
`button.ghost`'s situation and the reason that one is excluded from the sweep.

### There is no underline, and that is the contentious part

Every link in every browser is underlined, and WCAG 1.4.1 is the reason: colour
alone must not be the distinguishing feature.

That rule is about a link **inside a block of text**, where position says nothing
and colour is all there is. `button.link` is not that. It is a standalone control
in the tab order, with a focus ring, carrying three signals at once — no fill
where its neighbours have one, a different colour, and a different weight. The
widget that *is* a word inside a sentence is §2's `link`, and for that one the
underline is not optional.

Two further things point the same way:

- **§2.1 says hover changes the surface and never the text colour.** So the hover
  affordance is `ghost`'s overlay wash, and there is no "underline on hover" to
  reach for without contradicting a rule one section up.
- **§8's CSS subset has no `text-decoration`.** Drawing one would mean either a
  new property threaded from `ComputedStyle` through `Box.Text` into the
  paragraph painter, or a child box in `Button`'s Java — and `Button` builds its
  content as boxes with no widget children, so the second means teaching Java
  which class it is wearing, which is the thing this widget is careful not to do.

So the underline is filed with §2's `link`, where it is load-bearing, and
`text-decoration` lands for that widget's sake rather than for this one's.

## Consequences

- **Two golden images moved** — `button-variants-dark` and
  `button-variants-light` — and they are now five buttons wide. The light one is
  where the two themes visibly disagree about this variant: the ink differs where
  every other variant's *fill* does.
- **The showcase's Basic screen has a buttons card**, in `basic.kdl`, showing all
  five variants, both icon forms and a disabled one — nine buttons and not one
  line of Java, which is the argument for appearance being a class.
- **`--gb-button-link-text` is a new component token**, so an application
  restyles every link button by overriding one name.
- **§2's `link` is still unbuilt**, and `docs/design-system.md` now says so on its
  own row rather than leaving two rows that look like the same feature.
- **The disabled link button is legible and unremarkable**, because `:disabled`
  is a global rule on opacity rather than per variant.

## Alternatives considered

- **`--gb-accent` as the ink.** Rejected by the table above, on the light theme
  by a wide margin. Worth recording because it is the change somebody will
  propose as a simplification.
- **An underline drawn as a child box**, the way `tabs` draws its indicator and
  `text-input` draws a composition's rule (ADR-0292). It works and it puts
  `attributes.classes().contains("link")` in `Button.render` — a widget reading
  its own class to decide what to draw, which is precisely the line
  `controls.css` exists on the other side of.
- **`text-decoration: underline` in §8's subset**, now, for this. It is the right
  feature and the wrong reason: its consumer is §2's `link`, and building a CSS
  property for a variant that does not need it would fix its shape around the
  wrong case.
- **A `link` *widget* instead of a button variant**, satisfying §2 directly. A
  bigger piece of work with an unbuilt CSS property under it, and it would not
  have answered the thing that prompted this — an *action*, in a card, that
  should not look like a button.
