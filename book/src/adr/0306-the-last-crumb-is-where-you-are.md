# 306. The last crumb is where you are

Date: 2026-09-13

## Status

Accepted. Builds `docs/core-widgets.md` §6's `breadcrumbs` and opens the `nav`
package, which §11's table has named since v0.2 and which had nothing in it.

## Context

`nav` was added to the package table as the tenth group, with one sentence
justifying it:

> `breadcrumbs`, `steps` and `wizard` all answer "where am I in a sequence",
> which is neither a surface (`panel`) nor a control that reports a value
> (`controls`); folding them into either would have made that package's name a
> lie.

`breadcrumbs` is the first of the three. §6 specifies it in four sentences and
every one of them is a decision somebody could get wrong:

1. `crumb` children with a label, an optional icon and an action.
2. **The last is the current page and is not a link.**
3. Overflow collapses the middle into a `…` that opens a `menu` of the hidden
   crumbs, rather than eliding characters.
4. The separator is a `chevron-right` icon in `--gb-text-muted`, **not a
   character**, so it never joins the text run.

## Decision

### The trail decides which crumb is current, and a document cannot

The last one, written onto the crumb on every build — `tabs` telling a `tab` it
is selected, exactly ([ADR-0107](0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
A document that could mark a middle crumb current, or none of them, would be able
to describe a path that does not end anywhere, and a trail's only invariant is
that it ends where you are.

A current crumb is **silently demoted**: not focusable, and its handler does not
run, whatever `press=` it was written with. That is deliberate rather than a
refusal, and the reason is how trails are actually built — from a loop over a
path, where every crumb gets the same handler and the last one is *supposed* to
be inert. Refusing it would make the common case an error.

The enforcement is on `Crumb` and not on the trail's wiring, so a crumb built by
hand in a test behaves the way one built by a trail does.

### Overflow keeps the first and the tail

Past `collapseAfter` crumbs the row shows the **first**, a `…`, and the last
`collapseAfter − 2` — so the row holds exactly `collapseAfter` things, counting
the `…` as one. The first stays because "where does this tree start" is the
question a deep path makes hardest; the tail stays because that is where you are.

`collapseAfter` defaults to §3's 4 and is raised to 3 rather than refused if
something asks for less: the number is a hint about width, and a hint that cannot
be met should be met as closely as possible rather than stop a window opening.

**Nothing is elided inside a name**, which is §6's own argument: a truncated
folder name is worse than a hidden one because it still looks like a name.

### The `…` is the one part in the catalog that takes the focus

`tab-close`, `select-chip-remove` and `chip-dismiss` are all deliberately not Tab
stops, because each has a keyboard route through the control it sits in. This one
has none — **the crumbs behind it are not in the tree**, so there is no node for
the keyboard to reach and no key on the trail that could stand for "the fourth of
the hidden ones". A `…` only a pointer could open would put part of a navigation
path out of a keyboard's reach, which §13 does not allow.

It answers `Space`, `Enter` and `Down`, and reports `Role.MENU_BUTTON`.

### The separator is a mark

[Box.Mark.Kind#CHEVRON_END], whose own documentation anticipated this use. A `>`
typed between two labels is part of a paragraph: it shapes with the words, takes
their colour, wraps with them and is read aloud. A node of its own does none of
those.

### The widget is stateful, and it is stateful for two facts

Where its `…` was painted, and which window it is in. Neither is describable: a
widget is a value rebuilt every frame, and opening a popup needs a `Host`
([ADR-0140](0140-a-widget-may-reach-its-window.md)). Everything else about a trail
is a pure function of its crumbs.

`breadcrumbs` as a **CSS type** is therefore the node the stateful one builds, per
[ADR-0109](0109-a-tab-arrives-and-departs-on-the-frame-clock.md): two `breadcrumbs` nodes
nested in the cascade would take every rule twice.

## Consequences

**The `nav` package exists**, and `steps` and `wizard` have somewhere to land that
is not `panel`.

**A trail does not wrap and does not shrink its crumbs.** A path wrapped onto two
lines puts where-you-are under where-you-started, which is the one arrangement it
must not have — so the answer to a narrow window is the overflow menu, which is
why there is one.

**The semantics are incomplete and say so.** §6 asks for "a navigation landmark
containing links"; `Role` has no `LINK` and no landmark. The crumbs answer
`BUTTON` and the row answers `GROUP`, which is honest — a role nothing can consume
is a value written for a bridge that does not exist. `book/src/TODO.md` carries the
rest until the AccessKit bridge.

**The overflow menu is built at the moment of the click**, not banked. It is a
function of the crumbs and the crumbs are the application's, so a menu held from
build time would be the path as it was one frame ago.

**A hidden crumb with no `press` still gets a menu row, disabled.** It is part of
the path, and hiding it would make the menu a different list from the trail.

## Alternatives considered

**Let `current` be an attribute.** Rejected — see above. It is the whole
invariant, and `tabs` already settled the same question the same way.

**Refuse a `press` on the last crumb rather than ignoring it.** Rejected: a trail
is built from a loop, so this would make the ordinary way of writing one an
error, and the workaround would be a conditional in every caller.

**Elide long labels and keep every crumb.** Rejected by §6 in the specification,
and it is right: `…/Ref…/Mid…/Shi…` is four names a reader cannot identify, where
`Home … Hobbiton The Red Book` is three they can plus a control for the rest.

**Keep the *last* `collapseAfter − 1` and drop the first.** Rejected: the root is
the crumb that tells you which tree you are in, and a trail that starts with `…`
has thrown that away to save the same amount of width.

**Anchor the menu by id rather than by a reported rectangle.** Rejected: it would
make an `id` mandatory on any trail that might overflow, and two trails in one
window would need two ids to tell their overflows apart. [Located] already answers
the question, and its answer is the *painted* rectangle, which is where the user
is looking ([ADR-0270](0270-a-popup-is-placed-again-when-its-window-moves.md)).
