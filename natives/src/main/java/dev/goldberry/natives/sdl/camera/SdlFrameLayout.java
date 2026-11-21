package dev.goldberry.natives.sdl.camera;

import java.util.List;

/// Where a camera frame's planes are in its memory, from the format, the size
/// and the pitch SDL reports.
///
/// SDL puts every plane of a frame in one allocation, the first plane's rows
/// `pitch` bytes apart and the others straight after it. Interleaved chroma
/// (NV12, NV21) has rows as long as luma's, rounded up to whole pairs; separate
/// chroma planes (IYUV, YV12) have rows half as long. A packed format is one
/// plane. A compressed one (MJPG) is `pitch` bytes of data, which is how SDL
/// reports its size.
///
/// @param offsets where each plane starts, in bytes
/// @param strides bytes from one row of each plane to the next
/// @param bytes   the frame's whole extent
public record SdlFrameLayout(List<Integer> offsets, List<Integer> strides, long bytes) {

    public SdlFrameLayout {
        offsets = List.copyOf(offsets);
        strides = List.copyOf(strides);
        if (offsets.size() != strides.size() || offsets.isEmpty()) {
            throw new IllegalArgumentException(offsets.size() + " offsets, " + strides.size() + " strides");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes " + bytes);
        }
    }

    /// The layout of a `width`×`height` frame in `format` whose first plane's
    /// rows are `pitch` bytes apart.
    ///
    /// @throws IllegalArgumentException when a dimension is not positive
    public static SdlFrameLayout of(int format, int width, int height, int pitch) {
        if (width <= 0 || height <= 0 || pitch <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height + ", pitch " + pitch);
        }
        var luma = (long) pitch * height;
        var chromaRows = (height + 1) / 2;
        return switch (SdlCameraPixelFormat.of(format).orElse(null)) {
            case NV12, NV21 -> {
                var chromaPitch = (pitch + 1) & ~1;
                yield new SdlFrameLayout(
                        List.of(0, Math.toIntExact(luma)),
                        List.of(pitch, chromaPitch),
                        luma + (long) chromaPitch * chromaRows);
            }
            case IYUV, YV12 -> {
                var chromaPitch = (pitch + 1) / 2;
                var chroma = (long) chromaPitch * chromaRows;
                yield new SdlFrameLayout(
                        List.of(0, Math.toIntExact(luma), Math.toIntExact(luma + chroma)),
                        List.of(pitch, chromaPitch, chromaPitch),
                        luma + 2 * chroma);
            }
            case MJPG -> new SdlFrameLayout(List.of(0), List.of(pitch), pitch);
            case YUY2, UYVY, YVYU -> new SdlFrameLayout(List.of(0), List.of(pitch), luma);
            case null -> new SdlFrameLayout(List.of(0), List.of(pitch), luma);
        };
    }

    /// How many planes the frame has.
    public int planes() {
        return offsets.size();
    }
}
