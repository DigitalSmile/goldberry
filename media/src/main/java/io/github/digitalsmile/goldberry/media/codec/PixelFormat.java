package io.github.digitalsmile.goldberry.media.codec;

/// How the planes of a [VideoFrame] are laid out: the three formats of the frame
/// contract in `docs/goldberry-media.md` §5.
public enum PixelFormat {
    /// 8-bit luma plane, then a half-resolution plane of interleaved U and V. What
    /// hardware decoders produce and what copy-back delivers.
    NV12(2),
    /// 8-bit luma, then half-resolution U, then V: three planes. What software
    /// decoders produce for 8-bit 4:2:0.
    I420(3),
    /// NV12's layout with 16-bit samples holding 10 significant bits: 10-bit
    /// content, from hardware or copy-back.
    P010(2);

    private final int planes;

    PixelFormat(int planes) {
        this.planes = planes;
    }

    /// How many planes a frame of this format has.
    public int planes() {
        return planes;
    }
}
