package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// An AV1 sequence header (§5.5): what holds for every frame of a coded video
/// sequence, as Vulkan Video's `StdVideoAV1SequenceHeader` and its colour
/// and timing structs want it.
///
/// @param seqProfile                    `seq_profile`: 0 Main, 1 High, 2
///                                      Professional
/// @param stillPicture                  `still_picture`
/// @param reducedStillPictureHeader     `reduced_still_picture_header`
/// @param timingInfo                    `timing_info()`, when
///                                      `timing_info_present_flag`
/// @param decoderModelInfo              `decoder_model_info()`, when
///                                      `decoder_model_info_present_flag`
/// @param initialDisplayDelayPresent    `initial_display_delay_present_flag`
/// @param operatingPoints               each operating point, from 0
/// @param frameWidthBitsMinus1          `frame_width_bits_minus_1`
/// @param frameHeightBitsMinus1         `frame_height_bits_minus_1`
/// @param maxFrameWidthMinus1           `max_frame_width_minus_1`
/// @param maxFrameHeightMinus1          `max_frame_height_minus_1`
/// @param frameIdNumbersPresent         `frame_id_numbers_present_flag`
/// @param deltaFrameIdLengthMinus2      `delta_frame_id_length_minus_2`, 0
///                                      without frame ids
/// @param additionalFrameIdLengthMinus1 `additional_frame_id_length_minus_1`,
///                                      0 without frame ids
/// @param use128x128Superblock          `use_128x128_superblock`
/// @param enableFilterIntra             `enable_filter_intra`
/// @param enableIntraEdgeFilter         `enable_intra_edge_filter`
/// @param enableInterintraCompound      `enable_interintra_compound`
/// @param enableMaskedCompound          `enable_masked_compound`
/// @param enableWarpedMotion            `enable_warped_motion`
/// @param enableDualFilter              `enable_dual_filter`
/// @param enableOrderHint               `enable_order_hint`
/// @param enableJntComp                 `enable_jnt_comp`
/// @param enableRefFrameMvs             `enable_ref_frame_mvs`
/// @param seqForceScreenContentTools    `seq_force_screen_content_tools`: 0,
///                                      1, or [#SELECT_SCREEN_CONTENT_TOOLS]
///                                      for each frame to say
/// @param seqForceIntegerMv             `seq_force_integer_mv`: 0, 1, or
///                                      [#SELECT_INTEGER_MV]
/// @param orderHintBitsMinus1           `order_hint_bits_minus_1`, 0 without
///                                      order hints
/// @param enableSuperres                `enable_superres`
/// @param enableCdef                    `enable_cdef`
/// @param enableRestoration             `enable_restoration`
/// @param colorConfig                   `color_config()`
/// @param filmGrainParamsPresent        `film_grain_params_present`
public record SequenceHeader(
        int seqProfile,
        boolean stillPicture,
        boolean reducedStillPictureHeader,
        Optional<TimingInfo> timingInfo,
        Optional<DecoderModelInfo> decoderModelInfo,
        boolean initialDisplayDelayPresent,
        List<OperatingPoint> operatingPoints,
        int frameWidthBitsMinus1,
        int frameHeightBitsMinus1,
        int maxFrameWidthMinus1,
        int maxFrameHeightMinus1,
        boolean frameIdNumbersPresent,
        int deltaFrameIdLengthMinus2,
        int additionalFrameIdLengthMinus1,
        boolean use128x128Superblock,
        boolean enableFilterIntra,
        boolean enableIntraEdgeFilter,
        boolean enableInterintraCompound,
        boolean enableMaskedCompound,
        boolean enableWarpedMotion,
        boolean enableDualFilter,
        boolean enableOrderHint,
        boolean enableJntComp,
        boolean enableRefFrameMvs,
        int seqForceScreenContentTools,
        int seqForceIntegerMv,
        int orderHintBitsMinus1,
        boolean enableSuperres,
        boolean enableCdef,
        boolean enableRestoration,
        ColorConfig colorConfig,
        boolean filmGrainParamsPresent)
        implements ObuContent {

    /// `SELECT_SCREEN_CONTENT_TOOLS`: each frame says whether it allows them.
    public static final int SELECT_SCREEN_CONTENT_TOOLS = 2;

    /// `SELECT_INTEGER_MV`: each frame says whether its motion vectors are
    /// whole samples.
    public static final int SELECT_INTEGER_MV = 2;

    /// `timing_info()` (§5.5.3).
    ///
    /// @param numUnitsInDisplayTick   `num_units_in_display_tick`
    /// @param timeScale               `time_scale`
    /// @param equalPictureInterval    `equal_picture_interval`
    /// @param numTicksPerPictureMinus1 `num_ticks_per_picture_minus_1`, 0
    ///                                unless `equal_picture_interval`
    public record TimingInfo(
            long numUnitsInDisplayTick, long timeScale, boolean equalPictureInterval, long numTicksPerPictureMinus1) {}

    /// `decoder_model_info()` (§5.5.4).
    ///
    /// @param bufferDelayLengthMinus1           `buffer_delay_length_minus_1`
    /// @param numUnitsInDecodingTick            `num_units_in_decoding_tick`
    /// @param bufferRemovalTimeLengthMinus1     `buffer_removal_time_length_minus_1`
    /// @param framePresentationTimeLengthMinus1 `frame_presentation_time_length_minus_1`
    public record DecoderModelInfo(
            int bufferDelayLengthMinus1,
            long numUnitsInDecodingTick,
            int bufferRemovalTimeLengthMinus1,
            int framePresentationTimeLengthMinus1) {}

    /// One operating point: the layers it decodes, and its level.
    ///
    /// @param idc                        `operating_point_idc`: a bit per
    ///                                   temporal layer, then per spatial layer
    ///                                   from bit 8; 0 for a stream of one layer
    /// @param seqLevelIdx                `seq_level_idx`
    /// @param seqTier                    `seq_tier`
    /// @param decoderModelPresent        `decoder_model_present_for_this_op`
    /// @param decoderBufferDelay         `decoder_buffer_delay`
    /// @param encoderBufferDelay         `encoder_buffer_delay`
    /// @param lowDelayMode               `low_delay_mode_flag`
    /// @param initialDisplayDelayPresent `initial_display_delay_present_for_this_op`
    /// @param initialDisplayDelayMinus1  `initial_display_delay_minus_1`
    public record OperatingPoint(
            int idc,
            int seqLevelIdx,
            int seqTier,
            boolean decoderModelPresent,
            long decoderBufferDelay,
            long encoderBufferDelay,
            boolean lowDelayMode,
            boolean initialDisplayDelayPresent,
            int initialDisplayDelayMinus1) {}

    /// `color_config()` (§5.5.2), with what it implies worked out.
    ///
    /// @param bitDepth                `BitDepth`: 8, 10 or 12
    /// @param monoChrome              `mono_chrome`
    /// @param colorDescriptionPresent `color_description_present_flag`
    /// @param colorPrimaries          `color_primaries`, 2 (unspecified)
    ///                                when not described
    /// @param transferCharacteristics `transfer_characteristics`, 2 when not
    ///                                described
    /// @param matrixCoefficients      `matrix_coefficients`, 2 when not
    ///                                described
    /// @param colorRange              `color_range`: full range
    /// @param subsamplingX            `subsampling_x`
    /// @param subsamplingY            `subsampling_y`
    /// @param chromaSamplePosition    `chroma_sample_position`, 0 (unknown)
    ///                                unless 4:2:0
    /// @param separateUvDeltaQ        `separate_uv_delta_q`
    public record ColorConfig(
            int bitDepth,
            boolean monoChrome,
            boolean colorDescriptionPresent,
            int colorPrimaries,
            int transferCharacteristics,
            int matrixCoefficients,
            boolean colorRange,
            int subsamplingX,
            int subsamplingY,
            int chromaSamplePosition,
            boolean separateUvDeltaQ) {

        /// `NumPlanes`: 1 for monochrome, else 3.
        public int numPlanes() {
            return monoChrome ? 1 : 3;
        }
    }

    public SequenceHeader {
        Objects.requireNonNull(timingInfo, "timingInfo");
        Objects.requireNonNull(decoderModelInfo, "decoderModelInfo");
        operatingPoints = List.copyOf(operatingPoints);
        Objects.requireNonNull(colorConfig, "colorConfig");
    }

    /// The sequence header OBU `obu` of `data`.
    ///
    /// @throws IllegalArgumentException when it is malformed
    public static SequenceHeader parse(byte[] data, Obu obu) {
        if (obu.header().type() != ObuHeader.SEQUENCE_HEADER) {
            throw new IllegalArgumentException("OBU type " + obu.header().type() + " is not a sequence header");
        }
        return read(obu.payload(data, null));
    }

    /// The sequence header among the `configOBUs` of an `av1C` record (the
    /// AV1 codec configuration box of MP4 and Matroska's `CodecPrivate`), if it
    /// carries one.
    ///
    /// @throws IllegalArgumentException when the record or its OBUs are
    ///                                  malformed
    public static Optional<SequenceHeader> fromAv1C(byte[] record) {
        for (var obu : configObus(record)) {
            if (obu.header().type() == ObuHeader.SEQUENCE_HEADER) {
                return Optional.of(parse(record, obu));
            }
        }
        return Optional.empty();
    }

    /// The OBUs after the four bytes of an `av1C` record, the first of them
    /// with its marker bit and version 1.
    static List<Obu> configObus(byte[] record) {
        if (record.length < 4 || (record[0] & 0xFF) != 0x81) {
            throw new IllegalArgumentException("not an av1C record of version 1");
        }
        return Obu.split(record, 4, record.length - 4);
    }

    /// The payload `s` is at.
    static SequenceHeader read(Syntax s) {
        s.heading("Sequence Header");
        var profile = s.f(3, "seq_profile");
        if (profile > 2) {
            throw new IllegalArgumentException("seq_profile " + profile);
        }
        var still = s.flag("still_picture");
        var reduced = s.flag("reduced_still_picture_header");
        Optional<TimingInfo> timing = Optional.empty();
        Optional<DecoderModelInfo> model = Optional.empty();
        var initialDisplayDelay = false;
        var points = new ArrayList<OperatingPoint>();
        if (reduced) {
            points.add(new OperatingPoint(0, s.f(5, "seq_level_idx[0]"), 0, false, 0, 0, false, false, 0));
        } else {
            if (s.flag("timing_info_present_flag")) {
                var units = s.fLong(32, "num_units_in_display_tick");
                var scale = s.fLong(32, "time_scale");
                var equal = s.flag("equal_picture_interval");
                var ticks = equal ? s.uvlc("num_ticks_per_picture_minus_1") : 0;
                timing = Optional.of(new TimingInfo(units, scale, equal, ticks));
                if (s.flag("decoder_model_info_present_flag")) {
                    model = Optional.of(new DecoderModelInfo(
                            s.f(5, "buffer_delay_length_minus_1"),
                            s.fLong(32, "num_units_in_decoding_tick"),
                            s.f(5, "buffer_removal_time_length_minus_1"),
                            s.f(5, "frame_presentation_time_length_minus_1")));
                }
            }
            initialDisplayDelay = s.flag("initial_display_delay_present_flag");
            var count = s.f(5, "operating_points_cnt_minus_1") + 1;
            for (var i = 0; i < count; i++) {
                var idc = s.f(12, "operating_point_idc", i);
                var level = s.f(5, "seq_level_idx", i);
                var tier = level > 7 ? s.f(1, "seq_tier", i) : 0;
                var present = false;
                long decoderDelay = 0;
                long encoderDelay = 0;
                var lowDelay = false;
                if (model.isPresent()) {
                    present = s.flag("decoder_model_present_for_this_op", i);
                    if (present) {
                        var n = model.get().bufferDelayLengthMinus1() + 1;
                        decoderDelay = s.fLong(n, "decoder_buffer_delay", i);
                        encoderDelay = s.fLong(n, "encoder_buffer_delay", i);
                        lowDelay = s.flag("low_delay_mode_flag", i);
                    }
                }
                var delayPresent = false;
                var delay = 0;
                if (initialDisplayDelay) {
                    delayPresent = s.flag("initial_display_delay_present_for_this_op", i);
                    if (delayPresent) {
                        delay = s.f(4, "initial_display_delay_minus_1", i);
                    }
                }
                points.add(new OperatingPoint(
                        idc, level, tier, present, decoderDelay, encoderDelay, lowDelay, delayPresent, delay));
            }
        }
        var widthBits = s.f(4, "frame_width_bits_minus_1");
        var heightBits = s.f(4, "frame_height_bits_minus_1");
        var maxWidth = s.f(widthBits + 1, "max_frame_width_minus_1");
        var maxHeight = s.f(heightBits + 1, "max_frame_height_minus_1");
        var frameIds = !reduced && s.flag("frame_id_numbers_present_flag");
        var deltaIdLength = 0;
        var additionalIdLength = 0;
        if (frameIds) {
            deltaIdLength = s.f(4, "delta_frame_id_length_minus_2");
            additionalIdLength = s.f(3, "additional_frame_id_length_minus_1");
        }
        var sb128 = s.flag("use_128x128_superblock");
        var filterIntra = s.flag("enable_filter_intra");
        var intraEdge = s.flag("enable_intra_edge_filter");
        var interintra = false;
        var masked = false;
        var warped = false;
        var dual = false;
        var orderHint = false;
        var jnt = false;
        var refMvs = false;
        var screenContent = SELECT_SCREEN_CONTENT_TOOLS;
        var integerMv = SELECT_INTEGER_MV;
        var orderHintBits = 0;
        if (!reduced) {
            interintra = s.flag("enable_interintra_compound");
            masked = s.flag("enable_masked_compound");
            warped = s.flag("enable_warped_motion");
            dual = s.flag("enable_dual_filter");
            orderHint = s.flag("enable_order_hint");
            if (orderHint) {
                jnt = s.flag("enable_jnt_comp");
                refMvs = s.flag("enable_ref_frame_mvs");
            }
            if (!s.flag("seq_choose_screen_content_tools")) {
                screenContent = s.f(1, "seq_force_screen_content_tools");
            }
            if (screenContent > 0) {
                if (!s.flag("seq_choose_integer_mv")) {
                    integerMv = s.f(1, "seq_force_integer_mv");
                }
            }
            if (orderHint) {
                orderHintBits = s.f(3, "order_hint_bits_minus_1");
            }
        }
        var superres = s.flag("enable_superres");
        var cdef = s.flag("enable_cdef");
        var restoration = s.flag("enable_restoration");
        var color = colorConfig(s, profile);
        var grain = s.flag("film_grain_params_present");
        return new SequenceHeader(
                profile,
                still,
                reduced,
                timing,
                model,
                initialDisplayDelay,
                points,
                widthBits,
                heightBits,
                maxWidth,
                maxHeight,
                frameIds,
                deltaIdLength,
                additionalIdLength,
                sb128,
                filterIntra,
                intraEdge,
                interintra,
                masked,
                warped,
                dual,
                orderHint,
                jnt,
                refMvs,
                screenContent,
                integerMv,
                orderHintBits,
                superres,
                cdef,
                restoration,
                color,
                grain);
    }

    private static ColorConfig colorConfig(Syntax s, int profile) {
        var high = s.flag("high_bitdepth");
        var depth = high ? 10 : 8;
        if (profile == 2 && high) {
            depth = s.flag("twelve_bit") ? 12 : 10;
        }
        var mono = profile != 1 && s.flag("mono_chrome");
        var described = s.flag("color_description_present_flag");
        var primaries = 2;
        var transfer = 2;
        var matrix = 2;
        if (described) {
            primaries = s.f(8, "color_primaries");
            transfer = s.f(8, "transfer_characteristics");
            matrix = s.f(8, "matrix_coefficients");
        }
        if (mono) {
            var range = s.flag("color_range");
            return new ColorConfig(depth, true, described, primaries, transfer, matrix, range, 1, 1, 0, false);
        }
        boolean range;
        var x = 1;
        var y = 1;
        var position = 0;
        // BT.709 primaries, sRGB transfer and the identity matrix: RGB, which
        // is never subsampled.
        if (primaries == 1 && transfer == 13 && matrix == 0) {
            range = true;
            x = 0;
            y = 0;
        } else {
            range = s.flag("color_range");
            if (profile == 1) {
                x = 0;
                y = 0;
            } else if (profile == 2) {
                if (depth == 12) {
                    x = s.f(1, "subsampling_x");
                    y = x == 1 ? s.f(1, "subsampling_y") : 0;
                } else {
                    y = 0;
                }
            }
            if (x == 1 && y == 1) {
                position = s.f(2, "chroma_sample_position");
            }
        }
        var separate = s.flag("separate_uv_delta_q");
        return new ColorConfig(depth, false, described, primaries, transfer, matrix, range, x, y, position, separate);
    }

    /// `OrderHintBits`: 0 without order hints.
    public int orderHintBits() {
        return enableOrderHint ? orderHintBitsMinus1 + 1 : 0;
    }

    /// `idLen`, the bits of a frame id; 0 without frame ids.
    public int frameIdLength() {
        return frameIdNumbersPresent ? additionalFrameIdLengthMinus1 + deltaFrameIdLengthMinus2 + 3 : 0;
    }

    /// `get_relative_dist(a, b)`: how far order hint `a` is after
    /// `b`, in the window of order hints; 0 without order hints.
    public int relativeDistance(int a, int b) {
        if (!enableOrderHint) {
            return 0;
        }
        var diff = a - b;
        var m = 1 << orderHintBitsMinus1;
        return (diff & (m - 1)) - (diff & m);
    }
}
