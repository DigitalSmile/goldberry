# 185. A list that hangs off a field does not take the keyboard

Date: 2026-08-23

## Status

Accepted. Fixes three defects found by **running the application** against work
that ADR-0182, ADR-0183 and ADR-0184 had shipped with passing tests.

## Context

Three of §3's newest select forms were broken on screen and green in CI:

1. A `multiple`'s chip appeared and **the row it came from stayed grey**.
2. An `autocomplete` took **one character and then went dead**.
3. A `tree`'s branches **would not open at all** with a mouse.

The tests that should have caught them are the ones this ADR is really about.
Every one drove the widget by hand — calling `onChange`, pressing a key on a row,
asserting on what came back — and every one passed, because each defect lives in
the seam between the widget and something that only exists in a running window:
the application's rebuild, the platform's focus, and the pointer.

That is [ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md)'s lesson
arriving a third time. A golden photographed an animation that never ran; here a
unit test exercised a control nobody could use.

## Decision

### A control may not read its own widget between telling the application and being rebuilt

`select multiple` kept its list open and re-described the rows **at the moment of
the click**, immediately after `onChange`. At that instant `widget()` is still the
description that was current *before* the application was told — so the rows were
drawn from the selection the list already had, and the tick never appeared. The
chip appeared because the chip is drawn by the next build, which does see the new
model.

The refresh moved into `build`, which is by definition the first moment the
application's answer is visible. The general rule is worth stating because it will
catch the next one: **after reporting upward, a controlled widget knows nothing
new until it is rebuilt.** Reading `widget()` there is reading the past.

The one place it is still safe is a value the control owns rather than reports —
an autocomplete's query is this control's own state and does not travel through
the application before the list has to narrow.

### A popup that hangs off a field does not take the keyboard

`Popup` focused its first row on its first frame, always. For a menu that is
right — a menu is what you are now operating. For a **suggestion list under a
field you are typing into** it is a bug with a very specific shape: the first
keystroke opened the list, the list took the keyboard, and every keystroke after
it went to a row instead of the editor.

So `Popup.takesFocus(false)`, used by both autocomplete forms. The arrows still
reach the list, because the owner forwards keys to whatever popup is open
([ADR-0104](0104-a-popup-is-measured-then-placed.md)) — a mechanism that exists
because a popup may or may not have platform focus, and which turns out to be
exactly what makes this safe rather than a compromise.

### A combobox opens on focus, not on the click

The editor consumes the press to place its caret, so a click reaching the plate
underneath could not be relied on. Focus is the honest signal: it is what a click,
a `Tab` and `Alt+Down` all produce, and a combobox the user is inside with no
options showing is a text box that has forgotten what it is.

### A tree opens with the pointer

Nothing handled a click. `TreeRow` selected when the row was selectable, and in a
**leaf-only** tree — §3's default — a parent is not selectable, so a click on
"Europe" did nothing. The chevron had no handler either. Every keyboard test
passed.

Now: a click on the chevron toggles and consumes; a click on the rest of the row
chooses if it is an answer and otherwise opens. A row that is both — a parent in
an `any` tree — chooses, and its chevron is how it opens, which is every file
manager's arrangement.

### The wither check covers the catalog

`RecordWitherTest` covered `Box` and `ComputedStyle`
([ADR-0181](0181-a-box-may-say-how-small-and-how-large.md)). The argument has
since moved: `Select` grew to twelve components across four sessions, and every
option added churned every hand-written positional copy in the file.

`WidgetWitherTest` walks the **compiled classes** — `HolderShapeTest`'s approach,
for its reason: a list somebody must remember to extend is a list that stops being
true — and asks every wither on every widget record to set its component to the
value it already holds. Verified by swapping two same-typed arguments in
`Select.placeholder`; the test named the method.

## Consequences

- **Five widgets cannot be built by the check** — `Toaster`, `SplitPane`,
  `CheckMark`, `CheckIndicator`, `SliderTicks` — because their constructors refuse
  the values it invents. They are **reported in the failure message** rather than
  skipped silently, so the number can be seen going the wrong way. Twenty-seven
  withers across seventeen widgets are covered.
- **A component read through its accessor is not always the component.**
  `SplitPaneView.children()` computes a divider between two panes rather than
  returning what it was built with, and throws on a fixture with none. The check
  reads the **field**, because the field is the component and the accessor is a
  method that happens to share its name.
- **The fourth defect is not fixed.** Popups left open when the application loses
  focus to another window hang on screen after the window hides. The mechanism
  ADR-0144 describes is wired — the launcher watches `FocusChanged` and dismisses
  after a settle delay — so this is not a missing feature but a fault inside it,
  and diagnosing it needs a real window and a real compositor rather than the
  headless backend. Filed with what is known.
- **These three were found by running the application, and that is now twice.**
  What would have caught them is a test that drives the *loop* rather than the
  widget. `MenusTest` does exactly that through the real launcher and the headless
  backend, and nothing in §3's select family does. That is the gap worth closing
  next, and it is bigger than any of the three bugs it would have caught.
