# ADR-0578: A sampler takes an anisotropy, clamped to sixteen

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-031),
  [ADR-0563](0563-a-texture-has-layers-levels-and-samples-and-a-pass-may-have-no-colour-target.md)

## Context

A `SamplerSpec` was a filter, an address mode, a mip filter and a compare
op. The downstream's camera looks down at about 63°, so the far rows' cards
and the boards' ground are sampled at a slant, and a trilinear sampler picks
the mip level of the footprint's longer axis and blurs the shorter one. Its
design system asks for 8× anisotropic filtering.

SDL's `SDL_GPUSamplerCreateInfo` already has `enable_anisotropy` and
`max_anisotropy`, and the layout table already probes both fields, so the
change is Java alone. SDL reports no device limit for anisotropy. Vulkan,
Direct3D 12 and Metal all take up to 16, and SDL's Vulkan driver asks for
the `samplerAnisotropy` feature by default.

## Decision

**`SamplerSpec` gains a `maxAnisotropy` component**, 1 (off) by default,
with `withAnisotropy(float)` and `isAnisotropic()`. The four-component
constructor stays and means 1, and every `with` method keeps the value.

**Clamped, not refused, above 16.** `MAX_ANISOTROPY` is 16, the ceiling of
every backend SDL drives, so `withAnisotropy(64)` is 16: a setting of
"as much as you have" works everywhere. NaN and anything below 1 are
refused, since they are mistakes rather than settings.

**`SdlGpuSamplerDescription` carries it** as a fifth component, checked to
be 1 to 16, and `SdlGpuDevice.createSampler` writes `enable_anisotropy`
(above 1) and `max_anisotropy`. `GpuDevice.createSampler` passes the spec's
value through.

## Alternatives considered

- **Refusing above 16.** A caller would have to know the limit, which no
  device reports. Clamping matches what the drivers do with a larger value.
- **A device query for the limit.** SDL has none, and every driver it runs on
  has the same one.

## Consequences

- No native change: the struct fields were already in the layout table.
- `GpuSpecsTest` checks the default, the clamp, that `with` methods keep the
  value, and the refusals. `SdlGpuValuesTest` checks the description's
  range. `GpuTextureShapesTest` makes a trilinear sampler with 8× and
  samples through it, on lavapipe and on NVIDIA (Vulkan).
