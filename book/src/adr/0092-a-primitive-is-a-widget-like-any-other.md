# ADR-0092: A primitive is a widget like any other

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md`, extends [ADR-0091](0091-one-module-a-package-per-control.md)

## Context

`:core` shipped five widgets: `text`, `row`, `column`, `panel` and `spacer`, as
nested records inside a `Widgets` class in
`io.github.digitalsmile.goldberry.widget`.

They were there for a good reason that stopped applying. The widget tree, the
element tree, the cascade and the painter all had to be provable before there was
a catalog to prove them with — `WidgetParityTest` needed *something* whose KDL
node, CSS type and Java record could be checked against each other, and
`StyleCacheTest` needed a node with a type and classes to be wrong about. Five
primitives were the smallest set that made the invariant testable, and the class
comment said exactly that.

Then `:widgets` arrived, reached thirty types, and got a package per control
([ADR-0091](0091-one-module-a-package-per-control.md)). At which point:

- **`:core` was a widget toolkit's engine that also shipped five widgets.** No
  code in `:core`'s *main* sources referenced any of them — the survey found zero
  uses. Only five of its tests did.
- **`core-widgets.md` had specified their packages since v0.1** — `row`, `column`
  and `spacer` under `core`, `text` under `text`, `panel` under `panel` — and the
  code had them nested inside one class in a different module.
- **They were second-class in their own catalog.** Every other widget is a
  top-level record in a package named after it; these five were
  `Widgets.Row`, `Widgets.Panel`, reachable only through a holder class whose
  name means "all of them".

## Decision

**The five move to `:widgets`, as ordinary top-level records in the packages
`core-widgets.md` gives them**: `…widgets.core.Row`, `.Column`, `.Spacer`,
`…widgets.text.Text`, `…widgets.panel.Panel`. The `Widgets` holder class is
deleted. `:core` now has **no widgets at all**.

**`Attributes` stays in `:core`, promoted to a top-level type.** It is not a
widget — it is part of the widget *contract*: `Styled` asks for an `id` and
classes and the cascade matches on the answers, `Widget.key()` is what the
reconciler pairs two builds by, and `Attributes.of(KdlNode)` parses them off a
markup node the inflater owns. A widget in an application's own module implements
the same three methods and should not have to depend on the catalog to hold them
in a value. It was the only member of `Widgets` that belonged where it was.

**The registry follows the widgets**, as `…widgets.core.Primitives`, and
`Controls.inflater(…)` composes it exactly as it used to compose
`Widgets.inflater(…)`. Keeping it separate from `Controls` keeps that class's
sentence true — the catalog is what `:widgets` *adds* to the structural widgets —
and lets an application that wants a layout and no controls register just these.

**Four of the five affected tests moved with them; two split.**
`WidgetParityTest` and `FrameBenchmark` are about the catalog and went to
`…widgets.core` whole. `StyleCacheTest` and `BindingTest` did not, because they
reach into `Element`'s package-private internals — `update`, `cachedStyle`,
`WidgetRenderer.resolver` — which is exactly right for a test of the element tree
and impossible from another module. They stay in `:core` and use **local test
widgets**, the pattern `DragOriginTest` and `GestureAnchorTest` already
established. `BindingTest` split along a seam that turned out to be real: reading
`bind=` off markup is `:widgets`', and what an element does with a binding once it
holds one is `:core`'s `BindingLifecycleTest`.

## Alternatives considered

**Leave them in `:core`.** They work, and moving them touched 39 files. Rejected
because the reason they were there had expired: they existed to make the engines
testable before a catalog existed, the catalog exists, and a module that is
explicitly *not* the widget toolkit should not be the thing that ships `panel`.
The documentation had said so since v0.1 and the code had never caught up.

**Move `Attributes` too, so `:core` has nothing widget-shaped left.** Tempting for
symmetry, and wrong: `Styled` and the reconciler are core contracts, and an
application widget that wanted an id would have had to depend on the catalog to
get one. Symmetry is not a reason.

**Keep a `Widgets` facade in `:widgets` re-exporting the five**, so
`Widgets.Row` keeps compiling. Rejected as exactly the duplication this change
exists to remove: two names for one type is how the two stop agreeing, and there
is no external consumer to break.

**Give `:core` its own test-only widget set in `testFixtures`.** It would have let
all five tests stay put. Rejected because two of them do not want widgets at all —
they want *a* node with a type and a binding, which is four lines of local record —
and the other three genuinely test the catalog and belong beside it. A fixture
module would have been a third place for widgets to live.

**Fold the primitives' registry into `Controls`.** One fewer class, and it makes
`Controls` — the class named for one group — the thing that registers `row` and
`text`. A name that lies, and the codebase keeps rejecting those.

## Consequences

**`:core` no longer depends on anything widget-shaped to test itself.** Its
element-tree and cascade tests use local records, so they say what they are about:
nothing in `StyleCacheTest` is a fact about `panel`, and it used to look as though
it might be.

**`:core`'s test count fell by 25 and `:widgets`' rose by 25.** Nothing was lost —
the same 1,641 tests run — but coverage moved modules, and `:core`'s suite is now
smaller than the engine it covers might suggest. That is the honest shape: a test
that needs a widget is a test of the catalog.

**Three more exported packages** — `…widgets.core`, `.text`, `.panel` — on top of
ADR-0091's eleven. The descriptor grows with the catalog, which is the property
that makes it useful.

**`docs/core-widgets.md`'s preamble was wrong and is now right.** It claimed
`:core` held the primitives; it holds none. The package table is a description of
`:widgets` and nothing else.

**Every `Widgets.Attributes` in the tree became `Attributes`** — 139 of them —
plus 111 uses of the five types. Mechanical, done once, and the last time it is
cheap: the same argument ADR-0091 made about doing a rename at ten controls
rather than thirty.

**`text` is alone in its package** and will be until `span` and `link` are built.
That is the package `core-widgets.md` §2 specifies, and a package with one type in
it is a cheaper thing to look at than a type in the wrong package.

**The five are now stylable, keyed and documented on exactly the same terms as
`button`.** They were already, in principle — the parity invariant held for them
from the start. What changed is that a reader looking for `Panel` now finds it
where `core-widgets.md` says it is, instead of inside a class called `Widgets` in
a module called `core`.
