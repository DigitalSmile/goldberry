# 139. Actions are annotated as actions

Date: 2026-08-19

## Status

Accepted. Finishes [ADR-0138](0138-a-window-s-actions-are-a-model-of-their-own.md),
which moved the window's actions off the `Application` and left them still calling
themselves a model.

## Context

`@Model` marked two different things. A class of `@Bind` values is a model. A
class of `@Action` methods that operates on somebody else's values is not — it
holds nothing, publishes no paths, and is the opposite half of the pair. Marking
both `@Model` said otherwise on every one of them:

```java
@Model                                  // holds no values
public record WindowActions(Runnable openMenu, Runnable toggleHud) { … }
```

ADR-0138 fixed the worst instance — `@Model` sitting on top of
`implements Application` — by extracting exactly this record, and in doing so
made the mislabelling more obvious rather than less: a type whose entire content
is actions, called a model.

## Decision

**`@Actions` marks a class of `@Action` methods. `@Model` keeps the values.**

```java
@Model
public final class Settings {

    @Bind("app.gain") private Number gain = 40;

    @Actions
    public record Commands(Settings values) {
        @Action("app.louder") public void louder() { values.gain = … }
    }
}
```

Both markers produce the same woven shape — `BoundModel`, a listener store, and
the two registries — because a class with no `@Bind` field simply has an empty
half. What the second annotation buys is that the declaration says what the class
*is*.

Three rules, all build failures:

- **`@Actions` with a `@Bind` field** is refused: a class that holds values is a
  model, and the message says to annotate it `@Model` or move the field.
- **`@Actions` with no `@Action` method** is refused, the same way an empty
  `@Model` already was.
- **Both markers on one class** is refused. A class holds values or it does not.

A `@Model` may still carry `@Action` methods. That is the right shape for a model
small enough that splitting it would be ceremony (ADR-0136), and taking it away
would have made a second annotation a tax rather than a clarification.

### The registries were renamed to make room

`io.github.digitalsmile.goldberry.bind.Actions` was already taken — by the
*registry* of name-to-handler that `Wiring` holds. So `Bindings` and `Actions`
became `BindingRegistry` and `ActionRegistry`.

That is a rename made to free a name, which is a bad reason on its own. It turns
out to be an improvement for a better one: there were four near-identical names in
one package — `Bind`, `Bindings`, `Action`, `Actions` — where two were annotations
on members and two were runtime registries. Now each family reads distinctly:
`@Bind`/`@Action` on members, `@Model`/`@Actions` on types,
`BindingRegistry`/`ActionRegistry` as the things they resolve against.

Both registries are plumbing an application no longer touches: `Wiring.of` builds
them and `Models.observable` reads them.

## Consequences

**A type named `Actions` cannot use the simple annotation name.** Inside a class
that declares a nested `Actions`, `@Actions` resolves to that record rather than
to the annotation, and the fully-qualified name is required:

```java
@io.github.digitalsmile.goldberry.bind.Actions
public record Actions(ShowcaseModel values) { … }
```

That is a real wart and it is in the showcase, which uses exactly that name. The
way out is to name the type for its domain — `Commands`, `Editing`, `Playback` —
which reads better anyway; `Actions` describes the annotation's job, not the
class's. The guide recommends it and the showcase deliberately does not, so that
one worked example of the wart exists to look at.

**Renaming across markdown was a mistake, briefly.** The first pass rewrote
`Actions` in prose as well as in code, producing "GitHub ActionRegistry matrix"
and "Bindings are hand-written" in a dozen ADRs. Reverted: the decision log
records what the names were when the decision was made, and this record is where
the rename belongs.
