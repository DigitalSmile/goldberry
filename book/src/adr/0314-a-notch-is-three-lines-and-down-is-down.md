# 314. A notch is three lines, and down is down

Date: 2026-09-14

## Status

Accepted. Corrects two bugs against
[ADR-0115](0115-a-wheel-reports-a-fraction-and-a-detent.md) and
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md); does not change
what either decided.

## Context

Two reports, one sentence apart: *"scrolling is slower than the system one"*, and
*"two scrolls on the page work opposite to the wheel — e.g. in the tab with md"*.

Both are true, and they are the same mistake made twice: **a wheel event counts
detents, and a viewport moves in lines, and the conversion between them was
written down in prose and never in code.**

### Slower than the system

`ScrollViewport.LINE` has carried this paragraph since the widget was written:

> What one wheel line moves, in logical pixels. Three of these is the conventional
> notch — around 60px, which is what every other application on the machine does.

And the handler below it read:

```java
var moved = scrollBy(event.deltaX() * line, event.deltaY() * line, …);
```

One line per notch. A `deltaY` of ±1 is one detent of a real wheel, so the toolkit
moved 20px where the desktop moves 60 — a third of the speed of every other window
on the screen, which is not a number anybody reads off a stylesheet. It is a feel.

Its own test agreed with it, because the test asserted `LINE` and the code
multiplied by `LINE`. Two copies of the same misreading is not two witnesses.

### Opposite to the wheel

The Markdown screen is a `split-pane`: a `text-area` on the left, a `scroll`
around the rendered preview on the right. They scrolled **opposite ways**, at
wildly different speeds.

`TextAreaBox` is the one scrollable thing in the toolkit that is not a `scroll` —
it holds its own offset because the text inside it is a paragraph rather than a
subtree. So it handles the wheel itself, and the handler read:

```java
if (editor.scrollBy(-event.deltaY())) {
```

Negated, and in **pixels**. The editor's `scrollOffset` is "how far the content is
scrolled up", positive down the document, which is `scroll`'s own convention and
`deltaY`'s; the minus sign inverted it. And the argument is a distance in logical
pixels against a delta in lines, so one notch moved the document **one pixel**.

Four lines above it, unused, declared and never referenced:

```java
/// How many lines a wheel notch moves. Three, which is what every scroll view
/// on every desktop does and what `scroll` itself uses.
static final int WHEEL_LINES = 3;
```

Which `scroll` did not, in fact, use — see above.

## Decision

**A wheel notch is three lines, everywhere, and the sign is `deltaY`'s.**

- `ScrollViewport.LINES_PER_NOTCH = 3`, applied to the wheel and to nothing else.
  `--gb-scroll-line` still says how far a *line* is
  ([ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)); three of them is a notch,
  whatever an author sets a line to.
- `AreaEditor.scrollBy(double dy)` becomes **`scrollByLines(double lines)`**. The
  unit is the one the event is in, and the conversion moves to the side that knows
  what a line of *this* control's text is tall — a `mono` area at 13px and a body
  one at 15px move different distances for the same turn of the wheel, and both of
  them move a line at a time.
- `TextAreaBox` calls `editor.scrollByLines(event.deltaY() * WHEEL_LINES)`, with
  the sign left alone.

### The keys are untouched

An arrow key means a line and `ARROW` is one; `PageDown` means a viewport. The
notch is the only unit here that is a **platform convention** rather than a
document's own idea, which is why it is a separate number from the token an author
can set. `ScrollTest` now asserts the relationship directly: one notch covers the
same ground as three arrow presses, whatever `--gb-scroll-line` is set to.

### A trackpad is unaffected in the way that matters

ADR-0115's finding was that the *fraction* is what stops a trackpad moving in
jerks, and the handler still reads `deltaY` rather than the accumulated
`ticksY`. A trackpad's eighths are multiplied by the same three, so the same
gesture covers the same ground it would on any other application.

## Alternatives considered

**Set `LINE` to 60 and delete the multiplier.** Fewer numbers, and it breaks the
token: `--gb-scroll-line: 50px` would then mean "a notch is 50px" on the wheel and
"an arrow moves 50px" on the keyboard, which are two different claims about one
declaration. The arrow and the notch have to be able to differ.

**Read the platform's own scroll setting.** GTK, Windows and macOS all expose a
lines-per-notch preference, and honouring it is the genuinely correct answer. It
needs an SPI call on three backends and belongs with the other platform settings
nothing reads yet. Three is what all three default to.

**Give `text-area` a real `scroll` inside it.** It would delete the second copy of
this convention, which is the root cause. It cannot be done as things stand: the
text is a `Paragraph` painted by the box, not a subtree, so there is nothing for a
viewport to translate. Worth revisiting if a `text-area` ever lays its lines out as
elements.

**Negate at the router instead.** The router already flips SDL's sign so that
positive `deltaY` is down the document. Doing it twice in one direction and once in
the other is how this happened; the fix is one convention stated once, which is
what `PointerEvent#deltaY` documents.

## Consequences

**Scrolling is three times faster and matches the desktop.** That is the whole
user-visible change, and it is a change to a feel rather than to an API.

**Four tests moved, and each of them for a reason worth reading:**

- `ScrollTest` asserted `LINE` per notch. It now asserts `LINE *
  LINES_PER_NOTCH`, and the harness's parameter is called `notches` rather than
  `lines` — the distinction the handler used to collapse.
- `KnobChainingTest` pre-scrolls its list off the top so a chained wheel has
  somewhere to go. Two notches used to be 40px and are now 120px, which carried
  the knob out of the viewport. Its own guard — *"the knob was scrolled out of the
  viewport before the wheel"* — is what caught it, which is what it was written
  for. One notch now.
- `AffixGoldenTest` is a picture of a list scrolled exactly 140px. It asks for
  seven thirds of a notch, which is a fraction a trackpad sends all the time and
  the only way an event that counts detents can say "this far".
- `TextAreaTest` had **no wheel test at all**, which is why a control that
  scrolled backwards one pixel at a time survived. It has four now, and the one
  about distance asserts what is at the top of the pane rather than multiplying a
  line height out — the conversion is the thing that was wrong.

**`AreaEditor.scrollBy` is gone.** It is a package-private interface with one
implementation, so the rename costs nothing outside `widgets.form.textarea`.

**The number is still the toolkit's and not the platform's.** An application on a
desktop configured for five lines a notch gets three. That is the same trade the
rest of the metrics make and it is now in one constant rather than in a paragraph
that disagreed with the line under it.
