package dev.goldberry.gpu.render.staging;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// Where an upload's regions go in staging memory, and how much of it they
/// take: each region's rows one after another at its [pitch][UploadAlignment#pitch],
/// and the regions one after another, each [placed][UploadAlignment#place].
/// Arithmetic only, so it is tested without a device.
///
/// @param regions the regions in the order given, each with its offset and
///                row pitch
/// @param size    the bytes they take, from offset 0 to the end of the last
public record StagingLayout(List<StagedRegion> regions, long size) {

    /// Copies the list.
    public StagingLayout {
        regions = List.copyOf(regions);
        if (size < 0) {
            throw new IllegalArgumentException("a layout of " + size + " bytes");
        }
    }

    /// Lays out `regions` of `bytesPerBlock`-byte texels, or blocks, under
    /// `alignment`.
    ///
    /// @throws IllegalArgumentException when `bytesPerBlock` is not positive,
    ///                                  or the layout is past 2 GiB, which is
    ///                                  as far as SDL's offsets reach
    public static StagingLayout of(UploadAlignment alignment, int bytesPerBlock, List<SdlGpuRegion> regions) {
        Objects.requireNonNull(alignment, "alignment");
        if (bytesPerBlock <= 0) {
            throw new IllegalArgumentException(bytesPerBlock + " bytes per texel");
        }
        var staged = new ArrayList<StagedRegion>(regions.size());
        var at = 0L;
        for (var region : regions) {
            var start = alignment.place(at);
            var pitch = alignment.pitch((long) region.width() * bytesPerBlock);
            var end = start + (long) pitch * region.height();
            if (end > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("an upload of " + end + " bytes is past SDL's 2 GiB");
            }
            staged.add(new StagedRegion(region, (int) start, pitch));
            at = end;
        }
        return new StagingLayout(staged, at);
    }
}
