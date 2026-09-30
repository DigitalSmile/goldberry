package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuPrimitiveType;

/// How a pipeline assembles vertices into what it rasterizes.
public enum PrimitiveType {
    /// Every three vertices a triangle.
    TRIANGLE_LIST,
    /// Each vertex after the second a triangle with the two before it.
    TRIANGLE_STRIP,
    /// Every two vertices a line, one pixel wide.
    LINE_LIST,
    /// Each vertex after the first a line with the one before it.
    LINE_STRIP,
    /// Every vertex a point, one pixel wide.
    POINT_LIST;

    SdlGpuPrimitiveType sdl() {
        return switch (this) {
            case TRIANGLE_LIST -> SdlGpuPrimitiveType.TRIANGLE_LIST;
            case TRIANGLE_STRIP -> SdlGpuPrimitiveType.TRIANGLE_STRIP;
            case LINE_LIST -> SdlGpuPrimitiveType.LINE_LIST;
            case LINE_STRIP -> SdlGpuPrimitiveType.LINE_STRIP;
            case POINT_LIST -> SdlGpuPrimitiveType.POINT_LIST;
        };
    }
}
