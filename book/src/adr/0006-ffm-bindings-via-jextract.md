# ADR-0006: FFM bindings via jextract, generated in CI

- **Status:** Superseded by [ADR-0010](0010-hand-written-ffm-bindings.md)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §3.2

> **Superseded on 2026-08-15.** Bindings are hand-written; jextract is not used.
> The arena discipline, the wrapper rule, and the shared upcall stub described
> below all still hold — [ADR-0010](0010-hand-written-ffm-bindings.md) changes
> only how the binding code is produced, and replaces the cross-platform layout
> diff with a stronger check against the compiled library.

## Context

Goldberry binds five native libraries and has no JNI. The Foreign Function &
Memory API is final in Java 22 and Java 25 is the project's floor, so FFM is the
mechanism. What remains is *how the bindings are produced and where they live*.

Hand-writing `MethodHandle` and `MemoryLayout` declarations for Blend2D, Yoga,
HarfBuzz, SDL3, and libxkbcommon is thousands of lines of mechanical, silently
wrong-able code. `jextract` generates it from the headers. But jextract is not
part of the JDK — it is a separate download tracking specific JDK releases — and
its output is platform-specific, because struct layouts genuinely differ across
platforms.

## Decision

Generate FFM bindings with jextract, per platform, in CI. Verify with an
automated check that struct layouts agree across platforms where they are
expected to, which is what guards against the classic traps — `long` being
32-bit on Win64, differing struct padding, differing enum widths.

Around the generated code, three rules from §3.1 hold:

- **Wrappers, not raw segments.** Every native object gets a thin Java wrapper
  with explicit ownership. Raw `MemorySegment` never leaves the `natives` module
  (see ADR-0007, which makes this enforceable rather than aspirational).
- **Arena discipline.** One shared `Arena` per window for long-lived objects
  (fonts, Yoga config, xkb keymap); a confined per-frame arena for scratch
  (HarfBuzz buffers, glyph arrays, damage lists) that dies at frame end. Wrappers
  expose `close()`; a `Cleaner` is the safety net, never the mechanism.
- **One shared upcall stub.** Yoga measure callbacks dispatch through a single
  stub keyed on the node's context pointer rather than allocating a stub per node.

## Alternatives considered

- **Hand-written FFM bindings.** Rejected: volume and error rate. The errors are
  the bad kind — a wrong offset is memory corruption, not an exception.
- **JNI.** Rejected: a C compiler in the loop for every binding change, no
  native-image story worth having, and worse performance than FFM downcalls.
- **Panama's `jextract` at build time on every developer machine.** Rejected as
  the default: it makes jextract a hard prerequisite for anyone building the
  project, including people who only touch Java code.

## Consequences

- The binding layer is mechanical and regenerable; upgrading a native dependency
  is a regeneration plus a layout diff, not an audit.
- jextract becomes a CI prerequisite, and its version is coupled to the JDK
  version. A JDK upgrade may require a jextract upgrade.
- The measure-callback upcall returns `YGSize`, a struct of two floats, **by
  value**. Struct-by-value returns from upcalls are the fiddliest corner of FFM,
  and the ABI classification differs across SysV, Win64, and AAPCS. This should
  be the first native thing proven in M0 — if it does not work cleanly, the
  layout-engine boundary changes shape, and that is much cheaper to learn now
  than after the layout code is written.
- GraalVM native-image and FFM need reachability metadata and
  `--enable-native-access`; the config ships in the jars under
  `META-INF/native-image` (§15).
- **Open:** whether generated bindings are committed to version control or
  generated during the build. Committing them gives IDE navigation, reviewable
  diffs on native upgrades, and makes the cross-platform layout check a plain
  `git diff`; the cost is a large volume of generated code in the repository and
  a regeneration step that can be forgotten. Recommendation is to commit, but
  this is not yet decided.
