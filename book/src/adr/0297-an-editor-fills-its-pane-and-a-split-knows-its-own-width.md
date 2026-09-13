# 297. An editor fills its pane, and a split knows its own width

Date: 2026-09-13

## Status

Accepted. Two defects in `:widgets`, both found by building
[ADR-0296](0296-a-preview-is-a-binding-not-a-callback.md)'s screen, and both
older than it.

## Context

The Markdown screen is a `split-pane` holding a `text-area` and a preview. Three
frames of it were wrong, and none of the three was Markdown's fault.

**The divider was not where it was told to be.** `position=0.5` put it at 123
points of a 1200-point pane. `SplitPaneState` takes the pane's measured length and
assigns it to a field — deliberately not through `setState`, with a note saying
"nothing drawn depends on it directly" and citing ADR-0119's warning about a
widget that rebuilds for ever from its own size.

That note stopped being true when the first pane started being sized in *points*
from that number. Without a rebuild the view kept the `-1` it had been built with,
so the pane stayed on its first-frame proportional guess **for ever** — and a drag
was the only thing that ever corrected it. Every test that passed, passed because
it dragged.

**The editor opened at the end of the document.** `TextEdit.of` puts the caret at
the end of the value it is given, which is right for a field somebody is about to
type into, and `TextAreaState.laidOut` keeps the caret's line in view from the
first layout. A `text-area` holding a hundred-line note therefore opened on line
one hundred, and the reader had to scroll up to find the beginning of their own
document.

**The editor could not fill its pane.** §4's `text-area` grows to fit its text
between `rows` and `max-rows`, which is what a field in a form should do. An
editor is the other thing a multi-line field is: it wants the height its container
has, with the text scrolling inside it. `rows=22` left a third of the pane empty
and clipped the editor on a short window.

## Decision

### A measurement that changes the pane asks for a rebuild

```java
private void measured(Extent bounds, Extent part) {
    var measured = widget().axis().isVertical() ? bounds.height() : bounds.width();
    if (Math.abs(measured - length) < 0.5) {
        return;
    }
    setState(() -> length = measured);
}
```

ADR-0119's warning is still the rule this obeys rather than an argument against
reacting at all. What is measured is the **split pane's own** length; what the
rebuild changes is its *children's*. The value cannot feed itself, so the second
frame is a fixed point — which the new tests assert by measuring twice and
checking the second one asks for nothing.

The one arrangement where that is not true is a split pane inside a parent that
sizes to its content, and `controls.css` rules that out for the default case by
giving every `split-pane` `flex-grow: 1`.

### The caret is only chased once somebody touches the control

`TextAreaState` keeps a `caretMatters` flag, false until a press, a key, an edit or
the focus arrives. Until then `laidOut` leaves the scroll offset where it is, so an
untouched area shows the **top** of its value — which is what every text box on the
web does and what a reader handed a document expects.

`TextEdit.of`'s caret-at-the-end is untouched: it is right, and the moment the
control is focused the content follows the caret exactly as it did before.

### `text-area fill=#true` takes the height its container gives it

```kdl
text-area class="mono" bind="md.source" change="md.set-source" fill=#true
```

The box grows (`flex-grow: 1`) instead of sizing itself from its line count, and
the number of visible lines — which decides how far the text may scroll and how
many selection highlights are built — comes from the **measured** height instead of
from `max-rows`. One method, `visibleRows()`, is read by all three, because the
three disagreeing is a selection that runs out half way down a pane.

`rows` and `max-rows` are then ignored, and that is stated rather than reconciled:
a filling area's height is the layout's answer.

This is an **addition to `core-widgets.md` §4**, which describes the field and not
the editor; recorded in `docs/ARCHITECTURE.md` §17.1 with the rest.

## Consequences

- **Every gallery golden moved**, and two of them moved for a reason worth
  reading: the Panels screen's split pane now honours the `position` its document
  asked for, where before it landed wherever its two children's content did.
- **A resize costs one extra build** in a split pane and in a filling area — the
  frame that learns the new size. `Measured` already fires only on a change, and
  both guards ignore a sub-pixel difference, so a still window still rebuilds
  nothing.
- **A filling area in a container with no height is one line tall.** That is
  flexbox being asked for something impossible rather than the control being
  subtle, and it is what the javadoc says.
- **`text-input` has the same caret-at-the-end behaviour** horizontally: a
  single-line field holding a long value shows its end. It is one control, one
  flag and the same argument, and it is in `book/src/TODO.md` rather than done
  here — a field is not a document, and changing two controls on one screen's
  evidence is how a fix becomes a regression somewhere nobody looked.
- **Neither fix is Markdown's.** Both are in `:widgets` and both are covered by
  `:widgets` tests, which is where the next screen that puts a document in a pane
  will find them already working.

## Alternatives considered

- **Sizing the split's first pane as a percentage.** No measurement, no rebuild,
  correct on the first frame — and the divider's six points cannot be taken out of
  a percentage, so the drag arithmetic (pixels to a fraction) and every point-exact
  assertion in `SplitPaneTest` would have had to change with it. The measurement
  is needed for dragging anyway.
- **A CSS `height` on the `text-area`.** The stylesheet says how tall the editor
  is. It contradicts the note in `TextAreaBox` — auto-grow is a function of how
  many lines the text wrapped into, which no selector can ask — and it would make
  a theme able to stop a form's fields growing.
- **Caret at 0 in `TextEdit.of`.** Fixes the opening scroll for both controls in
  one line, and changes what happens when an application *replaces* a field's value
  while somebody is typing, which is a behaviour four other controls lean on.
- **Leaving the split alone and giving the screen fixed pane widths.** The screen
  would look right and the widget would still be wrong for everybody else.
