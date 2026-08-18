# ADR-0096: A registry is generated, not reflected

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §9, extends [ADR-0062](0062-bind-is-a-path-and-nothing-else.md)

## Context

Wiring a model to markup was two hand-written methods:

```java
public Bindings bindings() {
    return Bindings.strict()
            .bind("app.clicks", clicks)
            .bind("app.gain", gain)
            …                                  // one line per property
}

public Actions actions() {
    return Actions.strict()
            .bind("app.click", this::click)
            .bind("app.set-gain", value -> setGain(Double.parseDouble(value)))
            …                                  // one line per handler, plus the parse
}
```

Fifteen lines of pure copying in the showcase, and the failure mode is the worst
kind: a property that exists and is never registered inflates to a control that
renders perfectly and never moves. Nothing points at it.

The obvious fix is to scan the object reflectively. §9 forbids exactly that —
"`action` names bound against a controller object explicitly … no reflective
`#handler` magic" — and it is right to: a runtime scanner needs the application's
package `opens`, costs start-up, and turns a typo into the same silent
non-moving control.

## Decision

**An annotation processor writes the explicit calls.** `@Bind` on a `Property`
field, `@Action` on a method, `@Registry` on the class; the processor generates
`ShowcaseModelRegistry.bindings(model)` and `.actions(model)` containing exactly
the code a person would have written, parse and all.

That satisfies §9 rather than bending it. The generated file is ordinary Java:
you can open it, step into it, and get a stack trace out of it. The annotations
move the **copying**, not the explicitness. And there is nothing on the runtime
path — the processor is build-time only, like `:assets`.

**The refusals are the point.** Every rule is checked at compile time with the
member named:

- a `private` field or method the generated code cannot see, with the fix in the
  message;
- `@Bind` on something that is not a `Property`;
- two members claiming one path — which `Bindings` refuses at run time and this
  refuses before there is a run time;
- an `@Action` taking more than one argument, or one the toolkit cannot parse
  from the `String` a valued action crosses as;
- an annotated member on a class that is not `@Registry`, which is the mistake
  with no other symptom at all.

**`@Registry` is explicit rather than inferred** from the presence of a `@Bind`,
so the processor never writes a file nobody asked for and the generated type has
a name someone chose to create.

## Alternatives considered

**Runtime reflection over the model.** The version everyone writes first.
Rejected by §9, and independently by the cost: an `opens` per model package,
class-scanning at start-up on a toolkit that tracks "starts in milliseconds", and
a typo that still fails silently.

**`MethodHandles.Lookup` passed in by the application** — `Bindings.of(lookup(),
model)`. Authorised reflection, no `opens`, and the application opts in. Rejected
because it is still a lookup by name resolved at run time: the failure moves from
"silent" to "an exception when that path is first used", which is better and
still not compile time.

**A builder DSL** — `Bindings.forModel(m).bind("app.gain", m::gain)`. No new
module and no processor. Rejected because it is the same fifteen lines with a
different shape; the copying is the problem, not its syntax.

**Generate from the KDL instead** — read `bind=` out of the documents and demand
the model supply them. Backwards: it would make the markup the source of truth
for the model's shape, and a document is the thing most likely to be edited by
someone who cannot compile.

## Consequences

**A typo in a path is a compile error naming the field.** That is the whole
change, and it is the one the hand-written registry could never give.

**A new build-time module, `:processor`.** It never ships and has no
`module-info`, because an annotation processor is loaded by the compiler rather
than the module system. `:assets` set that precedent (ADR-0033).

**Annotated members cannot be `private`.** Generated code sits in the same
package and cannot see one, so a model's fields become package-private. That is
where they belonged — the accessors are the API — but it is a real constraint and
the error message says so rather than leaving it to be discovered.

**Generated `actions()` needs `:widgets` on the classpath**, because `Actions`
lives there. An application using only `@Bind` does not: `actions()` is generated
only when there is an `@Action` to put in it. Found by the processor's own test
suite, which compiles its output.

**The annotations are `SOURCE`-retained** and vanish from the class file, so
nothing at run time can be tempted to read them and re-introduce the thing §9
rules out.

**Two ways to build a registry now exist** — by hand and generated — and they
must not drift. They cannot: the generated one *is* the hand-written one, emitted
by a program, and `ShowcaseDocumentsTest` asserts the generated registry resolves
what the documents name, including that a valued action parses the value it is
handed.
