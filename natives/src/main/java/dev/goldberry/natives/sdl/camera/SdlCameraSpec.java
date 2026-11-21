package dev.goldberry.natives.sdl.camera;

import java.util.Optional;

/// One mode a camera offers, or the one it is opened or running in:
/// `SDL_CameraSpec`.
///
/// @param format               the `SDL_PixelFormat`, kept as SDL said it so a
///                             format [SdlCameraPixelFormat] does not name is not
///                             lost
/// @param colorspace           the `SDL_Colorspace`
/// @param width                pixels
/// @param height               pixels
/// @param framerateNumerator   frames …
/// @param framerateDenominator … per this many seconds
public record SdlCameraSpec(
        int format, int colorspace, int width, int height, int framerateNumerator, int framerateDenominator) {

    public SdlCameraSpec {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        if (framerateNumerator < 0 || framerateDenominator < 0) {
            throw new IllegalArgumentException("frame rate " + framerateNumerator + "/" + framerateDenominator);
        }
    }

    /// A spec asking for `format` at `width`×`height` and the given rate, in
    /// whatever colorspace SDL picks (zero, `SDL_COLORSPACE_UNKNOWN`).
    public static SdlCameraSpec of(
            SdlCameraPixelFormat format, int width, int height, int fpsNumerator, int fpsDenominator) {
        return new SdlCameraSpec(format.value(), 0, width, height, fpsNumerator, fpsDenominator);
    }

    /// The format, when [SdlCameraPixelFormat] names it.
    public Optional<SdlCameraPixelFormat> pixelFormat() {
        return SdlCameraPixelFormat.of(format);
    }

    /// Frames a second, or zero when the camera does not say.
    public double framesPerSecond() {
        return framerateDenominator == 0 ? 0 : (double) framerateNumerator / framerateDenominator;
    }

    /// The same mode with its pixels in `format`: what a camera is opened with
    /// to have SDL convert.
    public SdlCameraSpec withFormat(SdlCameraPixelFormat format) {
        return new SdlCameraSpec(format.value(), colorspace, width, height, framerateNumerator, framerateDenominator);
    }
}
