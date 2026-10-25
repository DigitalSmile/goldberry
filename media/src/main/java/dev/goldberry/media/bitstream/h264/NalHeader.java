package dev.goldberry.media.bitstream.h264;

/// An H.264 NAL unit's one-byte header (H.264 §7.3.1), and the unit types a
/// decoder tells apart.
///
/// @param refIdc `nal_ref_idc`: zero for a picture nothing refers to
/// @param type   `nal_unit_type`
public record NalHeader(int refIdc, int type) {

    public static final int SLICE = 1;
    public static final int IDR = 5;
    public static final int SEI = 6;
    public static final int SPS = 7;
    public static final int PPS = 8;
    public static final int AUD = 9;
    public static final int END_OF_SEQUENCE = 10;
    public static final int END_OF_STREAM = 11;
    public static final int FILLER = 12;
    public static final int SPS_EXTENSION = 13;
    public static final int PREFIX = 14;
    public static final int SUBSET_SPS = 15;
    public static final int SLICE_EXTENSION = 20;

    /// The header at the start of `nal`.
    ///
    /// @throws IllegalArgumentException for an empty unit, or one with its
    ///                                  forbidden bit set
    public static NalHeader of(byte[] nal) {
        if (nal.length < 1 || (nal[0] & 0x80) != 0) {
            throw new IllegalArgumentException("not an H.264 NAL unit");
        }
        return new NalHeader((nal[0] >> 5) & 3, nal[0] & 0x1F);
    }

    /// Whether it carries a slice of a picture of the base layer.
    public boolean isSlice() {
        return type == SLICE || type == IDR;
    }

    /// Whether it starts a coded video sequence anew.
    public boolean isIdr() {
        return type == IDR;
    }
}
