# ADR-0583: A volume is uploaded a slice at a time, and is not rendered into

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-030),
  [ADR-0582](0582-a-cube-is-six-square-layers-and-a-face-is-a-view.md),
  [ADR-0580](0580-the-native-abi-is-21-and-a-texture-may-hold-srgb-colour.md)

## Context

There was no 3D texture. The downstream grades colour with a 32³ table per
board, read with one trilinear fetch; as a 2D array of 32 layers it takes
two fetches and a blend written by hand, since an array is not filtered
between its layers.

SDL's `SDL_GPU_TEXTURETYPE_3D` puts the depth in `layer_count_or_depth`. A
3D texture has one layer, and its sub-resources are its depth slices: an
upload's region names a slice with `z` and leaves `layer` at 0. The bindings
always wrote the sub-resource into `layer` and left `z` at 0, which for a
volume would upload every slice onto the first.

## Decision

**`TextureSpec.volume(format, width, height, depth)`**: type `THREE_D`, one
layer, a `depth` component (1 for every other type) and sampled usage.
`withMipChain` counts the longest of the three axes, and `levelDepth(level)`
halves the depth with the other two.

**`GpuTexture.slice(level, z)`** is a `TextureView` whose `layer` is the
slice, checked against the level's depth. It is an upload destination and a
readback source. `SdlGpuTexture` carries its type and depth,
`requireSubresource` checks a slice against the level's depth, and the copy
region of a 3D texture writes the slice into `z` and 0 into `layer`.

**A whole-slice upload does not cycle.** Cycling gives the texture fresh
memory, right only when every texel is about to be written; a slice of a
volume is not all of it, so `CopyPass` cycles only a texture of one level,
one layer and one slice.

**Not a render target, nor storage, in this pass.** `TextureSpec` refuses
any usage but `SAMPLER` for a volume. SDL can render into a depth plane,
and a compute shader could write one, but nothing downstream asks for it,
and both would need their own tests.

## Alternatives considered

- **A volume as an array with a flag.** The shader type, the filtering and
  the region fields all differ; the type component already says which.
- **Uploading a volume in one call.** A table is produced a slice at a time,
  and the view is the shape the other uploads already take.

## Consequences

- `SdlGpuDeviceTest` makes a cube and a volume through the bindings and
  checks a volume's slices per level. `GpuTextureShapesTest` uploads a 4×4×4
  volume's four slices in one copy pass, reads slice 2 back, and samples the
  volume at slice 0, at slice 1, and halfway between slices 1 and 2, which
  reads half of each: the trilinear fetch the entry asks for, and proof that
  later slices did not overwrite the first. It passes on lavapipe and on
  NVIDIA (Vulkan).
