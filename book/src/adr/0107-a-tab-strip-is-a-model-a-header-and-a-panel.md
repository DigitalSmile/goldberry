# ADR-0107: A tab strip is a model, a header and a panel

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §5, applies
  [ADR-0063](0063-data-flows-down-events-flow-up.md) and
  [ADR-0073](0073-a-composite-is-one-tab-stop.md) to a set whose *membership*
  changes, confirms [ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md)'s
  finding about §8's border properties

## Context

`docs/core-widgets.md` §5 asks for "top placement v1; keyboard (arrows between
tabs, Tab into content); closable tabs optional; lazy content instantiation;
selected tab is retained state".

A tab strip is the fourth exactly-one set in this catalog — after `radio-group`,
`segmented` and `select` — and the first where the **set itself changes**: tabs
are closed and opened while the control is on screen. That turns out to need no
new mechanism at all, and the reason is worth writing down.

## Decision

### Adding and removing need no API, because the list is the application's

`tabs` reads which tab is selected through `bind` and reports what the user asked
for through `change`, exactly as `radio-group` and `segmented` do
([ADR-0063](0063-data-flows-down-events-flow-up.md)). Extending that to
membership is the same sentence twice more:

- `close` asks for a tab to go. The strip removes nothing.
- `new` asks for one to arrive. The strip adds nothing.

A strip whose `close` handler does nothing keeps its tab, which is the visible
form of "the model did not change" and is where the bug is when a tab will not
close. There is no `addTab`, no `removeTab` and no internal list, because a
control that owned its own tabs would be a control an application has to keep in
step with the thing the tabs are *of*.

`new` is not in §5 — it asks for "closable tabs optional" and says nothing about
adding — but a strip that can lose tabs and never gain them is half a control,
and the alternative is every application drawing its own `+` and lining it up
with the row by hand.

### A colour is a value, and it is the first one in the catalog

`tab colour="#bf616a"` is the one place a widget here takes a colour as data
rather than from a stylesheet. The justification is that **a stylesheet cannot
know it**: a tab coloured after the project it belongs to is application data,
like a label, and there is no selector for "the tab whose project is red".

It is written through `restyle`, which is the seam for exactly this — a value only
the widget can know, applied where a transition can still see it
([ADR-0099](0099-an-indicator-travels-on-a-grid.md)) — so a stylesheet still
decides what the colour *means*: `controls.css` puts it on the label and on the
underline, and a tab given none is styled entirely by the theme. The syntax is
CSS's, parsed by the CSS engine's own colour parser, because an author who knows
how to write `#bf616a` in a stylesheet should write it the same way here.

### Lazy content is lazy by omission

`tab-panel` holds the selected tab's content and nothing else: an unselected tab's
widgets are never built into elements at all. Nine background tabs cost nine
headers.

The consequence is the one to know, and it is `collapse`'s: a tab's content is
**rebuilt when it is selected again**, so anything that must not be lost — a
scroll position, a caret, a half-typed form — belongs in the application's model
rather than in the subtree. Cheap to rebuild is what the widget tree is for
([ADR-0004](0004-three-tree-retained-declarative-model.md)).

### The underline is a box, and the golden image is what said so

The first version wrote `border-bottom: 2px solid transparent` on a tab and
`border-bottom-color: currentColor` on the selected one. Both were **silently
dropped**: §8's subset has one `border` property covering all four edges and no
`currentColor` at all — which is [ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md)'s
finding about per-corner radii, in a second corner of the same subset.

Every number in the layout was correct and the underline was simply not there.
The golden image is what found it, which is now the fifth occasion.

So the underline is `tab-indicator`, a 2px box pinned across the bottom of the
header and out of flow, and the rule under the row is `tab-rule`, the same shape
across the list — `segmented-indicator`'s anatomy for the same reason. The
indicator is **always built**, selected or not, so that it can transition rather
than appear ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)), and
it is listed before the label so the label is painted over it.

The rule belongs to `tab-list` and not to `tabs`, because it has to stop where the
headers stop: on the outer box it would run under the panel as well.

### One Tab stop, and the × is not in it

`HORIZONTAL`, because a top-placed strip is a row and `Up`/`Down` belong to
whatever is above it ([ADR-0078](0078-a-focus-scope-has-an-axis.md)).

**`tab-close` is deliberately not focusable.** A focusable × would make a strip
two stops per tab — nine tabs would be nineteen stops between the strip and the
content. The keyboard's way to close a tab is `Delete` on the tab itself. The `+`
*is* focusable, and the difference is that adding a tab is a destination the
roving selection should reach, where closing one belongs to the tab it is on.

### Two new marks rather than two icons

`CROSS` and `PLUS` join `CHECK`, `DASH`, `DOT` and `ARC` in the painter. At eight
to ten logical pixels inside another control, an icon's metrics and lookup buy
nothing: what a close × has to do is line up with the glyph beside it and take the
colour of the thing it closes, and a mark does both for free.

## Alternatives considered

- **A strip that owns its tabs**, with `addTab`/`removeTab`. It is what most
  toolkits do, and it puts the list in two places — the control's and the
  application's — with no rule about which wins when they disagree.
- **`closable` on the strip rather than on the tab.** A file that cannot be closed
  because it is unsaved sits next to nine that can; §5 says "closable tabs
  optional" and the option is per tab.
- **A colour as a class** — `tab class="danger"`. Right for a *meaning* and wrong
  for an identity: there is no fixed set of project colours, and a stylesheet
  cannot enumerate one.
- **Keeping every tab's content in the tree and hiding the unselected ones.** It
  makes reselecting a tab free and makes nine tabs cost nine subtrees, their
  subscriptions and their images. §5 asked for the other trade by name.
- **`border-bottom`.** Not available, as above — and adding per-edge borders to
  §8's subset to draw one underline would be a change to the CSS engine for a
  widget that can express it with a box.

## Consequences

- **`tabs` is `panel`'s first widget**, and `card`, `group-box`, `split-pane`,
  `collapse`, `carousel`, `statistic` and `skeleton` are still unbuilt.
- **A tab strip does not scroll.** Enough tabs and the row overflows its window,
  because §1's `scroll` does not exist — the same sentence that ends the popup
  work.
- **Nothing reorders tabs.** §5 does not ask for drag-to-reorder, and the model
  shape here would take it without a change: the strip draws the list it is given.
- **`CssColor.parse(String)` is new**, and is the door for every other value that
  arrives as text rather than as a parsed declaration.
- **A tab's content is rebuilt on reselection**, which is the cost of the laziness
  §5 asked for and is stated on `tab-panel` where somebody will read it.
