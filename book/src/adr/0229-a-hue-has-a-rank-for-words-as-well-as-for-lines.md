# 229. A hue has a rank for words as well as for lines

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0175](0175-a-banner-says-its-kind-twice.md).

## Context

ADR-0175 added a third rank to each semantic hue — `--gb-<hue>-line`, the hue
moved until it clears §1.2's 3:1 floor for a stroke on a surface — because
measuring `--gb-danger` as a border disproved the sentence both theme files had
been carrying: "what a label, an icon or a border is drawn in".

`message` was its only consumer, and the entry that recorded that said what was
owed:

> a `field`'s `:invalid` edge and a `badge`'s border are the same thing and still
> read the hue directly. `ContrastTest`'s 3:1 sweep covers the *tokens*, not every
> widget that draws one, so this is a survey somebody has to do rather than a
> failure waiting to happen.

The survey found five rules drawing ink in a bare semantic hue, and it found
something the entry had not anticipated. Only **one** of the five is a line:

| rule | draws | bare hue, worst measured |
|---|---|---|
| `field:invalid text-input` | a **border** | 2.46:1 on dark `--gb-surface` |
| `field-message` | **words** | 2.46:1 on dark `--gb-surface` |
| `statistic-delta.up` | **words** | 2.04:1 on light `--gb-surface` |
| `statistic-delta.down` | **words** | 2.46:1 on dark `--gb-surface` |
| `chart-message.failed` | **words** | 2.46:1 on dark `--gb-surface` |
| `hud-reading.over` | **words**, on the HUD's own plate | 3.95:1 |

Four of the six are text, and §1.2's floor for text is **4.5:1**, not 3:1. The
`-line` rank is a 3:1 rank by construction, so it does not cover them either:
`--gb-danger-line` measures 3.53:1 on the dark theme's `--gb-surface`. Pointing
those rules at `-line` would have moved them from clearly wrong to quietly wrong.

The `badge` half of the entry turned out to be a false memory: a badge is a
**filled** chip with its own `--gb-badge-*-bg`/`-text` pair, already covered by
the text-on-fill sweep since ADR-0087. It has no border.

## Decision

**A fourth rank: `--gb-<hue>-text`.** The hue moved in lightness until the
**worst** of the three surfaces a window paints clears 4.5:1, per theme, with the
measurement written beside it — the same derivation and the same house style the
`-fill` and `-line` ranks use. Three are derived per theme and one aliases in
each: only the dark theme's yellow and the light theme's red already carry, which
is the usual shape (a dark theme's trouble is the dark end of the palette and a
light theme's is the pale end).

So a hue is now four things, and the names say what each is *for* rather than how
it was made:

| token | for | floor |
|---|---|---|
| `--gb-<hue>` | a fill | — |
| `--gb-<hue>-fill` | words **on** that fill | 4.5:1 |
| `--gb-<hue>-line` | a stroke or a glyph on a surface | 3:1 |
| `--gb-<hue>-text` | words on a surface | 4.5:1 |

**The HUD gets its own two tokens instead.** `--gb-hud-warning` and
`--gb-hud-danger`, identical in both themes, beside the `--gb-hud-text` and
`--gb-hud-bg` that were already theme-invariant for the same reason: the plate
lies over the application's own colours, which the toolkit does not know, so it
carries its own contrast. A theme-varying hue is the wrong thing to draw on it —
the light theme's `-text` red is a *dark* red, and a dark red on a near-black
plate is not a warning, it is an absence.

**`field:invalid` takes `-line`**, which is the one case the entry named
correctly and the rank's whole purpose.

**And the survey becomes a lint.** `ContrastTest.noBareHueDrawsInk` scans
`controls.css` for `color:` or `border-color:` set to a bare `var(--gb-<hue>)`
and fails. `background:` is deliberately not included — a background *is* the
fill rank, and the text on it is measured by the sweep that has existed since
ADR-0087.

## Alternatives considered

- **Pointing the text cases at `-line`.** The obvious reading of the entry, and
  wrong: `-line` is derived against a 3:1 target, so `--gb-danger-line` at 3.53:1
  would have left `field-message` below the text floor while looking fixed. The
  ranks are named for their *use*, and using one for the other defeats the
  naming.
- **One rank at 4.5:1 for both lines and words.** Fewer tokens, and it drags every
  border to a lightness §1.2 does not ask for — a 4.5:1 border on a surface reads
  as a heavier box than the design system draws.
- **Changing the widgets not to colour their text.** It is a real option for
  `statistic-delta` and no option at all for `field-message`, whose whole job §4
  describes in terms of the danger hue.
- **Giving the HUD the theme's `-text` rank.** One fewer pair of tokens, and it
  puts a dark red on a near-black plate whenever the light theme is on.
- **Leaving it as a survey.** What the entry itself argued against. A survey is
  done once; the next widget that colours a word is written by somebody who has
  not read this record.

## Consequences

- **Eight new tokens, four per theme**, plus two for the HUD. The tokens file is
  the largest single artefact in the design system and it grew by ten lines with
  a measured ratio on each.
- **Six rules changed and eight golden images with them** — three `field`
  screens, the HUD, a statistic, a chart failure, and two gallery screens. Every
  one of those images was previously showing a colour below §1.2's floor, which
  is the point: the corpus recorded the defect faithfully for months.
- **Three sweeps and a lint** where there was one sweep. `everyTextRankIsLegible`
  measures the new rank on every surface in both themes;
  `everyHudReadingIsLegible` measures the HUD's plate on its own;
  `noBareHueDrawsInk` is what makes the other two a guarantee rather than a
  sample.
- **The lint reads the stylesheet as text**, which is exactly what the rest of
  `ContrastTest` refuses to do — its opening note says parsing CSS "would check
  that a token has the value someone wrote down". That refusal is right for a
  *contrast* claim and wrong for a *coverage* one: the question here is which
  rules exist, and only the source can answer it. The two are separate tests for
  that reason.
- **An application's own stylesheet is not linted**, and cannot be: the rule is
  about the toolkit's sheet. An application that draws its own words in
  `var(--gb-danger)` gets the same defect and no warning. That is the same limit
  `--gb-*` tokens have everywhere, and the entry about validating an
  application's theme is still open.
