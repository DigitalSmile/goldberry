# ADR-0062: `bind` is a path, and nothing else

- **Status:** Accepted, **amended by
  [ADR-0063](0063-data-flows-down-events-flow-up.md)** — binding is one-way, and
  the writing half described below is withdrawn
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §9, §17; completes the markup contract
  begun in [ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md) and
  the registry pattern of [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md)

## Context

§9 gives markup two ways to reach the application: `action`, which names something
to *do*, and `bind`, which names a value to *follow*. The first shipped with the
button. This is the second.

§17 left one thing open: **how much of an expression a `bind` attribute may
contain** — "dotted paths only vs. mini-expressions". It is a real fork. Once
`bind="!prefs.enabled"` is legal, so is `bind="a && b"`, then comparisons, then
string interpolation, and the markup contract has acquired an expression language
that has to be specified, parsed, error-reported, versioned and kept stable
forever — in a file format whose whole justification is that it is *data*, and
that is reloaded from disk on every keystroke.

The second question is where the binding lives once it is resolved. The obvious
answer — a `Bound` widget that wraps the real one and rebuilds it — is wrong here,
and it took drawing the element tree to see why: a wrapper is an element, an
element is a link in the chain the cascade walks, and `panel > text` would then
match an unbound `text` and miss a bound one. The same node, styled differently,
for a reason no stylesheet can see.

## Decision

**A path, and nothing else.** A `bind` value is `identifier(.identifier)*` —
`frost`, `prefs.frost`, `prefs.window.opacity` — enforced by a regular expression
at the registry, so `bind="!prefs.frost"` fails at inflation with the text quoted
rather than resolving to nothing. Negation, comparison and formatting stay in
Java, where they are already expressible and already testable.

**A `Property<T>` is a cell with listeners**, and that is all: `get`, `set`,
`subscribe`. No computed values, no dependency tracking, no streams — those are
what a framework brings, and §9 asks for a binding that needs none. `set` compares
with `equals` and does nothing when the value is unchanged, which is what makes
two properties mirroring each other terminate instead of recursing.

**`Bindings` is the third registry**, alongside `Actions` and `Icons`, deliberately
the same shape: markup names, the registry resolves, and strict is the default
because a control bound to nothing looks exactly like a control bound to something
that never changes. A document reloaded at runtime re-resolves every path against
the properties the application already holds, so the *values* survive the reload
along with the markup.

**The binding lives on the widget, and the subscription on its element.**
`Widget.binding()` returns the property a widget follows, defaulting to null;
`Element` subscribes when it is mounted, follows the property across rebuilds by
identity, and closes the subscription when it unmounts. A change calls
`markNeedsBuild`, which is the same route `setState` takes — so ten changes in one
frame cost one build, and the coalescing was already written
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)).

**What a bound value *means* is the widget's business.** For `text` it is the
content, read at render rather than captured at build. For a future `checkbox` it
will be the checked state.

> **Amended.** This record originally continued: *"…which that control will also
> write back to — which is the whole of 'two-way'."* That plan is withdrawn.
> Binding is one-way, enforced by handing widgets an `Observable` rather than a
> `Property`, and a control reports what the user did through its action instead
> ([ADR-0063](0063-data-flows-down-events-flow-up.md)). Everything else in this
> record stands.

## Alternatives considered

**Mini-expressions.** The expressive option, and the one that keeps
`disabled bind="!prefs.enabled"` out of Java. Rejected because the cost is not the
parser — it is that an expression language in a reloadable data file is a second
programming language in the product, with its own semantics to specify and its own
errors to report at 3 a.m. from a file someone was mid-edit in. A mirrored
property costs one line of Java; the negation operator that saves it costs a
grammar.

**Dotted paths plus `!`.** The tempting middle. Rejected for exactly the reason it
is tempting: it is the first step of the argument above, and there is no principled
place to stop after it. If negation earns its keep it can be added later, and
adding an operator to a grammar of none is a smaller change than removing one.

**Reflection over a model object** — `bind(controller)` walking `prefs.frost` with
`getPrefs().isFrost()`. Rejected on §9's own terms: it says wiring is explicit and
there is "no reflective `#handler` magic", and a path that resolves through
reflection is exactly that magic, with a refactor that renames a getter breaking a
markup file that names no Java at all.

**A scoped model**, where `prefs` is a sub-model that can be handed to a subtree.
Deferred, not rejected: the registry is flat today and the dots are part of the
name. Nothing yet renders a subtree against a different model, and adding scopes
later is additive. This is written down as a floor rather than left implicit,
because a flat registry that quietly grew scopes would break paths.

**A `Bound` wrapper widget.** The design most toolkits reach for, and the one this
started as. It would have needed the cascade's ancestor chain to skip elements with
no CSS type — a defensible change, and one that would fix the same latent problem
for every composition wrapper — but it is a change to how *everything* is styled,
made in service of a feature that does not need it. The binding went on the widget
instead, and the cascade was left alone.

**Firing the listener on subscribe.** Rejected: a widget subscribes while it is
being built, and firing there would mark it as needing a rebuild before its first
build had finished. A subscriber reads `get()` when it is ready.

## Consequences

**`bind` is finished for the widgets that exist, and specified for the ones that
do not.** `text bind="user.name"` works from KDL and from Java, with the parity
test extended to cover it. What is *not* here — and, after ADR-0063, will not be
— is a control that writes: a control reports what the user did through its
action, and the application sets the property.

**A malformed `bind` fails loudly, including on reload.** That is the intended
behaviour and it has a cost: hot reload is otherwise deliberately forgiving
(ADR-0051), and a half-typed `bind="prefs."` now stops that document from
inflating until it is finished. Refusing is still right — the alternative is a
control that silently never updates — but it is a place where the forgiving path
and the strict one disagree.

**Every widget now answers `binding()`.** One default method on the core interface,
and one subscription field per element. The cost is a null check per mount and per
update on every element in the tree, which is nothing; the risk is that
`binding()` is now a place where a widget can hold a reference to something the
application owns, and an element that failed to unsubscribe would keep a whole
subtree alive. That is why the unsubscribe is unconditional in `unmount`, ahead of
the state's own `dispose`, and why there is a test that counts listeners.

**A property change asks for a build, not a frame.** The element is marked dirty
and `tree.needsBuild()` reports it — but nothing repaints the window, exactly as
nothing repaints it for a `setState`. The application wires that today (the
showcase passes `window::repaint`), and it stays that way until the retained render
tree makes the host own it ([ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)).
It is the same gap in both directions rather than a new one, but a bound value
that appears a frame late has a more obvious owner than a `setState` does, so it
is worth naming here.

**A `Property` holds a value, and a mutable object is not one.** `set` with the
same list instance notifies nobody, however much the list changed inside. Records
and immutable values are the intended contents; anything else works until it
quietly does not.
