# 161. A downcall handle is a constant, or it is not a call

Date: 2026-08-20

## Status

Accepted. Changes how every binding written under
[ADR-0010](0010-hand-written-ffm-bindings.md) holds what it bound, and adds a
second thing `:natives` declares for an image build alongside the resources of
[ADR-0160](0160-a-modules-own-resources-are-declared-not-traced.md).

## Context

The showcase, built as a native image and painting the same scene as the JVM
does, was reporting this on its `hud`:

```
paint    37.5 / 41 / 53 ms
raster   34.7 / 36 / 52 ms
```

Against a 16.7 ms budget, painting two and a half frames' worth of work per
frame — and the JVM build of the same code, on the same machine, sits at a
fraction of it. `raster` being almost all of `paint` said where to look: the
Blend2D calls, which is to say the Foreign Function & Memory API.

This is a known GraalVM limitation, and an open one.
[oracle/graal#8113](https://github.com/oracle/graal/issues/8113) has *"Improve
downcall performance (currently always unoptimized)"* on its list of unfinished
work, and [oracle/graal#12219](https://github.com/oracle/graal/issues/12219) is
somebody else's SDL application going from 400 fps to 25. A GraalVM engineer
answered that one in May 2026: it is hard to fix in general, there are no
resources to fix it, and **there is a workaround** on 25.1 and later — build the
downcall handle *unbound*, and initialise the class holding it at image build
time.

So: is that the cause here, and does the workaround work?

### Measured, before deciding anything

`goldberry_abi_version` is the cheapest function `libgoldberry` exports — it
returns a constant — so timing it in a loop times the crossing and nothing else.
Five million calls, GraalVM CE 25.2.4 (JDK 25.0.4), linux-x64:

| how the handle is held                  | JVM   | native image |
|-----------------------------------------|-------|--------------|
| bound to its address, built at run time | 10 ns | **4560 ns**  |
| unbound, built at run time              | 10 ns | **4500 ns**  |
| unbound, built at **image build time**  | 10 ns | **10 ns**    |

**450x**, and both halves of the workaround are needed: the middle row is the
one that says so. An unbound handle built at run time is exactly as slow as a
bound one.

The reason is the same one behind every `MethodHandle` performance note. A
handle is only a *call* when the compiler can see which handle it is; otherwise
it is an interpreted lambda form. On the JVM the JIT gets there anyway — it
watches the field, sees one value, and folds it. **A native image has no second
chance**: whatever the compiler could not prove at build time, it emits the slow
path for, once, forever.

And a handle *bound to an address* can never be proven at build time, because
the address does not exist yet — `libgoldberry` is `dlopen`ed by the process
that runs, which is the whole point of
[ADR-0159](0159-a-native-image-carries-its-own-library.md).

## Decision

**A binding keeps the address it looked up. The handle is a constant, shared by
every symbol with the same signature, and it is linked while the image is being
built.**

`Downcalls` is that set of constants — one `static final MethodHandle` per
signature, each linked from a `FunctionDescriptor` alone:

```java
public static final MethodHandle INT__PTR_PTR_INT = of(INT, PTR, PTR, INT);
```

An unbound handle takes the function to call as its leading argument, so the
class depends on no `SymbolLookup` and can be initialised in the builder. A
binding then holds a `MemorySegment` where it used to hold a `MethodHandle`:

```java
this.contextFillRectDRgba32 = Downcalls.symbol(lookup, "bl_context_fill_rect_d_rgba32");
...
check("bl_context_fill_rect_d_rgba32",
        (int) Downcalls.INT__PTR_PTR_INT.invokeExact(contextFillRectDRgba32, context, rect, argb));
```

**134 bindings across Blend2D, Yoga, HarfBuzz, SDL and the shim share 56
signatures**, which is what makes this a small file rather than a parallel copy
of the bindings.

`:natives` ships the flag that makes it work, in
`META-INF/native-image/io.github.digitalsmile/goldberry-natives/native-image.properties`:

```
Args = --initialize-at-build-time=io.github.digitalsmile.goldberry.natives.Downcalls \
       --initialize-at-run-time=io.github.digitalsmile.goldberry.natives.NativeLibrary
```

Both classes have an opinion about *when* they are initialised and they are
opposite ones, so both belong beside the code that holds the opinion — not in
the build file of every application that wants an image. This is ADR-0160's
argument for resources, applied to class initialisation: it travels in the jar,
and a consumer building an image gets it without knowing it needs it.
`--initialize-at-run-time=…NativeLibrary` moves here from
`example/build.gradle`, where it had been since ADR-0127.

### Naming a signature

`<return>__<arguments>`, in **C's words rather than Java's**: `INT__PTR_PTR_INT`
is `int f(void*, void*, int)` and `INT__VOID` is `int f(void)`. `BOOL` is C's
`_Bool` — one byte, not the four `JAVA_BOOLEAN` suggests — and `PTR` is any
pointer. The declaration reads the same way, because the layout constants are
spelled with the same words:

```java
public static final MethodHandle INT__PTR_PTR_INT = of(INT, PTR, PTR, INT);
```

This started out as JVM descriptor letters — `I_PPI`, `V_PFFI` — which is
shorter and needs a legend. It was not worth the legend: these names appear at
two hundred call sites and are read far more often than they are typed.

The name is not decoration. `invokeExact` checks the constant's type against the
**static types at the call site**, so a call site that reads the name correctly
and reaches for a constant that does not match its arguments throws
`WrongMethodTypeException` on the first call rather than pushing four bytes where
the ABI wanted eight. `DowncallsTest` checks the other direction — that every
name describes the layouts beside it — so the two halves cannot drift.

## Alternatives considered

**One handle per function, named for it, instead of one per signature.** The
obvious reading of ADR-0010, and the descriptor would sit beside the symbol
again. It is not available, and the reason is the same trap one level down:
**the constant has to be read by the method that calls it.** Measured, same
harness — a constant handle passed *into* a three-line static helper costs
**810 ns** in an image against **8.9 ns** when the helper names it itself. The
JVM inlines and folds either way; native-image does not.

The binding classes call through shape-generic helpers — `Yoga.call`,
`SdlVideo.callBoolean`, `Blend2D.invoke` — exactly so that a hundred call sites
share one `try`/`catch`. A handle named for one C function cannot be read inside
a helper shared by forty of them, so per-function naming would mean deleting
every helper and inlining it at roughly five extra lines a site. Yoga's eleven
length properties would not even benefit: they compose their symbol names at run
time (`YGNodeStyleSet` + property + `Percent`/`Auto`), so those thirty-three
symbols have no compile-time name to be called after. The signature is what the
helpers have in common, so the signature is what the constants are named for.
`DowncallBenchmark.throughAHelper` keeps the number honest.

**Overloaded call helpers instead of named constants** — `Downcalls.callInt(fn,
a, b, c)`, with Java's overload resolution picking the signature. Shorter at
every call site, and rejected: when no overload matches exactly, overload
resolution does not fail, it **widens**. A call site passing an `int` where only
a `(void*, long)` helper exists compiles silently and corrupts the frame. Named
constants turn the same mistake into "cannot find symbol".

**Keep the descriptor beside the symbol, and name the constant at the call site
as well.** Preserves the ADR-0010 reading of the constructor — symbol, C
prototype, descriptor, in one place — at the cost of stating the signature
twice, in two files, where the compiler checks neither against the other. The C
prototypes stay as comments; the descriptor does not.

**`Linker.Option.critical()`.** GraalVM's FFM documentation offers it as a
performance option, and it is the wrong tool here: it removes the thread-state
transition, which is a real gain for a trivially short function and a real
hazard for `bl_context_fill_path_d_rgba32`, which is neither short nor a thing
that should be holding off a safepoint. It also does nothing about the 4.5 µs of
lambda-form interpretation, which is the actual cost. Nothing here is marked
critical.

**Wait for GraalVM.** #8113 is open, unticked, and answered with "we do not
currently have the resources". Waiting means shipping an image that paints at
25 fps.

## Consequences

**The showcase's native image went from 42 ms a frame to 1.0 ms.** Sixty frames
headless (`-Dgoldberry.backend.videoDriver=dummy --frames=60`), same binary
shape, same machine:

| build                                    | 60 frames | per frame |
|------------------------------------------|-----------|-----------|
| before                                    | 2.533 s   | 42.2 ms   |
| this change, with the flag withheld       | 2.55 s    | 42.5 ms   |
| this change                               | 0.061 s   | **1.0 ms**|

The middle row is a control: the same code, built with the properties file moved
aside. It reproduces the original number exactly, which is what makes the last
row attributable to the flag rather than to anything else in the change.

The image is now **faster than the JVM over a short run** — the JVM spends its
first frames compiling (83 ms of style resolution on frame 0, 24 ms on frame 1),
and an image has nothing to compile. That is what a native image was supposed to
be for, and until now the FFM path was taking it back.

**The JVM is unaffected.** 9.81 ns bound against 9.27 ns unbound, measured by
`DowncallBenchmark`: the address arrives as a value the JIT folds just as it
folded the bound handle's. Nothing was traded away.

**Two invocation paths that boxed every argument are gone.** `SdlVideo` and
`SdlCursors` called through `invokeWithArguments(Object...)`, which boxes each
argument and decides the shape at run time from what it was handed. Both are now
`invokeExact` against a constant. That is a JVM improvement as well as an image
one, and it was not the point — it fell out of having to name a signature.

**The FFM half of the traced metadata stopped depending on the run.** ADR-0156
warns that a trace is only as good as the run that produced it, and
[ADR-0160](0160-a-modules-own-resources-are-declared-not-traced.md) took
resources out of the trace for exactly that reason. Descriptors are now linked
in `Downcalls`' class initialiser, which runs on any JVM start — so the agent
records all 56 whether or not the run reached the screen that uses them. The
`directUpcalls` entries still depend on the run.

**A signature that is used once still needs a constant.**
`INT__PTR_INT_INT_INT_PTR_LONG_INT_PTR_PTR` exists
for `bl_image_init_as_from_data` alone. That is the cost of sharing by shape
rather than by symbol, and it is paid in one line.

**This is a workaround, and it is load-bearing.** If #8113 is ever finished,
`Downcalls` becomes an ordinary way to write bindings rather than a necessary
one, the properties file can lose a line, and the handles can move back beside
their symbols. Until then, deleting either half —
the unbound handle or the build-time initialisation — silently costs a factor of
forty, with nothing failing and no test going red. The `--initialize-at-run-time`
control above is the check; `book/src/native.md` says how to run it.
