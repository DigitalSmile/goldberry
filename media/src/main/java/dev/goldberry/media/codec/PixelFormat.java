package dev.goldberry.media.codec;

/// How the planes of a [VideoFrame] are laid out: the formats a decoder may
/// hand the Engine.
///
/// All four are 4:2:0: the chroma planes are half the picture's width and half
/// its height, rounded up. That is what every decoder this module ships produces
/// for ordinary content, and what hardware decoders and copy-back deliver. A
/// decoder whose output is something else (4:2:2, 4:4:4, 12-bit, RGB) converts it
/// to [#I420] or [#I010] before handing it over, so the present path has four
/// layouts to know and not forty.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public enum PixelFormat {
    /// 8-bit luma plane, then a half-resolution plane of interleaved U and V. What
    /// hardware decoders produce and what copy-back delivers.
    NV12(2, 1),
    /// 8-bit luma, then half-resolution U, then V: three planes. What software
    /// decoders produce for 8-bit 4:2:0.
    I420(3, 1),
    /// NV12's layout with 16-bit little-endian samples holding 10 significant bits
    /// in their high bits: 10-bit content, from hardware or copy-back.
    P010(2, 2),
    /// I420's layout with 16-bit little-endian samples holding 10 significant bits
    /// in their low bits: what software decoders (dav1d, VP9 profile 2) produce
    /// for 10-bit 4:2:0. In the contract so that the common 10-bit case is lent
    /// without a copy.
    I010(3, 2);

    private final int planes;
    private final int bytesPerSample;

    PixelFormat(int planes, int bytesPerSample) {
        this.planes = planes;
        this.bytesPerSample = bytesPerSample;
    }

    /// How many planes a frame of this format has.
    public int planes() {
        return planes;
    }

    /// The bytes one sample of one component takes: 1 for 8-bit, 2 for 10-bit.
    public int bytesPerSample() {
        return bytesPerSample;
    }

    /// The rows `plane` has in a picture `height` rows tall.
    ///
    /// @throws IndexOutOfBoundsException for a plane this format does not have
    public int planeRows(int plane, int height) {
        checkPlane(plane);
        return plane == 0 ? height : (height + 1) / 2;
    }

    /// The bytes of one row of `plane` that hold picture, in a picture `width`
    /// pixels wide. A stride is at least this.
    ///
    /// @throws IndexOutOfBoundsException for a plane this format does not have
    public int planeRowBytes(int plane, int width) {
        checkPlane(plane);
        if (plane == 0) {
            return width * bytesPerSample;
        }
        var chromaWidth = (width + 1) / 2;
        // A semi-planar format interleaves U and V in its second plane.
        return planes == 2 ? chromaWidth * 2 * bytesPerSample : chromaWidth * bytesPerSample;
    }

    private void checkPlane(int plane) {
        if (plane < 0 || plane >= planes) {
            throw new IndexOutOfBoundsException(this + " has no plane " + plane);
        }
    }
}
