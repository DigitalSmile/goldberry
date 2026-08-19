# 133. A restyle is declared

Date: 2026-08-19

## Status

Accepted. The other half of [ADR-0128](0128-a-change-is-its-own-frame-request.md),
which handled repainting and left this behind.

## Context

ADR-0128 made a change its own frame request, and the showcase's `onRestyle`
callback went with it — replaced by two subscriptions:

```java
Runnable restyle = () -> host.restyle();
Models.observable(model, "app.theme").subscribe(value -> restyle.run());
Models.observable(model, "app.density").subscribe(value -> restyle.run());
```

Which is three lines saying what one word could. Worse, it is three lines with
exactly the property ADR-0128 was written to remove: they are never *wrong*, only
ever **missing**, and when they are missing the symptom is a theme that changes
and a window that keeps painting the old one. Moving `changed()` out of nine
methods and into two subscriptions is moving the bug, not fixing it.

A restyle is genuinely not a repaint — every resolved style is thrown away, which
is why `Host.restyle` is a separate call and the common case is a change that
moves no rule at all. So it cannot simply be folded into the frame request.

## Decision

**The field says so.**

```java
@Bind(value = "app.theme",   restyle = true) private String theme = "dark";
@Bind(value = "app.density", restyle = true) private Density density = Density.REGULAR;
```

The weaver emits an extra call in that field's setter, and whatever installed the
model subscribes. An application declares which values a *rule* depends on and
says nothing else.

**Before the frame request**, deliberately: the setter calls `restyled()` and then
`fire()`, so a window has dropped its resolved styles by the time it is asked for
the frame that will use them. A window that repainted first would paint one frame
with the old theme.

**Only on a real change**, like everything else here: assigning the theme it
already has restyles nothing.

### Why a flag on `@Bind` and not a second annotation

Because it is a property of the binding, not a separate declaration. `@Restyle`
on a field would be a second thing to put next to the first, and one that means
nothing without it.

### Why not a method-level `@OnChange("app.theme")`

More general, and generality is the wrong instinct here. The toolkit knows what a
restyle is; an application saying "call *this* when *that* path moves" is back to
writing the subscription by hand with a shorter syntax. If an application needs an
arbitrary reaction it still has `Models.observable(model, path).subscribe(…)`,
which is the honest way to spell an arbitrary reaction.

## Consequences

`Density` became a bound field to get this, having been an ordinary one. Nothing
displays it — but "nothing displays it" was never the same question as "does
anything depend on it", and a stylesheet does.

**A `Property` field cannot ask for a restyle**, and the weaver refuses one. It
rewires no writes to a `Property`, so there is nowhere to put the call; the error
says to hold the value as a plain field or call `Host.restyle()` directly. That is
a real asymmetry between the two kinds of `@Bind` field, and the only one.

**The toolkit now subscribes to every model an application names**, in `Launcher`,
after `start`. A model with no restyling field costs one empty listener list.

`@Model(repaint = false)` turns the frame request off for a model the UI does not
show — one driving a background job — where every write would otherwise wake a
window with nothing new to draw. Restyle has no equivalent switch: a field that
asked for one asked for it.
