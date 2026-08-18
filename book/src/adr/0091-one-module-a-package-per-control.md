# ADR-0091: One module, a package per control

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md`, amends [ADR-0014](0014-single-widgets-module.md)

## Context

[ADR-0014](0014-single-widgets-module.md) made two decisions and only one of them
has held.

**One module** — `goldberry-widgets` rather than `goldberry-controls` plus
`goldberry-charts` plus the rest — is still right, for the reason it gave: a
`button` and a `line-chart` are the same kind of dependency to an application, and
splitting them makes every consumer's build file longer for no benefit anyone
could name.

**One package** was the same argument applied one level down, and it does not
survive contact with the size of the catalog. `io.github.digitalsmile.goldberry.widgets`
holds thirty types today — ten controls and twenty of their parts — with `form`,
`panel`, `nav`, `overlay` and `collection` still to come, and
`docs/core-widgets.md` has specified packages for all of them since v0.1:

> All of these live in the **single `goldberry-core` Gradle module** — separated
> by *package*, not by artifact.

So the document and the code disagreed, and the document was the one that had
thought about where `date-picker` goes.

There was also a concrete cost. Every part in the catalog is package-private on
purpose ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)) — a
`slider-thumb` is CSS-selectable and deliberately not constructible. With one
package, "package-private" meant "visible to the entire catalog", so nothing
stopped `Checkbox` from reaching into `SliderThumb`. The encapsulation the parts
rely on was a convention rather than a boundary.

## Decision

**One module, packages by group** — `core-widgets.md`'s table, verbatim — **and
one package per control inside each group.** `…widgets.controls.slider` holds
`Slider` and its nine parts; `…controls.checkbox`, `.radio`, `.toggle`, `.knob`,
`.button`, `.badge`, `.progressbar` and `.spinner` are its siblings. `form`,
`panel`, `nav`, `overlay` and `collection` follow as they are built.

The second level is the one that does the work. Stopping at `controls` would have
grouped the catalog without changing what "package-private" means — thirty types
sharing one namespace is a smaller version of the same problem. Splitting per
control makes a part invisible to every control but its own, which is the
strongest form of the rule ADR-0065 states and the only one a compiler enforces.

`…controls` itself keeps exactly what its members share: `Scale`, which `slider`
uses and a future `fader` will.

**`Controls`, `Actions`, `Icons` and `Density` stay at the root**, because they
are not widgets. They are the module's furniture: the KDL registry, the
stylesheets, and the three lookups a document resolves names against. An
application touches exactly one of them to wire a window up and then never again,
and burying that behind `.controls` would put the entry point inside one of the
things it assembles.

**`nav` is added to the document's nine.** `breadcrumbs`, `steps` and `wizard`
all answer "where am I in a sequence", which is neither a surface nor a control
that reports a value; folding them into `panel` or `controls` would have made
that package's name a lie. Principle 3: extend deliberately.

## Alternatives considered

**Keep one package.** It is one fewer thing to decide when adding a widget, and
that is genuinely worth something. Rejected on the arithmetic: thirty types now,
and the specified catalog is roughly triple that. A package whose contents nobody
can hold in their head is not organised, it is merely flat.

**Stop at `controls`,** one package for the whole group. Half the directories and
one import line per consumer instead of two. Rejected on the encapsulation
argument above: it groups the catalog without making the grouping mean anything
to the compiler, and the parts are the reason the split is worth doing at all.

**A package per widget with no group level** — `widgets.slider` rather than
`widgets.controls.slider`. Shorter, and rejected because the group level is what
`core-widgets.md` specifies and what tells a reader where `date-picker` goes when
it arrives. A flat list of forty packages is not a structure.

**Split into modules after all**, one per group. Rejected for ADR-0014's original
reason, which has not changed: JPMS module boundaries are a *distribution*
decision, and nobody wants to depend on five sixths of a widget toolkit.

## Consequences

**Parts are properly encapsulated for the first time.** Package-private now means
"inside this control", so `Checkbox` cannot reach `SliderThumb` — which it could
have all along, and which nothing but discipline was preventing. The move found
one place where that discipline had already slipped: `ProgressFill` had been
widened to `public` during the restructure, and is package-private again.

**A cross-package javadoc link to a part is impossible, by construction.** Nine
`[SomePart]` links became code spans, because a package-private type in another
package cannot be linked and a link that does not resolve is worse than a name.
Links to public widgets are fully qualified instead — the form the codebase
already used for `[io.github.digitalsmile.goldberry.icon.Icon]`. Verbose, and the
verbosity is load-bearing: it is visible in the source that the reference crosses
a boundary.

**Every import of a control changed**, in the showcase and in the tests. Cheap
once, and it is the last time it will be this cheap: doing it at ten controls is a
mechanical rename, and doing it at thirty is a merge conflict with everything in
flight.

**Two javadoc links in `Controls` became code spans.** Its `controlTypes()`
comment names the parts it deliberately excludes, and those names are no longer
resolvable from the root package. A link that cannot resolve is worse than a name,
so they are names.

**`module-info` exports eleven packages and will export many more.** One line per
control, each exporting exactly one public widget (two for `radio`) and hiding its
parts — which is the property that made JPMS worth the trouble in the first place
([ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md)): a package that is
not exported is not API, and that is now a per-*control* statement rather than an
all-or-nothing one. The cost is a descriptor that grows with the catalog, and an
`exports` line is the cheapest possible place to notice a new public type.

**Tests mirror the structure**, so a control's tests sit in its package and can
reach its parts. Two cross-cutting suites stay at `…controls` — `ControlShrinkTest`
and `MotionGoldenTest`, which are about every control at once — and three stay at
the root with the furniture they exercise. `TestFont` widened three members to
`public` to be reachable from the packages it serves, which is the ordinary cost
of a shared test harness crossing a boundary it did not used to cross.

**`docs/core-widgets.md` is the authority again**, which is the state it was
supposed to be in. The package table there is now a description rather than an
aspiration, and a widget that does not fit one of its packages is a signal to
extend the table — as `nav` did here — rather than to drop the type somewhere
convenient.
