# ADR-0562: The native ABI grows once for the whole texture model and compute

- **Status:** Accepted
- **Date:** 2026-10-05
- **Relates to:** the Gwent clone's issue list (GB-005 to GB-010),
  [ADR-0504](0504-the-native-abi-is-17.md),
  [ADR-0558](0558-a-video-track-says-its-frame-rate-and-count.md)

## Context

The Gwent clone filed seven gaps in the GPU API: texture arrays, float colour
formats, multisampling, compute and storage, sampled depth with depth-only
passes, mip levels, and an application's way to render offscreen. Six of them
need something from SDL that the bindings do not yet name.

What the bindings name is checked. Every enumerator the Java side hard-codes
is in `NativeConstants.registry()` and verified against the shim's table when
the library loads, and every SDL function called is in the export list, which
`ExportListTest` holds equal to the set of holders. So a new SDL constant is a
shim change, a new SDL function is an export-list change and a holder, and
either is a native rebuild on three platforms with the ABI number moved, as
ADR-0558 was for one FFmpeg field.

Six items, each with its own constants, would have been four or five bumps in
a row, each a CI rebuild of the natives and a macOS and Windows build this
machine cannot do. And a downstream jar refuses a library older than it was
written against, so each bump is a step the downstream has to take with it.

## Decision

**ABI 19 carries everything the batch needs, in one bump.** The shim's table
gains the three float colour formats, the 2D-array texture type, the sample
counts, the resolve store ops, the linear mipmap mode, the three storage
usages of buffers and the three of textures, and the layouts of
`SDL_GPUComputePipelineCreateInfo` and the two read-write storage bindings.
The export list gains the compute pass, compute pipeline, storage binding,
vertex-sampler and mipmap-generation calls. Every one has a holder and a
registered constant on the Java side now, before any of them has a caller in
`:gpu`, so the Java phases that follow are Java-only changes against a
library that does not move again.

The Java side names the sample count as an enum, `SdlGpuSampleCount`, in place
of the one hard-coded constant, and the compute holders sit in a record of
their own, `SdlGpuComputeCalls`, wired into `GpuCalls`.

## Alternatives considered

- **A bump per item, as the item lands.** The honest shape of "nothing is
  exported ahead of a caller", which the export list's header still says.
  Rejected for the cost above: the items are one batch with one downstream,
  and a holder with no caller yet is a week's drift, not a year's.
- **Reading constants from the library at run time instead of hard-coding
  them.** It would end the rebuilds for constants, not for functions, and it
  is a different design of the bindings than ADR-0010's, which is not what
  this batch is for.

## Consequences

- One native rebuild. Linux is built here; macOS and Windows build in CI, and
  the shipped classifier jars of the next snapshot refuse ABI 18 libraries,
  which the downstream's pinned build (the Gwent clone's issue list) has to
  move past.
- Fourteen holders and three layouts have no caller until phases 2 and 4 of
  the plan land. `ExportListTest` keeps them honest in the meantime: each is
  bound, and each is exported.
- `SdlGpuBufferUsage.mask` of every usage is no longer 3; the test that said
  so now says which bits are which.
- The export list's header rule, "nothing here is exported ahead of a caller",
  has one recorded exception, this one.
