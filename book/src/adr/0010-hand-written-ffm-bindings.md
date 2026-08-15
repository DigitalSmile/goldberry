# ADR-0010: Hand-written FFM bindings

- **Status:** Accepted
- **Date:** 2026-08-15
- **Supersedes:** [ADR-0006](0006-ffm-bindings-via-jextract.md)
- **Relates to:** `docs/ARCHITECTURE.md` §3.1

## Context

[ADR-0006](0006-ffm-bindings-via-jextract.md) chose jextract on the reasoning
that hand-writing `MethodHandle` and `MemoryLayout` declarations is thousands of
lines of mechanical, silently wrong-able code. That reasoning was about *volume*,
and it assumed the volume was fixed. It is not.

jextract binds a *header*, not an API surface. Pointed at SDL3 it emits bindings
for every public symbol in SDL — thousands of functions and hundreds of structs —
when Goldberry calls perhaps eighty of them. Repeat across Blend2D, HarfBuzz,
Yoga, and libxkbcommon and the generated surface dwarfs the toolkit. That surface
is not free: it is code to compile, review on every upgrade, keep out of the
module's exports, and make reachable for native-image.

Three further costs pushed the same way:

- **jextract is a separate toolchain coupled to JDK releases.** It is not part of
  the JDK, and a JDK upgrade can require a jextract upgrade before the project
  builds at all.
- **Generated bindings are not the bindings we want.** §3.1 requires every native
  object to be wrapped with explicit ownership and arena discipline. Generated
  code knows nothing about that, so it gets wrapped anyway — meaning the
  generated layer is an intermediate that exists only to be hidden.
- **The generated code is the wrong shape for the upcall design.** The single
  shared measure-callback stub keyed on a context pointer is a hand-written
  optimisation regardless.

## Decision

Hand-write the FFM bindings. Bind only what Goldberry actually calls, in the
shape the wrapper layer wants, inside the `natives` module.

Because this gives up jextract's layout correctness guarantee, replace it with a
stronger one. **The compiled library reports its own layout, and a test asserts
the Java side agrees.** `libgoldberry` exports a probe table — for every struct
Goldberry binds, the `sizeof`, the alignment, and the `offsetof` of every bound
field, as the C compiler computed them for that exact target. A test walks the
Java `MemoryLayout` declarations against that table and fails on any
disagreement.

This is a better check than the one it replaces. ADR-0006's cross-platform layout
diff compared jextract's *output* across platforms; this compares the Java
declaration against the *actual compiled library* on the actual target, which is
the thing that has to be right.

## Alternatives considered

- **jextract, with the generated surface trimmed by header filtering.** Rejected:
  the `--include-*` flags make the binding set a build-configuration problem, and
  a symbol that falls out of the filter fails at build time in a way that reads
  as a jextract bug rather than a missing include.
- **jextract for structs, hand-written for functions.** Rejected: it keeps the
  toolchain dependency for a fraction of the benefit.
- **Hand-written with no verification.** Rejected outright. A wrong offset is
  memory corruption, not an exception — silent, target-specific, and it surfaces
  as a crash somewhere unrelated. Without the probe table this decision would be
  irresponsible.

## Consequences

- The binding layer is small, readable, and shaped for the wrapper and arena
  design rather than adapted to it. Reviewing it is possible.
- No jextract in CI, and no JDK-to-jextract version coupling. This also settles
  ADR-0006's open question about committing generated bindings: there is no
  generated code, so the question disappears.
- **Every bound struct must be registered in the C probe table**, and a field
  bound in Java but absent from the probe is a test failure. This is a real
  ongoing cost and it is deliberately not optional — it is the entire safety
  argument for this decision.
- Upgrading a native dependency becomes a manual review of the changed headers
  rather than a regeneration plus diff. The probe test catches layout changes,
  but a *semantic* change to a function's contract is caught only by reading.
- Binding a new function is now a small, deliberate act. That is a feature: it
  keeps the native surface honest and visible, and §3.1's rule that raw
  `MemorySegment` never escapes the module is easier to hold when a human writes
  every crossing.
- The probe table only validates structs Goldberry binds *and remembers to
  register*. A struct bound in Java and forgotten in C is unverified. Mitigation:
  the registry is the single place Java layouts are declared, and the test fails
  on any registered layout missing from the probe.
