# 141. A select is a closed control and a list

Date: 2026-08-19

## Status

Accepted. Closes M2's catalog: `select` was the last control in
`docs/core-widgets.md` §3 with a specification, a metrics row and no code.

## Context

§3 asks for "closed control + popup list (backend popup window, so it escapes
window bounds); typeahead; keyboard open (Space/Alt+Down), arrows, Enter/Esc.
Option model or inline KDL `option` children."

Everything that sentence needs now exists and did not before. `scroll` unblocked
a list longer than the screen ([ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)),
`host.popup(content, anchor, placement)` measures and places a panel against a
rectangle ([ADR-0104](0104-a-popup-is-measured-then-placed.md)), and
[ADR-0140](0140-a-widget-may-reach-its-window.md) gives the control a way to ask
for one.

The value model needed nothing new at all: it is `segmented`'s, which is
`radio-group`'s, which §3 says outright.

## Decision

### `option` moved, because it now has two callers

§3 gives `segmented` and `select` the same child node. `Option` lived in
`…controls.segmented` under
[ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)'s rule about not
generalising from one caller, and `book/src/TODO.md` recorded exactly what would
move it. It is `…controls.option` now — one record, one CSS type, one markup
name, two controls.

`Option.within(…)` went from package-private to public with it, and the
visibility was not protecting what its comment claimed. Both controls rewrite
every option on every build, so a `selected` an application sets is discarded
before it is drawn. What keeps a set from having two selected options is that
"exactly one" is computed in one place from the bound value and stored nowhere.

**The drawing is not shared and does not need to be.** A segment is a cell in a
bar and a choice in a dropdown is a row: `segmented option` against
`select-list option`. That is a descendant selector telling one widget's two
*surroundings* apart, which is not the improvisation
[ADR-0065](0065-a-part-is-styleable-and-not-constructible.md) warns about — that
one uses an ancestor to tell two different widgets apart. The difference is
whether the selector describes where a thing is or what it is.

### Two keyboards, and the option carries which one it is in

This is the one place the shared node genuinely diverges, and §3 says so in as
many words. A `radio-group` — and therefore a `segmented` — has "arrow keys move
selection (roving focus)": the arrow **is** the choice. A `select` has "arrows,
Enter/Esc": the arrow moves and `Enter` chooses.

`Option.inAList()` is that difference, one flag rather than two because the two
halves are one decision: a set where the keyboard chooses has no use for a
separate commit, and a set with a commit must not choose before it. It also
unlocks `Enter`, which every other control in this catalog refuses on the grounds
that it belongs to a dialog's default action — a list is in a popup over
everything, and there is no default action behind it to take.

**This was found by a test and not by reading.** The first cut left the roving
behaviour on, so the first `Down` in an open list selected a row, and selecting
closes the list — leaving the second and third arrows with nothing to move.
`arrowsMoveTheHighlight` is that failure, kept.

### What it is made of

```
select                 stateful, styles nothing, holds whether the list is open
└── select-field       CSS type `select`: focusable, takes the click and the keys
    ├── select-value   the chosen label, or the placeholder — `.placeholder` marks which
    └── select-chevron the mark saying there is a list under this

select-list            in a popup window of its own, when open
└── option × n         the same widget a `segmented` puts in a bar, `.inAList()`
```

Stateful and unstyled for
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)'s reason: a
stateful widget that also carried the CSS type would put two `select` nodes in the
cascade, one inside the other, and every rule would apply twice. Parity is checked
against what the widget *describes*, which the parity test already knew how to do.

`select-list` is a sibling of `menu` rather than a use of it. The two are the same
drawing and different meanings — §3's list is a set of *values*, §8's is a set of
*commands* — and neither has an ancestor to be told apart by, because each is the
root of its own tree ([ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md)).

### It anchors to itself, by rectangle and not by id

`SelectField` implements [`Located`](0119-a-widget-may-be-told-where-it-is.md), so
it is told where the last frame painted it and hands that to the state, which
opens the popup against it. Anchoring by `id` was the alternative and is worse
here: a `select` a document gave no `id` would have to be given a generated one to
be able to open itself, and two of them in one window would then depend on that
generation being unique.

The rule `Located` carries — a widget told where it is must not move itself —
holds trivially: nothing here does anything with the rectangle until something is
clicked.

### The list opens on the row that is already chosen

`Popup.focusOn(String id)` is new. A popup focuses its first focusable node after
its first frame so that an arrow has somewhere to start
([ADR-0112](0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md)); for
a `select` showing its third option, that makes `Down` mean "the second option"
whatever the value was, which is a control that loses the user's place every time
it opens.

Focused **not** "from the keyboard", which matters more here than it does for a
menu: a row focused from the keyboard would be chosen on the spot in a control
whose options rove, so opening the list would report a change nobody asked for.

### The field is a field

§3 files this row with `text-input` rather than with the buttons — "height 32
(28); padding-x 8; radius 4" — so it carries a border and a 4px radius where
`button` carries 8 and none. Its fill is `--gb-surface-2` rather than
`--gb-surface`, because a field whose fill *was* the panel behind it is a control
held together by one pixel of border. That is the defect `controls-on-surface-*`
exists to catch, which is why `select` is in that scene rather than exempt from
it.

## Consequences

**M2's catalog is complete.** Every §3 control is built.

**Typeahead works on the closed control and not in the open list.** A `TextEvent`
goes to the focused node, which in an open list is an `option`, and there is no
text *capture* phase for the list to intercept it in — `onKeyCapture` exists and
`onTextCapture` does not. Recorded in `book/src/TODO.md` with what it needs.

**A `select` is as wide as its current value.** Nothing sizes it to its widest
option, because no selector can measure options — the same wall `segmented` hit,
where the answer was that the control writes the width itself
([ADR-0099](0099-an-indicator-travels-on-a-grid.md)). An application gives it a
width today, which is what a form does anyway. Also in TODO.

**`multiple`, `autocomplete` and `tree` are not built**, and two of the three are
waiting on widgets rather than on decisions: `autocomplete=#true` makes the closed
control an editable `text-input`, and `tree=#true` takes a `tree`'s model. Neither
exists. `multiple=#true` renders the selection as `badge` chips, which do exist,
and is deferred as scope rather than as a blocker.

**The list is not clamped to the screen and does not scroll.** `Menus` caps its own
content by estimating a row height ([ADR-0118](0118-a-popup-that-does-not-fit-scrolls.md));
`select` does not, so a list longer than the display is clamped by `Placement` as
every other popup is. It is the same gap, in one more place, and it will be fixed
in one place when the popup facility can report what it measured.
