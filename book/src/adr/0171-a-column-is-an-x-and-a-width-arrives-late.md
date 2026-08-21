# 171. A column is an x, and a width arrives late

Date: 2026-08-21

## Status

Accepted. Builds `docs/core-widgets.md` §4's `text-area` on
[ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md)'s model, and
finds two things about the render order that the single-line control could not.

## Context

`text-input` shipped the editing model, the undo history, the clipboard, the
caret's blink and the geometry. `text-area` is the same control with a second
dimension, and the question worth answering before building it was *which* of
those the second dimension actually changes.

The answer is: almost none of them. `TextEdit` was written without a line in it
about how many lines there are, and needed two helpers rather than a rewrite.

## Decision

### The parts are shared, in a package nothing can see

`text-caret`, `text-selection` and `text-value` are drawn by both controls. A
part is **styleable and not constructible**
([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)), which in this
catalog has always meant package-private — because until now one widget owned its
parts.

Two widgets own these. So they are `public` in
`…widgets.form.parts`, which the module **does not export**: an application
cannot construct one, both widgets can, and there is one `text-caret` rather than
two that have to be kept looking alike by hand. The rule was only ever
"package-private" because there was no other way to say it; JPMS has one.

### A column is an x, and it has to be remembered

`Up` keeps the column. A column is an **x** and not an offset — which is why this
is the one piece of editing state the second dimension adds, and why it cannot
live in `TextEdit`: the model has no font, no width and no layout, and a
character offset is not a column in any of them.

It is captured on the first vertical move of a run and held until something
horizontal happens. That is what makes walking *down* through a short line and
out the other side come back to the column you started in, rather than stranding
the caret at the short line's end. Every editor that gets this wrong is
immediately noticeable and hard to name.

"Something horizontal" is every other operation, so the column is cleared in the
one place every operation goes through, and `moveLine` sets it back afterwards.
`NaN` means "no run in progress", which is the arithmetic saying it rather than a
second flag — `dragX` uses the same trick for "this is not a drag".

### A selection is one rectangle per visual line

A run of wrapped text is not a rectangle. This is the whole of what the second
dimension costs the selection, and it is why `Paragraph`'s two measurements take
a **line's** range rather than an offset: they were written for this in ADR-0167,
one widget early.

The count of highlight nodes is **`maxRows`, always** — a bound rather than the
exact number. How many a selection needs is a question about the layout, and
`children()` is asked before there is one, so the choice was between a mutable
field on a value, a count one frame stale, or the bound. The bound is small and
correct: a selection can cover at most as many *visible* lines as the control
shows, because the rest are scrolled away and draw nothing.

### The height is the widget's, not the stylesheet's

§4's auto-grow between min and max rows is a function of how many lines the text
wrapped into, which no selector can ask. A `height` a stylesheet set would be a
control that stopped growing the moment somebody themed it.

Everything else — padding, border, fill, line height — stays the cascade's, and
the control shares `text-input`'s rules for its parts so the two do not drift.

### Two things about the render order, both found by looking

**`render` runs before Yoga, so a box does not know its width.** `text-input` has
the same gap and nothing visible depends on it, because one line does not wrap.
Here it decides where the text breaks — and the first version reported
`max(1, measured)`, so before anything had been measured it wrapped at one point
and put **every word on a line of its own**. The Forms golden showed it
immediately.

The fix is that "I do not know" is `UNCONSTRAINED` and not one point: the text
keeps its hard lines for one frame and wraps properly on the next, which is wrong
in the direction nobody sees.

**And a measurement has to ask for a frame.** `text-input` records its width and
requests nothing, because the width only decides how far it has scrolled and the
next keystroke redraws anyway. A `text-area` that did the same would show its
first frame's guess until something unrelated caused another frame — which, for a
form nobody has touched, is never.

It converges rather than looping, which is what
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md) warns about: the only frame
it asks for is one where the width **changed**, and the width the next frame
measures is the same one. Two frames on mount, one per resize, none after.

## Consequences

- **§4's editing surface is done bar the pickers.** `code-input` and the typed
  fields of `date-picker`, `time-picker` and `color-picker` are all `TextEdit`
  plus a different keyboard map or a popup.
- **There is no visible scrollbar.** It scrolls with the wheel and to keep the
  caret in view; `scroll`'s bars belong to a viewport rather than to a control,
  and putting one inside a `text-area` means either a `scroll` around the text —
  which would fight the auto-grow — or a second bar implementation. §4 asks for
  one and this does not have it.
- **A golden of a `text-area` is a golden of its first frame.** The gallery
  renders once, and the settled wrap needs the measurement that only a painted
  frame produces. The image is honest about what one frame shows and is not what
  the application shows a moment later, which is a limitation of the corpus
  rather than of the control.
- **`Enter` is consumed here and nowhere else in §4.** A form's default button
  cannot have it in a multi-line control, and a read-only one leaves it alone so
  the form still can.
