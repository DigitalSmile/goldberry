package io.github.digitalsmile.goldberry.natives.blend2d;

/// A pixel format Blend2D can rasterize into — `BLFormat`.
///
/// The names describe a 32-bit *value*, not a byte order: `PRGB32` is
/// `0xAARRGGBB` as a `uint32_t`, which on a little-endian machine is B, G, R, A
/// in memory. That is the same arrangement `PixelBuffer`'s
/// `BGRA32_PREMULTIPLIED` describes from the other direction, and the two
/// agreeing is what makes it possible to hand Blend2D the compositor's own
/// buffer instead of translating one into the other.
///
/// Every target Goldberry builds for is little-endian, so the equivalence holds
/// everywhere it needs to. It is written down here because it is an assumption,
/// and because a big-endian port would have to revisit exactly this.
public enum BlendFormat implements BlendEnum {

    /// No format. What an uninitialised image reports, and never something to
    /// ask for.
    NONE(0, "BL_FORMAT_NONE"),

    /// 32-bit **premultiplied** ARGB. The frame format: a compositor expects
    /// premultiplied data, and so does Blend2D when it blends.
    PRGB32(1, "BL_FORMAT_PRGB32"),

    /// 32-bit RGB with the fourth byte ignored. An opaque surface — some
    /// platforms hand one of these back rather than an alpha-capable buffer.
    XRGB32(2, "BL_FORMAT_XRGB32"),

    /// 8-bit alpha only. What a glyph mask is, which is why it is bound before
    /// there is any text to use it.
    A8(3, "BL_FORMAT_A8");

    private final int nativeValue;
    private final String nativeName;

    BlendFormat(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    /// The `BLFormat` value this maps to.
    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// Maps a `BLFormat` back to Java.
    ///
    /// @throws IllegalArgumentException if no format has this value
    public static BlendFormat of(int nativeValue) {
        return switch (nativeValue) {
            case 0 -> NONE;
            case 1 -> PRGB32;
            case 2 -> XRGB32;
            case 3 -> A8;
            default -> throw new IllegalArgumentException(
                    "BLFormat " + nativeValue + " is not one Blend2D defines");
        };
    }
}
