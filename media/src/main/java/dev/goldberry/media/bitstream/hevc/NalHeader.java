package dev.goldberry.media.bitstream.hevc;

/// An HEVC NAL unit's two-byte header (H.265 §7.3.1.2), and the unit types
/// a decoder tells apart.
///
/// @param type          `nal_unit_type`
/// @param layerId       `nuh_layer_id`: 0 for the base layer, the only one
///                      decoded
/// @param temporalId    `nuh_temporal_id_plus1` − 1
public record NalHeader(int type, int layerId, int temporalId) {

    public static final int TRAIL_N = 0;
    public static final int TRAIL_R = 1;
    public static final int TSA_N = 2;
    public static final int RADL_N = 6;
    public static final int RADL_R = 7;
    public static final int RASL_N = 8;
    public static final int RASL_R = 9;
    public static final int RSV_VCL_N14 = 14;
    public static final int BLA_W_LP = 16;
    public static final int BLA_W_RADL = 17;
    public static final int BLA_N_LP = 18;
    public static final int IDR_W_RADL = 19;
    public static final int IDR_N_LP = 20;
    public static final int CRA_NUT = 21;
    public static final int RSV_IRAP_23 = 23;
    public static final int VPS = 32;
    public static final int SPS = 33;
    public static final int PPS = 34;
    public static final int AUD = 35;
    public static final int EOS = 36;
    public static final int EOB = 37;
    public static final int FD = 38;
    public static final int PREFIX_SEI = 39;
    public static final int SUFFIX_SEI = 40;

    /// The header at the start of `nal`.
    ///
    /// @throws IllegalArgumentException when `nal` is shorter than two bytes
    ///                                  or its forbidden bit is set
    public static NalHeader of(byte[] nal) {
        if (nal.length < 2) {
            throw new IllegalArgumentException("an HEVC NAL unit of " + nal.length + " bytes");
        }
        if ((nal[0] & 0x80) != 0) {
            throw new IllegalArgumentException("an HEVC NAL unit with forbidden_zero_bit set");
        }
        var type = (nal[0] >> 1) & 0x3F;
        var layer = ((nal[0] & 1) << 5) | ((nal[1] >> 3) & 0x1F);
        var temporal = (nal[1] & 7) - 1;
        if (temporal < 0) {
            throw new IllegalArgumentException("an HEVC NAL unit with nuh_temporal_id_plus1 zero");
        }
        return new NalHeader(type, layer, temporal);
    }

    /// Whether it carries a slice segment of a picture.
    public boolean isSlice() {
        return type <= RSV_IRAP_23 && (type <= RASL_R || type >= BLA_W_LP);
    }

    /// An intra random access point: BLA, IDR, CRA.
    public boolean isIrap() {
        return type >= BLA_W_LP && type <= RSV_IRAP_23;
    }

    /// An instantaneous decoding refresh.
    public boolean isIdr() {
        return type == IDR_W_RADL || type == IDR_N_LP;
    }

    /// A broken link access.
    public boolean isBla() {
        return type >= BLA_W_LP && type <= BLA_N_LP;
    }

    /// A random access skipped leading picture.
    public boolean isRasl() {
        return type == RASL_N || type == RASL_R;
    }

    /// A sub-layer non-reference picture (§7.4.2.2): `_N` types below 16.
    public boolean isSubLayerNonReference() {
        return type <= RSV_VCL_N14 && (type & 1) == 0;
    }
}
