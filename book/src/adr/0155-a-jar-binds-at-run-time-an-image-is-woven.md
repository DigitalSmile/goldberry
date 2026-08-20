# 155. A jar binds at run time; an image is woven

Date: 2026-08-20

## Status

Accepted. Amends [ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md) and
[ADR-0127](0127-the-binding-schema-fits-a-closed-world.md): both stand, and what
changes is *who has to run the weaver*.

## Context

ADR-0125 made a `@Bind` field observable by rewriting the compiled class, and
ADR-0127 explained why that has to happen in the build rather than at run time —
a GraalVM native image has no class loading and no class generation, so anything
generative has to be finished before the image is.

That reasoning is still right, and it justified making the weaver **mandatory**.
Every module keeping a model applied `goldberry.weave`, `Models` threw at the
first sight of an unwoven class, and the message named the missing build step. A
model that compiled and was not woven was treated as a broken build.

What that overlooked is who pays. The weaving is cheap; the *mandatory* is not,
and it is charged to everybody who is not building an image — which is nearly
everybody:

- **Every consumer has to install a build step.** Gradle gets a plugin, Maven
  gets two `<execution>` blocks of `exec-maven-plugin` because there is no Mojo,
  and anything else gets `java -jar goldberry-weaver.jar target/classes` and a
  place to put it. A toolkit whose "hello window" needs a class post-processor
  configured before the first field notifies is a toolkit with a much steeper
  first hour than it needs.
- **An IDE does not run it.** Running a main class from IntelliJ compiles with
  the IDE's own compiler into the IDE's own output directory, and nothing weaves
  it. The failure was loud and clear and *still* meant "do not run this from your
  IDE", which is where an application author spends the day.
- **It is unnecessary for what a jar can do.** Everything a woven class does is
  reachable reflectively — the annotations are on the members, `VarHandle` reads
  the field, `MethodHandle` calls the action. Everything except the one thing:
  seeing the write.

That last point is the real question, and it is worth being exact about. A
`putfield` is not virtual; no subclass, proxy or handle can intercept one, and
the class that declares the field is the only place the write can be observed.
That is ADR-0125's whole argument and it has not changed. Weaving is not one way
of noticing a change — it is the *only* way of noticing it **at the instant it
happens**.

## Decision

**A model is bound one of two ways, and the build decides which.**

- **Woven** — the weaver rewrote the class. What a **native image** is built
  from: nothing is reflected, nothing is looked up, a change notifies from inside
  the assignment that made it, and ADR-0127's closed-world claim holds unchanged.
- **Bound at run time** — the class is as javac left it, and `Models` builds the
  same two registries reflectively. What an **ordinary jar** uses, and the
  default: `./gradlew run`, `mvn exec:java`, a green Run button and a `java -jar`
  all work with no build step at all.

`Models` picks. `Models.bindings(model)` returns the woven registry if the class
implements `BoundModel` and a reflective one otherwise, and every method on that
class answers the same for both. An application does not branch on it; the only
thing that reports which it got is `Models.isWoven`, which is a diagnostic.

`@Bind` and `@Action` become `RUNTIME`-retained, because a `CLASS`-retained
annotation is exactly the one the reflective binder cannot read.

### A change is noticed by a sweep

The one thing reflection cannot do, done the only way it can be:
`RuntimeBinding` keeps what each field held at the end of the last sweep, and a
**sweep** compares, fires the listeners of the fields that moved, and asks for
the restyle and the frame each of them declared. Read access is unaffected and
exact — an `Observable` over a `VarHandle` sees the field itself, so a value is
never stale when something asks for it. Only the *notification* is deferred.

Three places sweep, chosen so that the deferral is invisible in the cases that
actually arise:

1. **After every action a registry dispatched** — and after it, every other model
   bound at run time, not only the one that published the action. An `@Actions`
   record holds no fields of its own and writes to the model beside it
   ([ADR-0134](0134-a-write-is-rewritten-wherever-it-is.md),
   [ADR-0136](0136-an-application-is-values-actions-views.md)), so sweeping only
   itself would sweep nothing at all.
2. **At the top of every frame**, over the models an `Application` named. A
   change made from a timer or a finished background job therefore reaches the
   screen with the next frame the window was going to paint anyway.
3. **Wherever the application says so**, with `Models.refresh(model)` — a no-op
   returning `false` for a woven model, so the call is correct in both forms.

What is left is one honest gap: a field written from **neither** an action nor
anything that leads to a frame, and followed by nothing. The showcase has exactly
one — a background job's continuation calling `setStatus` — and it is one line of
`Models.refresh` with a comment saying why.

### The catalog half is not affected

The weaver does two unrelated things, and only one of them moves. Collecting a
module's `@Markup` widgets into a `WidgetCatalog` and patching `provides` into
`module-info.class` ([ADR-0131](0131-a-widget-package-announces-itself.md)) has
**no** runtime equivalent — finding annotated classes while the program runs
means scanning the path, which is the thing a `provides` exists to avoid. So
`WeaverMain` grows `--models` and `--catalog`, the Gradle plugin registers a task
per half, and the catalog half stays hung off `classes` for every build while the
model half waits for `-Pgoldberry.nativeImage=true`.

## Alternatives considered

**Keep weaving mandatory and improve the error message.** The message was already
good — it named the class, the annotation and the Gradle plugin. The problem was
never that the failure was confusing; it was that there was a failure at all, in
a case where the toolkit could simply have worked.

**Generate the woven class at run time and load it.** `ClassFile.of().build(...)`
plus `defineHiddenClass` would produce exactly the right bytes, and cannot be
used: `new Settings()` in the application's own code names the class javac
compiled, and no hidden class can take its place. This is also precisely the
mechanism ADR-0127 spent its argument avoiding.

**Generate the *binding* at run time, rather than the model.** The near miss, and
the one worth writing down properly, because it does work. A hidden class defined
`NESTMATE` through the `privateLookupIn` the reflective binder already holds may
read the model's private fields with a plain `getfield` — no `setAccessible`, no
handle, no accessor. So a jar could bind reflectively at start-up and, on first
use, emit a per-model sweeper and reader that run at woven speed. It cannot
intercept the write — nothing can, and the sweep would remain — but it would make
the sweep nearly free.

`BindingCodegenBenchmark` builds it and measures it. Reading one `int` field and
comparing it against the last value seen, which is what a sweep does per field:

| | ns/op | |
|---|---|---|
| boxed reflective — what the sweep does today | 15.7 | |
| unboxed reflective — same `VarHandle`, asked for an `int` | **5.2** | no codegen |
| generated nestmate — `checkcast`, `getfield`, `if_icmpne` | **0.60** | |
| a plain Java call | 0.61 | the floor |

Generating one costs 1.9 ms for the first in the process and 124 µs after, paid on
first use — a hitch in the first frame rather than a line in the start-up timeline.

Rejected, for four reasons in ascending order of weight.

*Half the gap was not reflection* — and this was checked rather than asserted. A
sweep measured 38 ns per model per press, of which only ~16 ns was the
read-and-compare above; the rest was this implementation's own plumbing. Doing the
plain specialization the middle row of that table describes took the sweep to 9 ns
per field and 14 ns per model, and the press from 107 ns to 45. That is most of
the distance, for one afternoon and no new mechanism.

*What is left buys nothing anybody is spending.* After that work ten models cost a
button press ~140 ns and a frame's sweep the same, against a 16 ms budget
(ADR-0147). The remaining ~4 ns per field that codegen would recover is real, and
is not a cost this toolkit has.

*It costs `:core` its innocence with GraalVM.* `defineHiddenClass` and
`java.lang.classfile` would become reachable from the module every image is built
from. Models in an image are woven, so the generator would never be *called* — but
reachability analysis cannot prove that, so the image carries the class-file API
and a code path that throws if it is ever reached. ADR-0127's claim is currently
one sentence; it would become one sentence and a substitution.

*And it makes the wrong half faster.* Codegen would make the sweep quick. It would
not make it unnecessary: notification stays deferred, `Models.refresh` stays, and
the semantic difference this ADR spends its length on is untouched. The mechanism
that removes the sweep entirely already exists, is already tested, and is one flag
away. Building a second code generator to make the inferior semantics run at the
speed of the better ones is effort pointed away from the problem.

Revisit if a real workload puts the sweep on a frame profile — the `hud`
(ADR-0146) is what would show it — or if an application's model count reaches the
hundreds.

**A `-javaagent` that weaves at class load.** It works, it is the JavaFX/Hibernate
answer, and it swaps one mandatory build step for one mandatory JVM flag — a
worse trade, since a flag is invisible in the failure it causes and a build step
at least appears in a build file.

**Make the model hold `Property` fields again.** Then nothing needs weaving and
nothing needs sweeping. This is the design ADR-0125 replaced, for the reasons it
records: `clicks.set(clicks.get() + 1)`, an object per value, and a vocabulary
that spreads through every method of the model.

**Sweep on a timer.** Rejected as the thing that turns an idle window into a busy
one. §1.7's "the frame loop is fully idle when no animation is active" is a
property worth more than the last edge case of change detection.

## Consequences

**The first hour is a `dependencies` block.** An application depends on
`goldberry-core` and `goldberry-widgets`, writes a `@Model`, and runs it — from
Gradle, from Maven, from an IDE, from a jar. Nothing to install, nothing to
configure, and the weaving page is now something read by whoever is building an
image rather than by everybody.

**A native image is a flag.** `-Pgoldberry.nativeImage=true` and the model half
of the weaver runs; the image is built from woven classes and ADR-0127's test
still holds over them. The fast form did not get slower, it got optional.

**Two implementations of one contract, and one test that holds them together.**
`RuntimeAgreesWithWovenTest` drives the same model class — once raw, once woven —
through the same actions and asserts the same paths, names, values, notifications
and frame requests. Without it this decision would be two behaviours with one
name, which is worse than either.

**A model in a named module has to open its package.** `opens com.example.app to
io.github.digitalsmile.goldberry.core;`, and the refusal says so in those words.
This is the cost the woven form does not have, and it is a real one: it is a line
in a file most application authors do not otherwise edit. The toolkit adds its
own *read* edge (`Module::addReads`), because that half is not the application's
business — but the `opens` is, and nothing can supply it from outside. A
classpath application is unaffected: the unnamed module is open.

**The registry order is no longer promised across the two forms.** The weaver
publishes in class-file order; `getDeclaredFields` and `getDeclaredMethods`
promise no order at all, so the reflective form sorts by member name rather than
leaving it to the JVM. Both are deterministic, and they differ. The order shows
up in one place — the `Bound: ...` list a strict registry prints when it refuses
a name — so this is a cosmetic difference in a diagnostic, written down here so
it is not found as a surprise.

**Notification is deferred, and the deferral is observable.** A woven model
notifies inside the assignment; a bound one notifies at the next sweep. The three
sweep points cover the paths a document takes, and code that writes a field
outside all of them and expects an immediate callback will not get one. This is
the price of the whole arrangement, it cannot be engineered away, and
`Models.refresh` is the escape hatch.

**An image now carries annotation metadata nothing reads.** `@Bind` and `@Action`
are `RUNTIME`-retained for the jar's sake, and a woven class in an image consults
neither — a few bytes per member against a build step every consumer would
otherwise have to install. `NativeImageComplianceTest` asserts the second half of
that sentence: the woven registries are emitted code, and no annotation is read
to run them.

**Sweeping is O(models × fields) per action, and here is what that is.**
`BindingSchemeBenchmark` measures both forms of one model class in one JVM
(Corretto 25.0.4, i7-4790K, median of 200 samples of 100 000 presses):

| | woven | bound at run time | first cut |
|---|---|---|---|
| a press one widget is watching | 15 ns | **45 ns** | 107 ns |
| per extra model attached, per press | — | **+14 ns** | +38 ns |
| per extra bound field on a model, per press | — | **+9 ns** | +31 ns |
| a read through a binding | 1.3 ns | **10 ns** | 32 ns |
| rebuilding both registries (a document reload) | 570 ns | 630 ns | 617 ns |
| binding a class the first time | 0.58 ms | 2.8 ms first, 0.22 ms after | — |

The last column is the reflective binder as first written, and it is in the table
because the difference between the two is the point: **most of what looked like
the cost of reflection was the cost of writing it carelessly.** The first cut
walked a `List` with an enhanced `for` (an iterator allocated per sweep), asked a
`VarHandle` for an `Object` (a box allocated per field per sweep, thrown away
unread), compared the two boxes with `Objects.equals`, and copied the attached-map
values into a fresh `List` on every action dispatch.

None of that is reflection. Reading the field through the same `VarHandle` as an
`int` and comparing two `long`s — one class per primitive kind, which is verbose
and entirely mechanical — with arrays instead of lists and a snapshot rebuilt on
change instead of copied per press, took a press from 107 ns to 45 and a read from
32 ns to 10, with no new mechanism and no change to a single test.

So a press is **~3× dearer** and a read **~8×**, from a base small enough that it
does not matter: an application with 10 models pays about 140 ns per button press,
against a frame budget of 16 ms (ADR-0147). What is still worth watching is the
slope, because it is paid by every model in the process rather than by the one
that changed — but at 14 ns a model, 40 models cost a press 0.6 µs.

A document reload costs the same either way, which is the number that could most
easily have gone wrong: it is the one thing here that happens per frame in a
development loop.

What remains is genuinely reflection. A `VarHandle` read that the JIT cannot
constant-fold is ~5 ns where a `getfield` is ~0.6, and no amount of care around it
closes that. An index of which model an action writes to would remove the model
axis and cannot be built reflectively either — finding out means reading the
method's bytecode, which is the weaver's job and the thing this exists to avoid
needing.
