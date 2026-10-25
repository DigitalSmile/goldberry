package dev.goldberry.media.platform.vulkan;

import java.util.Objects;

import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.VideoFrame;

/// What a stream's VUI says of its pictures' colour, with the built-in
/// decoders' defaults for what it does not: BT.709 from 720 rows, BT.601
/// below, limited range.
///
/// @param matrix    the matrix
/// @param fullRange whether luma spans the whole code range
/// @param colour    the primaries, transfer function and light levels
public record StreamSignal(VideoFrame.ColorMatrix matrix, boolean fullRange, ColorDescription colour) {

    public StreamSignal {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(colour, "colour");
    }

    /// The defaults for a picture of `height` rows that says nothing.
    public static StreamSignal unsignalled(int height) {
        return new StreamSignal(defaultMatrix(height), false, ColorDescription.UNSPECIFIED);
    }

    /// What a VUI says: `matrixCoefficients` when `described`, the range when
    /// `signalled`, and `colour`; the defaults for a picture of `height` rows
    /// fill the rest.
    public static StreamSignal of(
            boolean described,
            int matrixCoefficients,
            boolean signalled,
            boolean fullRange,
            ColorDescription colour,
            int height) {
        var matrix = described
                ? switch (matrixCoefficients) {
                    case 1 -> VideoFrame.ColorMatrix.BT709;
                    case 5, 6 -> VideoFrame.ColorMatrix.BT601;
                    case 9, 10 -> VideoFrame.ColorMatrix.BT2020;
                    default -> defaultMatrix(height);
                }
                : defaultMatrix(height);
        return new StreamSignal(matrix, signalled && fullRange, colour);
    }

    private static VideoFrame.ColorMatrix defaultMatrix(int height) {
        return height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
    }
}
