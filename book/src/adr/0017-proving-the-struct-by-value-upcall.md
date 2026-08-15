# ADR-0017: Prove the struct-by-value upcall from C, and make it cheap

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §5, [ADR-0010](0010-hand-written-ffm-bindings.md)

## Context

Yoga measures a leaf node by calling back into the host:

```c
typedef YGSize (*YGMeasureFunc)(
    YGNodeConstRef node,
    float width, YGMeasureMode widthMode,
    float height, YGMeasureMode heightMode);
```

`YGSize` is two floats, and it comes back **by value**. That is the fiddliest
thing Goldberry asks of the Foreign Function & Memory API, and it sits on the
layout hot path: once per measured node, per layout pass, per frame, for every
piece of text on screen.

It is also the one crossing the layout table cannot check. ADR-0010 accepts
hand-written bindings because `libgoldberry` reports its own `sizeof`,
`offsetof` and `_Alignof`, and a test asserts the Java declarations agree. That
argument covers memory. It says nothing about *registers*. `YGSize` is eight
bytes with no padding on every target we ship, so its row in the layout table is
identical everywhere — and yet the return convention differs on each: packed into
XMM0 on SysV x86-64, a homogeneous float aggregate in `s0`/`s1` on AArch64,
folded into RAX on Win64. A `FunctionDescriptor` that is wrong about this
produces an upcall stub the JVM builds without complaint and C reads as garbage.
Yoga would take that garbage as a measurement and lay out around it.

Nothing on the Java side can catch that. Asserting what the callback returns
proves only that Java can read back what Java just wrote.

Two smaller forces, both consequences of this being a hot path and a callback:

An upcall's return value has to live somewhere. The obvious implementation
allocates a segment per call — putting an allocation in the inner loop of layout
to hold a value the linker copies out microseconds later.

And an exception thrown inside an upcall has nowhere to go. There is no Java
frame beneath it, only Yoga's C++. The JVM's answer is to terminate the process.
A measure function calls into text shaping, which loads fonts, which can fail.

## Decision

The check comes from C. `libgoldberry` exports `goldberry_probe_measure`, which
takes a `YGMeasureFunc`, calls it, and reports what arrived through
out-parameters. It is compiled by the target's own C compiler against Yoga's own
header, so what it receives is what Yoga would receive. `MeasureUpcallTest` calls
it through an upcall stub with two distinct, exactly representable values and
asserts both survive. That test runs on every target in CI, which is the only
place the question is actually answered.

Out-parameters rather than a returned `YGSize`, deliberately: returning one would
put a struct-by-value *downcall* return in the same test, and a failure could
then be either mechanism.

`MeasureCallback` allocates its return segment **once**, in the arena it owns,
and hands the same segment back on every call. The callback is synchronous — the
linker has copied the result before Yoga can call again — so one segment per
callback is enough, and the hot path allocates nothing.

And a measure function that throws does not reach C. `MeasureCallback` catches
everything, reports zero to Yoga, holds the first exception, and rethrows it from
`throwIfFailed()` once control is back in Java. One node is laid out wrongly; the
alternative is losing the process.

## Alternatives considered

**Prove it by binding Yoga's node API instead.** `YGNodeSetMeasureFunc` plus a
real `YGNodeCalculateLayout` would exercise the callback the way production will.
It is the stronger end-to-end test and it should exist — but it answers this
question no better, because the ABI is the risk and both callers use the same
one, and it needs six more exported symbols and an opaque-handle design that is
not written yet. Proving the mechanism first is what M0 asked for; the node
binding follows.

**Trust the layout table.** It is already the safety argument for everything
else, and extending it here would cost nothing. It would also be worthless:
`YGSize` has the same size, alignment and offsets on all six targets, so the row
passes whether or not the return convention is right. A check that cannot fail
is worse than no check, because it reads like coverage.

**Allocate the returned segment per call**, from a confined arena closed
immediately. Simple, obviously correct, and it puts an allocation and an arena
close on the path that runs once per text node per frame. Rejected on cost, not
on correctness — but the reuse it replaces is only safe while the callback is
synchronous, which is now a documented assumption rather than an obvious truth.

**Let exceptions propagate.** Honest, in that a broken measure function is a
serious bug and a hard crash is unambiguous. It is also unrecoverable and
untestable: `MeasureUpcallTest` could not assert on a failing callback at all,
because the JVM running the assertion would be gone.

**jextract.** It generates upcall stubs and would have got the descriptor right
without anyone reasoning about XMM0. ADR-0006 chose it and ADR-0010 replaced it;
this is the class of bug that decision took on, and the answer is not to
re-litigate it but to make the check specific enough to catch this.

## Consequences

The `YGSize` return is proven on real hardware for every target, and the proof
runs on every push rather than being asserted once and assumed after.

`goldberry_probe_measure` is a test-only symbol in a shipped library. It is four
lines and it is on the export list, which is the honest place for it — the
alternative is a second artifact built with different flags, which is a worse
thing to have to trust.

Measure functions may not assume the process dies when they fail. Every native
call that can invoke a callback must be followed by `throwIfFailed()`, and
forgetting it makes a failure look like a measurement of zero. This is the one
sharp edge the design adds, and it is currently a documentation obligation rather
than something the compiler enforces.

`MeasureCallback` is confined to its creating thread and must be closed, and
closing it while a Yoga node still holds the pointer leaves that node calling
freed memory. Node lifetime and callback lifetime are now coupled, and nothing
yet enforces the coupling — the Yoga node binding will have to.

The reuse of the return segment is safe only while the callback is synchronous
and non-reentrant. Yoga's measure functions are leaf calls, so this holds today.
If a future engine measures in parallel, the segment becomes per-thread or the
design goes back to allocating.
