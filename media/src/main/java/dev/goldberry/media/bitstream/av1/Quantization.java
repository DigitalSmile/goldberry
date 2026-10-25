package dev.goldberry.media.bitstream.av1;

/// `quantization_params()` (§5.9.12), as Vulkan Video's
/// `StdVideoAV1Quantization` wants it.
///
/// @param baseQIdx      `base_q_idx`
/// @param deltaQYDc     `DeltaQYDc`
/// @param deltaQUDc     `DeltaQUDc`
/// @param deltaQUAc     `DeltaQUAc`
/// @param deltaQVDc     `DeltaQVDc`: `DeltaQUDc` unless `diff_uv_delta`
/// @param deltaQVAc     `DeltaQVAc`
/// @param diffUvDelta   `diff_uv_delta`
/// @param usingQmatrix  `using_qmatrix`
/// @param qmY           `qm_y`, 0 without matrices
/// @param qmU           `qm_u`
/// @param qmV           `qm_v`: `qm_u` unless `separate_uv_delta_q`
public record Quantization(
        int baseQIdx,
        int deltaQYDc,
        int deltaQUDc,
        int deltaQUAc,
        int deltaQVDc,
        int deltaQVAc,
        boolean diffUvDelta,
        boolean usingQmatrix,
        int qmY,
        int qmU,
        int qmV) {}
