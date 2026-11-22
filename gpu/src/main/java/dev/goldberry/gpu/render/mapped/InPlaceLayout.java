package dev.goldberry.gpu.render.mapped;

import java.util.List;
import java.util.Objects;

import dev.goldberry.gpu.render.staging.UploadAlignment;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// How a raster painted in place is laid out on a driver, and what is copied
/// out of it.
///
/// | Driver | Stride | Copies |
/// |---|---|---|
/// | Vulkan | packed | [CopyShape#RECTANGLES] |
/// | Metal | packed, so a band is one command | [CopyShape#BANDS] |
/// | Direct3D 12 | rounded up to 256 bytes | [CopyShape#BANDS], on rows at multiples of 512 bytes |
///
/// @param alignment how rows and copies are aligned
/// @param shape     what is copied for a frame's damage
public record InPlaceLayout(UploadAlignment alignment, CopyShape shape) {

    /// Checks nothing is missing.
    public InPlaceLayout {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(shape, "shape");
    }

    /// The layout for `driver`, by SDL's name for it, which records padded rows
    /// in one go or does not (Metal).
    public static InPlaceLayout of(String driver, boolean uploadsPaddedRows) {
        var alignment = UploadAlignment.of(driver);
        var bands = alignment.padsRows() || !uploadsPaddedRows;
        return new InPlaceLayout(alignment, bands ? CopyShape.BANDS : CopyShape.RECTANGLES);
    }

    /// The bytes from one row of a raster `width` BGRA texels wide to the
    /// next.
    public int stride(int width) {
        return alignment.pitch((long) width * 4);
    }

    /// The regions to copy for `damage` of a raster `width` texels wide.
    public List<SdlGpuRegion> copies(List<SdlGpuRegion> damage, int width) {
        return shape.copies(damage, width, stride(width), alignment);
    }
}
