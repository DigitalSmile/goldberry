# ADR-0077: Disabled propagates for input and not for paint

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/core-widgets.md` (states); `docs/design-system.md` §2.1;
  closes the question left open by
  [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md) and
  [ADR-0073](0073-a-composite-is-one-tab-stop.md); extends
  [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md)

## Context

`docs/core-widgets.md` is one sentence: *"Disabled state propagates down the
tree; a disabled container disables its descendants for input and semantics."*

What shipped was a control passing the flag to the children **it builds itself** —
`checkbox` to its glyph, `radio-group` to its options. That is enough for a
control that constructs its own subtree and useless for `form` and `group-box`,
which contain widgets they did not build and know nothing about.

`radio-group` also had a rule that should have been read as a warning:

```css
radio-group:disabled radio:disabled { opacity: 1 }
```

The group is 45% *and* passed `disabled` to every option, so without that undo
the fade applied twice and landed at 20%. **A rule whose only job is to undo its
own mechanism is the mechanism telling you it is the wrong one**, and ADR-0073
recorded it as an open question rather than a fix.

## Decision

### Input propagates; paint does not

The sentence says "for input and semantics". It does **not** say "for paint", and
paint is where the double-fade came from.

- **Input** — a descendant of a disabled container is unreachable: no press, no
  click, no wheel, no focus, no keys.
- **Paint** — `:disabled` stays on the node that **declared** it. The container's
  own 45% already fades everything under it, because the painter multiplies
  opacity down a subtree. A descendant that also matched would be faded twice.

That split is what deletes the undo rule rather than generalising it, and
`radio-group` stops pushing `disabled` onto its options entirely. An option's
*own* flag is kept, because a document may disable one option in a group that is
otherwise available.

It costs nothing in expressiveness: §2.1 requires disabled to be 45% opacity on
the whole control and **never** a colour remap, so a descendant has no
disabled-specific appearance to express in the first place.

### Effective disabled is derived, not stored

`PointerRouter.isDisabled(element)` walks up the ancestors and asks each widget's
own `Styled.isDisabled()`.

The obvious alternative is to push a flag down the tree on every build, or to
mirror it onto each element as `:disabled` the way the renderer mirrors `:checked`.
Both are **a second copy of a fact the tree already holds**, and ADR-0073 has
already been through what that costs: the two disagree the first time something
changes without telling the thing that cached it, and there is no event that
fixes it because a widget being rebuilt does not know a router exists.

Derived, there is nothing to invalidate, nothing to leak when an element
unmounts, and no frame ordering to get wrong — the answer cannot be stale because
it is computed from the tree at the moment it is asked. It costs a walk up the
ancestors, on input events only, which is the same walk `chain()` already does
for every dispatch.

### The router is the choke point, not the widget

One guard in `dispatch`, and `isFocusable` gaining `&& !isDisabled(element)`.

This is the argument ADR-0073 already made for `:hover`: *one choke point, every
control, forever.* A control's own `disabled` check becomes a second line of
defence rather than the only one, and a control written **without** one is still
unavailable inside a disabled container. `DisabledPropagationTest`'s widget
deliberately has no `disabled` check and deliberately reports `isFocusable() ==
true` regardless, so the tests cannot pass by the widget quietly opting out.

**The keyboard needed no guard at all**, and that is the part worth noticing:
focus is the only route a key event has, so a subtree that cannot be focused
cannot be typed into. One line about focus covers `onKey`, `onKeyCapture` and
`onText` together.

### The cut is input versus observation

`PRESSED`, `RELEASED`, `CLICKED` and `WHEEL` are refused. `MOVED`, `ENTERED` and
`EXITED` still arrive, and hit testing and the cursor are untouched.

That line keeps ADR-0059's two cases working, both of which "drop every event"
would have broken:

- a disabled control still **hit-tests**, so a click cannot fall through to
  whatever is behind it — unavailable is not invisible;
- a tooltip explaining **why** something is unavailable needs the enter and the
  exit, and that is the one case that most wants an event from a disabled thing.

`cursor: not-allowed` also still resolves, because the cursor rides on the
painted box (ADR-0057) and never asked about input.

## Consequences

- `form` and `group-box` can be built without inventing anything: they declare
  `isDisabled()` and everything inside them becomes unavailable. So can a
  `dialog` running §1.7's `closing` phase, which asks for "input disabled the
  instant closing starts (no ghost clicks)" — the same mechanism.
- **The undo rule is deleted rather than generalised.** Generalising it would
  have needed `:not()`, which is not in §8's subset, or a universal selector and
  a descendant combinator — and either would have been machinery in service of a
  design that was wrong.
- The test lives in `:core` against bare widgets, because its users — `form`,
  `group-box`, `dialog` — do not exist yet and will look nothing like a radio
  group. Same reason as `FocusScopeTest` and `DragOriginTest`.
- **Open: semantics is half the sentence and there is no semantics layer.**
  AccessKit is M5. When it arrives, "disabled" for a11y should read the same
  derived walk rather than a second copy — which is the whole reason this one is
  derived.
- **Open: a control's own `disabled` check is now redundant.** Every control in
  the catalog still has one and none of them is reachable. They are kept: they
  are one line, they make a widget correct when called directly from a test, and
  removing them would make the widgets depend on the router for correctness
  rather than merely for reachability.
- **Open: `Widgets.Row`, `Column` and `Panel` cannot be disabled.** None of them
  has a `disabled` flag, so today the only container that exercises this is
  `radio-group`. That is a gap in the primitives rather than in this mechanism.
