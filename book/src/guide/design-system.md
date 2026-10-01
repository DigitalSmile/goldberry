# The design system

<p class="gb-lede">What every built-in widget is drawn to, as it is built: Nord's sixteen colours behind alias tokens, one type scale, a 4 px ramp, three motion durations, and the rules that keep them honest.</p>

By the end of this chapter you know which `--gb-*` tokens exist and what each
is for, what the type classes measure, how big a control is at each density,
and which parts of the written design are built and which are not. The full
specification is `docs/design-system.md` in the repository. This page is the
built subset.

## Principles

1. **Desktop only.** Pointer and keyboard first. Hit targets are 32 by 32 at
   regular density.
2. **Opaque first.** Every surface is designed opaque. Frost is an enhancement
   layer, and it is not built.
3. **Token or extend.** A screen that needs a value no token names extends
   the system rather than improvising.
4. **Keyboard complete.** Every interaction is reachable without a pointer and
   `:focus-visible` is always legible.
5. **Deterministic.** Embedded fonts, CPU raster and token-driven colour: the
   same markup renders the same bytes on every machine, and golden images are
   the arbiter.

## Colour

The raw palette is Nord, exposed as `--nord0` to `--nord15` in both themes.
Widgets never read the palette. They read alias tokens, and only the alias
tokens change between the two themes.

| Token | Hex | | Token | Hex |
|---|---|---|---|---|
| `--nord0` | `#2E3440` | | `--nord8` | `#88C0D0` |
| `--nord1` | `#3B4252` | | `--nord9` | `#81A1C1` |
| `--nord2` | `#434C5E` | | `--nord10` | `#5E81AC` |
| `--nord3` | `#4C566A` | | `--nord11` | `#BF616A` |
| `--nord4` | `#D8DEE9` | | `--nord12` | `#D08770` |
| `--nord5` | `#E5E9F0` | | `--nord13` | `#EBCB8B` |
| `--nord6` | `#ECEFF4` | | `--nord14` | `#A3BE8C` |
| `--nord7` | `#8FBCBB` | | `--nord15` | `#B48EAD` |

The first four are Polar Night, the next three Snow Storm, then Frost, then
Aurora. Aurora hues carry meaning: danger, warning, success, info and chart
series. They are never decoration on a control.

### Alias tokens

```css
panel { background: var(--gb-surface); color: var(--gb-text); border: 1px solid var(--gb-border) }
```

| Token | Dark | Light | For |
|---|---|---|---|
| `--gb-bg` | nord0 | nord6 | the window |
| `--gb-surface` | nord1 | `#ffffff` | a panel, a card, a menu |
| `--gb-surface-2` | nord2 | nord5 | a second plate that is distinct, in no direction |
| `--gb-surface-raised` | nord2 | `#ffffff` | one step up |
| `--gb-surface-sunken` | black at 22% | black at 7% | a field's well |
| `--gb-text` | nord6 | nord0 | text |
| `--gb-text-muted` | nord4 | nord3 | secondary text |
| `--gb-text-placeholder` | nord6 at 60% | nord0 at 75% | a field's placeholder |
| `--gb-border` | nord3 | nord4 | a divider, a card edge |
| `--gb-border-strong` | white at 20% | black at 16% | an edge that must read |
| `--gb-accent` | nord8 | `#5c7ea8` | the primary action, a selected tab |
| `--gb-focus` | nord8 | nord10 | the focus ring |
| `--gb-selection` | nord10 at 40% | nord8 at 30% | selected text and rows |
| `--gb-scrim` | black at 55% | black at 40% | the veil behind a dialog or a tour |

A semantic hue has three ranks. `--gb-danger` is what danger is, a fill;
`--gb-danger-fill` is what you may put words on; `--gb-danger-line` is what
you may draw a glyph or a border with on a surface, and it clears 3:1 against
it. `--gb-warning`, `--gb-success` and `--gb-info` have `-line` ranks for
the same reason, and `-text` ranks for words on their fills
([ADR-0175](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0175-a-banner-says-its-kind-twice.md)).

Above the aliases sit component tokens: `--gb-button-bg`,
`--gb-button-bg-hover`, `--gb-checkbox-mark-checked`, `--gb-toggle-track-bg`,
`--gb-slider-thumb-border`, `--gb-chart-1` to `--gb-chart-8`, and their kin.
An application may override component tokens, never structure. Every one is
listed in `nord-dark.css` with a comment saying why it exists.

Every text and surface pair in both themes is measured at 4.5:1, and every
mark against the box it is drawn in at 3:1, by `ContrastTest` in CI.

## Spacing

The base unit is 4 px. The legal ramp is 2, 4, 8, 12, 16, 20, 24, 32, 40,
48 and 64, and nothing off it. Component padding is 8 or 12, the gap between
related controls 8, between groups 16, and the window's content margin 16.
The ramp is a rule, not a set of tokens: a stylesheet writes `gap: 8px`.

## Type

```kdl
column {
  text class="display" "Goldberry"
  text class="title" "Preferences"
  text class="heading" "Appearance"
  text class="body" "Default UI text."
  text class="body-strong" "Emphasis, and a button's label."
  text class="caption" "Secondary metadata"
  text class="mono" "goldberry.log.level=TRACE"
}
```

| Class | Face | Size / line | Tokens |
|---|---|---|---|
| `display` | Inter 600 | 28 / 34 | `--gb-font-display`, `--gb-line-display` |
| `title` | Inter 600 | 20 / 26 | `--gb-font-title`, `--gb-line-title` |
| `heading` | Inter 600 | 15 / 20 | `--gb-font-heading`, `--gb-line-heading` |
| `body` | Inter 400 | 13 / 18 | `--gb-font-body`, `--gb-line-body` |
| `body-strong` | Inter 600 | 13 / 18 | the body tokens and `--gb-weight-strong` |
| `caption` | Inter 400 | 11 / 14 | `--gb-font-caption`, `--gb-line-caption` |
| `mono` | JetBrains Mono 400 | 13 / 18 | `--gb-font-code`, `--gb-line-code` |

The two weights are `--gb-weight-regular` at 400 and `--gb-weight-strong` at
600, and each is a face rather than an axis. Inter ships upright and italic
in both weights. The numbers live in the theme, so a large-text theme moves
all of them at once. Text scale from 90% to 150% is a renderer switch beside
them, and scales the text without the boxes
([Text, fonts and icons](text.md#text-scale)).

## Shape, elevation and materials

**Radii** are literals in `controls.css`: 4 on fields, text areas and the
checkbox glyph; 8 on buttons, cards, panels, toasts and messages; 12 on
dialogs; a full radius on badges, chips, progress bars, thumbs and step
markers.

**Elevation** is three shadow tokens, and a rule never writes the numbers:

```css
card   { box-shadow: var(--gb-elevation-1) }
dialog { box-shadow: var(--gb-elevation-2) }
```

| Token | Geometry | Dark alpha | Light alpha |
|---|---|---|---|
| `--gb-elevation-1` | `0 2px 8px` | 44% | 16% |
| `--gb-elevation-2` | `0 8px 32px` | 56% | 22% |
| `--gb-elevation-3` | `0 16px 48px` | 68% | 28% |

The geometry is the same in both themes and the alpha is not, which is why
they are tokens. A card wears level 1 and lifts to 2 on
`card.interactive:hover`; a dialog, a toast and a tour card wear level 2; a
popup is an edge rather than a shadow, because it is drawn in a window sized
to itself and a shadow would be clipped
([ADR-0312](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0312-the-catalog-puts-the-two-new-properties-on.md)).
Level 3 is defined and used by nothing.

**Materials.** Of the three the specification names, `opaque` is every
surface, and `veil` is built as `--gb-scrim` behind a dialog and a tour.
`frost` is not built: there is no `backdrop-filter` in the CSS subset and no
blur in the rasterizer. A design that wants a frosted panel gets an opaque
raised one, which is the fallback the specification mandates anyway.

## Icons

Lucide, 1544 icons on a 24 by 24 grid with a 2 px round-capped stroke,
compiled into `goldberry-core` as path data. Display sizes are 16, 20 and 24,
and an icon in a button's lead slot is 16. An icon is tinted by `color`, like
text. An icon-only control needs an accessible name
([Text, fonts and icons](text.md#icons)).

## Motion

```css
button        { transition: background-color var(--gb-motion-fast) ease-enter }
button:active { transition: background-color 0ms }
```

| Token | Value | For |
|---|---|---|
| `--gb-motion-fast` | 100 ms | state feedback: hover, check, selection |
| `--gb-motion-base` | 160 ms | component transitions: popover, tabs, toggle |
| `--gb-motion-overlay` | 240 ms | overlays: dialog, toast |

Three easing keywords: `ease-enter` decelerates, `ease-exit` accelerates,
`linear` is for continuous indicators. The rules are: input feedback is
instant and only its release fades; exits are faster than enters; the focus
ring is never delayed; nothing loops except a spinner, an indeterminate
progress and a skeleton; and under reduced motion every transition is zero
and keyframe animations do not run. The mechanics are in
[Styling](styling.md#transition-and-animation).

## States

Every control renders rest, `:hover`, `:active`, `:focus-visible` and
`:disabled`, and where it applies `:checked`, `:indeterminate` and
`:invalid`. Hover moves the surface one step and never the text colour.
Disabled is 45% opacity on the whole control and never a colour remap, so a
disabled danger button still reads as danger.

## Focus

```css
button:focus-visible,
checkbox:focus-visible { outline: 2px solid var(--gb-focus); outline-offset: 2px }
```

One focus owner per window. The ring is 2 px of `--gb-focus`, 2 px outside
the control, following its radius, and it appears only for keyboard focus. It
is one rule over a list of types in the base layer, not one rule per control.
A composite is one Tab stop with the arrow keys moving inside it
([ADR-0073](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0073-a-composite-is-one-tab-stop.md)).

## Keyboard conventions

```java
host.shortcut(Shortcut.primary(Key.S), this::save);     // Cmd+S on macOS, Ctrl+S elsewhere
host.shortcut("Primary+Shift+Z", this::redo);
```

Accelerators are written against the desktop's **primary modifier**, which
has a name: `Primary` in a string, `Shortcut.primary(key)` in Java. It is
`Cmd` on macOS and `Ctrl` everywhere else, and nothing is translated: a
shortcut that says `Ctrl` means the control key on every desktop
([ADR-0378](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0378-the-desktops-own-modifier-has-a-name.md)).

In a dialog `Enter` presses the affirmative action and `Escape` the
dismissive one, and the affirmative sits on the right. A button activates on
`Space` and `Enter`; a checkbox on `Space` only, so `Enter` still reaches the
form's default action. Tab order is document order.

## Density

| | Regular | Compact |
|---|---|---|
| control height, `--gb-control-height` | 32 | 28 |
| list row, `--gb-list-row-height` | 32 | 26 |
| table header, `--gb-table-header-height` | 36 | 30 |
| code-input box | 40 by 48 | 36 by 44 |
| calendar day | 32 | 28 |

`Density.COMPACT` is those tokens on `:root` and nothing else. The 16 px glyph
inside a checkbox or a radio does not shrink, so compact costs margin around
the target rather than a smaller target
([ADR-0074](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)).

## Scrollbars

Overlay by default: a 6 px thumb over the content that widens to 10 px with a
track on hover, with no gutter reserved. `Scrollbars.ALWAYS` swaps in a
classic 12 px gutter with an 8 px thumb and a `--gb-surface-2` track, and a
layout must survive the gutter appearing
([ADR-0364](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0364-always-shown-scroll-bars-are-a-token-sheet.md)).

## Component metrics

Heights at regular density, with compact in brackets, as `controls.css` sets
them.

| Component | Metrics |
|---|---|
| `button` | height 32 (28); padding-x 12; icon gap 6; radius 8; `body` size at the strong weight |
| `text-input`, `select` | height 32 (28); padding-x 8; radius 4; fill `--gb-surface-sunken` |
| `text-area` | padding 6/8; radius 4 |
| `checkbox`, `radio` | glyph 16; hit 32; label gap 8 |
| `toggle` | track 36 by 20; thumb 16 |
| `slider` | track 4; thumb 16 with a 1 px edge |
| `knob` | 32 or 48 across |
| `menu` row | height 32 (28); padding-x 8 |
| `tab` | height 32 (28); padding-x 12 |
| `list` row | height 32 (26); padding-x 12 |
| `table` header | height 36 (30) |
| `badge` | height 20; min-width 20; padding-x 4; full radius |
| `chip` | height 24; padding-x 8; radius 12 |
| `progress` | track 4; full radius |
| `card` | padding 12; gap 8; elevation 1 |
| `panel` | radius 8 |
| `dialog` | padding 24; min-width 320; max-width 80%; radius 12; elevation 2 |
| `toast` | width 360; padding 12/16; radius 8 |
| `tooltip` | padding 8/12; radius 4; max-width 320 |
| `message` | padding 12/16; radius 8; icon gap 12 |
| `steps` marker | 24, full radius |
| `code-input` box | 40 by 48 (36 by 44); radius 4 |

## Accessibility baseline

Built: contrast measured in CI for both themes; every control operable from
the keyboard, with the per-widget maps in each chapter; text scale to 150%
without the boxes moving; reduced motion; hit targets of 32 at regular
density; and a `Role` and an accessible name on every focusable widget,
enforced by a sweep over the source tree.

Not built: a screen-reader bridge. Nothing on any platform reads the
semantics tree, and the bridge is on hold with no milestone owning it
([ADR-0440](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md)).
The tree stays, so a bridge would be an adapter rather than a rearchitecture.

## Governance

A new widget enters the canon with a specification, a metrics row and
gallery coverage in both themes before code, and the alias tokens, the
component contracts and the metrics tables are the stable tier.

## Read more

- [Styling](styling.md): the CSS that reads these tokens
- [ADR-0074](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md): density
- [ADR-0175](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0175-a-banner-says-its-kind-twice.md): three ranks of a hue
- [ADR-0310](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0310-a-shadow-is-a-stack-of-rectangles.md): elevation
- [ADR-0378](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0378-the-desktops-own-modifier-has-a-name.md): the primary modifier
- [ADR-0440](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md): the bridge on hold
