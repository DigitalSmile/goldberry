# ADR-0065: A part is styleable and not constructible

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §11; `docs/core-widgets.md` §3;
  `docs/design-system.md` §3; extends
  [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md); applies
  [ADR-0063](0063-data-flows-down-events-flow-up.md)

## Context

`checkbox` is the second control, and the first with a problem `button` did not
have: **it has two surfaces a theme must style differently.** The control is 32
tall, holds the label and is the hit target (§1.3: "hit targets ≥ 32×32 even when
the visual is smaller — checkbox glyph 16px, hit area 32"). The glyph is 16
square, has its own radius, its own border, and is the thing that turns blue.

A `ComputedStyle` carries one background, one radius and one border. One cascade
node cannot describe both. The options were:

1. **Hard-code the glyph in Java.** Then no stylesheet can touch it, which
   contradicts §11's "colours and metrics only via `--gb-*` tokens — themes
   restyle everything", and makes a high-contrast theme (§4, "an alias swap, not
   a special code path") impossible for this control.
2. **Make the glyph the checkbox's own box and hang the label beside it.** Then
   the gap, the alignment and the hit height have nowhere to come from, and the
   focus ring goes round a 16px square rather than the control.
3. **Give the glyph a cascade node of its own.**

## Decision

**The glyph is a part: `check-indicator` is a CSS type selector, and is
deliberately not registered in the KDL inflater.**

`Checkbox` is a `Widget.Leaf` whose `children()` are a `CheckIndicator` and a
`text` — child widgets, not child boxes, because a child widget becomes a child
*element* and an element is what the cascade can reach.

This is an **exception to the parity invariant, and a stated one rather than an
oversight.** §11 says every widget is a Java record, a KDL node and a CSS type.
That invariant is about the widgets in the **catalog**: an author picks one from
a list and puts it in a document, so all three forms have to agree or two of them
drift. A part is not in the catalog and has no independent existence — a
`check-indicator` outside a `checkbox` is a 16px square that means nothing, and
registering the node would let a document create exactly that.

What an author wants from a part is to *restyle* it, and a type selector is the
whole of that. So `Controls.controlTypes()` lists `checkbox` and not
`check-indicator`, and the parity test is never asked to inflate a node with no
meaning. The test asserts the absence, so the exception cannot become an accident
later.

### Three states, and `:indeterminate`

`docs/core-widgets.md` asks for tri-state. `MIXED` is a real state: a "select
all" over a partial selection is neither on nor off, and drawing it as either is
a lie about the data.

It matches **`:indeterminate`, not `:checked`** — an eighth pseudo-class in a set
the contract lists as seven. Two pseudo-classes cannot describe three states, and
the alternative ("checked plus a modifier") makes every stylesheet that wrote
`checkbox:checked` and meant "the tick is showing" silently wrong for the mixed
case. `Styled` gains `isChecked()` and `isIndeterminate()`, mutually exclusive,
mirrored onto the element by `WidgetRenderer` on the same pass and by the same
argument as `:disabled`: they are facts about the **description**, so the
stylesheet, the hit test and the semantics cannot disagree about them.

**Toggling never produces `MIXED`.** Mixed is a state the application can
describe and the user cannot reach — clicking a partial selection asks for "all
of them", which is `CHECKED`. Every desktop toolkit agrees, and the alternative
is a control that cycles through a state nobody wants.

### The mark is a toolkit shape, not an icon

The tick, the mixed-state dash and (ahead of `radio`) the dot are a closed
`Box.Mark` enum drawn by the painter, not `Icon`s. An `Icon` owns native memory
and must be closed exactly once; a widget is a value rebuilt every frame and
cannot hold one, which is why markup names an icon against a registry
([ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md)). Asking an
application to register a Lucide icon in order to get a tick inside its own
checkbox would be absurd.

The mark is drawn in `style.color()` — the foreground of that node, exactly as
text is the foreground of a `text` node — so `check-indicator:checked { color: … }`
is the one rule that moves it. Its proportions are of the box rather than
absolute, chosen against the 24×24 Lucide grid (§1.6), so a tick beside a Lucide
icon reads as the same hand at any size a stylesheet asks for.

### The value is controlled

Straight application of
[ADR-0063](0063-data-flows-down-events-flow-up.md): `bind` is where the value is
read from and `change` is what the click runs.

```kdl
checkbox bind="prefs.frost" change="toggleFrost" "Frosted sidebar"
```

The widget holds the read-only `Observable` half and has **no method** with which
to write. The tick moves when the application moves it. A checkbox whose `change`
handler does nothing does not move — which looks like a bug and *is* one, in the
application, exactly where it should be. A test asserts precisely that: a click
on a bound checkbox with an empty handler leaves the property and the tick alone.

A bound property may hold a `Value` **or** a `Boolean`, because an application
modelling a binary preference should not have to import a tri-state enum to bind
one. A null — a property that has not loaded — falls back to the markup's own
value rather than guessing that "not loaded" means "off".

### `Space`, and deliberately not `Enter`

`button` takes both, because activating it is the only thing it does. `Enter`
belongs to a dialog's default action (§2.3), and a checkbox that swallowed it
would leave a form with no keyboard route to submit once focus was on one. Every
desktop platform draws the line in the same place.

A click anywhere in the control toggles, label included, per
`docs/core-widgets.md` §3 — which matters more than it sounds, since a 16px
square is a small target and the label is usually five times as wide.

## Consequences

- `checkbox` ships: record, node, CSS type, three states, `bind` + `change`,
  keyboard, `:disabled`, and three golden images across both themes.
- Parts are now a category, and `radio`, `slider`, `select` and `tabs` all have
  one waiting. `Box.Mark.DOT` is already there for `radio`, which is the next
  control and the one that brings §7.2's roving arrow-key focus with it.
- `:indeterminate` is the first pseudo-class added since the CSS engine was
  written, and `docs/core-widgets.md`'s "states are pseudo-classes" list is one
  longer than the document says. The document is what should change.
- The showcase carries three checkboxes: one bound and wired — clicking it really
  does remove the paragraph, so the round trip is visible rather than described —
  one `MIXED`, and one disabled.
- **Open:** a part inherits `:disabled` by being handed the flag, not by the
  cascade. `docs/core-widgets.md` says "disabled state propagates down the tree;
  a disabled container disables its descendants", and nothing implements that
  generally. Passing the flag works for a control that builds its own parts and
  will not work for `form` or `group-box`, which is where it will have to be
  faced.
