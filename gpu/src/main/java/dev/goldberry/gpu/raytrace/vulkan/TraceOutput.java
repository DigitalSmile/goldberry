package dev.goldberry.gpu.raytrace.vulkan;

import dev.goldberry.gpu.raytrace.Hits;
import dev.goldberry.gpu.raytrace.Lighting;

/// What one dispatch of `trace.lib.hlsl` writes, chosen by its `output` push
/// constant, a ray generation a pixel each way: how many bytes that is for
/// a picture, and whether the buffer is zeroed first.
enum TraceOutput {

    /// A [Hits] record a pixel, each written whole.
    RECORDS(0, false) {
        @Override
        long bytes(int width, int height) {
            return (long) width * height * Hits.RECORD_BYTES;
        }
    },

    /// A byte a pixel, rows with no padding, the whole rounded up to a word:
    /// zeroed, then each lit pixel ORs its byte into its word.
    SHADOW_MASK(1, true) {
        @Override
        long bytes(int width, int height) {
            return ((long) width * height + Integer.BYTES - 1) / Integer.BYTES * Integer.BYTES;
        }
    },

    /// Two planes, each written whole: four bytes of shading a pixel, then a
    /// float a pixel, the id of the instance its reflection sees.
    LIGHTING(2, false) {
        @Override
        long bytes(int width, int height) {
            return (long) width * height * Lighting.BYTES_PER_PIXEL;
        }
    };

    private final int code;
    private final boolean zeroed;

    TraceOutput(int code, boolean zeroed) {
        this.code = code;
        this.zeroed = zeroed;
    }

    /// The value of the shader's `output` constant.
    int code() {
        return code;
    }

    /// Whether the buffer is zeroed before the dispatch, because the shaders
    /// write only some of it.
    boolean zeroed() {
        return zeroed;
    }

    /// How many bytes a `width` by `height` picture takes on the GPU.
    abstract long bytes(int width, int height);
}
