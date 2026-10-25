package dev.goldberry.media.bitstream.av1;

/// A frame's size (§5.9.5 to §5.9.8, §7.21): as coded, as upscaled by
/// superres, and as it is meant to be shown.
///
/// @param frameWidth                  `FrameWidth`: the coded width, after
///                                    superres's downscaling
/// @param frameHeight                 `FrameHeight`
/// @param upscaledWidth               `UpscaledWidth`: the width superres
///                                    restores, the frame's width as a
///                                    reference
/// @param renderWidth                 `RenderWidth`
/// @param renderHeight                `RenderHeight`
/// @param renderAndFrameSizeDifferent `render_and_frame_size_different`
/// @param useSuperres                 `use_superres`
/// @param codedDenom                  `coded_denom`, 0 without superres
public record FrameSize(
        int frameWidth,
        int frameHeight,
        int upscaledWidth,
        int renderWidth,
        int renderHeight,
        boolean renderAndFrameSizeDifferent,
        boolean useSuperres,
        int codedDenom) {

    /// `SUPERRES_NUM`: the numerator of superres's scale.
    public static final int SUPERRES_NUM = 8;

    /// `SuperresDenom`: 9 to 16, or [#SUPERRES_NUM] without superres.
    public int superresDenom() {
        return useSuperres ? codedDenom + 9 : SUPERRES_NUM;
    }

    /// `MiCols`: the width in 4×4 blocks, rounded up to a multiple of two.
    public int miCols() {
        return 2 * ((frameWidth + 7) >> 3);
    }

    /// `MiRows`.
    public int miRows() {
        return 2 * ((frameHeight + 7) >> 3);
    }
}
