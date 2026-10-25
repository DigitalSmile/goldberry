package dev.goldberry.media.bitstream.vp9;

import dev.goldberry.media.bitstream.BitReader;

/// `quantization_params()`: the frame's quantizer index, and the offsets from
/// it of the luma DC and the chroma coefficients.
///
/// @param baseQIdx   `base_q_idx`, 0 to 255
/// @param deltaQYDc  `delta_q_y_dc`, −15 to 15, 0 when not coded
/// @param deltaQUvDc `delta_q_uv_dc`, likewise
/// @param deltaQUvAc `delta_q_uv_ac`, likewise
public record Quantization(int baseQIdx, int deltaQYDc, int deltaQUvDc, int deltaQUvAc) {

    /// `Lossless`: a zero index with no offsets, which codes every block with
    /// the 4×4 Walsh-Hadamard transform.
    public boolean lossless() {
        return baseQIdx == 0 && deltaQYDc == 0 && deltaQUvDc == 0 && deltaQUvAc == 0;
    }

    static Quantization read(BitReader r) {
        return new Quantization(r.bits(8), delta(r), delta(r), delta(r));
    }

    /// `read_delta_q()`: a flag, then `su(4)` when it is set.
    private static int delta(BitReader r) {
        return r.flag() ? r.signMagnitude(4) : 0;
    }
}
