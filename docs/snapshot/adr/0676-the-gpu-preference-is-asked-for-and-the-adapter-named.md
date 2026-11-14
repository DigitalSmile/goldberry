# ADR-0676: The GPU a device prefers can be asked for, and an offscreen GPU names its adapter

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0479 (a window is composited through a seam core declares and gpu provides), ADR-0669 (a game's SDL_GPU calls are bound, `GpuDevice.adapter()` among them)

## Context

A downstream game measures its frame budget on the integrated GPU of a laptop
that also has a discrete one. Its benchmark opened `OffscreenGpu.open()` and
printed `OffscreenGpu[vulkan]` whichever GPU ran, and nothing could point the
device at either GPU.

Two halves of that were already true and not known: `SdlGpuDevice.Options`
asks SDL for the low-power GPU by default (`SDL_PROP_GPU_DEVICE_CREATE_PREFERLOWPOWER_BOOLEAN`),
for windows and the offscreen GPU alike, and `GpuDevice.adapter()` has named
the adapter since ADR-0669. What was missing was a way to turn the preference
round, and a report that says which GPU ran.

SDL reports the adapter as free text only: no vendor id and no
integrated/discrete kind. Neither is invented here.

## Decision

- **`GpuPower` (`LOW`, `HIGH`)** in `dev.goldberry.gpu`, exported, with
  `GpuPower.parse` for a property's text.
- **`goldberry.gpu.power=low|high`** sets the preference for the process's one
  device, read by `DeviceOptions` beside `goldberry.gpu.driver` and
  `goldberry.gpu.debug`. Anything else is the default, as every tuning flag is.
- **`OffscreenGpu.open(GpuPower)`** prefers that GPU whatever the property
  says. The offscreen GPU makes its own compositor, so the preference is per
  call there without contradicting "one device per process" for windows.
- **`OffscreenGpu.adapter()`** answers the device's `GpuAdapter`, and
  **`toString()`** names it: `OffscreenGpu[vulkan, AMD Radeon 780M]`.
- The compositor's start-up log line names the adapter, and says when the
  high-performance GPU was preferred.

## Consequences

- A preference, not a choice: the driver decides, and a machine with one GPU
  gives that one either way. The adapter name is how a report proves which.
- `SdlGpuDevice.Options.withPreferLowPower` is new in `:natives`.
- Tested by `DeviceOptionsTest` (the property, the override), `GpuPowerTest`,
  and `OffscreenGpuTest.powerAndAdapter` on the GPU lane.
