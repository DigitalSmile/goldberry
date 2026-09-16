# 327. A hover is a node property, not a menu's

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G33.

## Context

The router has derived `PointerEvent.Kind.ENTERED` and `EXITED` since it was
written. They are synthetic — computed from pointer flow, in the same walk that
moves `:hover` along the ancestor chain — and exactly two widgets could hear them:
`menu.MenuTitle` and `menu.Item`, each of which takes an `onHovered` because a
menu bar opens on hover.

Nothing else could ask. An application that wanted a hover-hold preview on a
search result had two ways out, and both are wrong:

- Make the row a `menu.Item`, which is choosing a widget for its event hook. An
  `Item` brings a tick column, an accelerator and a chevron that a search result
  has no use for, and its role is `menuitem`, which a screen reader will announce.
- Write a `HoverRegion` widget in the application — a `Widget.Leaf` implementing
  `Handles` whose whole body is a three-arm switch over `event.kind()`. It
  reimplements nothing, because the events are the toolkit's own, but it is a
  widget an application owns for an *input* concern, which is the shape this
  project's no-reimplementation rule exists to delete.

Nothing was broken. The hook had simply only ever been needed by menus, which is
why it was only on menus.

## Decision

**Two `Runnable`s on `Attributes`, run by the router beside a widget's own
handler.**

```java
// io.github.digitalsmile.goldberry.widget.attr.Attributes
public Attributes onPointerEnter(Runnable action);
public Attributes onPointerExit(Runnable action);
```

with the chainable pair on `Attributed`, so they compose with any widget:

```java
new Row(new Button("Open", this::open), new Spacer(), new Text(name))
    .onPointerEnter(this::armPeek)
    .onPointerExit(this::cancelPeek)
```

### On `Attributes`, not as a widget

That is where every other cross-cutting node property already lives — `tooltip`,
`name`, `contextMenu` — and the argument is the same one ADR-0105 made for a
tooltip: "attaches to any widget" is what this means, and a catalog where each
control had to carry its own would have thirty chances to forget. A `HoverRegion`
container would also have to sit in the tree, take part in layout, and be
explained to anyone reading the markup; an attribute is invisible to all three.

### It is about the subtree

The hooks are run from `PointerRouter.emit`, which is called from `updateHover` —
the same walk that moves `:hover`. `:hover` applies to a node **and every ancestor
of it**, because `.card:hover .title` has to work, and so does this. A hook on a
`row` fires once when the pointer arrives anywhere inside it and once when it
leaves altogether; moving between the row's own children raises nothing.

That is the behaviour a hover-hold wants and is awkward to build from the outside:
a per-leaf hook would have to be debounced against the gaps between siblings.

### It consumes nothing, and cannot

The event these derive from is synthetic and delivered to one element rather than
down a capture/bubble chain, so there is nothing here a hook could swallow. A
press that lands inside still belongs to whatever is inside. This is the concrete
difference from the container widget the stopgap was: that one sat in the tree
and had to be trusted to pass events through.

The hook runs **after** the widget's own `Handles.onPointer`, so a control that
already acts on hover — a `menu-title` opening its menu — has done its work before
an application's hook sees the same arrival.

### There is no markup form

`Attributes.of(KdlNode)` parses `id`, `class`, `tooltip`, `context-menu` and
`name`, and it cannot parse this: a `Runnable` is not a KDL value, and the
registry that turns `press="app.save"` into one is the *inflater's* `Wiring`,
which `Attributes.of` does not see. Widening `Attributes.of` to take a `Wiring`
would push the action registry into `:core`'s widget contract for two attributes
nothing has asked for in markup.

So this is Java-only, and said so out loud in the javadoc rather than left as a
hole somebody rediscovers. §11's parity invariant is about widgets built two ways
producing the same *value*; an attribute markup cannot express is not two values,
it is one form.

## Consequences

- `Attributes` grows from six components to eight. The six-argument constructor is
  kept, exactly as the three- and five-argument ones were (ADR-0105, ADR-0260), so
  no existing call site moves.
- `menu.MenuTitle` and `menu.Item` keep their own `onHovered`. They are not the
  same thing: a menu's hover drives *the menu's* state machine — intent delays,
  submenu opening, sibling collapse — and is wired by `Menus` on every open rather
  than written by an author. Replacing it with this would be a rewrite of the menu
  for no gain.
- Two `Runnable` fields on a value every widget carries. They are null for
  effectively every node, and `PointerRouter.hook` returns after one `instanceof`
  when they are.
- **A node unmounted under the pointer still hears its exit.** The router lets go
  of an element that has left the tree and re-hit-tests against the frame just
  painted (ADR-0303), and that is the same walk these are raised from — so the
  hook fires on the frame the router notices. What is still not guaranteed is a
  teardown with no frame after it: a window closing takes its tree with it and
  nobody is told, so a caller holding a timer cancels it on dispose as well.
