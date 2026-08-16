# ADR-0063: Data flows down, events flow up

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §9, §11; narrows
  [ADR-0062](0062-bind-is-a-path-and-nothing-else.md); **amends §9's "one/two-way
  binding"**

## Context

§9 says `bind` "is one/two-way binding against an observable model".
[ADR-0062](0062-bind-is-a-path-and-nothing-else.md) shipped the reading half and
described the writing half as waiting for a control that writes — a checkbox
resolving a typed property and calling `set` from its own click handler.

That plan is at odds with the rest of the toolkit. A widget is an **immutable
description** ([ADR-0004](0004-three-tree-retained-declarative-model.md)); state
lives on the element and changes only through `setState`
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)); a
build must be pure. A control that writes to the application's model breaks that
in the one place it is hardest to see: the write does not come from application
code at all, it comes from a `bind=` attribute in a data file, so the answer to
"what changed this value?" is a string somebody typed into markup — possibly
while the window was open, since markup hot-reloads
([ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)).

Two-way binding is also the feature that makes the update graph a graph. Once a
control writes to a model that other controls read, the order of updates is a
property of the binding topology rather than of the code, and every framework
that shipped it — WPF, Angular 1, Knockout — grew a vocabulary for controlling
it: modes, triggers, delays, `UpdateSourceTrigger`, `$digest` cycles. Goldberry's
entire update story today is "mark dirty, flush once per frame", which is
comprehensible because it is one direction.

## Decision

**Binding is one-way, and the type system says so.** `Observable<T>` is the half
of a `Property<T>` that can be read and watched; `Property<T>` adds `set` and is
what the application keeps. `Bindings.resolve` hands out an `Observable`, and
`Widget.binding()` returns one — so a widget built from markup **cannot** write to
the model, because there is no method to call.

**A control reports, and the application decides.** What the user did travels back
up the way it already does: as an action, through the `Actions` registry
([ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md)). A checkbox will be
`checkbox bind="prefs.frost" change="toggleFrost"` — the value flows down through
`bind`, the intent flows up through `change`, and the one line that mutates
anything is Java the application wrote.

**The registry is not a way back to a writable handle.** `Bindings.bound()`
returns observables, and there is no `writable(path)`. A path is how a value is
*published* to markup; something that could also fetch it back for writing would
make the registry a service locator, and any code holding the `Bindings` object
could then mutate any model it names.

§9's "one/two-way" is amended to say one-way. That is a change to the
architecture document, made deliberately and recorded here rather than by editing
history.

## Alternatives considered

**Two-way, as §9 originally said.** The reason it is in the document is real:
`checkbox bind="prefs.frost"` with no handler is less to write than a `bind` plus
a `change`, and for a settings dialog of thirty toggles that difference is thirty
handlers. Rejected because the saving is at the wrong end — it saves typing in the
easy case and costs comprehensibility in the hard one, and the hard one is a
control writing a value another control's `bind` reads, mid-frame, from markup.

**Two-way as an opt-in**, `bind` versus a `bind-two-way`. Rejected: an opt-in is
still the feature, with all of its semantics to specify, plus a second spelling.
An escape hatch that is used once is a feature that has to work forever.

**Convention, not types** — hand widgets the `Property` and write down that they
must not call `set`. That was the position ADR-0062 shipped with, and it is the
weaker one: the rule holds until somebody in a hurry reaches for the method that
is right there. The split costs one interface.

**A read-only *wrapper*** rather than a supertype, so `Observable` cannot be cast
back to `Property`. Rejected as disproportionate: it allocates per resolve and
breaks identity comparisons, to stop a downcast that a widget author could only
write on purpose. This is a design boundary, not a security boundary — see below.

## Consequences

**Controls are "controlled" in the React sense**, and this is the consequence
worth understanding before writing the next widget. A checkbox draws the value it
is bound to. Clicking it does *not* move the tick; it raises a change, and the tick
moves when the application sets the property. A handler that forgets to set
produces a control that visibly does nothing — which is the same class of bug
React's controlled inputs have, and the same defence applies: the UI is a function
of the state, so a control that will not move means the state did not change, and
that is exactly where the bug is.

**Local, ephemeral state stays local.** Nothing here says a control may not have
state of its own — a text field's caret and selection, a spinner's mid-edit text,
an IME's preedit — that is what `State` on the element is for. The rule is about
the *model*: what the application owns, only the application writes.
`text-input` will be where this line has to be drawn precisely, and M5's IME work
is where it will hurt if it was drawn wrong.

**The type split is defeatable by a cast.** `Property implements Observable`, so a
determined widget can `(Property<?>) binding()` and write. Nothing prevents that,
and nothing tries to: the point is that the honest path is one-way and the
dishonest one has to be typed out deliberately. A test pins the *signatures*, so
widening `resolve` or `binding()` back to `Property` fails the build rather than
being noticed later.

**§9 is now narrower than it was**, and any reader who took "one/two-way" as a
promise will find one direction. That is why this is a record and not a silent
edit: the document is amended, ADR-0062's plan for the writing half is withdrawn,
and both say so.

**`Property.set` remains fully public.** It is the application's API, used from a
button handler, from a completion on the UI thread, from a hot-reload callback.
One-way is about which *layer* may write, not about ceremony around writing.
