# 125. A raw field is woven into a binding

Date: 2026-08-19

## Status

Accepted. Supersedes [ADR-0096](0096-a-registry-is-generated-not-reflected.md)
(the annotation processor) and
[ADR-0098](0098-a-private-member-is-reached-by-a-handle.md) (private members
reached by `VarHandle`). Both were solving problems that this removes rather than
answers.

## Context

§9's binding half asked for "a small built-in `Property<T>` type; no framework
dependency", and that is what was built. Five years of JavaFX say what happens
next, and it had already started happening here:

```java
private final Property<Integer> clicks = Property.of(0);

@Action("app.click")
public void click() {
    changed(() -> clicks.set(clicks.get() + 1));
}
```

`clicks.set(clicks.get() + 1)` is `clicks++` with three extra tokens and an
object. The model cannot touch its own state without going through an accessor
nobody chose to write, and every value costs a heap object whose only job is to
hold a reference to another heap object. The showcase's model had eight of them.

Worse, the shape is contagious. Because the field is a `Property`, every read
inside the model is `get()`, every write is `set()`, and a method that wants to
compare two values writes `Objects.equals(a.get(), b.get())`. None of that is
about binding. It is about the container the binding needed.

The obvious fix — intercept the field — does not work in Java. `getfield` and
`putfield` are not virtual, so a subclass cannot hook them; a proxy cannot either.
**The class that declares the field is the only place the write can be seen.**

## Decision

**The build rewrites the model's own bytecode, with the JDK's class-file API
(JEP 484).**

An author writes plain Java:

```java
@Model
public final class Settings {
    @Bind("app.gain") private int gain = 40;

    @Action("app.louder") private void louder() { gain++; }
}
```

and `Settings.class` comes out of the build with:

1. `implements BoundModel`, and a lazily created `FieldListeners`;
2. a synthesised `goldberry$set$gain(int)` — compare, store, notify;
3. every `putfield gain` **in that class** rewritten into a call to it;
4. `bindings()` and `actions()`, built from the annotations.

Step 3 is a one-for-one instruction swap. `putfield` pops *objectref, value*, and
so does an instance call taking one argument — the stack either side is identical
and nothing around it moves. That is the whole trick, and the reason this is a
small transform rather than a compiler.

### Reads are left alone

Deliberately. `getfield` is already the fastest thing that could happen and there
is nothing to observe about a read, so a model pays for a binding only where it
writes. The cost lands on the other side: reading *through* the binding goes via
`boundValue`'s switch and, for a primitive, a box — measurably slower than
`Property.get`, and the right trade, because a model writes on every event and the
tree reads once per rebuild.

### Nothing notifies on a write that changed nothing

`Property.set` compared with `Objects.equals`, and the woven setter reaches the
same answer the cheap way for each type: `if_icmpne` for the small integrals,
`lcmp` for a `long`, and `Float.compare`/`Double.compare` for the floating types —
*not* `==`, so that `NaN` and `-0.0` answer the way a boxed comparison would. The
rule that makes two mirrored values terminate instead of recursing is the same
rule, and it had to be.

### `Property` does not go away

A `@Bind` field that already is one is bound directly and not rewired. That is
what lets a model publish a value somebody else owns beside its own, and it is
why `Bindings` now holds `Observable<?>` rather than `Property<?>` — the registry
hands out the read-only half either way and has no reason to know which it was
given.

### Why a build step and not an agent

An agent means `-javaagent` on every launch, a second thing to configure, and no
image (ADR-0127). Weaving the compiled class in the build needs none of that, and
what ships is an ordinary class file that `javap` explains.

### Why the annotations moved to `CLASS` retention

The weaver reads them out of the class file and nothing reads them afterwards. An
image that carried `@Bind` would be carrying metadata for nobody. `@Model` stays
`RUNTIME`, for one purpose: so `Models` can tell an author that their class was
annotated and never woven, instead of handing back a binding that silently
notifies nobody.

## Consequences

The showcase's model lost every `Property`, every `.get()` and every `.set()`.
`isProseShown()` went from `Boolean.TRUE.equals(showProse.get())` to
`showProse`. That is the change this was for.

**Measured** (`./gradlew :example:benchmark --tests '*BindingBenchmark*'`, one
machine, medians, per operation):

| | `Property` | woven field | |
|---|---|---|---|
| write, no listeners | 9.5 ns | 2.5 ns | **3.9× faster** |
| write, one listener | 19.0 ns | 12.9 ns | **1.5× faster** |
| write, value unchanged | 0.28 ns | 0.22 ns | 1.3× faster |
| read through binding, `int` | 1.2 ns | 2.3 ns | **1.9× slower** |
| read through binding, reference | 1.1 ns | 1.6 ns | **1.4× slower** |
| construct the model | 23.8 ns | 2.8 ns | **8.6× faster** |
| build both registries | 1.15 µs | 1.15 µs | the same |

The read row is the cost, stated plainly. It is boxing: `Property<Integer>` holds
a box already and hands it back, where a woven `int` makes one per read. For a
reference-typed field the gap is the switch alone.

**A field written from outside its declaring class is not observed.** An inner
class assigning to its outer's `@Bind` field compiles to a `putfield` in a
*different* class, which this transform never sees. Nested classes of the model
are the realistic case, and the failure is silent. Lambdas are fine — javac
compiles them into synthetic methods of the same class, and there is a test that
proves the rewrite reaches them.

**A `@Model` may not extend a `@Model`,** and that is a build error rather than
another silent gap. Both classes would get a `goldberry$listeners` field, the
subclass's would shadow the superclass's, and the inherited `@Bind` fields would
then notify a store nothing subscribes through — half a model working, which is
the worst thing this design could produce. Catching it needs the whole tree,
because neither class can see the problem alone, so the weaver takes two passes:
one to learn which classes are models, one to weave them. A model extending an
ordinary class is untouched by the rule.

**An array cannot be bound.** Only the assignment is observed, so `values[0] = x`
would notify nobody; the weaver refuses one rather than letting that be
discovered later. Hold a `List` and assign a new one — the same rule ADR-0109
already stated for `Property`.

**A model is now a build-time contract.** A module that keeps one applies
`goldberry.weave`, and forgetting to is a loud failure at the first
`Models.bindings(...)` rather than a quiet one at the first click. That is worse
than an annotation processor, which needed only a dependency; it is the price of
touching bytecode, and the error message names the missing step.

Every rule ADR-0096's processor enforced is enforced by the weaver instead, and
each has a test, because a rule with no test is a rule that quietly stopped
applying. What was a compile error is now a build error one phase later — the
same feedback, from a different tool.
