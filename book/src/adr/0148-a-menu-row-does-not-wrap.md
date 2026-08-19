# 148. A menu row does not wrap

Date: 2026-08-19

## Status

Accepted. Explains a report — "the menu item after the iconed one is vertically
aligned to top" — that four rounds of measuring the *rows* could not reproduce,
because the rows were never wrong.

## Context

Every row of the showcase's menu measures 32 tall, and every label sits 8 from
the top of its row, at both densities and at every display scale. The layout was
right. What was reported was the **text**, and the text was right too — until the
menu was narrower than its content.

A menu row is a row of **measured leaves**: `Box.text` asks the paragraph how
tall it is at the width Yoga proposes. Nothing in `controls.css` stops those
boxes shrinking, so a row squeezed narrower than its content does not clip the
label — it wraps it. A two-line label measures 32 in a 32-tall row, and
`align-items: center` then puts it at the row's top edge, against 8 for every
row that still fits.

**The widest row wraps first**, and the widest row is rarely the one with the
icon: "Switch density Ctrl+D" is longer than "Switch theme Ctrl+T". So the
symptom presents as *the row after the iconed one*, which is why it read as
something the icon had done.

A popup gets a definite width when it would be wider than the window
([ADR-0104](0104-a-popup-is-measured-then-placed.md)) — which is the second
measuring pass working exactly as designed, and is where the squeeze comes from.

## Decision

**A menu row's label and accelerator do not shrink.**

```java
content.add(Box.text(context.paragraph(style, label), style.color()).shrink(0));
```

The cost is the one `option` already documents and takes for the same reason: a
label longer than the room for it overflows, because nothing in this toolkit
clips. A menu one word too wide is legible; a menu of two-line rows is not — and
a row that wrapped also breaks `Menus.fitted`, which caps a long menu by assuming
34 pixels a row ([ADR-0118](0118-a-popup-that-does-not-fit-scrolls.md)).

## Consequences

**The test asserts where the paragraph was painted, not where the row was.**
`ItemAlignmentTest` sweeps both densities and four display scales for the general
claim, and squeezes the menu to 160 logical pixels for the reported one. With the
fix reverted it fails naming the row; without the squeeze it passes with the
defect in place, which is why the general sweep alone was not enough.

**`item` is still absent from `controls.css`'s no-shrink list**, and that is
deliberate: the list is about *controls* keeping their metrics (ADR-0076), and
what this needed was two anonymous boxes inside one widget's `render` rather than
a rule about the widget. A stylesheet cannot reach those boxes, which is also why
this could not have been fixed in CSS.

**Clipping would be the better answer and does not exist.** §8's subset has no
`text-overflow`, so an overflowing label is what there is. When clipping arrives
this is where it belongs — a menu row that ellipsises is right where one that
wraps is wrong.
