# 263. Three numbers in one row, and nothing watching

Date: 2026-09-05

## Status

Accepted. Amends one number in `design-system.md` §3's `tooltip` row, records two
in `ARCHITECTURE.md` §17.1, and puts a test under all four.

## Context

This was not the entry being worked on. `TODO.md`'s popup-inheritance entry says
a tooltip "wants the styling of the thing it describes", so the tooltip's own
styling was read to see what it would inherit — and the row it is specified by
turned out to disagree with the rule that implements it in **three** places out
of four:

| | `design-system.md` §3 | `controls.css` |
|---|---|---|
| padding | `6/8` | `8px 12px` |
| radius | `4` | `8px` |
| type rank | `caption` | `body` |
| delays | `500ms` / `100ms` | both, since [ADR-0262](0262-a-delay-is-a-metric-and-metrics-are-tokens.md) |

Only the third had a comment saying so. The other two had nothing at all.

**Nothing was watching, and that is the finding.** `SupportedPropertyTest` asks
whether a declaration does *something*; `ContrastTest` asks what colours measure;
`RuleBucketTest` asks how rules are shaped. No test asks whether a metric is the
metric §3 pinned, so a row can drift a number at a time and each drift looks like
the file it is in.

## The one that is a document bug

§3 said `padding 6/8`. **6 is not on §1.3's ramp** — that section lists `2, 4, 8,
12, 16, 20, 24, 32, 40, 48, 64` and introduces it with "no off-ramp values".

So the row as written could not be implemented without breaking a rule one
section above it. That is not a design decision the code overrode; it is the
document contradicting itself, and the shipped `8/12` is two legal steps.

Amended in §3, with the reason, because there is nothing to decide.

## The two that are decisions

**The radius.** §1.5 groups radii as `4` (inputs, small controls) · `8` (buttons,
cards) · `12` (dialogs, popovers, frost panels), and names no tooltip in any
group. The nearest named thing is a popover at 12, and a tooltip is a small
popover — so §3's 4 and the shipped 8 are both readings and neither follows from
§1.5. Recorded rather than resolved.

**The type rank.** §3 says `caption`; the rule writes `body`, with its argument
beside it: §1.4 gives `caption` to secondary text *under* a control, where the
reader has the control itself for context, and a tooltip is the only text on
screen at the moment it is read. That is a good argument and it is not an
agreement. Recorded rather than resolved.

Both go to §17.1, which exists for exactly this: "the design documents are the
authority and each of these needs a decision rather than an edit". Amending §3 to
match the code on either would be taking the decision by writing it down, which
is the move §17.1 was created to prevent.

## Decision

**Amend the padding. Record the other two. Test all four.**

`TooltipMetricsTest` asserts the **shipped** numbers, and each assertion's
javadoc says where that number stands — settled and amended, or open and in
§17.1. So a fourth departure is a failing test rather than a fourth silent one,
and the two open ones stay exactly one number wide until somebody decides them.

Asserting the shipped values rather than §3's is deliberate. A test that asserted
the document would fail today and would have to be disabled, which is how a
disagreement becomes invisible again.

## What this says about the entry it came from

`TODO.md`'s popup-inheritance entry proposes that a tooltip should be passed "the
anchor's resolved style". The tooltip's own rule **pins** its typography for a
stated reason, so inheriting the anchor's `font-size` would override the one
departure in that row somebody had actually thought about — a tooltip on a
`display`-ranked heading would be drawn at 28px.

So the entry's suggested answer is wrong for the widget it names, and its subject
is unchanged: a popup inheriting nothing is right for `menu`, and what `tooltip`
wanted from it was already decided the other way.

## Alternatives considered

- **Amending §3 to match the code on all three.** Fast, and it converts two open
  design decisions into edits by an implementer who noticed them. §17.1's opening
  paragraph is the argument against.
- **Moving `controls.css` to match §3 on all three.** It would put an off-ramp `6`
  into the stylesheet, which `SupportedPropertyTest` would not catch and §1.3
  forbids.
- **A general test that every §3 row matches the cascade.** The right shape and
  the wrong week: §3's rows are prose with numbers in them — "height 20; padding-x
  8; radius `full`; `caption`" — and parsing thirty of those is a markdown parser
  with opinions. Doing it per widget, as `BadgeTest.metrics` and this do, is what
  the catalog has been doing and it works; the general version is worth having
  once a third row has drifted.

## Consequences

- **§3's tooltip padding is `8/12`**, and the row says why in one clause.
- **§17.1 gained an entry**, which is the seventh open disagreement and the first
  found by reading a metrics row against a stylesheet rather than by building
  something.
- **Four tests**, and they are the first that assert a widget's metrics against
  the document for a widget that has **no widget class** — `tooltip` is an
  attribute, and the only thing carrying its type is `TooltipPanel` in `:core`.
  The test lives in `:widgets` because that is where `controls.css` is.
- **The drift was three deep before anybody looked.** The lesson is not about
  tooltips: every other metrics row in §3 is currently unchecked the same way, and
  this closes one of about thirty.
