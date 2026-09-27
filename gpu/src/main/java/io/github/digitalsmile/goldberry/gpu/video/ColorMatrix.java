package io.github.digitalsmile.goldberry.gpu.video;

import io.github.digitalsmile.goldberry.gpu.render.YuvMatrix;

/// The Y'CbCr → R'G'B' matrix a picture was encoded with.
public enum ColorMatrix {
    /// SD video.
    BT601(YuvMatrix.BT601),
    /// HD video.
    BT709(YuvMatrix.BT709),
    /// UHD video, non-constant luminance.
    BT2020(YuvMatrix.BT2020);

    private final YuvMatrix yuv;

    ColorMatrix(YuvMatrix yuv) {
        this.yuv = yuv;
    }

    /// The shaders' name for this matrix.
    YuvMatrix yuv() {
        return yuv;
    }
}
