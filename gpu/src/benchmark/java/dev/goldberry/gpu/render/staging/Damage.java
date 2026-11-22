package dev.goldberry.gpu.render.staging;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// How much of a frame changed: the three amounts the plan measures, as
/// rectangles that start on odd pixels, as a caret's or a list row's do.
enum Damage {
    /// One rectangle, a tenth of the frame each way.
    ONE_PERCENT("1%"),
    /// Four rectangles, a quarter wide and a tenth tall each.
    TEN_PERCENT("10%"),
    /// The whole frame, as after a resize or a theme change.
    WHOLE("100%");

    private final String label;

    Damage(String label) {
        this.label = label;
    }

    /// The rectangles of a `width` by `height` frame.
    List<SdlGpuRegion> regions(int width, int height) {
        return switch (this) {
            case ONE_PERCENT ->
                List.of(new SdlGpuRegion(width * 3 / 10 + 3, height * 4 / 10 + 1, width / 10, height / 10));
            case TEN_PERCENT -> {
                var w = width / 4;
                var h = height / 10;
                yield List.of(
                        new SdlGpuRegion(13, 7, w, h),
                        new SdlGpuRegion(width / 2 + 5, height / 5 + 3, w, h),
                        new SdlGpuRegion(width / 8 + 1, height * 3 / 5 + 9, w, h),
                        new SdlGpuRegion(width * 5 / 8 + 7, height * 4 / 5 + 1, w, h));
            }
            case WHOLE -> List.of(new SdlGpuRegion(0, 0, width, height));
        };
    }

    /// `regions` widened to whole rows of a frame `width` wide whose rows are
    /// `stride` bytes apart, each band starting on a row whose offset is a
    /// multiple of 512: what Direct3D 12 copies straight out of a frame-sized
    /// buffer, since a region's pitch there must be its own row rounded up to
    /// 256, which only a full-width region's is.
    static List<SdlGpuRegion> bands(List<SdlGpuRegion> regions, int width, int stride) {
        var bands = new ArrayList<SdlGpuRegion>(regions.size());
        // Rows `step` apart start at multiples of 512 bytes.
        var step = 512 / gcd(stride, 512);
        for (var region : regions) {
            var top = region.y() / step * step;
            var bottom = region.y() + region.height();
            bands.add(new SdlGpuRegion(0, top, width, bottom - top));
        }
        return bands;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    @Override
    public String toString() {
        return label;
    }
}
