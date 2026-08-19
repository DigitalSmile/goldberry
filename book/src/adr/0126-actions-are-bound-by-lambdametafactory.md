# 126. Actions are bound by `LambdaMetafactory`

Date: 2026-08-19

## Status

Accepted. The action half of
[ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md); together they supersede
[ADR-0096](0096-a-registry-is-generated-not-reflected.md).

## Context

ADR-0096 generated Java source. `@Action("app.click") void click()` became a line
in a `ShowcaseModelRegistry.java` that a person could open:

```java
return Actions.strict()
        .bind("app.click", target::click)
        .bind("app.set-gain", value -> call(ACTION_SET_GAIN, target, Double.parseDouble(value)));
```

That worked, and the readable-output argument was a real one. But it bought the
readability with two things that were not free.

The first is that a private method could not be named. Generated code sits in the
same package and cannot see one, so ADR-0098 reached it through a `MethodHandle`
looked up in a static initializer via `MethodHandles.privateLookupIn`. Explicit,
yes — and a reflective lookup on the startup path, running for every action of
every model, in a scheme whose stated selling point was that it did no reflection.

The second is that the generated file is a *second* artefact for one class. It
had to be named, placed in a package, kept from colliding, and regenerated
whenever the model moved. `ShowcaseModelRegistry` is not a thing anybody wanted;
it is a thing the mechanism needed.

Once ADR-0125 was already rewriting the model's bytecode for the `@Bind` half,
the `@Action` half had somewhere better to go.

## Decision

**The weaver writes one `invokedynamic` per action, into the model's own class,
bootstrapped by `LambdaMetafactory.metafactory`.**

It is byte for byte the call site `javac` emits for `model::click`: the same
bootstrap, the same three static arguments, the model captured as the single
dynamic argument. This is not a new mechanism. It is the mechanism a method
reference has always used, written by something other than javac.

Because the call site is *inside the model's own class*, the lookup that
bootstraps it is the model's own lookup — which has private access to the model.
So a private `@Action` is reached the way any code in a class reaches its own
private method, with no handle, no `privateLookupIn`, and nothing on the startup
path. ADR-0098's problem does not get a better answer; it stops existing.

### Every action goes through a synthesised bridge

`private void goldberry$action$click()`, calling `click()`. Uniform, even for the
no-argument case that could have referenced the method directly, because:

- it is where the parse lives. A valued action crosses as the `String` the
  document wrote down, and `goldberry$action$setGain(String v)` calls
  `setGain(Double.parseDouble(v))` — one place, visible in `javap`, rather than a
  coercion the registry performs on the way past.
- it makes every call site in the class the same shape: one bridge, `private`,
  returning void, reached by `invokespecial`. An action that returns something is
  called for its effect and the value dropped, which is what a `Runnable` wrapping
  it would do anyway.

### Why not keep generating source

Because the readable artefact was answering a question nobody was asking. The
thing a person wants to read is *which name is bound to which method*, and that is
in the model, next to the method, as `@Action("app.click")`. The generated file
restated it in a second place that could only ever agree.

What is genuinely lost is steppability: you can no longer put a breakpoint in the
registry. In exchange the stack trace goes straight from `Actions.resolve` to the
model's own method, with one synthetic frame between — shorter than it was.

## Consequences

**Dispatch is exactly as fast as before, and that is the finding.** Measured
against a method that does nothing, so the number is the call site and not the
model behind it, the two are indistinguishable — both loops optimise away
entirely. They should: both are a `LambdaMetafactory` call site, and the JIT has
been inlining through those since Java 8. The action half of ADR-0096 was never
slow, and nothing here claims to have made it faster.

The first version of that benchmark measured `app.click` on both models and
reported the woven form 5.8× faster. That was the *write* path from ADR-0125
showing up under an "action dispatch" label. The measurement was replaced with one
against a no-op, and the honest answer is "no difference".

The valued path is likewise unchanged: at ~35 ns per call both schemes are
dominated by `Double.parseDouble`, which is the same parse either way and costs
more than everything around it.

**`ShowcaseModelRegistry` is gone, and so is the `:processor` module.** One fewer
build-time tool, one fewer generated source root, and one fewer thing an IDE has
to be told about.

**Every bootstrap in a woven class is `LambdaMetafactory.metafactory`,** and a
test asserts it — which is what ADR-0127 needs, and the reason that assertion is
worth making mechanically rather than by inspection.
