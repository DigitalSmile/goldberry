# 129. A value is named one way

Date: 2026-08-19

## Status

Accepted. Removes the second half of what
[ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md) left behind.

## Context

After the weaver landed, `ShowcaseModel` still carried nine methods like this:

```java
public Observable<String> tab() {
    return observable("app.tab");
}
```

plus the two private helpers behind them and a cached `Bindings` field. Twenty
lines of a model whose entire point was that it had stopped being plumbing.

They existed because a widget built in Java had no other way in. A document says
`bind="app.tab"` and resolves it against the registry; a widget built in Java had
to ask the model, and the model had to offer a method per path. So every bound
value was named **twice** — once in the annotation, once in an accessor that said
the same thing in Java — and the two could disagree.

## Decision

**One lookup, and it is the one markup uses.**

```java
Models.observable(model, "app.tab")
```

`bind="app.tab"` in KDL and `Models.observable(model, "app.tab")` in Java resolve
the same path against the same registry. There is no second vocabulary.

To make that affordable, the weaver now **caches the `Bindings`** it builds in a
synthetic field. A path lookup is a map get on a registry built once, so calling
it while building a widget costs what reading a field costs — it happens on every
frame, for every widget, and rebuilding a registry each time would have been the
kind of cost nobody goes looking for.

`actions()` is deliberately **not** cached, and the asymmetry is the point: an
application routinely *extends* the action registry — the showcase adds
`app.open-menu` and `app.toggle-hud`, which are the window's actions and not the
model's — and handing out a shared one would make the second caller fail with
"already bound" for doing exactly what the first did. Bindings are the model's
values and nothing adds to them.

### Why not keep the accessors and generate them

Because generating them would put back the thing ADR-0126 deleted: a second
artefact restating what the annotation already said. And a generated
`Observable<String> tab()` cannot be more type-safe than the field it reads,
which is the only argument the hand-written ones had.

### Why not pass `Bindings` to every widget instead

It is the same lookup by a different route, and it means threading a registry
through every constructor of every application widget. The model is already
there; asking it is shorter and reads the same as the markup does.

## Consequences

**Java call sites are stringly typed, and the type is inferred rather than
checked.** `Models.observable(model, path)` returns `Observable<T>` with `T`
taken from where the result is used. That is a real loss against
`Observable<String> tab()`, and it is the price of having one name for a value
instead of two. Two things soften it: the registry is **strict**, so a typo
throws at construction naming every path that *is* bound, and
`Models.observable(model, path, Class<T>)` checks the value where the answer
matters more than the brevity.

**Derived getters stay.** `theme()`, `density()`, `isProseShown()` and
`hasClicks()` are still on the model, because they are not plumbing — they are
the application deciding what its own values *mean*. `isProseShown()` is now
`return showProse;`, which is what it always meant.

**The cached registry is shared.** `Models.bindings(model)` returns the same
object every time, so an application that calls `rebind` on it changes what every
document resolves. That is defensible — it is the model's registry — but it is a
change from the previous fresh-per-call behaviour and would surprise somebody who
expected a copy.
