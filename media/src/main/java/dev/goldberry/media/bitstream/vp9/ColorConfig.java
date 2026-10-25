package dev.goldberry.media.bitstream.vp9;

import dev.goldberry.media.bitstream.BitReader;

/// `color_config()` of a VP9 uncompressed header: the bit depth, the matrix,
/// the range and the chroma subsampling. A keyframe states them, and so does
/// an intra-only frame of profile 1 to 3; an intra-only frame of profile 0 is
/// [#PROFILE_0_INTRA_ONLY], and every other frame keeps the last ones stated.
///
/// @param bitDepth     `BitDepth`: 8, or 10 or 12 from `ten_or_twelve_bit` in
///                     profiles 2 and 3
/// @param colorSpace   `color_space`: [#CS_UNKNOWN] to [#CS_RGB], as Vulkan's
///                     `StdVideoVP9ColorSpace` numbers them
/// @param fullRange    `color_range`: full range, as RGB always is
/// @param subsamplingX `subsampling_x`: 1 when chroma has half the columns
/// @param subsamplingY `subsampling_y`: 1 when chroma has half the rows
public record ColorConfig(int bitDepth, int colorSpace, boolean fullRange, int subsamplingX, int subsamplingY) {

    public static final int CS_UNKNOWN = 0;
    public static final int CS_BT_601 = 1;
    public static final int CS_BT_709 = 2;
    public static final int CS_SMPTE_170 = 3;
    public static final int CS_SMPTE_240 = 4;
    public static final int CS_BT_2020 = 5;
    public static final int CS_RESERVED = 6;
    public static final int CS_RGB = 7;

    /// What an intra-only frame of profile 0 is, which states no colour:
    /// 8-bit 4:2:0, BT.601, limited range.
    public static final ColorConfig PROFILE_0_INTRA_ONLY = new ColorConfig(8, CS_BT_601, false, 1, 1);

    /// `color_config()` of a frame of `profile`.
    ///
    /// @throws IllegalArgumentException when it is not one `profile` allows:
    ///                                  4:2:0 in profile 1 or 3, anything else
    ///                                  in 0 or 2, or the reserved bit set
    static ColorConfig read(BitReader r, int profile) {
        var bitDepth = profile >= 2 ? (r.flag() ? 12 : 10) : 8;
        var colorSpace = r.bits(3);
        var fullRange = true;
        var x = 0;
        var y = 0;
        var oddProfile = profile == 1 || profile == 3;
        if (colorSpace != CS_RGB) {
            fullRange = r.flag();
            if (oddProfile) {
                x = r.bit();
                y = r.bit();
                if (x == 1 && y == 1) {
                    throw new IllegalArgumentException("4:2:0 in VP9 profile " + profile);
                }
                reservedZero(r);
            } else {
                x = 1;
                y = 1;
            }
        } else {
            if (!oddProfile) {
                throw new IllegalArgumentException("RGB in VP9 profile " + profile);
            }
            reservedZero(r);
        }
        return new ColorConfig(bitDepth, colorSpace, fullRange, x, y);
    }

    private static void reservedZero(BitReader r) {
        if (r.flag()) {
            throw new IllegalArgumentException("the reserved bit of a VP9 colour configuration is set");
        }
    }
}
