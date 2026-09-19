package io.github.digitalsmile.goldberry.natives.blend2d.enums;

/// How `bl_image_scale` resamples — `BLImageScaleFilter`.
///
/// Four of Blend2D's five. The omitted one is `BL_IMAGE_SCALE_FILTER_NONE`,
/// which is the absence of a filter rather than one of them: it is what the
/// enum's zero value has to be, and asking for it is asking for a scale that
/// does not scale. Naming it here would be naming a constant nothing can
/// usefully pass, which is the same rule [BlendStrokeJoin] applies to the two
/// miter variants it leaves out.
///
/// The four that are here are a real choice and not a quality slider — see
/// ADR-0428. They enumerate positionally from 1, so a value inserted upstream
/// shifts every one after it; the layout verifier is what notices.
public enum BlendImageScaleFilter implements BlendEnum {

    /// Take the nearest source pixel. The only filter that invents no colours,
    /// and therefore the only one that doubles a 16×16 icon into a 32×32 one
    /// rather than into a blur of it.
    NEAREST(1, "BL_IMAGE_SCALE_FILTER_NEAREST"),

    /// A weighted average of the four neighbours. Cheap, and soft when the
    /// factor is far from one.
    BILINEAR(2, "BL_IMAGE_SCALE_FILTER_BILINEAR"),

    /// A cubic through sixteen neighbours. Sharper than bilinear and without
    /// the ringing a windowed sinc can produce on a hard edge.
    BICUBIC(3, "BL_IMAGE_SCALE_FILTER_BICUBIC"),

    /// A windowed sinc. The sharpest of the four when an image is made
    /// **smaller**, which is what a thumbnail is, and the one most likely to put
    /// a halo beside a hard edge when it is made larger.
    LANCZOS(4, "BL_IMAGE_SCALE_FILTER_LANCZOS");

    private final int nativeValue;
    private final String nativeName;

    BlendImageScaleFilter(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }
}
