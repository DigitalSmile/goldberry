# ADR-0031: Blend2D, and painting into a borrowed buffer

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §5, [ADR-0002](0002-cpu-rasterization-with-blend2d.md), [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0018](0018-sdl-conventions-stop-at-the-boundary.md), [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md)

## Context

[ADR-0002](0002-cpu-rasterization-with-blend2d.md) chose Blend2D as the
rasterizer on day one, and until now nothing was bound to it. `Frame` wrote
pixels by hand — a row of bytes built once and copied down a rectangle — with a
comment saying it was a placeholder and Blend2D would take over in M1. It is M1.

Blend2D's C API has three shapes worth deciding about before writing any of it.

**One object model, sixteen bytes wide.** Every "core" object — `BLImageCore`,
`BLContextCore`, `BLPathCore` — is a single `BLObjectDetail`: a union that
overlaps a static payload with a pointer to a dynamic `Impl`, plus a 32-bit
info word. Not a type per object; one union, reused.

**`BLResult` on everything, and no way to ask what it means.** Every function
returns an unsigned code, zero for success. There is no `GetError`, no errno,
and no result-to-string function to bind: `blend2d-debug.h` is header-only, so
there is no symbol for it.

**The buffer already exists.** `:core` does not need Blend2D to allocate a
frame. When the platform lends its own surface — which SDL does — the pixels to
draw into are the compositor's, and copying into them afterwards is a full frame
of memory traffic per frame.

## Decision

**The image borrows; it never allocates.** Only `bl_image_init_as_from_data` is
bound, with a NULL destroy callback: Blend2D is told to free nothing, because
the memory was never its own. `BlendImage.wrapping` refuses a heap `ByteBuffer`
outright rather than copying one — a heap buffer has no address the collector
will not move, and copying would hand back an image that paints perfectly into
memory nobody presents.

**The display scale is a context transform, not arithmetic.** `BlendContext.on`
scales the context once, and everything drawn afterwards is in logical
coordinates. This is the fractional-DPI story on the paint side, and it is a
real difference rather than a tidier spelling: the old `Frame` rounded logical
to physical with `Math.round` before writing bytes, so on a 1.5&times; display
every edge moved by up to a third of a logical pixel. Blend2D antialiases the
coverage instead — a test asserts that a one-logical-pixel rectangle at 1.5&times;
leaves the second physical pixel about half lit.

**Colours are straight alpha; the buffer is premultiplied; Blend2D converts.**
This inverts what writing pixels by hand required, and is the single easiest
thing to get wrong when moving from one to the other. `Frame.premultiply` is
gone — a caller who still premultiplied would apply alpha twice and get a frame
that is visibly too dark with nothing reporting a problem. The test asserts the
exact number: `0x80402010` in, `0x80201008` stored.

**`fill` replaces, `fillRect` blends.** A background is a replacement — blending
a translucent colour over the previous frame composites onto it, so the same call
every frame darkens until it is opaque. `fillRect` is a blend, which is a
behaviour change: the hand-written path overwrote pixels and ignored alpha
entirely, so a translucent rectangle used to come out solid.

**`BLResult` becomes an exception at the boundary**, and only codes worth
reading are named. A name in `BlendResultCode` is a constant the layout probe
checks against the compiled library, so naming codes nobody can trigger would be
registry weight for no reading; an unnamed code still reports its hex value.

**The binding class is package-private**, as Yoga's is
([ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md)): `BlendImage` and
`BlendContext` are the only way in, and there is no second path that reaches
`bl_context_destroy` without the wrapper that knows whether the context is still
attached.

**Blend2D is compiled with default symbol visibility, alone among the
upstreams.** This is the finding that cost the most to reach, and it is
[ADR-0018](0018-sdl-conventions-stop-at-the-boundary.md)'s lesson in a new
costume — *a version script cannot promote a symbol that is already hidden.*
There, `--exclude-libs,ALL` did the hiding. Here it is Blend2D's own header:

```c
#if !defined(BL_STATIC)
  ... #define BL_API __attribute__((visibility("default")))
#endif
#ifndef BL_API
  #define BL_API          /* a static build gets nothing at all */
#endif
```

A static Blend2D defines `BL_STATIC`, so `BL_API` expands to nothing, and the
`CMAKE_CXX_VISIBILITY_PRESET=hidden` set for the whole superbuild then applies to
every one of its functions. The symptom is precise and misleading: the symbols
link in perfectly well — `-u` pulls each one out of the archive — and arrive in
`libgoldberry.so` as **local**. `nm -D` shows no `bl_*` at all while plain `nm`
shows every one of them as a lowercase `t`. The exported count sat at 99 against
an export list of 112, and the missing 13 were exactly the Blend2D ones.

Yoga and SDL do not have this problem because `YG_EXPORT` and `SDL_DECLSPEC` are
unconditionally `visibility("default")`, static build or not.

## Alternatives considered

**Let Blend2D allocate the image and blit into the surface afterwards.** The
obvious shape, and what most Blend2D examples do. It is a full frame of memory
traffic every frame — the thing ADR-0019's borrowed-buffer path exists to avoid.

**Keep the hand-written pixel loops for flat fills and use Blend2D only for
paths.** Tempting because a flat fill really is faster as a `memcpy` down the
rows. It also means two paint paths that disagree about whether alpha means
anything, and the disagreement shows up as a colour being subtly wrong depending
on which one drew it.

**Round logical coordinates to physical in `Frame`, as before.** Simpler, and
wrong in a way that is invisible in a screenshot at 100%. Every fractional scale
moves edges.

**Model each core object type separately.** `BLImageCore` and `BLContextCore` as
their own layouts. They are the same union, so the layouts would be identical
copies; instead there is one `BLObjectDetail` and the C table registers all
three, which turns "they are all the same shape" from an assumption into an
assertion.

**Define `BL_BUILD_EXPORT` for the Blend2D target instead of changing its
visibility preset.** It is the macro that produces `visibility("default")` — but
only on the `!BL_STATIC` branch, which a static build is not on, and on Windows
it flips `dllimport` to `dllexport`, which is meaningless for a static archive
and would be one more thing to reason about on the target that has never been
built.

**Name every `BLResult` code.** Around forty constants, most of them for
operations Goldberry does not perform.

## Consequences

**The toolkit rasterizes through Blend2D.** The showcase opens a window and
presents three frames through the new path — image, context, scale transform,
fills, end, present — on linux-x64 with AsmJit compiling its pipelines at run
time.

**The ABI version is now 6.** The shim gained ten struct layouts and twenty-odd
constant rows, and the export list gained 13 symbols. A `libgoldberry` built
before this change is refused at load rather than crashing.

**`fillRect` blends where it used to overwrite.** Any code relying on the old
behaviour — a translucent colour coming out solid — changes. Nothing in the
showcase does.

**The visibility fix is untested on macOS and Windows.** The Mach-O
`-exported_symbols_list` branch has the *same* requirement as the ELF version
script: a local symbol cannot be exported, so this fix is load-bearing there too
and has never run. The MSVC `.def` branch does not depend on visibility
attributes at all, so it is unaffected — which is its own kind of untested.

**Apple Silicon's W^X handling is now actually exercised.** ADR-0002 flagged that
AsmJit allocates executable memory and that macOS needs `MAP_JIT` plus
`pthread_jit_write_protect_np`, and asked for it to be verified on the macOS leg.
Nothing triggered it before, because nothing created a rendering context. The
first frame the macOS build paints is the test.

**A context is created and destroyed per frame,** and the cost has not been
measured. Blend2D compiles its pipelines with AsmJit on first use, so the first
frame is dearer than the rest; the start-up timeline (ADR-0028) is where that
will show. `thread_count` is left at zero — synchronous, on the calling thread —
so Blend2D's banded multithreading is a knob away and deliberately not turned
until there is a frame worth measuring.

**`:core` tests can now need a native library, and get one by default.**
`:natives` publishes the host library's path as an extension property rather
than letting `:core` re-derive it — the host-target detection stays in one
place, so adding a target cannot leave the two projects testing different
libraries. `:core:test` depends on `:natives:cmakeBuild` under the same bargain
`:natives` already makes, with the same escape hatches:
`-Pgoldberry.skipNative=true` for a Java-only build, and an explicit
`-Dgoldberry.native.library=<path>` for verifying an artifact built elsewhere.
Without any library at all the rendering tests skip rather than fail.

**Deliberately not bound.** Paths, gradients, strokes, images and image codecs,
clipping, `save`/`restore`, and all of the text API. Fonts and glyph runs are the
next piece and are what HarfBuzz feeds.
