# ADR-0741: `libgoldberry-vector` is built by a superbuild of its own

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0740 (the plan's decisions), ADR-0651 (the media
  library, whose superbuild and loader this one follows)

## Context

ADR-0740 put ThorVG in a library of its own, shipped as `goldberry-vector`'s
`natives-<target>` classifiers. Phase 1 of `docs/vector-svg.md` builds it, loads
it and draws a document through the bare bindings, with nothing above them yet.

Two facts surfaced while building it. ThorVG's static build defines
`TVG_STATIC`, which leaves its C API with no visibility attribute, and compiles
everything with `-fvisibility=hidden`: every `tvg_*` function is hidden in the
archive, and no export list can export a hidden symbol from an ELF or Mach-O
library. And D6 asked for the cost of SIMD off on x64 to be measured.

## Decision

1. **`vector/src/main/cmake` builds ThorVG v1.1.4 (`47658a80`) as a meson
   ExternalProject**, static, with the options ADR-0740 chose: the CPU engine,
   the svg, png and jpg loaders, the C API, no savers, tools, tests, logging,
   file I/O, task scheduler, partial rendering or extras. The pin and the
   commit are in `gradle/libs.versions.toml`; `checkout.cmake` refuses a clone
   at any other commit.
2. **ThorVG is compiled with `-fvisibility=default` after its own flag**, so the
   archive's symbols are visible and the export list decides, at the final
   link, what leaves the library, as in the media library.
3. **The library exports 17 symbols**: `goldberry_vector_abi`,
   `goldberry_vector_versions` and the 15 `tvg_*` functions the bindings call.
   `ExportListTest` holds the list and the bindings to each other.
   `tvg_engine_version` is called by `goldberry_vector.c` only, and
   `tvg_canvas_update` is not called, because `tvg_canvas_draw` updates first.
4. **`vector.properties` is written beside the library** (ABI, target, ThorVG
   tag and commit, SIMD, sanitizer). The loader refuses a directory whose file
   names another ABI or target before it maps anything, and then checks the ABI
   the library itself answers. ABI 1.
5. **`VectorNatives` loads it once per process**, from `-Dgoldberry.vector.libdir`
   or unpacked from the natives jar, and a missing or wrong library is a reason
   (`VectorCapabilities.unavailableReason()`), not an exception.
6. **The size gate fails above 1.5 MB.** The library is 486 KB stripped on
   linux-x64.
7. **D6 stands: SIMD stays off on x64.** Measured on an i7-4790K, best of 40
   draws of a 400-path document with gradients, strokes and opacity: 288–295 ms
   at 1024² with SIMD off against 215–219 ms with AVX2, about 35% more; at 256²,
   32 ms against 27–28 ms, about 17%. The pixels are the same. An icon-sized
   document draws in well under a millisecond either way, and the cost is paid
   only by large, heavy documents. `-Pgoldberry.vector.simd=on|off` builds a
   flavour of its own to measure it again.

## Consequences

- A sanitizer flavour (`nativesBuildAsan`) is in the superbuild already; the
  lane that runs malformed documents against it is phase 5.
- CI does not build the library yet (phase 5), so a snapshot carries no
  `goldberry-vector` natives until then.
- Should ThorVG gain run-time SIMD dispatch, D6 can be revisited by changing
  `auto` in the superbuild alone.
