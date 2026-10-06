# ADR-0573: The native ABI is 20, and a device says which sample counts it has

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-017),
  [ADR-0562](0562-the-native-abi-grows-once-for-the-whole-texture-model-and-compute.md),
  [ADR-0566](0566-a-multisampled-target-is-resolved-by-the-pass-that-draws-it.md)

## Context

ADR-0566 gave textures and pipelines a sample count. `GpuDevice.supports`
says whether a format can have a set of usages, but nothing said whether the
device could multisample a format at a given count. Asking for a count it
lacks failed only when the texture was made. The downstream's board renders
at 4× by default and offers `--msaa=1|2|4|8` by hand. With an answer it
would take the highest supported count up to the setting.

SDL has `SDL_GPUTextureSupportsSampleCount`. It was not in the export list,
and every SDL function the bindings call is exported and has a holder,
which `ExportListTest` keeps equal. A new function is a native rebuild on
three platforms with the ABI number moved. ADR-0562 grew the ABI once for
the whole of the previous batch so that its Java phases would not each need
a rebuild. This batch needs one function.

## Decision

**ABI 20 exports `SDL_GPUTextureSupportsSampleCount`**, with a
`TextureSupportsSampleCount` holder in `SdlGpuDeviceCalls` and
`SdlGpuDevice.supportsSamples(format, count)`, which answers one sample
without asking.

**`GpuDevice.supportsSamples(TextureFormat, int)`** answers true for 1 and
asks the driver for 2, 4 and 8. **`GpuDevice.maxSamples(TextureFormat, int
limit)`** gives the highest supported count at most `limit`, 1 when there is
none above it, which is the renderer's question in one call. Both refuse a
count other than 1, 2, 4 or 8, as `TextureSpec.withSamples` does.

## Alternatives considered

- **Probing by making a 1×1 texture** and catching the refusal. No ABI
  change, but SDL logs an error for each refusal, the probe costs an
  allocation on the GPU, and the answer would be a guess about sizes.
- **Waiting to bundle the export with a later native change.** The issue is
  low priority, but every other item of this batch is Java-only. One export
  is the whole native change, and the bump is the same work now or later.

## Consequences

- One native rebuild: Linux is built here; macOS and Windows build in CI.
  The classifier jars of the next snapshot refuse ABI 19 libraries, and the
  downstream's pinned build has to move with them.
- The export list's header rule, "nothing here is exported ahead of a
  caller", holds again: the symbol has a caller on the day it is exported.
- `SdlGpuDeviceTest` checks that one sample is always supported and that 4×
  is supported for the two formats a board renders with. `GpuApiTest` checks
  that the highest count up to 8 makes a texture, that a limit is a limit,
  and that a count outside 1, 2, 4 and 8 is refused. Both pass on NVIDIA
  (Vulkan) and on lavapipe.
