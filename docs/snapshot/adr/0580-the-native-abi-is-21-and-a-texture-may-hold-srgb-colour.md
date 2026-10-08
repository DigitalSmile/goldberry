# ADR-0580: The native ABI is 21, and a texture may hold sRGB colour

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-028),
  [ADR-0562](0562-the-native-abi-grows-once-for-the-whole-texture-model-and-compute.md),
  [ADR-0573](0573-the-native-abi-is-20-and-a-device-says-which-sample-counts-it-has.md)

## Context

There was no sRGB texture format. A colour image was sampled as it was
stored, so linear filtering and `generateMipmaps` averaged sRGB-encoded
values, and every lit shader decoded after the fetch. Averaging encoded
values darkens fine contrast in the far mip levels. The downstream lights
its scene in linear HDR, and its boards' and props' colour textures are
sRGB images.

Every SDL enumerator the bindings hard-code is a row of the shim's probe
table, checked against the compiled library, because SDL's texture format
list is long and has been inserted into. A new row changes the library's
shape, and the ABI number with it. This batch adds four kinds of texture
(sRGB, block-compressed, cube and 3D), all of them rows of that table and
none of them a new function.

## Decision

**ABI 21, once for the four.** The shim's table gains the two sRGB
formats, the nine block-compressed ones, `SDL_GPU_TEXTURETYPE_3D` and
`SDL_GPU_TEXTURETYPE_CUBE`, and `GOLDBERRY_ABI_VERSION` and
`GoldberryShim.SUPPORTED_ABI_VERSION` go to 21 together. The texture types
become an enum, `SdlGpuTextureType`, checked against the table like the
formats, in place of two `int` constants.

**`R8G8B8A8_UNORM_SRGB` and `B8G8R8A8_UNORM_SRGB`** in `TextureFormat` and
`SdlGpuTextureFormat`, with `isSrgb()`. They are sampled and colour targets
like their plain forms: a sampler decodes to linear, a render pass encodes
what it writes, and the drivers' blits that generate mip levels filter in
linear. `Readback.awaitPixels` gives them no `PixelBuffer` form, since
their bytes are encoded colour, and `await()` hands back the bytes.

## Alternatives considered

- **Decoding in the shader.** What the downstream did: a `pow` a fetch, and
  mip levels still averaged in the encoded space.
- **One bump per entry.** Four native rebuilds on three platforms for the
  same table. ADR-0562 grew the ABI once for a batch for the same reason.

## Consequences

- One native rebuild: Linux is built here; macOS and Windows build in CI.
  The next snapshot's classifier jars refuse ABI 20 libraries.
- `GpuTextureShapesTest` checks that an sRGB target cleared to linear 0.5
  stores 188, that a texel stored as 188 is sampled as 128, and that the
  mip level generated from a black and white checker is 188 where a plain
  format's is 128. It passes on lavapipe and on NVIDIA (Vulkan). The level
  is generated from a 4×4 checker: on lavapipe the 1×1 level of a 2×1 sRGB
  texture came out as its right texel alone, not a mean.
