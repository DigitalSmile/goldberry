package dev.goldberry.camera.backend;

import dev.goldberry.media.codec.VideoFrame;

/// The matrix and range an `SDL_Colorspace` names.
///
/// SDL packs a colorspace as `type << 28 | range << 24 | chroma << 20 |
/// primaries << 10 | transfer << 5 | matrix`. Range 2 is full; matrix 1 is
/// BT.709, 9 and 10 BT.2020, 5 and 6 BT.601. A camera that says nothing
/// (zero) is taken as BT.601 limited, what USB video class cameras send.
///
/// @param matrix    the Y'CbCr matrix
/// @param fullRange whether luma spans the whole code range
public record Colorimetry(VideoFrame.ColorMatrix matrix, boolean fullRange) {

    /// What a camera that does not say is taken to be.
    public static final Colorimetry UNSAID = new Colorimetry(VideoFrame.ColorMatrix.BT601, false);

    /// What `colorspace` names.
    public static Colorimetry of(int colorspace) {
        if (colorspace == 0) {
            return UNSAID;
        }
        var range = (colorspace >>> 24) & 0xF;
        var matrix = switch (colorspace & 0x1F) {
            case 1 -> VideoFrame.ColorMatrix.BT709;
            case 9, 10 -> VideoFrame.ColorMatrix.BT2020;
            default -> VideoFrame.ColorMatrix.BT601;
        };
        return new Colorimetry(matrix, range == 2);
    }
}
