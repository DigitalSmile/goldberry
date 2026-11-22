package dev.goldberry.gpu.render.staging;

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

    @Override
    public String toString() {
        return label;
    }
}
