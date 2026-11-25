# ADR-0745: ThorVG is patched where hostile geometry took the process down, and a sanitizer lane holds it there

- **Status:** Accepted
- **Date:** 2026-11-24
- **Relates to:** ADR-0740 (SVG on ThorVG), ADR-0741 (the superbuild),
  ADR-0656 (`:media`'s ASan and fuzz lane, which this one copies)

## Context

Phase 5 of `docs/vector-svg.md` asked for "an ASan build and a fuzz corpus run
over the SVG loader, as ADR-0656 does for `:media`". The corpus (nine documents
covering every part of the loader this build compiles) and eleven kinds of
damage (bit flips, overwrites, inserts, deletes, truncation, hostile numbers,
path-data soup, hostile attribute values, element surgery, self-references,
splices, and mixes of them) were written first. Their first runs found that a
damaged document does not only fail to parse: three took the process down, and
one took the machine with it.

1. **Unbounded dashing.** `stroke-dasharray` on a path to y = 10^12, `inf`, or
   any length where subtracting a dash leaves the float as it was: ThorVG's
   dasher (`_dashLineTo`) steps one dash at a time until the length is used up,
   appending a dash each step. It never finishes, and native memory ignores
   `-Xmx`: the desktop froze and rebooted. 150 bytes of SVG.
2. **Rasterizer overflow.** A `<use>` with `width="-2147483649"`, drawn 80 by 1,
   put a star's points ~27 million pixels out. The rasterizer's cells are 24.8
   fixed point in `int32`; the difference of two such points overflows, and a
   line's cell walk (`while (e1 != e2)`) overshoots its end and runs until the
   counter wraps: hours at 100% CPU, holding the engine's one lock.
3. **Non-finite strokes.** A rounded `<rect>` of height `-inf`, `NaN` or
   `3.4e38`, stroked: the stroker walks a corner's turn in steps that never end,
   growing its border buffer until memory runs out.

And the AddressSanitizer build reported an allocation mismatch on every
document, before any damage. ThorVG replaces the global `operator new` and
`delete` with `malloc` and `free` (`tvgInitializer.cpp`) and mixes the two by
design: `Inlist` `delete`s nodes it `malloc`ed, and a `std::string` is
allocated by the runtime's `new` and freed by the replacement. In the shipped
library both ends are `malloc`/`free` and nothing is wrong; ASan intercepts
`operator new` and the sized `delete` GCC emits, so under it every document is
a mismatch, and the lane could never have gone green. Compiling the
replacement out for the sanitizer build was tried and exposed the deliberate
mixing instead.

None of these is reachable only by a fuzzer: the inputs are the numbers a hand
edit, a broken export or a hostile mail writes, and Tessera draws SVG from
mail.

## Decision

1. **Three patches to ThorVG**, in `vector/src/main/cmake/patches/`, applied
   by the superbuild's `PATCH_COMMAND` through `apply_patch.cmake` (a copy of
   `:natives`' for SDL: idempotent, resets the files a patch touches first,
   stops the configure when a bump moves the lines under it; each patch's hash
   is in the command, so a changed patch reapplies). One patch per file set,
   since the reset would undo a second patch on the same file. Two patches:
   - `thorvg-bounded-strokes.patch` (`tvgSwShape.cpp`): a dashed path whose
     dashes would number more than a million, whose length is not finite, or
     whose shortest dash or gap is under 2^-22 of its longest segment (too short
     to move a float) is **stroked undashed**, as Skia does past
     `kMaxDashCount`. A path with a point, before or after its transform, that
     is not finite or is past 10^18 (or a transform or width that is) **draws
     no stroke**: the stroke fails, and ThorVG dismisses the shape.
   - `thorvg-bounded-coordinates.patch` (`tvgSwUtil.cpp`, `tvgSwRle.cpp`): a
     point is **clamped to ±2^21 device pixels** as it is converted to fixed
     point, and a `NaN` goes to the origin rather than through an undefined
     float-to-int conversion. Every difference then fits in `int32`; `HYPOT`
     is computed in 64 bits. The picture is at most 8192 pixels a side
     (`Svg.MAX_SIDE`), so geometry this far out is off it: an axis-aligned
     shape is drawn exactly as it was, and only a slanted edge to a clamped
     point changes angle.
   The NOTICE beside the library names both.
2. **The robustness tests** (`dev.goldberry.vector.robustness`, tagged
   `robustness`) run in `test` at 6 rounds per document and damage (about
   3 000 inputs, 12 s), deterministic by seed; every input is parsed, drawn at
   three sizes in every `Fit` and at its own size, and closed. A drawing or an
   `ImageDecodeException` is an answer; anything else fails. Each input can be
   named before it is drawn (`-Pgoldberry.vector.fuzz.trace=<file>`, with its
   bytes in `<file>.last`), because the failures this lane exists for leave no
   stack trace.
3. **`:vector:sanitizerTest`**, beside `nativesBuildAsan`, runs the
   robustness tests at 48 rounds and every test that draws through ThorVG
   against the ASan build, with libasan preloaded and HotSpot's signals left
   alone, `SanitizerLaneTest` checking the sanitized library is the one mapped,
   and `sanitizerReport` failing on any report, exactly as `:media`'s lane.
   One check differs: `alloc_dealloc_mismatch=0`, for ThorVG's deliberate
   mixing above. Overflows, use after free and double frees are reported.
   The CI job is `sanitizer` in `vector.yml` (ADR-0746).
4. **Regression tests pin each fix** without the fuzzer: `SvgDashesTest`
   (dashed under the bound, undashed over it, unstroked past 10^18 or at
   infinity) and `SvgFarGeometryTest` (the exact document that hung, a
   rectangle reaching 3.4e38 out filling the picture exactly, slanted shapes and
   strokes at no finite place).

## Consequences

- A document can no longer run ThorVG out of memory or time through dashes,
  far points or non-finite strokes. Other unbounded paths may exist; the lane
  at 48 rounds is where they will show, and the trace names the input.
- Two patches are carried against ThorVG v1.1.4. A ThorVG bump has to rebase
  or drop each; `apply_patch.cmake` refuses to build past one that no longer
  applies. Each is a candidate to send upstream.
- A stroke past the bounds is not drawn, where a browser would clip it and draw
  the visible part. That is the behaviour of a document whose geometry is at
  infinity or 10^18 pixels; no real document is.
- The robustness tests should be run under a memory cap on a desktop
  (`systemd-run --user --scope -p MemoryMax=…`): a regression of the first kind
  takes the machine, not the test.
