# 135. A frame is asked for by the value that moved

Date: 2026-08-19

## Status

Accepted. Refines [ADR-0128](0128-a-change-is-its-own-frame-request.md), and
supersedes the `@Model(repaint = false)` it introduced.

## Context

ADR-0128 made a change its own frame request and put the opt-out on the class:

```java
@Model(repaint = false)
public final class BackgroundJob { … }
```

The granularity is wrong, and obviously so once a real model is written. One
model routinely holds both the gain a slider shows and the byte counter nothing
shows. A switch on the class has to be wrong about one of them, and the way out
of being wrong is to split the model along a line that has nothing to do with
what the model is *about* — which is the tail wagging the dog.

The showcase hit it immediately: `added`, the counter behind "Untitled 3", is
genuinely part of what the model knows and is displayed by nothing.

## Decision

**The value says whether showing it needs a frame.**

```java
@Bind("app.gain")                              Number gain = 40;    // asks
@Bind(value = "app.tabs-added", repaint = false) int added;         // does not
```

`@Model(repaint = false)` is gone. There is one place to look, and it is next to
the value the question is about.

**Decided in the build.** The weaver emits the `repainted()` call in the setters
of fields that ask and emits nothing in the others — so a value declared
`repaint = false` costs an instruction that is not there rather than a branch that
is. The runtime has no notion of which fields are quiet, and needs none.

### It is not "do not observe"

A field declared `repaint = false` still notifies everything bound to it. Off
means *do not wake the window*; something else may perfectly well be watching a
value nothing on screen shows. There is a test for exactly that, because the two
are easy to conflate and the conflation would be silent.

## Consequences

The three signals a change can raise are now all per value and all declared in
one place:

| | Declared | Fires |
|---|---|---|
| the binding | `@Bind("a.b")` | always, on a real change |
| a frame | by default; off with `repaint = false` | after the binding's listeners |
| a restyle | `restyle = true` | before the binding's listeners |

Which reads as one idea rather than three, and is a better answer than the two
places it took before.

**`Models.repaints(model)` is gone**, and with it the only thing that read
`@Model`'s members at run time. `@Model` is now a bare marker again — still
`RUNTIME`-retained, for the single purpose of telling an author their class was
never woven.

**A model with every field quiet still gets a `repainted()` listener list**, empty
and never fired. One null field on one object per model; not worth a switch.
