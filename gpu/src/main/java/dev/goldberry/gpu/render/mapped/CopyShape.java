package dev.goldberry.gpu.render.mapped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.goldberry.gpu.render.staging.UploadAlignment;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// What is copied out of a frame-sized raster for a frame's damage.
///
/// The copy reads straight out of the raster, so every region's rows are the
/// raster's stride apart. Some drivers copy that as it is and some do not:
///
/// - **Rectangles**, the damage as it is. Vulkan copies any pitch in one go,
///   and a rectangle moves only the bytes that changed.
/// - **Bands**, each rectangle widened to whole rows of the frame. Metal reads
///   a padded row only one row per command, and Direct3D 12 copies a region as
///   it is only when its pitch is its own row rounded up to 256 bytes, which a
///   region of the frame's width is and a narrower one is not. A band also
///   starts on a row whose offset meets the alignment's region multiple, and
///   bands that overlap or touch are joined, so each is one command.
public enum CopyShape {
    /// The damage as it is.
    RECTANGLES,
    /// The damage as whole rows.
    BANDS;

    /// The regions to copy for `damage` of a frame `width` texels wide whose
    /// rows are `stride` bytes apart, under `alignment`. Rectangles come back
    /// in the order given; bands sorted top to bottom and joined.
    ///
    /// @throws IllegalArgumentException when a region does not fit the width
    public List<SdlGpuRegion> copies(List<SdlGpuRegion> damage, int width, int stride, UploadAlignment alignment) {
        Objects.requireNonNull(alignment, "alignment");
        for (var region : damage) {
            if (region.x() + (long) region.width() > width) {
                throw new IllegalArgumentException(region + " is wider than a frame of " + width);
            }
        }
        if (this == RECTANGLES || damage.isEmpty()) {
            return List.copyOf(damage);
        }
        // Rows `step` apart start at offsets that are multiples of the region
        // alignment: 1 when it is packed, 2 on Direct3D 12 when the stride is
        // an odd multiple of 256.
        var step = alignment.regionBytes() / gcd(stride, alignment.regionBytes());
        var sorted = new ArrayList<SdlGpuRegion>(damage);
        sorted.sort(Comparator.comparingInt(SdlGpuRegion::y));
        var bands = new ArrayList<SdlGpuRegion>(sorted.size());
        var top = -1;
        var bottom = -1;
        for (var region : sorted) {
            var start = region.y() / step * step;
            var end = region.y() + region.height();
            if (top >= 0 && start <= bottom) {
                bottom = Math.max(bottom, end);
                continue;
            }
            if (top >= 0) {
                bands.add(new SdlGpuRegion(0, top, width, bottom - top));
            }
            top = start;
            bottom = end;
        }
        bands.add(new SdlGpuRegion(0, top, width, bottom - top));
        return List.copyOf(bands);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
