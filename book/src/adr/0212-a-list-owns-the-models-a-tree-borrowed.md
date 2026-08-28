# 212. A list owns the models a tree borrowed

Date: 2026-08-28

## Status

Accepted. Settles the debt ADR-0184 and ADR-0210 each recorded.

## Context

`docs/core-widgets.md` §10's `list` is "a vertical list over an observable item
model with an item-factory (any widget as row); selection models: none / single /
multi (Ctrl/Shift semantics); full keyboard (arrows, Home/End, type-to-select when
items expose text); item context menus".

It has been specified and unbuilt while three widgets that §3 defines *in terms of
it* were built. `tree` "shares `list`'s item-factory" and takes "`list`'s
selection models", and `select tree=` needed `tree` — so each time the question
came up, the answer was ADR-0184's rule: the widget that needs a model first
defines it and **writes down that the other will have to agree**. That debt was
taken twice, knowingly, and it accrues: every month `list` stays unbuilt is a
month in which the definition of a selection model lives in the wrong widget and
a second borrower could arrive.

It is also the cheapest of the outstanding catalog entries to get wrong quietly.
A list is the widget every application has, and the shape of its API decides
whether the virtualization §10 promises for v1.x is a performance change or a
break.

## Decision

**`Selection` moves to `list` and `tree` imports it.** That is the debt paid the
way it was promised — the definition goes to the widget the specification names it
after, and nothing about the shape changed, which is the evidence that the promise
was a small one to make. `Checkable` stays in `tree`, because §10 gives a list no
checkbox and a model with one consumer belongs to that consumer.

**The class is `ListView` and the CSS type is `list`.** A widget record named
`List` would shadow `java.util.List` in every file that built one — including its
own, whose model is a `java.util.List`. `ListBox` is the styled node, in the
`Tree`/`TreeBox` arrangement every stateful widget in the catalog uses.

**An item is anything, and three functions describe it.** `identity` says what it
*is*, `factory` says what it *looks like*, and `text` says what it *reads as*.
Three lambdas rather than an interface to implement: the common case is three
method references, and an interface would make the trivial list — strings drawn as
text — the one that costs the most to write. `ListView.of(List<String>)` is that
case as a factory.

**`text` is optional, and its absence turns type-to-select off.** §10 makes the
feature conditional on "items expose text", so a list of colour swatches gets no
typeahead — and, more importantly, does not **consume** the keystroke. A row that
swallowed text it could not use would stop a field elsewhere from ever seeing one.

**§10's item context menus are named on the row.** A menu is a name on a widget
(ADR-0108) found by walking up from an element. Naming it on what the factory
returned would work for a right-click and **not for the keyboard**, because the
menu key walks up from the focused element (ADR-0208) and that is the row. So
`ListRow` carries an `Attributes` — the only part in the catalog that does — and
`itemMenu` is a function, because a folder and a file do not offer the same
commands.

**A row's focus name is scoped by its list's `id`.** `host.focus` takes a name
global to the window (ADR-0176), so two lists over items with equal identities
would each answer to the other's `Home`. Prefixing settles it wherever the
application named the list, which is the case a screen with two lists on it
already has because a stylesheet needs to tell them apart too.

**`Home` and `End` go to the ends of the model, not of the viewport.** A list's
own scrolling is a `scroll` ancestor's business and the focus ring is what asks it
to follow (ADR-0120) — the rule `tree` already states, and what `Ctrl+End` means in
every document.

**The focus ring stays on a `list` row where a dropdown's row has none.** In a
`select` the arrows move the *value*, so "where the keyboard is" and "what is
chosen" are one row and a ring would be a second marker for one place (ADR-0112).
Here the arrows move the **focus** and `Enter` chooses, so they are genuinely two
rows and need two marks.

**A `NONE` row is still focusable and does not consume its click.** §10's `none`
says what may be *chosen*, not what may be *read*: a list nobody can select from is
still one a keyboard user must be able to walk, or its rows are unreachable
content. And a row that swallowed the click would stop a button the item-factory
put on it from ever being pressed.

## Alternatives considered

- **Naming the widget `List` and asking callers to qualify `java.util.List`.**
  The CSS type is what a stylesheet writes and the class name is what an
  application writes; only one of them has to live in a file that also holds a
  collection.
- **An `Item` interface — `id()`, `label()`, `widget()` — as `tree` has
  `TreeNode`.** Right for a tree, whose model is a shape (a node with children)
  rather than a value; wrong here, because §10's item is explicitly the
  *application's* type and wrapping every row in an adapter is the ceremony
  ADR-0184 avoided for the tree by making the node a record the caller builds.
- **Sharing `TreeRow` between the two.** A tree row is an indent, a chevron, a
  box and a label; a list row is whatever the factory returned. The only shared
  part is the keyboard, which is fifteen lines, and a shared row would have to
  carry a depth and an expansion state that a list has no meaning for.
- **Building virtualization now.** §10 defers it to v1.x and says why the
  item-factory makes it a performance upgrade rather than an API break — which
  is only true if the factory ships first and is used. Building both at once
  would be designing the recycler against no callers.
- **Reporting the pressed id rather than the whole set.** ADR-0210's finding,
  inherited: a `Shift` range is computed over rows only the widget can see, so an
  id alone is an answer the application cannot turn back into a selection.

## Consequences

- **`tree` gains an import and loses a definition**, and every existing tree
  golden is byte-identical, because the enum's constants and their meanings did
  not move — only the package did.
- **`docs/core-widgets.md` §10's `list` row is built**, and `table` is the only
  entry left in that section — still deferred, still on virtualization.
- **The virtualization debt is now `list`'s own.** §10 promises recycling as a
  v1.x follow-up and this ships the API it promised would survive it; whether it
  actually does is untested until a recycler exists, which is the honest state of
  a promise about work not yet done.
- **`ListRow` carrying attributes is a precedent.** A part that says something on
  a widget's behalf is new — every other part in the catalog is drawing and
  handlers only — and the next widget with per-item metadata will find it.
- **Nothing in markup builds one.** An item-factory is a function and §8's
  documents have no way to write one, which is `canvas`'s wall and autocomplete's;
  a `list` is Java, like both.
