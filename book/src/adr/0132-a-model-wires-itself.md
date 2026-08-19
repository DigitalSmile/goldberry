# 132. A model wires itself

Date: 2026-08-19

## Status

Accepted. Finishes what [ADR-0129](0129-a-value-is-named-one-way.md) started.

## Context

After ADR-0129 the application still said this:

```java
Widgets.inflater(
        Showcase.actions(model, this::toggleMenu, this::toggleHud),
        icons,
        Models.bindings(model));
```

Three registries handed to a call that could have worked out two of them. A model
already declares its `@Bind` paths and its `@Action` names — fetching both and
passing them back is ceremony around a fact the object carries.

`Showcase.actions(model, openMenu, toggleHud)` is the other half of the problem.
Two of the window's actions are not the model's: "open the menu" needs a `Host`,
which a view model must not have. So the application wrote a static that took the
model's registry and added two handlers to it, and a test that wanted the same
document had to call the same static or pass while the application refused to
start.

## Decision

**Hand over the models. The toolkit reads them.**

```java
Widgets.inflater(icons, model, this);
```

`Wiring.of(icons, models…)` merges what each model publishes. Icons stay
explicit, and only icons: an `Icon` owns native memory and has to be closed, so
markup may *name* one and must never be able to build one — that registry is a
decision the application makes and the toolkit cannot.

**More than one model**, which is what makes the window's own actions ordinary.
`Showcase` is itself a `@Model` now, with `@Action("app.open-menu")` on the method
that opens the menu. The list is `List.of(model, this)`, and a document writes
`press="app.open-menu"` beside `press="app.click"` without knowing they came from
two objects.

The same list is [Application#models()], so the toolkit also uses it to wire
repainting (ADR-0128) and restyling (ADR-0133). One declaration answers three
questions.

### Names may not collide across models

`Wiring.of` refuses two models claiming one path. Two features quietly sharing
one name is a bug that presents as a value changing by itself, and it is no less
a bug for the two being in different classes — the check that already existed
within a model now spans them.

## Consequences

`Showcase.start` lost the three-registry call, the `Models.onChange` line, the two
restyle subscriptions and the `Showcase.actions` static. What is left is one
`Widgets.inflater(icons, model, this)` and a `models()` returning two objects.

**`Models.bindings` and `Models.actions` still exist**, and are still public. They
are what `Wiring.of` calls, and what an application building its registries some
other way needs. They are no longer on the path an ordinary application walks,
which was the ask; deleting them would have left the escape hatch nailed shut.

**The merge has to tell the two halves of the action registry apart.** `Actions`
keeps plain and valued handlers in separate maps for a reason — adapting a valued
one down to a `Runnable` would call it with a value it was never given — so the
merge inspects each and re-binds it into the right half. There is a test for it,
because the failure would be a slider that reports nothing.

**A test that wants the real document now constructs the real `Showcase`.** That
is better than the static it replaced: the names come from the object the
application uses, so a test cannot pass against a list the application does not
have.
