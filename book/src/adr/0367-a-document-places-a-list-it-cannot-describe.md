# 367. A document places a list it cannot describe

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `list` is Java, like `canvas` and like
autocomplete" and the markup half of "Autocomplete is Java-only". Answers
markup for `tour` and `toast` without building it.

## Context

`list`, `table` and `tree` had no markup. The TODO entry said why: "an
item-factory is a function and §8's documents have no way to write one". A
table's cell factory and a tree's child suppliers are the same wall. It proposed
a named item-factory.

Autocomplete had the same shape. §4 says the widget raises the query and the
application supplies the list, and in Java the list arrives by rebuilding the
field. A document has no rebuild, so a document's field offered nothing.

A named factory would still leave a document writing a list's items, its
identity, its selection and its handlers, which are the application's. What a
document *can* do is name a value that changes, and `bind=` exists for that.

## Decision

**`list`, `table` and `tree` inflate to `Bound`, which draws the widget a
`bind=` value holds. `text-input suggestions=` and `select options=` inflate to
`Suggested`, which describes the field again with whatever a bound list holds.**

- `Bound(source, type, attributes)` in `widgets.markup` is a stateless
  composition node whose binding is the source. It draws the value when it is a
  `type`, with the document's `id` winning and its classes added to the
  widget's own, and draws nothing otherwise. The element already subscribes to a
  widget's binding, so a model that replaces the `ListView` rebuilds the node,
  and the list under it reconciles by key.
- `ListView`, `Table` and `Tree` carry `@Markup` and an `inflate` returning a
  `Bound` over their own class.
- `Suggested(source, field)` in `controls.option` rebuilds its field whenever the
  bound collection changes. It offers `Option`s as they are and anything else as
  an option of its string. `TextInput.inflate` wraps with `field::suggesting`;
  `Select.inflate` wraps with the new `Select.withOptions`, which replaces the
  written options and keeps any other children.
- **`tour` and `toast` get no markup.** Starting a tour needs a `Host`, which a
  document node does not have (ADR-0121). A toast is raised through a
  `ToastController`, not placed. Both are imperative by design.

## Consequences

- The application model holds widgets for these three: a `@Bind` field of type
  `ListView<Person>` that it replaces when its data changes. That is where the
  factory, identity and handlers already live in Java.
- A document mid-edit, with nothing bound yet, shows nothing rather than failing.
- The catalog's chaining, immutability and parity tests cover the three new
  names through `Bound`.
