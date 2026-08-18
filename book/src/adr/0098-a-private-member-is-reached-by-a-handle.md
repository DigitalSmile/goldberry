# ADR-0098: A private member is reached by a handle

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §9, revises one consequence of
  [ADR-0096](0096-a-registry-is-generated-not-reflected.md)

## Context

[ADR-0096](0096-a-registry-is-generated-not-reflected.md) moved registry wiring
to an annotation processor and listed a cost:

> **Annotated members cannot be `private`.** Generated code sits in the same
> package and cannot see one, so a model's fields become package-private. That is
> where they belonged — the accessors are the API — but it is a real constraint
> and the error message says so rather than leaving it to be discovered.

The parenthesis was doing a lot of work. "That is where they belonged" is a claim
about *some* models — and the argument runs the wrong way round regardless: the
toolkit was deciding a model's encapsulation as a side effect of how it reads it.
An `@Action` that only markup ever calls has no business being part of a model's
API, and a `Property` field is exactly the kind of thing an author wants private,
with a read-only accessor beside it. The showcase's model had six fields and five
handlers widened for no reason other than this.

ADR-0096 also considered and rejected `MethodHandles.Lookup`:

> **`MethodHandles.Lookup` passed in by the application** — `Bindings.of(lookup(),
> model)`. Authorised reflection, no `opens`, and the application opts in.
> Rejected because it is still a lookup **by name resolved at run time**: the
> failure moves from "silent" to "an exception when that path is first used",
> which is better and still not compile time.

That rejection stands, and it is not what this record proposes. The difference is
which end the name comes from.

## Decision

**A private member is reached by a handle the processor writes; an accessible one
is still read directly.**

For each private `@Bind` field or `@Action` method, the generated registry
declares a constant and fills it in one static initializer:

```java
private static final java.lang.invoke.VarHandle BIND_GAIN;
private static final java.lang.invoke.MethodHandle ACTION_SET_GAIN;

static {
    try {
        var lookup = java.lang.invoke.MethodHandles.privateLookupIn(
                ShowcaseModel.class, java.lang.invoke.MethodHandles.lookup());
        BIND_GAIN = lookup.findVarHandle(ShowcaseModel.class, "gain", Property.class);
        ACTION_SET_GAIN = lookup.findVirtual(ShowcaseModel.class, "setGain",
                MethodType.methodType(void.class, double.class));
    } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

and the registration reads the same as before with the access swapped:

```java
.bind("app.gain", (Property<?>) BIND_GAIN.get(target))
.bind("app.set-gain", value -> call(ACTION_SET_GAIN, target, Double.parseDouble(value)))
```

### The name is still resolved at compile time

This is the whole of why it is not the alternative ADR-0096 rejected. The
processor has already proved, against the element model, that the member exists,
that a `@Bind` is a `Property`, that an `@Action` takes at most one parameter of a
type it can parse, and that no two members claim one path. It then writes the
descriptor it verified. **Nothing scans and no name is typed by a user.**

What the handle changes is *access*, not *discovery*. A misspelled path is still
a compile error naming the field; the lookup can only fail if the registry and
its target were compiled apart and drifted, which is the same skew that turns a
direct field reference into a `NoSuchFieldError` — arriving here as an
`ExceptionInInitializerError` with the member named instead.

### No `opens`, no `setAccessible`

`privateLookupIn` requires the target's module to open the target's package to
the caller's module. The generated class is in the target's own package and
therefore its own module, and **a module always opens its packages to itself** —
so an application adds nothing to its `module-info`. This is exactly the property
that a runtime scanner over the application's objects would not have had, and it
is why ADR-0096's cost of "an `opens` per model package" does not apply.

### An accessible member gets nothing

`target.gain` and `target::click` stay as they were. A handle constant, a static
initializer entry and a `call(...)` wrapper are three things a reader of the
generated file would otherwise have to understand for a member that never needed
them — and ADR-0096's argument is that the generated file is ordinary Java you
can open and step into. Mixed models get a mixed file, which is honest: the
handles are precisely the members that could not be reached any other way.

### One helper for the call

A private `@Action` goes through `handle.invokeWithArguments(...)` in a generated
`call` helper rather than `invokeExact` at each site. The shapes differ per action
and the conversion `invokeWithArguments` performs — unboxing the parsed value into
the parameter's primitive — is what the lambda would otherwise spell out per type.
An action runs on a user gesture, so its cost is not on a path that matters.

The helper rethrows `RuntimeException` and `Error` as themselves: an action that
throws must reach the application looking like what it threw, not like a wrapper.
A checked exception — which no `@Action` can declare without the model itself
failing to compile against the `Runnable`/`Consumer` it is bound as — becomes an
`IllegalStateException`.

## Alternatives considered

**Leave it refused.** The status quo, and it costs nothing to keep. Rejected
because the constraint is arbitrary from the author's side: nothing about binding
a value to markup implies the field must be visible to the rest of its package,
and the toolkit should not be the reason it is.

**Generate an accessor into the model.** An annotation processor cannot modify
the class it reads, so this needs a different mechanism entirely — a
`-Xplugin`, a bytecode step, or Lombok-style tree surgery. All three are worse
than a handle by a wide margin.

**Always use handles, for every member.** Uniform, and one code path in the
generator instead of two. Rejected because it makes every generated registry
harder to read and moves *every* member's failure from link time to class-init
time, to buy consistency nobody benefits from.

**`setAccessible(true)` over `getDeclaredField`.** Needs the module to be open,
which is the cost ADR-0096 rejected reflection for in the first place.

## Consequences

**A model can encapsulate its state again.** `ShowcaseModel`'s six `Property`
fields and its five markup-only handlers are private now, and the accessors that
remain — `gain()`, `themeName()`, `status()` — are the API because someone chose
them, not because a processor demanded them.

**Two failure modes are now class-init rather than compile or link time**, for
private members only: a target recompiled without the member, and a target whose
member changed type. Both are already impossible within one compilation, which is
how a registry and its model are always built.

**The generated file grew for models that use the feature.** A registry over six
private fields and five private actions carries eleven constants, a static block
and a helper — about forty lines that were not there. The test suite compiles and
*runs* generated output now rather than only checking that it compiles, because
"it compiles" was no longer the interesting half of the claim.

**A `static` `@Action` is still unsupported**, and now for a second reason: the
non-private path generates `target::method`, which does not compile for a static
method, and the private path writes `findVirtual`. Nothing refuses one explicitly;
it was broken before this record and remains so, recorded here rather than fixed
because no model has ever wanted one.
