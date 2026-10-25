package dev.goldberry.gpu.upscale.fsr;

/// The constants FSR 1's two shaders read, worked out on the CPU once a pass:
/// AMD's `FsrEasuCon` and `FsrRcasCon`, as floats rather than their bits.
///
/// Ported from AMD FidelityFX Super Resolution 1.0 (`ffx_fsr1.h`), MIT
/// licence. The input is the whole of its texture, which is what
/// `FsrEasuCon`'s viewport is when nothing is letterboxed.
public final class Fsr1 {

    /// How many floats [#easu] gives: four rows of four.
    public static final int EASU_FLOATS = 16;

    /// How many floats [#rcas] gives: one row.
    public static final int RCAS_FLOATS = 4;

    private Fsr1() {}

    /// EASU's uniform block for a picture of `inputWidth` by `inputHeight`
    /// drawn into `outputWidth` by `outputHeight`: the scale and offset from
    /// an output pixel to the input's texel centres, then one texel and the
    /// offsets of the four gathers in texture coordinates.
    ///
    /// @throws IllegalArgumentException when a size is below one
    public static float[] easu(int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
        if (inputWidth < 1 || inputHeight < 1 || outputWidth < 1 || outputHeight < 1) {
            throw new IllegalArgumentException(
                    inputWidth + "x" + inputHeight + " into " + outputWidth + "x" + outputHeight);
        }
        var inX = (float) inputWidth;
        var inY = (float) inputHeight;
        var texelX = 1f / inX;
        var texelY = 1f / inY;
        var scaleX = inX * (1f / outputWidth);
        var scaleY = inY * (1f / outputHeight);
        return new float[] {
            scaleX,
            scaleY,
            0.5f * scaleX - 0.5f,
            0.5f * scaleY - 0.5f,
            texelX,
            texelY,
            texelX,
            -texelY,
            -texelX,
            2f * texelY,
            texelX,
            2f * texelY,
            0f * texelX,
            4f * texelY,
            0f,
            0f
        };
    }

    /// RCAS's uniform block for a picture of `width` by `height`: how much it
    /// sharpens, then its last pixel, which the ring at its edges is clamped
    /// to.
    ///
    /// `sharpness` is the lobe's scale, 0 to 1: FSR's `exp2(-stops)`, so 1 is
    /// its 0 stops, the most, and each halving is a stop less.
    ///
    /// @throws IllegalArgumentException when the sharpness is outside 0 to 1
    ///                                  or a size is below one
    public static float[] rcas(double sharpness, int width, int height) {
        if (!(sharpness >= 0 && sharpness <= 1)) {
            throw new IllegalArgumentException("sharpness " + sharpness + "; it is 0 to 1");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException(width + "x" + height);
        }
        return new float[] {(float) sharpness, width - 1, height - 1, 0f};
    }
}
