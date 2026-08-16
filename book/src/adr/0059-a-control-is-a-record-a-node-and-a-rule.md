# ADR-0059: A control is a record, a node and a rule

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7, §8, §9, §11; `docs/core-widgets.md` §3; `docs/design-system.md` §3; [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md), [ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md), [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md), [ADR-0058](0058-a-press-captures-the-pointer.md)

## Context

`:widgets` held one `module-info.java`. Everything under it existed — the three
trees, the cascade, KDL, input, focus — and nothing had ever been assembled into
a control, so none of it had been asked the questions a control asks.

`button` first, because it is the smallest thing that touches every seam at once:
the §11 parity invariant, the cascade including pseudo-classes, pointer *and*
keyboard activation, and the `action` half of §9. Whatever shape it takes is the
shape the other twelve controls copy, so it is worth deciding rather than
discovering twelve times.

## Decision

**A control is a Java record, a KDL node and a CSS type, and the test says so.**
`ButtonTest` builds the same button both ways and asserts the two values are
equal. That is the §11 invariant made mechanical: two constructors for one widget
drift the first time either grows a field, and a test that compares them is the
only thing that notices.

**Variants are classes, not an enum.** `button.primary` in a stylesheet,
`class="primary"` in markup, `new Button("Save").styled("primary")` in Java. An
enum would read better in Java and would be a second vocabulary that KDL and CSS
could not use — and the parity invariant would then be comparing two things that
are not the same value.

**Nothing visual is in the widget.** The height, the padding, the colours and all
four variants are in `controls.css` in the toolkit-base layer. What the record
owns is behaviour: focusable, activates on a click and on `Space`/`Enter`,
consumes what it acts on. A control that hard-coded its own colour would be one a
theme could not reach.

**Metrics are the base layer's, colours are the theme's.** `docs/design-system.md`
§3 says component metrics ship as component-token defaults; this splits them by
who can know the answer. Height 32 and padding `0 12px` are theme-invariant and
sit in the base rule. What a *hover* looks like is not: it lightens on Nord dark
and darkens on Nord light, so `--gb-button-bg-hover` is defined in each theme
file. An application overriding a component token restyles every button without
touching a rule, which is what §3 asks for.

**The action is a field on the immutable record.** A `Runnable` is not state, it
is part of the description — rebuilding a button with a different action means it
now does something else, which is what the author intends. Nothing in the widget
remembers a press; the press belongs to the router, on the element
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md),
[ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)).

**Markup names an action; it cannot be one.** `button press="save"` resolves
against an `Actions` registry. KDL is data, and a markup file that could name a
Java method would be code with a different syntax — hot-reloading it would mean
hot-reloading code. The indirection is also what makes reload work: a reloaded
document re-resolves every name against the same registry, so the new tree's
buttons are wired to the handlers the old one had
([ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).

**A registry is strict by default and lenient on request.** An unbound name is a
typo, and a button that silently does nothing is the hardest kind of bug to
notice — no error, no log line, and it looks perfectly normal. But a preview or a
document mid-edit needs to inflate with handlers not yet written, so
`Actions.lenient()` exists and `Controls.inflater()` uses it. Same asymmetry
ADR-0051 drew between a first load and a reload.

**The router synthesizes `CLICKED`; controls do not derive it.** A click is a
press and a release on the same node. A release is not: dragging off a button and
letting go is how a user cancels, and it is a gesture people rely on. The release
still reaches the captor, because the captor has to stop looking pressed
([ADR-0058](0058-a-press-captures-the-pointer.md)) — but it is not an activation.
Deriving that in each control would mean each one re-deciding what "the same
node" means; here it means the release landed on the pressed element or inside
it, so releasing on a button's own label is a click on the button.

Only the primary button clicks. A right-click opens a context menu; a control
that activated on one would be a menu that also pressed the thing under it.

**`Space` and `Enter` activate, and a held key does not repeat.** Holding Space is
one activation, because a button is not a key. A control that wants the opposite —
a spinner's arrows — will say so, and `KeyEvent.isRepeat()` is what it will say it
with.

**Padding became four edges.** `padding: 0 12px` is the button's own metric from
the design system, and `ComputedStyle` carried a single `StyleLength`. So there is
now an `Insets`, CSS's 1–4 value shorthand, and the four longhands. A shorthand
with one bad part is dropped whole: half of it applied is harder to see than none
of it, because two edges move and two do not, and that reads as a layout bug
rather than a typo.

`Insets` lives beside `StyleLength` in `natives.yoga` rather than in `layout` or
`css`. Both of those name it — a `ComputedStyle` carries one and a `Box` is built
from one — and `layout` already depends on `css`; putting it in either would make
that dependency mutual for one record.

## Alternatives considered

- **A `Variant` enum on the record.** Rejected above: it cannot be spelled in KDL
  or matched in CSS, so it would break the invariant it was meant to serve.
- **Activate on `RELEASED` and let the widget check bounds.** Rejected: the widget
  does not know its bounds — that is the hit-test snapshot's, one layer down — and
  every control would need the same wrong guess.
- **A `Clickable` interface with an `onClick` method, instead of a `CLICKED`
  kind.** Rejected: it would need its own capture and bubble path and its own
  `consume()`, duplicating machinery that already exists to gain a method name.
- **Put the control's colours in the base layer.** Rejected: a base rule can only
  state one value, and hover lightens on one theme and darkens on the other.
- **Ship `button` with no variants until borders and radii exist.** Rejected: the
  variants cost four rules and a dozen tokens, and they are what proves classes
  are the right mechanism. What is genuinely missing is stated below rather than
  approximated.
- **Assert the appearance only as values.** Height, padding and resolved colour
  per state are what the cascade produces, and checking them is cheap and exact —
  but they cannot catch a padding applied to the wrong *edge* or an icon drawn at
  the wrong origin, because both resolve to the values asked for. So there are
  golden images as well. §14 ties the corpus to showcase screens, and those still
  do not exist; a control's own images are the smaller thing that can be built
  first.

## Consequences

- **The pattern is set and the next control is mechanical.** `toggle`,
  `checkbox`, `radio` and the rest are the same four pieces: a record, a registry
  line, a base rule, a parity test.
- **What `Box` cannot express is now visible in a shipped stylesheet.** The 8px
  radius, the 1px border on `ghost`, the `body-strong` weight, and the 2px
  `--gb-focus` ring at 2px offset are all in `docs/design-system.md` and none of
  them can be drawn. `controls.css` says so in a comment rather than
  approximating them, and `:focus-visible` stands in with a background change so
  keyboard focus is at least visible. Each arrives with the thing that paints it.
- **An icon is a `Box` now**, which closes what
  [ADR-0043](0043-icons-are-stroked-paths.md) left open — "nothing decides an
  icon's intrinsic size until the widget model does". The answer turned out
  simpler than the question: an icon is built at a size and that size *is* its
  intrinsic one, so unlike text it needs no measure function and no callback into
  C. `Button` takes a label, an icon, or both. The icon is **borrowed**: a widget
  is a value rebuilt every frame and must not own something with a `close()`, so
  markup names an icon against a registry rather than building one — a document
  reloaded on every keystroke would otherwise leak one per reload.
- **`:disabled` is the one pseudo-class a widget owns.** `:hover`, `:active` and
  `:focus` are facts about the pointer and the keyboard and the router derives
  them; `disabled` is a fact about the description. `WidgetRenderer` mirrors
  `Styled.isDisabled()` onto the element before the cascade is asked, so the
  stylesheet, the hit test and the activation cannot disagree. A disabled control
  still lays out, paints and hit-tests — it just does not act, and it leaves the
  Tab order, because a focusable control that never responds strands a keyboard
  user on it.
- **Four golden images.** The variants on both themes, the five states side by
  side, and the icon layout. Value assertions check what the cascade *resolved*;
  these check what Blend2D *drew*, which is a different question and the one that
  catches a padding applied to the wrong edge. `GoldenImage`, `TestFrames` and
  `RendererRequirement` moved into `:core`'s test fixtures to get there — shared
  rather than copied, because two golden comparators would drift on the
  tolerance and the tolerance is the whole argument of
  [ADR-0050](0050-golden-images-have-a-tolerance.md).
- **The showcase is a widget tree.** It was hand-built `Box`es; it is now a
  stateful widget with a bar, a sidebar, wrapped prose and a row of buttons —
  which is what makes `setState`, reconciliation, theme switching, focus
  traversal and `:hover` repaints run outside a test at all. `Window` now
  repaints itself when input changes a pseudo-class, because otherwise every
  application would have to remember and the one that forgot would have buttons
  that never light up.
- **`bind` is still missing** — the read half of §9. `action` is here; a control
  that shows a value rather than triggering one needs the other.
- **Two more themes' worth of tokens.** Every control that ships adds component
  tokens to both Nord files. That is the cost of the split above, and it is paid
  per control rather than per theme.
