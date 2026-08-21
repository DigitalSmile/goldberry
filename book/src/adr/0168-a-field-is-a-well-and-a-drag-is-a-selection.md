# 168. A field is a well, and a drag is a selection

Date: 2026-08-21

## Status

Accepted. Corrects four things in
[ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md), one in
[ADR-0141](0141-a-select-is-a-closed-control-and-a-list.md), and adds two design
tokens.

## Context

Six reports, from using the Forms screen:

> 1. Move fields to cards
> 2. For the first field — if I copy/paste multiple times it stops to paste
>    after third paste and shows only some characters. If there is a limit —
>    write it down.
> 3. The placeholder needs different text/style from the regular text
> 4. Caret is too large in height
> 5. In light theme the background of fields is too pale
> 6. I cannot select text if I just click/hold mouse and move it

Four are defects. One (2) is a feature behaving exactly as specified and looking
like a fault, which is its own kind of defect. One (1) is the screen.

Every one of them was invisible to the tests that existed. The unit tests asked
what the field *held*; four of these six are about what it *looked like* or what
the pointer *did*.

## Decision

### A drag is a selection, and the button is the press's question

`text-input` guarded its whole pointer handler with:

```java
if (disabled || event.button() != PointerEvent.Button.PRIMARY) {
    return;
}
```

**`PointerRouter.pointerMoved` builds its event with a null button**, because a
motion is not a button event — the button belongs to the press and the release.
So the guard threw away every drag before the switch could look at it, and
click-and-drag selected nothing, ever.

The button is asked about in the `PRESSED` arm now, where it means something.
Which button started a gesture is the press's question; what a motion carries is
`dragX()`, which is `NaN` when nothing is held and is how the router already
says "this is not a drag" ([ADR-0075](0075-a-gestures-origin-is-the-routers.md)).

The general lesson is worth having: **a guard at the top of `onPointer` is a
guard on every kind of pointer event**, and the kinds do not carry the same
fields. `Slider` gets this right by asking per kind, and it reads as a stylistic
choice until this happens.

### A caret is as tall as a line, not as tall as a control

The caret and the selection highlight were pinned top and bottom, so both filled
the control: an 18-point line of text with a 32-point caret through it, which
reads as a terminal cursor rather than an insertion point, and a highlight
standing well above and below the glyphs it is meant to be behind.

Both take the **font's line height** now and are centred by the field's own
`align-items`, exactly as the text is. The line height rather than a number in
the stylesheet, because it has to follow the text: a field at a larger
`font-size` has a taller line, and a CSS height that disagreed would be wrong at
every size but the one it was written for.

### A field is a well, and `--gb-surface-2` is still not a direction

`text-input` and `select` were both filled with `--gb-surface-2`. On the light
theme that is `--nord5` on an `--nord6` page: one rung, which is what "the
fields are too pale" is.

This is **the same defect [ADR-0166](0166-a-raised-thing-is-told-apart-by-its-edge.md)
corrected for `card`**, in a new place and found the same way — by looking at it.
`--gb-surface-2` means "the second surface" and promises no direction; a card
built on it read as recessed on light, and now a field built on it read as
absent. The token is not wrong, it is simply not an elevation, and the third
consumer to assume it was is the point at which the assumption should stop being
made.

So `--gb-surface-sunken`, `--gb-surface-raised`'s opposite: content sits *in* a
field and *on* a card.

It is an **alpha over whatever is underneath** rather than a rung on the ramp,
which is the technique `--gb-border-strong` already uses. A fixed value has to
pick one background to be right against and a field has three — the page, a
`panel`, and a `card` — and the first attempt proved it: `--nord2` on the dark
theme is *exactly* `--gb-surface-raised`, so a field on a card vanished into it
and was held together by its border. Darkening is right on all three, on both
themes, with one token instead of three that can each be wrong on their own.

`select` gets the same fill, because §3 gives the two controls one row and a form
where the field you type into and the field you pick from are different colours
looks assembled from two toolkits.

### A placeholder needs a token of its own, and it was never a matching problem

The rule was there and it applied. `text-value.placeholder { color: var(--gb-text-muted) }`
resolved correctly on both themes — and `--gb-text-muted` is `--nord4` where
`--gb-text` is `--nord6`. **Two rungs.** Inside a filled field that is not a
difference anybody can see, so an empty field looked like a filled one.

§1.2's rank for de-emphasised *labels* is right for labels and too strong for
something whose whole job is to look unwritten. `--gb-text-placeholder` is an
alpha over its own surface, for `--gb-surface-sunken`'s reason.

**The alpha is set by §1.2 rather than by taste.** At the first value tried it
was 2.4:1 on the light theme, which is not a hint, it is a smudge. The shipping
values are the lowest that clear **4.5:1 against the worst background a field
sits on** — a `card`, where the fill is lightest — and they land a placeholder at
roughly half a value's contrast: unmistakably dimmer *and* legible.

That measurement cannot go in `ContrastTest`, and that test says why in its own
words: a translucent fill has no contrast ratio, because the answer depends on
what it is composited over — the trap that keeps `button.ghost` out of it. Both
new tokens are translucent on purpose. So `PlaceholderContrastTest` composites
them explicitly against each surface a field can sit on, in both themes, and
asserts the floor and the gap.

The first version of that test resolved a bare `TextInput`, which is **stateful
and styles nothing**, and therefore measured a box with no background and no
colour: it reported a field whose fill was its own backdrop and a value with less
contrast than a placeholder. A test that resolves a widget rather than the node
the widget describes is measuring nothing at all — the same distinction
`WidgetParityTest` exists to keep.

### A limit that is not visible reads as a bug

The Forms screen's first field carries `max-length=40`. Pasting into it a few
times stops taking characters and clips the last paste, which is exactly what
`max-length` means, and is indistinguishable from a field that has broken.

Clipping a paste rather than refusing it stands — refusing means a field with a
limit silently ignores a paste somebody just made — but the **screen now says so
on the screen**. A showcase demonstrates behaviour, and behaviour a reader cannot
account for is a demonstration of a fault.

### The fields go in cards

A form is a set of groups rather than a list of lines, and a `card` is what makes
"these belong together" visible without a heading saying it. It is also what
shows a field is a well: the card is what it sits on.

## Consequences

- **`--gb-surface-sunken` and `--gb-text-placeholder` are design-system surface**,
  and both are translucent — so an application overriding either must think about
  what it composites over, exactly as `--gb-border-strong` requires.
- **`select` changed colour.** Its five golden images moved and were reviewed
  rather than regenerated blind: it reads as a well now instead of a raised chip,
  which is what a control you pick a value from should look like beside a control
  you type one into.
- **The Forms screen has a light-theme golden**, and it earned one by being wrong
  on light while right on dark. The gallery has had exactly one light image on the
  grounds that a theme is a stylesheet swap; that is true of a swap and not of a
  token whose *direction* differs per theme, which is the whole reason
  `--gb-surface-sunken` exists.
- **Three of these six were only findable by looking**, and the fourth only by
  dragging. That is not an argument for more golden images of everything — it is
  the argument ADR-0167 already made from the other end, when a golden caught the
  clipped first character that six unit tests had missed.
- **`text-area` inherits all of this.** The caret's height, the well, the
  placeholder token and the drag are the field's, not the single line's — what
  changes there is one highlight per line rather than one.
