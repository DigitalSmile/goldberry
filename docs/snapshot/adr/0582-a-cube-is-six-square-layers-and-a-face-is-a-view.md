# ADR-0582: A cube is six square layers, and a face is a view

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-029),
  [ADR-0563](0563-a-texture-has-layers-levels-and-samples-and-a-pass-may-have-no-colour-target.md),
  [ADR-0580](0580-the-native-abi-is-21-and-a-texture-may-hold-srgb-colour.md)

## Context

A `TextureSpec` was a 2D texture or, with more than one layer, a 2D array.
The downstream wants one prefiltered 128² cubemap per board for metal and
foil reflections: six faces bound to a `TextureCube`, each rendered into and
uploaded to, with a mip chain. SDL has `SDL_GPU_TEXTURETYPE_CUBE`, six
square layers in the order +X, -X, +Y, -Y, +Z, -Z.

The type was implicit: `SdlGpuDevice.createTexture` chose `2D_ARRAY` when
there was more than one layer. A cube is also six layers, so the layer count
can no longer say which type a texture is.

## Decision

**`TextureType` is a `TextureSpec` component**: `TWO_D`, `TWO_D_ARRAY`,
`CUBE` and `THREE_D`. The factories choose it: `sampled`, `renderTarget`,
`depth` and the four-argument constructor make `TWO_D`; `array` makes
`TWO_D_ARRAY`, even of one layer; `withLayers` of more than one turns a 2D
spec into an array; the old seven-component form is kept as a constructor
that derives 2D or array from the layers. Existing callers compile and mean
what they meant. `isArray()` is now the type, not the layer count.

**`TextureSpec.cube(format, size)`** is six square layers, sampled and a
colour target, and `cube(format, size, usages)` is any subset of those two,
which is how a compressed cube is made. Mip chains are allowed; storage,
depth and multisampling are refused.

**`CubeFace` and `GpuTexture.face(face, level)`.** A face is a
`TextureView` whose layer is the face's, so it is already an upload
destination, a readback source and a `RenderTarget`. A cube is bound through
`bindFragmentSamplers` like any texture. `SdlGpuTextureType` carries SDL's
values, checked against the ABI 21 table, and `SdlGpuDevice` gains
`createTexture(type, …)` and `supports(type, format, usages)`;
`GpuDevice.supports(type, format, usages)` asks for a cube or a volume.

## Alternatives considered

- **Deriving the type from the layer count alone.** Six layers would then be
  either an array or a cube, and a one-layer array would not be one.
- **A separate cube class.** A cube is sampled, uploaded and rendered into as
  any texture is; only naming its faces differs, which a view already does.
- **Cube arrays.** SDL has them; nothing downstream asks for one yet, and
  `withLayers` on a cube refuses any count but six.

## Consequences

- The record grew two components (`type`, and `depth` for ADR-0583), and the
  canonical constructor changed shape; no caller in the repository used it
  with positional arguments beyond the kept forms.
- `GpuTextureShapesTest` clears five faces of a 4×4 cube with a mip chain and
  uploads the sixth, reads one face back, and samples the cube along ±X, ±Y
  and ±Z through a test shader, which reads each face's colour. It passes on
  lavapipe and on NVIDIA (Vulkan). The test shaders were compiled here and
  the shipped shader sets restored afterwards.
