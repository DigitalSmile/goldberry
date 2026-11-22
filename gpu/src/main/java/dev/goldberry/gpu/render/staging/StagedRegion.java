package dev.goldberry.gpu.render.staging;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// Where one region of an upload lies in staging memory.
///
/// @param region   the region, in the units it was staged in: texels, or
///                 blocks of them for a compressed format
/// @param offset   the byte its first row starts at
/// @param rowBytes the bytes from one of its rows to the next; its packed row
///                 or more
public record StagedRegion(SdlGpuRegion region, int offset, int rowBytes) {

    /// Checks the placement makes sense.
    ///
    /// @throws IllegalArgumentException when the offset is negative or the
    ///                                  rows are not positive
    public StagedRegion {
        Objects.requireNonNull(region, "region");
        if (offset < 0) {
            throw new IllegalArgumentException("a region staged at byte " + offset);
        }
        if (rowBytes <= 0) {
            throw new IllegalArgumentException("rows of " + rowBytes + " bytes");
        }
    }

    /// The bytes from the first of its rows to the end of the last, padding
    /// included.
    public long bytes() {
        return (long) rowBytes * region.height();
    }

    /// The bytes from one of its rows to the next, in texels of
    /// `bytesPerPixel`: SDL's `pixels_per_row`.
    ///
    /// @throws IllegalArgumentException when the rows are not whole texels
    public int pixelsPerRow(int bytesPerPixel) {
        if (bytesPerPixel <= 0 || rowBytes % bytesPerPixel != 0) {
            throw new IllegalArgumentException(
                    "rows of " + rowBytes + " bytes are not whole " + bytesPerPixel + "-byte texels");
        }
        return rowBytes / bytesPerPixel;
    }
}
