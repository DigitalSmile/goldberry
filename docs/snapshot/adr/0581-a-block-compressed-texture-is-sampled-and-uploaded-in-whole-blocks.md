# ADR-0581: A block-compressed texture is sampled and uploaded in whole blocks

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-027),
  [ADR-0580](0580-the-native-abi-is-21-and-a-texture-may-hold-srgb-colour.md)

## Context

Every texture was uploaded uncompressed. A downstream board has up to twelve
materials of three 2K maps; in `R8G8B8A8` that is about 800 MB a half-board,
and about 200 MB in BC7 with BC5 normals. SDL has the BC and ASTC formats,
and `GpuDevice.supports` already answers whether a device decodes one.

A compressed format stores 4×4 texels in one block of 8 or 16 bytes. Every
size the toolkit computed was width × height × bytes a pixel: `CopyPass`'s
row and source checks, the staging buffer's packing, `SdlGpuTexture.byteSize`
and the transfer's row length. Each of those is wrong for a block format, and
silently: the bytes would be counted short and the upload would read past
them or stage too few.

## Decision

**Eleven formats**: `BC1_RGBA_UNORM`, `BC3_RGBA_UNORM`, `BC5_RG_UNORM`,
`BC7_RGBA_UNORM` and `ASTC_4x4_UNORM`, and the `_SRGB` forms of BC1, BC3,
BC7 and ASTC. `TextureFormat` gains `isCompressed()`, `blockSize()` (4×4,
or 1×1 for a plain format), `bytesPerBlock()`, `bytesPerRow(width)` and
`byteSize(width, height)`, which round up to whole blocks.

**`bytesPerPixel()` throws** `UnsupportedOperationException` for a
compressed format, at both layers, so no caller gets a wrong size without
noticing.

**Sampled and uploaded, nothing else.** `TextureSpec` refuses a compressed
format as a colour or depth target, as storage, multisampled, or with a
level 0 that is not whole blocks (Direct3D 12 requires that; the levels
below may be smaller than a block). `generateMipmaps` and `readback` refuse
one: its levels are encoded with it and uploaded. `SdlGpuDevice.createTexture`
makes the same refusals.

**Uploads count blocks.** Regions stay in texels, but start on a block and
end on one or at the level's edge. The source is rows of blocks, `rowBytes`
apart, and is staged a row of blocks at a time; `SdlGpuTexture.byteSize`
counts blocks; the transfer's `pixels_per_row` and `rows_per_layer` are
rounded to whole blocks, which Vulkan requires of a compressed copy. A
plain format is a format whose block is one texel, so the same code serves
both.

## Alternatives considered

- **A separate upload method for compressed data.** Two paths to keep in
  step for a difference that is one division.
- **Allowing any level 0 size.** Vulkan and Metal would take it, and the
  texture would then fail on Direct3D 12 alone.
- **Decoding ASTC on desktop.** Out of scope: an application ships BC for
  desktop and ASTC for Apple, and `supports` says which.

## Consequences

- The constants are rows of the ABI 21 table (ADR-0580).
- `GpuSpecsTest` checks block sizes, row and image sizes (a 2048² BC7 chain
  is 5,592,432 bytes), the region-to-block arithmetic and the spec's
  refusals. `GpuTextureShapesTest` uploads a BC7 texture of 2×2 blocks whole
  and then one block of it, samples it into an RGBA target, uploads every
  level of a mip chain down to 1×1, and checks the refusals. Its ASTC test is
  skipped where `supports` says no, which is lavapipe and NVIDIA here.
