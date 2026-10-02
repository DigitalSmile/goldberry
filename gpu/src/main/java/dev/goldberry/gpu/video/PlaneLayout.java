package dev.goldberry.gpu.video;

import java.util.List;

import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.render.YuvLayout;

/// How a picture's Y'CbCr planes are laid out: the four 4:2:0 layouts the
/// media engine hands over.
public enum PlaneLayout {
    /// 8-bit luma, then Cb and Cr interleaved at half size.
    NV12(YuvLayout.NV12),
    /// 8-bit luma, Cb and Cr: three planes, chroma at half size.
    I420(YuvLayout.I420),
    /// NV12's layout in 16-bit samples, 10 significant bits in the high bits.
    P010(YuvLayout.P010),
    /// I420's layout in 16-bit samples, 10 significant bits in the low bits.
    I010(YuvLayout.I010);

    private final YuvLayout yuv;

    PlaneLayout(YuvLayout yuv) {
        this.yuv = yuv;
    }

    /// How many planes a picture of this layout has.
    public int planes() {
        return textureFormats().size();
    }

    /// The texture each plane is uploaded into, luma first.
    List<TextureFormat> textureFormats() {
        return switch (this) {
            case NV12 -> List.of(TextureFormat.R8_UNORM, TextureFormat.R8G8_UNORM);
            case I420 -> List.of(TextureFormat.R8_UNORM, TextureFormat.R8_UNORM, TextureFormat.R8_UNORM);
            case P010 -> List.of(TextureFormat.R16_UNORM, TextureFormat.R16G16_UNORM);
            case I010 -> List.of(TextureFormat.R16_UNORM, TextureFormat.R16_UNORM, TextureFormat.R16_UNORM);
        };
    }

    /// The width of plane `index` of a picture `width` pixels wide, in texels.
    int planeWidth(int index, int width) {
        return yuv.planeWidth(index, width);
    }

    /// The height of plane `index` of a picture `height` pixels tall.
    int planeHeight(int index, int height) {
        return yuv.planeHeight(index, height);
    }

    /// The shaders' name for this layout.
    YuvLayout yuv() {
        return yuv;
    }
}
