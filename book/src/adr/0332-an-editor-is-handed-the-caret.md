# 332. An editor is handed the caret

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G38.

## Context

`text-area` reports **the new whole value** through `change=`, which is exactly
right for a form field and is what every other control in the catalog does.

It is not enough for an editor. `Ctrl+B` around a selection, `-` and `Enter`
continuing a list, `Tab` indenting one — every Markdown shortcut needs to know
*where the caret is* and *what is selected*, and a `String` says neither.

The value that holds exactly that already exists and is already what the widget
drives itself from: `text.edit.TextEdit`, a record of `(text, anchor, caret)` with
every motion and every deletion as a pure function. This was a request to let it
out, not to invent it.

### Why the caret cannot be inferred

It can, for typing, and that is the trap. An application can compute where a
change happened by diffing two versions of the string, and after a keystroke the
caret is at the splice's end. That inference is correct for typing and wrong for a
selection, a click, and every caret move that changes no text at all — three of
the four things a shortcut needs to know about.

An inference that is right often enough to be trusted and wrong exactly when a
shortcut fires is worse than no inference.

## Decision

**The change event in the richer currency, and a way to apply one back.**

```java
public TextArea onEdit(Consumer<TextEdit> listener);   // text, anchor and caret
public TextArea edit(TextEdit next);                   // an edit the application computed
```

`change=` stays as it is — a form does not want a caret — and these are beside it,
for the callers that are editors rather than fields. The two are independent: a
form may listen to one, an editor to the other, a screen that is both to each.

### `onEdit` fires on every change, including ones that changed no text

That is the whole point. It is raised after a keystroke, a click, a drag, an arrow
key, a select-all and an undo. `change=` still fires only when the text differs,
which is what a form wants and what every existing listener already assumes.

### `edit(TextEdit)` is half the request, not a convenience

Wrapping a selection in `**` is an edit **and** a caret move. An application that
could compute one but only push back a `String` would leave the caret wherever the
widget decided, which for a shortcut is the difference between working and not.

It is **offered, not imposed**, and works exactly as `value=` does: the control
adopts it when it *changes* and ignores it on every rebuild in between. A constant
edit adopted on every build would reset the caret after every keystroke. The state
keeps `lastPushed` beside the `lastOffered` that already does this for the value,
and for the same reason.

It is applied **after** `bind=` in the same build, so an area that is both bound
and pushed to in one frame takes its caret from here rather than from the clamp a
new value would leave behind — which is the case a Markdown shortcut always is:
the model's text changed *and* the caret moved, in one action.

### A pushed edit is recorded but not echoed

It goes into the undo history, so `Ctrl+Z` undoes a shortcut the way it undoes a
keystroke — an application's `**` is an edit and belongs in the same stack.

It is **not** reported back through `onEdit`, and `change=` is not raised for it
either. The caller already knows what it pushed, so a report would be an echo; and
it would be an echo raised from inside `build`, where a `setState` in reply is a
rebuild during a rebuild. `lastOffered` is deliberately left alone, so `follow()`
keeps comparing against what the model last said rather than against text the
application pushed without updating its own model.

### There is no markup form

`change=` names a method that takes a value. A `TextEdit` is not a value a KDL
document can write or an action registry can resolve, and an editor's shortcuts
are Java. Stated in the javadoc on `inflate` rather than left as a hole.

## Consequences

- `TextArea` grows two components here and two in ADR-0331, reaching fourteen. The
  eleven-argument constructor every existing call site uses is kept.
- `onEdit` fires more often than `change=` — on caret moves, which happen on every
  arrow key. It is null for every field that does not ask for it, and the call is
  one null check.
- An application can now put the caret anywhere, including somewhere absurd.
  `TextEdit`'s constructor clamps to the text's length and `snap` moves an offset
  to the nearest legal grapheme boundary, so "absurd" is bounded rather than
  corrupting.
- `text-input` does not get this. A single-line field is a form control; the one
  thing an editor needs that it does not have is exactly this seam, and adding it
  there would be API with no caller.
