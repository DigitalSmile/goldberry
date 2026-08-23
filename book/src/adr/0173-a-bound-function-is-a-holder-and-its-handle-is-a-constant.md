# 173. A bound function is a holder, and its handle is a constant

Date: 2026-08-23

## Status

Accepted. Rebuilds the call layer
[ADR-0161](0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md) designed,
keeping its measurement and dropping its shape. Relates to
`docs/ARCHITECTURE.md` §3.1.

## Context

ADR-0161 established the thing that matters and cannot be relaxed: **a downcall
handle is a compile-time constant or it is not a call.** Twenty million calls to
a trivial `int f(void)` on GraalVM CE 25.2.4:

| how the handle is held                    | JVM   | native image |
|-------------------------------------------|-------|--------------|
| bound to its address, built at run time   | 10 ns | 4560 ns      |
| unbound, built at run time                | 10 ns | 4500 ns      |
| unbound, built at **image build time**    | 10 ns | 10 ns        |

It then drew a conclusion from that which was one step too far: because the
constant must be *read by the method that calls it* — 8.9 ns when the helper
names it, 810 ns when the same constant arrives as a parameter — and because the
binding classes shared per-shape helpers, the handles were named for their
**signature** and shared across every symbol that had one. Fifty-six constants,
`INT__PTR_PTR_INT` and the like, for a hundred and thirty-four bound functions.

What that cost is visible in every binding. A call site named a shape rather
than a function; the function's own name travelled beside it as a string, for
the failure message; the address travelled as a third thing, in a
`private final MemorySegment` field; and each binding class grew its own set of
`call` / `invoke` / `callBoolean` / `getFloatKeyed` helpers so the `try`/`catch`
sat in one place. Yoga had nine such helpers, `SdlVideo` fourteen.

```java
private final MemorySegment contextEnd;                      // one
this.contextEnd = Downcalls.symbol(lookup, "bl_context_end"); // two
check("bl_context_end",                                       // three
        (int) Downcalls.INT__PTR.invokeExact(contextEnd, context));
```

Three things that are one thing, kept apart because of a performance claim about
a fourth.

## Decision

**A bound function is a holder**: a small final class holding the address of one
C function, with its handle as a `private static final MethodHandle FD_<symbol>`
and a `call` whose parameters are ordinary Java types.

```java
public static final class ContextEnd {
    private static final MethodHandle FD_bl_context_end =
            Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private final MemorySegment address;

    public int call(MemorySegment a1) {
        try {
            return (int) FD_bl_context_end.invokeExact(address, a1);
        } catch (Throwable t) {
            throw Downcalls.failure("bl_context_end", t);
        }
    }
}
```

**The holders are grouped in a record per subject** — not per library. A
`Blend2DCalls` of forty-six functions is a list, not a type; `ImageCalls`,
`ContextCalls`, `PathCalls`, `FontCalls` and `RuntimeCalls` are each the surface
of one object, and the binding class that holds one is the surface of one object
too. A record is what a binding class keeps instead of forty `MemorySegment`
fields:

```java
check("bl_context_end", calls.contextEnd().call(context));
```

ADR-0161's rule is not relaxed by this — it is satisfied more strictly than
before. `FD_bl_context_end` is `static final` and is read *inside* the method
that invokes it, which is the 8.9 ns case; and because there is now one handle
per function rather than one per shape, no call site reaches a constant through
a parameter at all. The per-shape helpers that forced the compromise are gone,
because the holder's `call` **is** that helper, one per function, naming its own
handle.

**The holders live in packages that contain nothing else** —
`…natives.calls`, `…natives.sdl.calls`, `…natives.yoga.calls`,
`…natives.blend2d.calls`, `…natives.harfbuzz.calls` — and those packages are what
`--initialize-at-build-time` names. That is not tidiness; it is the only form
that works, and the measurement is below.

## Alternatives considered

**`record Downcall(MethodHandle handle, MemorySegment address)`** — one holder
type for everything, which is the design anyone reaches for first and the one
this ADR started from. It puts the handle in an *instance* field: a value read
from an object, not a constant read from a class. Measured at **4539.53 ns/call**
in an image, which is ADR-0161's first row with a new spelling. Rejected on the
number.

**One holder type per *signature*** — fifty-six records, `VOID__PTR` and friends,
each with the address as its only field and its handle `static final` on the
enclosing `Downcalls`. This was built, and it works: it keeps ADR-0161's
constant-folding and removes the `try`/`catch` from the call sites. It was
rejected because it keeps the thing that was actually wrong — a call site still
names a shape, the function's name still travels separately as a string, and
`Downcalls.INT__PTR_PTR_INT.bind(lookup, "SDL_UpdateWindowSurfaceRects")` is not
an improvement on what it replaces.

**Naming the enclosing class in the build flag.** The obvious way to keep the
holders nested inside the binding they belong to. It silently does not work:

| flag                                            | where the handle is             | ns/call |
|-------------------------------------------------|---------------------------------|---------|
| `--initialize-at-build-time=Outer`              | `static final` on `Outer`       | 10.55   |
| `--initialize-at-build-time=Outer`              | `static final` on `Outer$Nested`| **4537.82** |
| `--initialize-at-build-time=Outer,Outer$Nested` | the same nested class           | 11.25   |
| `--initialize-at-build-time=<package>`          | nested, anywhere in it          | 8.07    |
| any                                             | instance field of a record      | 4539.53 |

Measured here, on GraalVM CE 25.2.4, twenty million calls to
`goldberry_abi_version`. The second row is the trap and it is silent — the image
builds, runs and paints correctly at a fortieth of the speed, which is exactly
the failure ADR-0161 was written about.

The third row works and is unmaintainable: a hundred and thirty-four nested class
names in a build flag, each of which has to be remembered when a symbol is added.
The fourth row is what shipped. Naming the *binding* packages instead was
rejected too — `…natives.sdl` holds `Sdl` and `SdlVideo`, whose holder idiom
`dlopen`s the library, and build-time initialising those would run the `dlopen`
in the builder.

**Yoga's length setters are the one exception**, and they are per *shape*.
`width: 50%` and `width: 50px` are `YGNodeStyleSetWidthPercent` and
`YGNodeStyleSetWidth`, and which is called depends on the value — so the function
is chosen at run time, and eleven properties × three functions would need eleven
record types to group them. `SetLength`, `SetAuto`, `SetKeyedLength` and
`SetKeyedAuto` each serve several symbols and carry the symbol they were bound
to, so a failure still names the function rather than the shape. The handle is
still a constant read inside `call`, which is the part that cannot bend.

## Consequences

**The binding classes lost a quarter to a half of their lines**, and all of it
was plumbing:

| binding | before | after |
|---|---|---|
| `Yoga` | 658 | 409 |
| `Blend2D` | 821 | 561 |
| `SdlVideo` | 837 | 653 |
| `HarfBuzz` | 393 | 249 |
| `Sdl` | 296 | 198 |

Thirty-six per-shape invocation helpers are gone, along with every
`MemorySegment` field and every function name written as a string argument. A
Yoga setter is now one line: `styleCalls.styleSetFlexGrow().call(node, value)`.
`Blend2D` went further and split into five classes of 78 to 248 lines, one per
Blend2D object, each holding the one record that is its own surface.

**Every `call` states its parameters.** `call(a1, a2, a3)` is `call(context,
rect, argb)`, under a summary, the C prototype it binds, and a `@param` for each
argument. The names were not invented: the wrapper method at each call site
already named them — `contextFillRect(MemorySegment context, MemorySegment rect,
int argb)` passes them straight through — so they were read back out of the
source and only the twenty-two that were literals had to be written by hand.

**A failure names the function it was.** `Blend2D`'s four `invoke` helpers
reported `"a Blend2D call"` for any of the eighteen symbols that went through
them, because a shared helper had no way to know which. A holder does.

**Verified end to end, not argued.** A native image built from the packaged
`goldberry-natives` jar — so with the shipped `native-image.properties` and
nothing added — calls `goldberry_abi_version` through its holder at
**9.84 ns/call**, against 10 ns on the JVM. `HolderShapeTest` checks the rest by
walking the compiled classes: that every holder's `call` is exactly its `FD_…`
descriptor with the address dropped, that each keeps one address, and that no
handle is anything but `static final`. It finds the holders rather than listing
them, so one added tomorrow is checked tomorrow.

**134 handles where there were 56.** Each is one `MethodHandle` linked from a
descriptor at image build time; the stubs behind identical descriptors are
shared by the linker. Nothing measurable, and it buys the naming.

**Roughly 3200 lines of holder code, all generated in shape and none of it
interesting.** That is the real cost, and it is why `DowncallsTest` was replaced
rather than deleted: what used to be checkable was only that a *name* matched its
layouts, because nothing tied either to a call site. A holder states its
signature twice — once in layouts, once in Java types — in one class, so the
check is now that the two agree, which is the check that was wanted all along.

**A new symbol is more work than it was.** It used to be one field, one lookup
and a call through an existing constant; it is now a holder class and a record
component. In exchange, adding one cannot get the shape wrong without the
compiler or `HolderShapeTest` saying so.
