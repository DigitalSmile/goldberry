# 138. A window's actions are a model of their own

Date: 2026-08-19

## Status

Accepted. Removes a leak [ADR-0132](0132-a-model-wires-itself.md) introduced.

## Context

ADR-0132 made the window's own actions ordinary by annotating the application:

```java
@Model
public final class Showcase implements Application {

    @Action("app.open-menu") private void toggleMenu() { … }
}
```

It solved the real problem — "open the menu" needs a `Host` and has no business on
a view model — and it solved it by putting two unrelated roles on one class. An
`Application` is the thing that owns the window, the lifecycle and the native
resources. A `@Model` is a thing markup resolves names against. Stacking the
annotation on the `implements` made that visible in the worst way: the first two
lines of the application's class declaration are now about two different
abstractions.

It was also the only place in the guide where the four kinds of class did not hold
— the application was quietly a fifth thing.

## Decision

**A small `@Model` record for the window's actions, holding what they do rather
than what does them.**

```java
@Model
public record WindowActions(Runnable openMenu, Runnable toggleHud) {

    @Action("app.open-menu")  public void open() { openMenu.run(); }
    @Action("app.toggle-hud") public void hud()  { toggleHud.run(); }
}
```

built in the application as `new WindowActions(this::toggleMenu, this::toggleHud)`
and added to `models()`. `Showcase` carries no annotation and keeps its methods
private.

Two `Runnable`s rather than a reference to the window, so this type knows what the
actions *are called* and nothing about who performs them. It is testable without a
`Host` and reads as what it is: the window's half of the name table.

## Consequences

The application is an `Application` again, and the guide's four kinds of class
hold everywhere including the showcase.

**It is one more object for two names**, which is the cost. A window with a dozen
actions would find this obviously worthwhile; a window with two finds it a wash,
and the reason to do it anyway is that the alternative was a leak that got worse
with every action added.

**`models()` is now three entries** — values, actions, window actions — which is a
better demonstration of multi-model wiring than the two it replaced, since the
third one genuinely comes from somewhere else.
