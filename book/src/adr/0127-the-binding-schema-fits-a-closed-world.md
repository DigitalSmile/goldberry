# 127. The binding schema fits a closed world

Date: 2026-08-19

## Status

Accepted. The constraint that decided
[ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md)'s *when* and
[ADR-0126](0126-actions-are-bound-by-lambdametafactory.md)'s *how*.

## Context

The brief for the binding redo asked for three things that do not obviously fit
together: the class-file API to wrap raw fields, `LambdaMetafactory` instead of an
annotation processor, and a result that builds as a GraalVM native image.

Taken literally, the first two contradict the third. Both name mechanisms whose
natural home is *runtime*:

- `ClassFile.of().build(...)` plus `Lookup::defineHiddenClass` generates a class
  while the program runs. A native image has no class loading and no class
  definition; the world is closed when the image is built.
- `LambdaMetafactory.metafactory(...)` **called as a method** spins a class per
  call site, at the moment it is called. Same problem.

A design that did either at runtime would satisfy points 1 and 2 and fail point 3
outright — not degrade, fail: the image would not build, or would build and throw
on the first model.

There is also a fourth mechanism the *previous* scheme used and which is a milder
version of the same problem. ADR-0098's generated registry ran
`MethodHandles.privateLookupIn`, `findVarHandle` and `findVirtual` in a static
initializer. Native image supports those, but only with reachability metadata
naming every field and method reached — a JSON file, per model, that has to be
kept in step with the code by hand or by an agent trace.

## Decision

**Everything generative happens at build time; nothing generative happens at
runtime.**

The same class-file API does the same work — it just runs between `compileJava`
and `jar` instead of during `main`. And `LambdaMetafactory` is used in the form a
closed world *can* resolve: as an `invokedynamic` bootstrap, which the image
builder links when it builds the image, exactly as it does for every method
reference `javac` ever emitted.

So the two "contradictory" requirements are met in full, and the contradiction was
only ever in the word *when*.

What a woven model needs at runtime is then: its own fields, `invokevirtual`,
`invokestatic`, and a handful of ordinary classes (`FieldListeners`,
`BoundField`, `Bindings`, `Actions`). No lookup, no handle, no proxy, no metadata
file.

### The claim is a test, not a sentence

`NativeImageComplianceTest` parses the woven bytecode and asserts:

- no call to `Class.forName`, `getDeclaredField`, `setAccessible`,
  `privateLookupIn`, `findVarHandle`, `findVirtual`, `defineClass`,
  `defineHiddenClass`, `Method.invoke`, or `LambdaMetafactory.metafactory`;
- every `invokedynamic` bootstrap is `LambdaMetafactory.metafactory`;
- one call site per action and no more, so the image carries one generated lambda
  class per handler rather than two;
- the runtime classes the woven code calls are themselves clean;
- `@Bind` and `@Action` are gone from the class at runtime, and `@Model` is the
  only one that stays.

`LambdaMetafactory.metafactory` being forbidden as a *call* and required as a
*bootstrap* is the whole decision in two assertions.

"We did not use reflection" is exactly the kind of claim that stops being true one
commit after somebody writes it down, which is why it is checked by machine.

## Consequences

**No native image has been built.** There is no GraalVM in this repository's
toolchain and none in CI, so what is verified is the structural property above and
not an image that starts. That is a real limit and worth stating rather than
implying otherwise: this ADR says the binding schema does not *stand in the way*
of an image, not that the toolkit produces one.

The rest of the toolkit is a separate and much larger question. `:natives` is FFM
downcalls into SDL3, Blend2D and HarfBuzz; an image of the showcase needs those
libraries handled, and JEP 472's `--enable-native-access` has an image equivalent
that nobody here has exercised. **The binding layer is no longer the reason it
cannot be tried**, which is the most this change can honestly claim.

The reachability metadata ADR-0098 would have needed was never written, because
the scheme that needed it was replaced before an image was attempted. If one is
ever needed for the toolkit's other halves, the `bind` package will not be in it.

**A runtime-weaving mode was considered and dropped.** It would have been genuinely
nice for development — edit a model, reload, no build step — and it is what a
literal reading of the brief describes. It was dropped because two code paths that
generate the same bytecode at two different times is two things to keep in step,
and the one that could not go in an image would have been the one everybody
developed against. One path, always the build.
