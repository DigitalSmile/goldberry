package dev.goldberry.media.bitstream.hevc;

import java.util.Optional;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.codec.ColorDescription;

/// `vui_parameters()` (H.265 §E.2.1): the video usability information, of
/// which a player wants the colour and Vulkan Video wants everything.
///
/// @param aspectRatioIdc              `aspect_ratio_idc`, 0 when not present
/// @param sarWidth                    `sar_width`
/// @param sarHeight                   `sar_height`
/// @param overscanInfoPresent         `overscan_info_present_flag`
/// @param overscanAppropriate         `overscan_appropriate_flag`
/// @param videoSignalTypePresent      `video_signal_type_present_flag`
/// @param videoFormat                 `video_format`, 5 when not present
/// @param fullRange                   `video_full_range_flag`
/// @param colourDescriptionPresent    `colour_description_present_flag`
/// @param colourPrimaries             `colour_primaries`, 2 when not present
/// @param transferCharacteristics     `transfer_characteristics`, 2 likewise
/// @param matrixCoefficients          `matrix_coefficients`, 2 likewise
/// @param chromaLocInfoPresent        `chroma_loc_info_present_flag`
/// @param chromaSampleLocTypeTop      `chroma_sample_loc_type_top_field`
/// @param chromaSampleLocTypeBottom   `chroma_sample_loc_type_bottom_field`
/// @param neutralChromaIndication     `neutral_chroma_indication_flag`
/// @param fieldSeq                    `field_seq_flag`
/// @param frameFieldInfoPresent       `frame_field_info_present_flag`
/// @param defaultDisplayWindow        `default_display_window_flag`
/// @param defaultDisplayWindowOffsets left, right, top and bottom
/// @param timingInfoPresent           `vui_timing_info_present_flag`
/// @param numUnitsInTick              `vui_num_units_in_tick`
/// @param timeScale                   `vui_time_scale`
/// @param pocProportionalToTiming     `vui_poc_proportional_to_timing_flag`
/// @param numTicksPocDiffOneMinus1    `vui_num_ticks_poc_diff_one_minus1`
/// @param hrd                         `hrd_parameters()`, when present
/// @param bitstreamRestriction        `bitstream_restriction_flag`
/// @param tilesFixedStructure         `tiles_fixed_structure_flag`
/// @param motionVectorsOverPicBoundaries `motion_vectors_over_pic_boundaries_flag`
/// @param restrictedRefPicLists       `restricted_ref_pic_lists_flag`
/// @param minSpatialSegmentationIdc   `min_spatial_segmentation_idc`
/// @param maxBytesPerPicDenom         `max_bytes_per_pic_denom`
/// @param maxBitsPerMinCuDenom        `max_bits_per_min_cu_denom`
/// @param log2MaxMvLengthHorizontal   `log2_max_mv_length_horizontal`
/// @param log2MaxMvLengthVertical     `log2_max_mv_length_vertical`
@SuppressWarnings("ArrayRecordComponent")
public record Vui(
        int aspectRatioIdc,
        int sarWidth,
        int sarHeight,
        boolean overscanInfoPresent,
        boolean overscanAppropriate,
        boolean videoSignalTypePresent,
        int videoFormat,
        boolean fullRange,
        boolean colourDescriptionPresent,
        int colourPrimaries,
        int transferCharacteristics,
        int matrixCoefficients,
        boolean chromaLocInfoPresent,
        int chromaSampleLocTypeTop,
        int chromaSampleLocTypeBottom,
        boolean neutralChromaIndication,
        boolean fieldSeq,
        boolean frameFieldInfoPresent,
        boolean defaultDisplayWindow,
        int[] defaultDisplayWindowOffsets,
        boolean timingInfoPresent,
        long numUnitsInTick,
        long timeScale,
        boolean pocProportionalToTiming,
        int numTicksPocDiffOneMinus1,
        Optional<Hrd> hrd,
        boolean bitstreamRestriction,
        boolean tilesFixedStructure,
        boolean motionVectorsOverPicBoundaries,
        boolean restrictedRefPicLists,
        int minSpatialSegmentationIdc,
        int maxBytesPerPicDenom,
        int maxBitsPerMinCuDenom,
        int log2MaxMvLengthHorizontal,
        int log2MaxMvLengthVertical) {

    /// The primaries and transfer function, unspecified unless signalled.
    public ColorDescription colour() {
        return colourDescriptionPresent
                ? ColorDescription.fromH273(colourPrimaries, transferCharacteristics)
                : ColorDescription.UNSPECIFIED;
    }

    /// `vui_parameters()` of an SPS with `maxSubLayersMinus1`.
    static Vui read(BitReader r, int maxSubLayersMinus1) {
        var aspectPresent = r.flag();
        var aspect = 0;
        var sarWidth = 0;
        var sarHeight = 0;
        if (aspectPresent) {
            aspect = r.bits(8);
            if (aspect == 255) {
                sarWidth = r.bits(16);
                sarHeight = r.bits(16);
            }
        }
        var overscanPresent = r.flag();
        var overscanAppropriate = overscanPresent && r.flag();
        var signalPresent = r.flag();
        var format = 5;
        var full = false;
        var colourPresent = false;
        var primaries = 2;
        var transfer = 2;
        var matrix = 2;
        if (signalPresent) {
            format = r.bits(3);
            full = r.flag();
            colourPresent = r.flag();
            if (colourPresent) {
                primaries = r.bits(8);
                transfer = r.bits(8);
                matrix = r.bits(8);
            }
        }
        var chromaLocPresent = r.flag();
        var top = 0;
        var bottom = 0;
        if (chromaLocPresent) {
            top = r.ue();
            bottom = r.ue();
        }
        var neutral = r.flag();
        var fieldSeq = r.flag();
        var frameField = r.flag();
        var window = r.flag();
        var offsets = new int[4];
        if (window) {
            for (var i = 0; i < 4; i++) {
                offsets[i] = r.ue();
            }
        }
        var timing = r.flag();
        var units = 0L;
        var scale = 0L;
        var proportional = false;
        var ticks = 0;
        Optional<Hrd> hrd = Optional.empty();
        if (timing) {
            units = r.bitsLong(32);
            scale = r.bitsLong(32);
            proportional = r.flag();
            if (proportional) {
                ticks = r.ue();
            }
            if (r.flag()) { // vui_hrd_parameters_present_flag
                hrd = Optional.of(Hrd.read(r, true, maxSubLayersMinus1));
            }
        }
        var restriction = r.flag();
        var tilesFixed = false;
        var mvOver = false;
        var restricted = false;
        var minSpatial = 0;
        var maxBytes = 0;
        var maxBits = 0;
        var mvH = 0;
        var mvV = 0;
        if (restriction) {
            tilesFixed = r.flag();
            mvOver = r.flag();
            restricted = r.flag();
            minSpatial = r.ue();
            maxBytes = r.ue();
            maxBits = r.ue();
            mvH = r.ue();
            mvV = r.ue();
        }
        return new Vui(
                aspect,
                sarWidth,
                sarHeight,
                overscanPresent,
                overscanAppropriate,
                signalPresent,
                format,
                full,
                colourPresent,
                primaries,
                transfer,
                matrix,
                chromaLocPresent,
                top,
                bottom,
                neutral,
                fieldSeq,
                frameField,
                window,
                offsets,
                timing,
                units,
                scale,
                proportional,
                ticks,
                hrd,
                restriction,
                tilesFixed,
                mvOver,
                restricted,
                minSpatial,
                maxBytes,
                maxBits,
                mvH,
                mvV);
    }
}
